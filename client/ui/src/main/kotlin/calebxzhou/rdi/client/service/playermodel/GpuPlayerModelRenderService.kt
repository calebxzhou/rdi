package calebxzhou.rdi.client.service.playermodel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.joml.Matrix4f
import org.joml.Vector3f
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.ImageInfo
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR
import org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR
import org.lwjgl.glfw.GLFW.GLFW_FALSE
import org.lwjgl.glfw.GLFW.GLFW_OPENGL_CORE_PROFILE
import org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE
import org.lwjgl.glfw.GLFW.GLFW_VISIBLE
import org.lwjgl.glfw.GLFW.glfwCreateWindow
import org.lwjgl.glfw.GLFW.glfwDefaultWindowHints
import org.lwjgl.glfw.GLFW.glfwDestroyWindow
import org.lwjgl.glfw.GLFW.glfwInit
import org.lwjgl.glfw.GLFW.glfwMakeContextCurrent
import org.lwjgl.glfw.GLFW.glfwTerminate
import org.lwjgl.glfw.GLFW.glfwWindowHint
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12.GL_BGRA
import org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.GL_TEXTURE1
import org.lwjgl.opengl.GL13.glActiveTexture
import org.lwjgl.opengl.GL14.GL_DEPTH_COMPONENT24
import org.lwjgl.opengl.GL14.glBlendFuncSeparate
import org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL15.GL_READ_ONLY
import org.lwjgl.opengl.GL15.GL_STATIC_DRAW
import org.lwjgl.opengl.GL15.GL_STREAM_READ
import org.lwjgl.opengl.GL15.glBindBuffer
import org.lwjgl.opengl.GL15.glBufferData
import org.lwjgl.opengl.GL15.glDeleteBuffers
import org.lwjgl.opengl.GL15.glGenBuffers
import org.lwjgl.opengl.GL15.glMapBuffer
import org.lwjgl.opengl.GL15.glUnmapBuffer
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER
import org.lwjgl.opengl.GL30.*
import org.lwjgl.system.MemoryUtil.NULL
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

internal const val DEFAULT_GPU_ORBIT_YAW_DEG = 180f

internal data class PlayerTexturePixels(
    val width: Int,
    val height: Int,
    val rgba: ByteArray,
    val slim: Boolean = false
) {
    companion object {
        fun skin(image: ImageBitmap, slim: Boolean?): PlayerTexturePixels {
            val rgba = image.toRgba()
            return PlayerTexturePixels(
                width = image.width,
                height = image.height,
                rgba = rgba,
                slim = slim ?: detectSlimSkin(image.width, image.height, rgba)
            )
        }

        fun cape(image: ImageBitmap): PlayerTexturePixels = PlayerTexturePixels(
            width = image.width,
            height = image.height,
            rgba = image.toRgba()
        )
    }
}

private fun ImageBitmap.toRgba(): ByteArray {
    val pixelMap = toPixelMap()
    val rgba = ByteArray(width * height * 4)
    var offset = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            val color = pixelMap[x, y]
            rgba[offset++] = (color.red * 255).roundToInt().toByte()
            rgba[offset++] = (color.green * 255).roundToInt().toByte()
            rgba[offset++] = (color.blue * 255).roundToInt().toByte()
            rgba[offset++] = (color.alpha * 255).roundToInt().toByte()
        }
    }
    return rgba
}

internal fun detectSlimSkin(width: Int, height: Int, rgba: ByteArray): Boolean {
    if (width < 64 || height < 64) return false
    var transparent = 0
    var total = 0
    fun checkAlpha(x: Int, y: Int) {
        total++
        val alpha = rgba[(y * width + x) * 4 + 3].toInt() and 0xFF
        if (alpha < 16) transparent++
    }
    for (y in 20..31) checkAlpha(54, y)
    for (y in 52..63) checkAlpha(46, y)
    return transparent >= total * 0.75f
}

internal data class PlayerModelRenderSize(val width: Int, val height: Int)

internal fun constrainedPlayerModelRenderSize(
    width: Int,
    height: Int,
    maxRenderSide: Int
): PlayerModelRenderSize {
    val longestSide = max(width, height)
    val limit = maxRenderSide.coerceAtLeast(1)
    if (longestSide <= limit) return PlayerModelRenderSize(width, height)
    val scale = limit.toFloat() / longestSide
    return PlayerModelRenderSize(
        width = (width * scale).roundToInt().coerceAtLeast(1),
        height = (height * scale).roundToInt().coerceAtLeast(1)
    )
}

internal data class RenderRequest(
    val width: Int = 0,
    val height: Int = 0,
    val skin: PlayerTexturePixels? = null,
    val cape: PlayerTexturePixels? = null,
    val autoRotate: Boolean = true,
    val animateWalk: Boolean = true,
    val showOuterLayer: Boolean = true,
    val maxRenderSide: Int = 256
)

