package calebxzau.rdi.bgrenderer

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Pixmap
import org.lwjgl.system.MemoryUtil.memAddress
import org.lwjgl.system.MemoryUtil.memCopy
import java.nio.ByteBuffer

/** The ownership states of one bounded frame slot. */
internal enum class FrameSlotState {
    FREE,
    WRITING,
    READY,
    DISPLAYED,
    CLOSED
}

internal data class FrameSlotSnapshot(
    val state: FrameSlotState,
    val readers: Int
)

/**
 * A bounded, reusable frame publication coordinator. The payload is created only when the
 * first writer is requested and is never replaced until the pool is closed.
 */
internal class FrameSlotPool<T : AutoCloseable>(
    private val slotCount: Int,
    private val createPayload: () -> T,
    private val drawPayload: (DrawScope, T) -> Unit
) : AutoCloseable {
    internal class Slot<T : AutoCloseable>(val id: Int, val payload: T) {
        var state = FrameSlotState.FREE
        var readers = 0
    }

    private val lock = Any()
    private var slots: List<Slot<T>> = emptyList()
    private var latestReady: Slot<T>? = null
    private var displayed: Slot<T>? = null
    private var closed = false

    init {
        require(slotCount > 0) { "Frame slot count must be positive" }
    }

    internal val createdSlotCount: Int
        get() = synchronized(lock) { slots.size }

    internal fun snapshots(): List<FrameSlotSnapshot> = synchronized(lock) {
        slots.map { FrameSlotSnapshot(it.state, it.readers) }
    }

    internal fun beginWrite(): FrameWrite<T>? = synchronized(lock) {
        if (closed) return@synchronized null
        if (slots.isEmpty()) {
            val created = ArrayList<Slot<T>>(slotCount)
            try {
                repeat(slotCount) { created += Slot(it, createPayload()) }
            } catch (error: Throwable) {
                created.forEach { it.payload.close() }
                throw error
            }
            slots = created
        }
        val slot = slots.firstOrNull { it.state == FrameSlotState.FREE } ?: return@synchronized null
        slot.state = FrameSlotState.WRITING
        FrameWrite(this, slot)
    }

    private fun publish(write: FrameWrite<T>): BackgroundFramePainter<T>? = synchronized(lock) {
        val slot = write.slot
        check(slot.state == FrameSlotState.WRITING) { "Frame slot is not being written" }
        if (closed) {
            releaseWritingLocked(slot)
            return@synchronized null
        }
        slot.state = FrameSlotState.READY
        latestReady?.let { oldReady ->
            if (oldReady !== slot) releaseReadyLocked(oldReady)
        }
        latestReady = slot
        BackgroundFramePainter(this, drawPayload)
    }

    private fun cancel(write: FrameWrite<T>) = synchronized(lock) {
        if (write.slot.state == FrameSlotState.WRITING) releaseWritingLocked(write.slot)
    }

    private fun acquireDrawLease(): FrameDrawLease<T>? = synchronized(lock) {
        if (closed) return@synchronized null
        latestReady?.let { next ->
            val oldDisplayed = displayed
            latestReady = null
            next.state = FrameSlotState.DISPLAYED
            displayed = next
            if (oldDisplayed != null && oldDisplayed !== next && oldDisplayed.readers == 0) {
                releaseDisplayedLocked(oldDisplayed)
            }
        }
        val slot = displayed ?: return@synchronized null
        slot.readers++
        FrameDrawLease(this, slot, slot.payload)
    }

    private fun releaseDrawLease(lease: FrameDrawLease<T>) = synchronized(lock) {
        val slot = lease.slot
        check(slot.readers > 0) { "Frame draw lease released more than once" }
        slot.readers--
        if (slot.readers == 0 && slot !== displayed && slot.state == FrameSlotState.DISPLAYED) {
            releaseDisplayedLocked(slot)
        } else if (closed && slot.readers == 0 && slot.state == FrameSlotState.DISPLAYED) {
            releaseDisplayedLocked(slot)
        }
    }

    private fun releaseWritingLocked(slot: Slot<T>) {
        slot.state = FrameSlotState.FREE
        if (closed) closeSlotLocked(slot)
    }

    private fun releaseReadyLocked(slot: Slot<T>) {
        if (latestReady === slot) latestReady = null
        slot.state = FrameSlotState.FREE
        if (closed) closeSlotLocked(slot)
    }

    private fun releaseDisplayedLocked(slot: Slot<T>) {
        if (displayed === slot) displayed = null
        slot.state = FrameSlotState.FREE
        if (closed) closeSlotLocked(slot)
    }

    private fun closeSlotLocked(slot: Slot<T>) {
        if (slot.state != FrameSlotState.CLOSED) {
            slot.payload.close()
            slot.state = FrameSlotState.CLOSED
        }
    }

    override fun close() = synchronized(lock) {
        if (closed) return@synchronized
        closed = true
        slots.forEach { slot ->
            when (slot.state) {
                FrameSlotState.FREE,
                FrameSlotState.READY -> {
                    slot.state = FrameSlotState.FREE
                    closeSlotLocked(slot)
                }

                FrameSlotState.DISPLAYED -> if (slot.readers == 0) {
                    closeSlotLocked(slot)
                }

                FrameSlotState.WRITING,
                FrameSlotState.CLOSED -> Unit
            }
        }
        latestReady = null
        displayed = displayed?.takeIf { it.readers > 0 }
    }

    internal fun isClosed(): Boolean = synchronized(lock) { closed }

    internal class FrameWrite<T : AutoCloseable> internal constructor(
        private val pool: FrameSlotPool<T>,
        internal val slot: Slot<T>
    ) : AutoCloseable {
        internal val payload: T get() = slot.payload
        private var completed = false

        internal fun publish(): BackgroundFramePainter<T>? {
            check(!completed) { "Frame write already completed" }
            completed = true
            return pool.publish(this)
        }

        override fun close() {
            if (!completed) {
                completed = true
                pool.cancel(this)
            }
        }
    }

    internal class FrameDrawLease<T : AutoCloseable> internal constructor(
        private val pool: FrameSlotPool<T>,
        internal val slot: Slot<T>,
        internal val payload: T
    ) : AutoCloseable {
        private var released = false

        override fun close() {
            check(!released) { "Frame draw lease released more than once" }
            released = true
            pool.releaseDrawLease(this)
        }
    }

    internal fun drawLease(): FrameDrawLease<T>? = acquireDrawLease()

    internal fun write(): FrameWrite<T>? = beginWrite()
}