internal class GpuPlayerModelController internal constructor(
    private val model: PlayerModelState
) : AutoCloseable {
    val frame = model.frame
    val failure = GpuPlayerModelRenderService.failure

    fun update(
        skin: PlayerTexturePixels?,
        cape: PlayerTexturePixels?,
        autoRotate: Boolean,
        animateWalk: Boolean,
        showOuterLayer: Boolean,
        maxRenderSide: Int
    ) {
        val previous = model.request.getAndUpdate {
            it.copy(
                skin = skin,
                cape = cape,
                autoRotate = autoRotate,
                animateWalk = animateWalk,
                showOuterLayer = showOuterLayer,
                maxRenderSide = maxRenderSide.coerceAtLeast(1)
            )
        }
        if (previous.skin !== skin || previous.cape !== cape) model.clearFrame()
        model.dirty.set(true)
        GpuPlayerModelRenderService.requestFrame()
    }

    fun resize(width: Int, height: Int) {
        val previous = model.request.getAndUpdate {
            if (it.width == width && it.height == height) it else it.copy(width = width, height = height)
        }
        if (previous.width != width || previous.height != height) {
            model.dirty.set(true)
            GpuPlayerModelRenderService.requestFrame()
        }
    }

    fun startDragging() {
        synchronized(model.viewLock) {
            model.dragging = true
        }
    }

    fun stopDragging() {
        synchronized(model.viewLock) {
            model.dragging = false
        }
    }

    fun drag(delta: Offset) {
        synchronized(model.viewLock) {
            model.yawDeg += delta.x * 0.65f
            model.pitchDeg = (model.pitchDeg - delta.y * 0.35f).coerceIn(-80f, 80f)
        }
        model.dirty.set(true)
        GpuPlayerModelRenderService.requestFrame()
    }

    fun resetView() {
        synchronized(model.viewLock) {
            model.yawDeg = DEFAULT_GPU_ORBIT_YAW_DEG
            model.pitchDeg = 0f
            model.dragging = false
        }
        model.dirty.set(true)
        GpuPlayerModelRenderService.requestFrame()
    }

    override fun close() {
        GpuPlayerModelRenderService.unregister(model)
    }
}

internal class PlayerModelState(val id: Long) {
    @Volatile
    var active = true
    val request = AtomicReference(RenderRequest())
    val dirty = AtomicBoolean(true)
    private val _frame = MutableStateFlow<ImageBitmap?>(null)
    val frame = _frame.asStateFlow()
    val viewLock = Any()
    var yawDeg = DEFAULT_GPU_ORBIT_YAW_DEG
    var pitchDeg = 0f
    var dragging = false
    val animationStartNanos = System.nanoTime()
    val gpu = PlayerModelGpuState()
    val readback = PboReadback()
    val bitmapBuffer = FrameBitmapBuffer()

    fun publish(bitmap: ImageBitmap) {
        _frame.value = bitmap
    }

    fun clearFrame() {
        _frame.value = null
    }
}

internal object GpuPlayerModelRenderService : AutoCloseable {
    private val logger = KotlinLogging.logger {}
    private val nextId = AtomicLong()
    private val models = ConcurrentHashMap<Long, PlayerModelState>()
    private val retiredModels = ConcurrentLinkedQueue<PlayerModelState>()
    private val threadLock = Any()
    private val _failure = MutableStateFlow<String?>(null)
    val failure = _failure.asStateFlow()
    @Volatile
    private var running = true
    @Volatile
    private var terminalFailure = false
    @Volatile
    private var renderThread: Thread? = null

    init {
        Runtime.getRuntime().addShutdownHook(Thread(::close, "rdi-player-model-shutdown"))
    }

    fun register(): GpuPlayerModelController {
        val model = PlayerModelState(nextId.incrementAndGet())
        models[model.id] = model
        requestFrame()
        return GpuPlayerModelController(model)
    }

    fun unregister(model: PlayerModelState) {
        model.active = false
        if (models.remove(model.id, model)) retiredModels += model
        wakeRenderer()
    }

    fun requestFrame() {
        ensureRenderThread()
        wakeRenderer()
    }

    private fun wakeRenderer() {
        renderThread?.let(LockSupport::unpark)
    }

    private fun ensureRenderThread() {
        if (!running || terminalFailure || renderThread?.isAlive == true) return
        synchronized(threadLock) {
            if (!running || terminalFailure || renderThread?.isAlive == true) return
            renderThread = Thread(::renderLoop, "rdi-player-model-renderer").apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun renderLoop() {
        var window = NULL
        var glfwReady = false
        try {
            check(glfwInit()) { "Failed to initialize GLFW" }
            glfwReady = true
            glfwDefaultWindowHints()
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4)
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 5)
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
            window = glfwCreateWindow(1, 1, "rdi-player-model", NULL, NULL)
            check(window != NULL) { "Failed to create the player-model OpenGL context" }
            glfwMakeContextCurrent(window)
            GL.createCapabilities()

            val renderer = PlayerModelGlRenderer()
            val framebuffers = MsaaFramebufferPool()
            try {
                renderer.initialize()
                var lastFrameNanos = 0L
                var nextFrameNanos = 0L
                while (running) {
                    releaseRetiredModels(renderer)
                    if (models.isEmpty()) {
                        lastFrameNanos = 0L
                        nextFrameNanos = 0L
                        LockSupport.park()
                        continue
                    }

                    val activeModels = models.values.filter { it.active }
                    val hasAnimation = activeModels.any { model ->
                        model.request.get().let { it.skin != null && (it.autoRotate || it.animateWalk) }
                    }
                    val hasDirtyModel = activeModels.any { it.dirty.get() }
                    if (!hasAnimation && !hasDirtyModel) {
                        lastFrameNanos = 0L
                        nextFrameNanos = 0L
                        LockSupport.park()
                        continue
                    }

                    val now = System.nanoTime()
                    if (hasAnimation && nextFrameNanos > now) {
                        LockSupport.parkNanos(nextFrameNanos - now)
                        continue
                    }
                    val deltaSeconds = if (lastFrameNanos == 0L) 0f else {
                        ((now - lastFrameNanos) / 1_000_000_000f).coerceAtMost(0.1f)
                    }
                    lastFrameNanos = now

                    activeModels.forEach { model ->
                        val request = model.request.get()
                        val shouldRender = model.dirty.getAndSet(false) ||
                            (request.skin != null && (request.autoRotate || request.animateWalk))
                        if (shouldRender) renderModel(renderer, framebuffers, model, request, now, deltaSeconds)
                    }

                    if (hasAnimation) {
                        nextFrameNanos = now + FRAME_INTERVAL_NANOS
                    } else {
                        nextFrameNanos = 0L
                    }
                }
            } finally {
                models.values.forEach { releaseModel(renderer, it) }
                releaseRetiredModels(renderer)
                framebuffers.close()
                renderer.close()
            }
        } catch (error: Throwable) {
            if (running) {
                terminalFailure = true
                _failure.value = "玩家模型渲染不可用"
                logger.error(error) { "GPU player model renderer failed" }
            }
        } finally {
            GL.setCapabilities(null)
            if (window != NULL) {
                glfwMakeContextCurrent(NULL)
                glfwDestroyWindow(window)
            }
            if (glfwReady) glfwTerminate()
            synchronized(threadLock) {
                if (renderThread === Thread.currentThread()) renderThread = null
            }
        }
    }

    private fun renderModel(
        renderer: PlayerModelGlRenderer,
        framebuffers: MsaaFramebufferPool,
        model: PlayerModelState,
        request: RenderRequest,
        now: Long,
        deltaSeconds: Float
    ) {
        val skin = request.skin ?: return
        if (request.width <= 0 || request.height <= 0) return
        val size = constrainedPlayerModelRenderSize(request.width, request.height, request.maxRenderSide)
        val framebuffer = framebuffers.get(size)
        renderer.updateTextures(model.gpu, skin, request.cape)
        val (yaw, pitch) = synchronized(model.viewLock) {
            if (request.autoRotate && !model.dragging) model.yawDeg -= ROTATION_SPEED_DEG_PER_SECOND * deltaSeconds
            model.yawDeg to model.pitchDeg
        }
        val walkPhaseDeg = if (request.animateWalk) {
            ((now - model.animationStartNanos) / 1_000_000_000f * 300f) % 360f
        } else {
            0f
        }

        framebuffer.bindForRender()
        renderer.render(
            model = model.gpu,
            width = size.width,
            height = size.height,
            yawDeg = yaw,
            pitchDeg = pitch,
            walkPhaseDeg = walkPhaseDeg,
            showOuterLayer = request.showOuterLayer
        )
        framebuffer.resolveForRead()
        val published = model.readback.capture(size.width, size.height) { pixels ->
            model.publish(model.bitmapBuffer.update(pixels, size.width, size.height))
        }
        if (!published) model.dirty.set(true)
    }

    private fun releaseRetiredModels(renderer: PlayerModelGlRenderer) {
        while (true) {
            val model = retiredModels.poll() ?: return
            releaseModel(renderer, model)
        }
    }

    private fun releaseModel(renderer: PlayerModelGlRenderer, model: PlayerModelState) {
        model.readback.close()
        model.bitmapBuffer.close()
        renderer.release(model.gpu)
    }

    override fun close() {
        if (!running) return
        running = false
        wakeRenderer()
        renderThread?.takeIf { it !== Thread.currentThread() }?.join()
    }
}

private class MsaaFramebufferPool : AutoCloseable {
    private val framebuffers = LinkedHashMap<PlayerModelRenderSize, OffscreenMsaaFramebuffer>(
        MAX_FRAMEBUFFER_CACHE_SIZE,
        0.75f,
        true
    )

    fun get(size: PlayerModelRenderSize): OffscreenMsaaFramebuffer {
        framebuffers[size]?.let { return it }
        if (framebuffers.size >= MAX_FRAMEBUFFER_CACHE_SIZE) {
            val iterator = framebuffers.entries.iterator()
            if (iterator.hasNext()) {
                iterator.next().also { it.value.close() }
                iterator.remove()
            }
        }
        return OffscreenMsaaFramebuffer(size.width, size.height).also { framebuffers[size] = it }
    }

    override fun close() {
        framebuffers.values.forEach(OffscreenMsaaFramebuffer::close)
        framebuffers.clear()
    }
}