/** A lightweight token; every token draws whichever frame is current at draw time. */
internal class BackgroundFramePainter<T : AutoCloseable>(
    private val pool: FrameSlotPool<T>,
    private val drawPayload: (DrawScope, T) -> Unit,
    private val frameWidth: Int = RENDER_WIDTH,
    private val frameHeight: Int = RENDER_HEIGHT
) : Painter() {
    override val intrinsicSize: Size = Size(frameWidth.toFloat(), frameHeight.toFloat())

    override fun DrawScope.onDraw() {
        val lease = pool.drawLease() ?: return
        try {
            drawPayload(this, lease.payload)
        } finally {
            lease.close()
        }
    }
}

internal class SkiaFrameSlot private constructor(
    private val bitmap: Bitmap,
    private val pixmap: Pixmap,
    val imageBitmap: ImageBitmap
) : AutoCloseable {
    companion object {
        internal fun create(width: Int, height: Int): SkiaFrameSlot {
            val imageInfo = ImageInfo.makeS32(width, height, ColorAlphaType.PREMUL)
            val bitmap = Bitmap()
            var pixmap: Pixmap? = null
            try {
                check(bitmap.allocPixels(imageInfo)) { "Unable to allocate background frame bitmap" }
                val readyPixmap = requireNotNull(bitmap.peekPixels()) { "Unable to access background frame pixels" }
                pixmap = readyPixmap
                val imageBitmap = bitmap.asComposeImageBitmap()
                return SkiaFrameSlot(bitmap, readyPixmap, imageBitmap)
            } catch (error: Throwable) {
                pixmap?.close()
                bitmap.close()
                throw error
            }
        }
    }

    private val pixelsAddress = pixmap.addr
    private val rowBytes = pixmap.rowBytes

    fun copyFrom(source: ByteBuffer, width: Int, height: Int) {
        val sourceAddress = memAddress(source)
        val sourceRowBytes = width * 4
        repeat(height) { targetY ->
            val sourceY = height - targetY - 1
            memCopy(
                sourceAddress + sourceY.toLong() * sourceRowBytes,
                pixelsAddress + targetY.toLong() * rowBytes,
                sourceRowBytes.toLong()
            )
        }
        bitmap.notifyPixelsChanged()
    }

    override fun close() {
        pixmap.close()
        bitmap.close()
    }
}

internal fun createBackgroundFramePool(): FrameSlotPool<SkiaFrameSlot> =
    FrameSlotPool(3, { SkiaFrameSlot.create(RENDER_WIDTH, RENDER_HEIGHT) }) { scope, slot ->
        scope.drawImage(
            slot.imageBitmap,
            dstSize = IntSize(scope.size.width.toInt(), scope.size.height.toInt())
        )
    }