private class OffscreenMsaaFramebuffer(
    private val width: Int,
    private val height: Int
) : AutoCloseable {
    private val multisampleFramebuffer = glGenFramebuffers()
    private val multisampleColor = glGenRenderbuffers()
    private val multisampleDepth = glGenRenderbuffers()
    private val resolveFramebuffer = glGenFramebuffers()
    private val resolveTexture = glGenTextures()

    init {
        glBindFramebuffer(GL_FRAMEBUFFER, multisampleFramebuffer)
        glBindRenderbuffer(GL_RENDERBUFFER, multisampleColor)
        glRenderbufferStorageMultisample(GL_RENDERBUFFER, MSAA_SAMPLES, GL_RGBA8, width, height)
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, multisampleColor)
        glBindRenderbuffer(GL_RENDERBUFFER, multisampleDepth)
        glRenderbufferStorageMultisample(GL_RENDERBUFFER, MSAA_SAMPLES, GL_DEPTH_COMPONENT24, width, height)
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, multisampleDepth)
        check(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE) {
            "Player-model MSAA framebuffer is incomplete"
        }

        glBindFramebuffer(GL_FRAMEBUFFER, resolveFramebuffer)
        glBindTexture(GL_TEXTURE_2D, resolveTexture)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, resolveTexture, 0)
        check(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE) {
            "Player-model resolve framebuffer is incomplete"
        }
    }

    fun bindForRender() {
        glBindFramebuffer(GL_FRAMEBUFFER, multisampleFramebuffer)
    }

    fun resolveForRead() {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, multisampleFramebuffer)
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, resolveFramebuffer)
        glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_NEAREST)
        glBindFramebuffer(GL_READ_FRAMEBUFFER, resolveFramebuffer)
    }

    override fun close() {
        glDeleteTextures(resolveTexture)
        glDeleteRenderbuffers(multisampleDepth)
        glDeleteRenderbuffers(multisampleColor)
        glDeleteFramebuffers(resolveFramebuffer)
        glDeleteFramebuffers(multisampleFramebuffer)
    }
}

internal class PboReadback : AutoCloseable {
    private val buffers = IntArray(2)
    private var byteSize = 0
    private var writeIndex = 0
    private var queuedFrames = 0

    fun capture(width: Int, height: Int, consume: (ByteBuffer) -> Unit): Boolean {
        ensureCapacity(width * height * 4)
        glBindBuffer(GL_PIXEL_PACK_BUFFER, buffers[writeIndex])
        glReadPixels(0, 0, width, height, GL_BGRA, GL_UNSIGNED_BYTE, 0L)

        var published = false
        if (queuedFrames > 0) {
            val readIndex = (writeIndex + 1) % buffers.size
            glBindBuffer(GL_PIXEL_PACK_BUFFER, buffers[readIndex])
            val mapped = glMapBuffer(GL_PIXEL_PACK_BUFFER, GL_READ_ONLY, byteSize.toLong(), null)
            mapped?.let {
                try {
                    it.position(0)
                    consume(it)
                    published = true
                } finally {
                    glUnmapBuffer(GL_PIXEL_PACK_BUFFER)
                }
            }
        } else {
            queuedFrames++
        }
        glBindBuffer(GL_PIXEL_PACK_BUFFER, 0)
        writeIndex = (writeIndex + 1) % buffers.size
        return published
    }

    private fun ensureCapacity(requiredBytes: Int) {
        if (byteSize == requiredBytes) return
        close()
        byteSize = requiredBytes
        repeat(buffers.size) { index ->
            buffers[index] = glGenBuffers()
            glBindBuffer(GL_PIXEL_PACK_BUFFER, buffers[index])
            glBufferData(GL_PIXEL_PACK_BUFFER, byteSize.toLong(), GL_STREAM_READ)
        }
        glBindBuffer(GL_PIXEL_PACK_BUFFER, 0)
        writeIndex = 0
        queuedFrames = 0
    }

    override fun close() {
        buffers.forEach { if (it != 0) glDeleteBuffers(it) }
        buffers.fill(0)
        byteSize = 0
        writeIndex = 0
        queuedFrames = 0
    }
}

internal class FrameBitmapBuffer : AutoCloseable {
    private var width = 0
    private var height = 0
    private var pixels = ByteArray(0)
    private var imageInfo = ImageInfo.makeS32(1, 1, ColorAlphaType.PREMUL)

    fun update(source: ByteBuffer, width: Int, height: Int): ImageBitmap {
        ensureSize(width, height)
        val rowBytes = width * 4
        repeat(height) { targetY ->
            source.position((height - targetY - 1) * rowBytes)
            source.get(pixels, targetY * rowBytes, rowBytes)
        }
        return SkiaImage.makeRaster(imageInfo, pixels, rowBytes).toComposeImageBitmap()
    }

    private fun ensureSize(width: Int, height: Int) {
        if (this.width == width && this.height == height) return
        this.width = width
        this.height = height
        pixels = ByteArray(width * height * 4)
        imageInfo = ImageInfo.makeS32(width, height, ColorAlphaType.PREMUL)
    }

    override fun close() {
        pixels = ByteArray(0)
        width = 0
        height = 0
    }
}

internal class PlayerModelGpuState {
    var skin: PlayerTexturePixels? = null
    var cape: PlayerTexturePixels? = null
    var skinTexture = 0
    var capeTexture = 0
}

private data class PlayerMeshKey(
    val slim: Boolean,
    val legacy: Boolean,
    val showOuterLayer: Boolean,
    val hasCape: Boolean
)

private data class GpuPlayerMesh(
    val vertexArray: Int,
    val vertexBuffer: Int,
    val vertexCount: Int,
    val armPivotX: Float
)

private class PlayerModelGlRenderer : AutoCloseable {
    private var program = 0
    private var projectionLocation = -1
    private var viewLocation = -1
    private var skinLocation = -1
    private var capeLocation = -1
    private val boneLocations = IntArray(ModelPart.entries.size)
    private val matrixBuffer = BufferUtils.createFloatBuffer(16)
    private val meshes = HashMap<PlayerMeshKey, GpuPlayerMesh>()
    private val projection = Matrix4f()
    private val view = Matrix4f()
    private val eye = Vector3f()
    private val target = Vector3f(0f, 16f, 0f)
    private val up = Vector3f(0f, 1f, 0f)
    private val bones = Array(ModelPart.entries.size) { Matrix4f() }

    fun initialize() {
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        projectionLocation = glGetUniformLocation(program, "uProjection")
        viewLocation = glGetUniformLocation(program, "uView")
        skinLocation = glGetUniformLocation(program, "uSkin")
        capeLocation = glGetUniformLocation(program, "uCape")
        ModelPart.entries.forEachIndexed { index, _ ->
            boneLocations[index] = glGetUniformLocation(program, "uBones[$index]")
        }
        glEnable(GL_DEPTH_TEST)
        glDepthFunc(GL_LEQUAL)
        glEnable(GL_BLEND)
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
    }

    fun updateTextures(model: PlayerModelGpuState, skin: PlayerTexturePixels, cape: PlayerTexturePixels?) {
        if (model.skin !== skin) {
            if (model.skinTexture != 0) glDeleteTextures(model.skinTexture)
            model.skin = skin
            model.skinTexture = uploadTexture(skin)
        }
        if (model.cape !== cape) {
            if (model.capeTexture != 0) glDeleteTextures(model.capeTexture)
            model.cape = cape
            model.capeTexture = cape?.let(::uploadTexture) ?: 0
        }
    }

    private fun uploadTexture(texture: PlayerTexturePixels): Int {
        val id = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, id)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
        val pixels = BufferUtils.createByteBuffer(texture.rgba.size).put(texture.rgba)
        pixels.flip()
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, texture.width, texture.height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels)
        return id
    }

    private fun mesh(key: PlayerMeshKey): GpuPlayerMesh = meshes.getOrPut(key) {
        val mesh = buildPlayerMesh(key.slim, key.legacy, key.showOuterLayer, key.hasCape)
        val vertexArray = glGenVertexArrays()
        val vertexBuffer = glGenBuffers()
        glBindVertexArray(vertexArray)
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer)
        val vertexData = BufferUtils.createFloatBuffer(mesh.vertices.size).put(mesh.vertices)
        vertexData.flip()
        glBufferData(GL_ARRAY_BUFFER, vertexData, GL_STATIC_DRAW)
        val stride = VERTEX_FLOATS * Float.SIZE_BYTES
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3L * Float.SIZE_BYTES)
        glVertexAttribPointer(2, 3, GL_FLOAT, false, stride, 5L * Float.SIZE_BYTES)
        glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, 8L * Float.SIZE_BYTES)
        glVertexAttribPointer(4, 1, GL_FLOAT, false, stride, 9L * Float.SIZE_BYTES)
        glVertexAttribPointer(5, 1, GL_FLOAT, false, stride, 10L * Float.SIZE_BYTES)
        repeat(6) { glEnableVertexAttribArray(it) }
        glBindVertexArray(0)
        GpuPlayerMesh(vertexArray, vertexBuffer, mesh.vertices.size / VERTEX_FLOATS, mesh.armPivotX)
    }

    fun render(
        model: PlayerModelGpuState,
        width: Int,
        height: Int,
        yawDeg: Float,
        pitchDeg: Float,
        walkPhaseDeg: Float,
        showOuterLayer: Boolean
    ) {
        val skin = model.skin ?: return
        if (model.skinTexture == 0) return
        val mesh = mesh(PlayerMeshKey(skin.slim, skin.height <= 32, showOuterLayer, model.cape != null))
        glViewport(0, 0, width, height)
        glClearColor(0f, 0f, 0f, 0f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

        projection.identity().perspective(Math.toRadians(35.0).toFloat(), width.toFloat() / height, 0.1f, 100f)
        val yaw = Math.toRadians(yawDeg.toDouble()).toFloat()
        val pitch = Math.toRadians(pitchDeg.coerceIn(-85f, 85f).toDouble()).toFloat()
        val horizontalRadius = cos(pitch) * CAMERA_RADIUS
        eye.set(
            sin(yaw) * horizontalRadius,
            16f + sin(pitch) * CAMERA_RADIUS,
            -cos(yaw) * horizontalRadius
        )
        view.identity().lookAt(eye, target, up)
        val walk = sin(Math.toRadians(walkPhaseDeg.toDouble()).toFloat()) * Math.toRadians(20.0).toFloat()
        val capeSwing = Math.toRadians(8.0).toFloat() +
            sin(Math.toRadians(walkPhaseDeg.toDouble()).toFloat() * 0.5f) * Math.toRadians(4.0).toFloat()
        bones[ModelPart.HEAD.ordinal].identity()
        bones[ModelPart.BODY.ordinal].identity()
        pivotRotation(bones[ModelPart.LEFT_ARM.ordinal], -mesh.armPivotX, 24f, 0f, walk)
        pivotRotation(bones[ModelPart.RIGHT_ARM.ordinal], mesh.armPivotX, 24f, 0f, -walk)
        pivotRotation(bones[ModelPart.LEFT_LEG.ordinal], -1.9f, 12f, -0.1f, -walk)
        pivotRotation(bones[ModelPart.RIGHT_LEG.ordinal], 1.9f, 12f, -0.1f, walk)
        pivotRotation(bones[ModelPart.CAPE.ordinal], 0f, 24f, -2.6f, capeSwing)

        glUseProgram(program)
        uploadMatrix(projectionLocation, projection)
        uploadMatrix(viewLocation, view)
        bones.forEachIndexed { index, matrix -> uploadMatrix(boneLocations[index], matrix) }
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, model.skinTexture)
        glUniform1i(skinLocation, 0)
        glActiveTexture(GL_TEXTURE1)
        glBindTexture(GL_TEXTURE_2D, model.capeTexture)
        glUniform1i(capeLocation, 1)
        glBindVertexArray(mesh.vertexArray)
        glDrawArrays(GL_TRIANGLES, 0, mesh.vertexCount)
        glBindVertexArray(0)
        glUseProgram(0)
    }

    fun release(model: PlayerModelGpuState) {
        if (model.skinTexture != 0) glDeleteTextures(model.skinTexture)
        if (model.capeTexture != 0) glDeleteTextures(model.capeTexture)
        model.skinTexture = 0
        model.capeTexture = 0
        model.skin = null
        model.cape = null
    }

    override fun close() {
        meshes.values.forEach { mesh ->
            glDeleteBuffers(mesh.vertexBuffer)
            glDeleteVertexArrays(mesh.vertexArray)
        }
        meshes.clear()
        if (program != 0) glDeleteProgram(program)
        program = 0
    }

    private fun uploadMatrix(location: Int, matrix: Matrix4f) {
        matrixBuffer.clear()
        matrix.get(matrixBuffer)
        matrixBuffer.position(0)
        glUniformMatrix4fv(location, false, matrixBuffer)
    }
}

private fun pivotRotation(matrix: Matrix4f, x: Float, y: Float, z: Float, angle: Float) {
    matrix.identity().translate(x, y, z).rotateX(angle).translate(-x, -y, -z)
}

private fun createProgram(vertexSource: String, fragmentSource: String): Int {
    val vertexShader = compileShader(GL_VERTEX_SHADER, vertexSource)
    val fragmentShader = compileShader(GL_FRAGMENT_SHADER, fragmentSource)
    val program = glCreateProgram()
    glAttachShader(program, vertexShader)
    glAttachShader(program, fragmentShader)
    glLinkProgram(program)
    val linked = glGetProgrami(program, GL_LINK_STATUS)
    val log = glGetProgramInfoLog(program)
    glDeleteShader(vertexShader)
    glDeleteShader(fragmentShader)
    check(linked != 0) { "Player model shader link failed: $log" }
    return program
}

private fun compileShader(type: Int, source: String): Int {
    val shader = glCreateShader(type)
    glShaderSource(shader, source)
    glCompileShader(shader)
    check(glGetShaderi(shader, GL_COMPILE_STATUS) != 0) {
        "Player model shader compile failed: ${glGetShaderInfoLog(shader)}"
    }
    return shader
}

internal data class PlayerMesh(
    val vertices: FloatArray,
    val armPivotX: Float,
    val skinVertexCount: Int,
    val capeVertexCount: Int
)

private data class UvRect(
    val u: Float,
    val v: Float,
    val width: Float,
    val height: Float,
    val flipX: Boolean = false
)

private data class CuboidUv(
    val top: UvRect,
    val bottom: UvRect,
    val left: UvRect,
    val front: UvRect,
    val right: UvRect,
    val back: UvRect
)

private data class MeshFace(
    val v0: Vector3f,
    val v1: Vector3f,
    val v2: Vector3f,
    val v3: Vector3f,
    val normal: Vector3f,
    val uv: UvRect,
    val shade: Float,
    val part: ModelPart,
    val texture: ModelTexture = ModelTexture.SKIN
)

private enum class ModelPart {
    HEAD,
    BODY,
    LEFT_ARM,
    RIGHT_ARM,
    LEFT_LEG,
    RIGHT_LEG,
    CAPE
}

private enum class ModelTexture { SKIN, CAPE }

internal fun buildPlayerMesh(
    slim: Boolean,
    legacy: Boolean,
    showOuterLayer: Boolean,
    hasCape: Boolean
): PlayerMesh {
    val faces = ArrayList<MeshFace>(96)
    val armWidth = if (slim) 3 else 4
    val armPivotX = if (slim) 5.5f else 6f
    addPlayerLayers(faces, armWidth, armPivotX, legacy, showOuterLayer, hasCape)
    val vertices = ArrayList<Float>(faces.size * 6 * VERTEX_FLOATS)
    faces.forEach { appendFace(vertices, it) }
    return PlayerMesh(
        vertices = vertices.toFloatArray(),
        armPivotX = armPivotX,
        skinVertexCount = faces.count { it.texture == ModelTexture.SKIN } * 6,
        capeVertexCount = faces.count { it.texture == ModelTexture.CAPE } * 6
    )
}

private fun addPlayerLayers(
    faces: MutableList<MeshFace>,
    armWidth: Int,
    armPivotX: Float,
    legacy: Boolean,
    showOuterLayer: Boolean,
    hasCape: Boolean
) {
    val armWidthF = armWidth.toFloat()
    addCuboid(faces, Vector3f(0f, 28f, 0f), Vector3f(8f, 8f, 8f), cubeUv(0, 0, 8, 8, 8), ModelPart.HEAD)
    addCuboid(faces, Vector3f(0f, 18f, 0f), Vector3f(8f, 12f, 4f), cubeUv(16, 16, 8, 12, 4), ModelPart.BODY)
    addCuboid(faces, Vector3f(-armPivotX, 18f, 0f), Vector3f(armWidthF, 12f, 4f), cubeUv(40, 16, armWidth, 12, 4), ModelPart.LEFT_ARM)
    addCuboid(
        faces,
        Vector3f(armPivotX, 18f, 0f),
        Vector3f(armWidthF, 12f, 4f),
        if (legacy) cubeUv(40, 16, armWidth, 12, 4) else cubeUv(32, 48, armWidth, 12, 4),
        ModelPart.RIGHT_ARM
    )
    addCuboid(faces, Vector3f(-1.9f, 6f, -0.1f), Vector3f(4f, 12f, 4f), cubeUv(0, 16, 4, 12, 4), ModelPart.LEFT_LEG)
    addCuboid(
        faces,
        Vector3f(1.9f, 6f, -0.1f),
        Vector3f(4f, 12f, 4f),
        if (legacy) cubeUv(0, 16, 4, 12, 4) else cubeUv(16, 48, 4, 12, 4),
        ModelPart.RIGHT_LEG
    )

    if (showOuterLayer) {
        addCuboid(faces, Vector3f(0f, 28f, 0f), Vector3f(8f, 8f, 8f), cubeUv(32, 0, 8, 8, 8), ModelPart.HEAD, 0.5f)
        if (!legacy) {
            addCuboid(faces, Vector3f(0f, 18f, 0f), Vector3f(8f, 12f, 4f), cubeUv(16, 32, 8, 12, 4), ModelPart.BODY, 0.25f)
            addCuboid(faces, Vector3f(-armPivotX, 18f, 0f), Vector3f(armWidthF, 12f, 4f), cubeUv(40, 32, armWidth, 12, 4), ModelPart.LEFT_ARM, 0.25f)
            addCuboid(faces, Vector3f(armPivotX, 18f, 0f), Vector3f(armWidthF, 12f, 4f), cubeUv(48, 48, armWidth, 12, 4), ModelPart.RIGHT_ARM, 0.25f)
            addCuboid(faces, Vector3f(-1.9f, 6f, -0.1f), Vector3f(4f, 12f, 4f), cubeUv(0, 32, 4, 12, 4), ModelPart.LEFT_LEG, 0.25f)
            addCuboid(faces, Vector3f(1.9f, 6f, -0.1f), Vector3f(4f, 12f, 4f), cubeUv(0, 48, 4, 12, 4), ModelPart.RIGHT_LEG, 0.25f)
        }
    }
    if (hasCape) {
        addCuboid(
            faces,
            Vector3f(0f, 16f, -2.6f),
            Vector3f(10f, 16f, 1f),
            cubeUv(0, 0, 10, 16, 1),
            ModelPart.CAPE,
            texture = ModelTexture.CAPE
        )
    }
}

private fun cubeUv(u: Int, v: Int, width: Int, height: Int, depth: Int) = CuboidUv(
    top = uv(u + depth, v, width, depth),
    bottom = uv(u + width + depth, v, width, depth),
    left = uv(u, v + depth, depth, height),
    front = uv(u + depth, v + depth, width, height),
    right = uv(u + width + depth, v + depth, depth, height),
    back = uv(u + width + depth * 2, v + depth, width, height, flipX = true)
)

private fun uv(u: Int, v: Int, width: Int, height: Int, flipX: Boolean = false) =
    UvRect(u.toFloat(), v.toFloat(), width.toFloat(), height.toFloat(), flipX)

private fun addCuboid(
    faces: MutableList<MeshFace>,
    center: Vector3f,
    size: Vector3f,
    uv: CuboidUv,
    part: ModelPart,
    inflate: Float = 0f,
    texture: ModelTexture = ModelTexture.SKIN
) {
    val halfX = size.x * 0.5f + inflate
    val halfY = size.y * 0.5f + inflate
    val halfZ = size.z * 0.5f + inflate
    val left = center.x - halfX
    val right = center.x + halfX
    val down = center.y - halfY
    val up = center.y + halfY
    val back = center.z - halfZ
    val front = center.z + halfZ
    faces += MeshFace(Vector3f(left, down, front), Vector3f(right, down, front), Vector3f(right, up, front), Vector3f(left, up, front), Vector3f(0f, 0f, 1f), uv.front, 1f, part, texture)
    faces += MeshFace(Vector3f(right, down, back), Vector3f(left, down, back), Vector3f(left, up, back), Vector3f(right, up, back), Vector3f(0f, 0f, -1f), uv.back, 0.82f, part, texture)
    faces += MeshFace(Vector3f(left, down, back), Vector3f(left, down, front), Vector3f(left, up, front), Vector3f(left, up, back), Vector3f(-1f, 0f, 0f), uv.left, 0.9f, part, texture)
    faces += MeshFace(Vector3f(right, down, front), Vector3f(right, down, back), Vector3f(right, up, back), Vector3f(right, up, front), Vector3f(1f, 0f, 0f), uv.right, 0.78f, part, texture)
    faces += MeshFace(Vector3f(left, up, back), Vector3f(right, up, back), Vector3f(right, up, front), Vector3f(left, up, front), Vector3f(0f, 1f, 0f), uv.top, 1.08f, part, texture)
    faces += MeshFace(Vector3f(left, down, front), Vector3f(right, down, front), Vector3f(right, down, back), Vector3f(left, down, back), Vector3f(0f, -1f, 0f), uv.bottom, 0.62f, part, texture)
}

private fun appendFace(out: MutableList<Float>, face: MeshFace) {
    val u0 = if (face.uv.flipX) face.uv.u + face.uv.width else face.uv.u
    val u1 = if (face.uv.flipX) face.uv.u else face.uv.u + face.uv.width
    val v0 = face.uv.v
    val v1 = face.uv.v + face.uv.height
    appendVertex(out, face.v0, u0, v1, face)
    appendVertex(out, face.v1, u1, v1, face)
    appendVertex(out, face.v2, u1, v0, face)
    appendVertex(out, face.v0, u0, v1, face)
    appendVertex(out, face.v2, u1, v0, face)
    appendVertex(out, face.v3, u0, v0, face)
}

private fun appendVertex(out: MutableList<Float>, position: Vector3f, u: Float, v: Float, face: MeshFace) {
    out += position.x
    out += position.y
    out += position.z
    out += u
    out += v
    out += face.normal.x
    out += face.normal.y
    out += face.normal.z
    out += face.part.ordinal.toFloat()
    out += face.shade
    out += face.texture.ordinal.toFloat()
}

private const val VERTEX_FLOATS = 11
private const val CAMERA_RADIUS = 60f
private const val FRAME_INTERVAL_NANOS = 16_666_667L
private const val ROTATION_SPEED_DEG_PER_SECOND = 30f
private const val MSAA_SAMPLES = 4
private const val MAX_FRAMEBUFFER_CACHE_SIZE = 4

private const val VERTEX_SHADER = """
    #version 450 core
    layout(location = 0) in vec3 aPosition;
    layout(location = 1) in vec2 aUv;
    layout(location = 2) in vec3 aNormal;
    layout(location = 3) in float aPart;
    layout(location = 4) in float aShade;
    layout(location = 5) in float aTexture;

    uniform mat4 uProjection;
    uniform mat4 uView;
    uniform mat4 uBones[7];

    out vec2 vUv;
    out float vLight;
    flat out int vTexture;

    void main() {
        mat4 bone = uBones[int(aPart + 0.5)];
        vec4 worldPosition = bone * vec4(aPosition, 1.0);
        vec3 normal = normalize(mat3(bone) * aNormal);
        vec3 lightDirection = normalize(vec3(0.35, 0.85, 0.75));
        vLight = aShade * (0.72 + max(dot(normal, lightDirection), 0.0) * 0.32);
        vUv = aUv;
        vTexture = int(aTexture + 0.5);
        gl_Position = uProjection * uView * worldPosition;
    }
"""

private const val FRAGMENT_SHADER = """
    #version 450 core
    in vec2 vUv;
    in float vLight;
    flat in int vTexture;

    uniform sampler2D uSkin;
    uniform sampler2D uCape;
    out vec4 fragColor;

    void main() {
        vec4 color = vTexture == 0
            ? texture(uSkin, vUv / vec2(textureSize(uSkin, 0)))
            : texture(uCape, vUv / vec2(textureSize(uCape, 0)));
        if (color.a < 0.05) discard;
        fragColor = vec4(color.rgb * vLight, color.a);
    }
"""
