package calebxzau.rdi.bgrenderer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import calebxzau.rdi.render.GlfwRuntime
import calebxzau.rdi.playermodel.core.PlayerModelPart
import calebxzau.rdi.playermodel.core.PlayerSkinTexture
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.ImageInfo
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW.glfwMakeContextCurrent
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12.GL_BGRA
import org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.GL_TEXTURE1
import org.lwjgl.opengl.GL13.GL_TEXTURE2
import org.lwjgl.opengl.GL13.GL_TEXTURE3
import org.lwjgl.opengl.GL13.GL_TEXTURE4
import org.lwjgl.opengl.GL13.GL_TEXTURE5
import org.lwjgl.opengl.GL13.GL_CLAMP_TO_BORDER
import org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE
import org.lwjgl.opengl.GL13.glActiveTexture
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import org.lwjgl.opengl.GL14.GL_COMPARE_R_TO_TEXTURE
import org.lwjgl.opengl.GL14.GL_DEPTH_COMPONENT24
import org.lwjgl.opengl.GL14.GL_TEXTURE_COMPARE_FUNC
import org.lwjgl.opengl.GL14.GL_TEXTURE_COMPARE_MODE
import org.lwjgl.opengl.GL14.glBlendFuncSeparate
import org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL15.GL_STATIC_DRAW
import org.lwjgl.opengl.GL15.glBindBuffer
import org.lwjgl.opengl.GL15.glBufferData
import org.lwjgl.opengl.GL15.glDeleteBuffers
import org.lwjgl.opengl.GL15.glGenBuffers
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL42.glTexStorage2D
import org.lwjgl.system.MemoryUtil.NULL
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

private data class PlayerMeshKey(val slim: Boolean, val legacy: Boolean, val showOuterLayer: Boolean, val hasCape: Boolean)

internal data class SceneMaterialBatch(
    val material: BlockMaterial,
    val firstIndex: Int,
    val indexCount: Int
)

internal data class SceneMesh(
    val vertices: FloatArray,
    val indices: IntArray,
    val batches: List<SceneMaterialBatch>,
    val staticAabbs: List<StaticAabb> = emptyList()
)

private class SceneVertexBuckets {
    private val values = Array(BlockMaterial.entries.size) { ArrayList<Float>() }
    private val indexValues = Array(BlockMaterial.entries.size) { ArrayList<Int>() }

    operator fun get(material: BlockMaterial): MutableList<Float> = values[material.ordinal]

    fun appendIndex(material: BlockMaterial, index: Int) {
        indexValues[material.ordinal] += index
    }

    fun flatten(staticAabbs: List<StaticAabb> = emptyList()): SceneMesh {
        val vertices = FloatArray(values.sumOf { it.size })
        val indices = IntArray(indexValues.sumOf { it.size })
        val batches = ArrayList<SceneMaterialBatch>(BlockMaterial.entries.size)
        var vertexOffset = 0
        var indexOffset = 0
        values.forEachIndexed { index, bucket ->
            bucket.toFloatArray().copyInto(vertices, vertexOffset)
            val localIndices = indexValues[index]
            localIndices.forEachIndexed { localIndex, value -> indices[indexOffset + localIndex] = value + vertexOffset / SCENE_VERTEX_FLOATS }
            batches += SceneMaterialBatch(BlockMaterial.entries[index], indexOffset, localIndices.size)
            vertexOffset += bucket.size
            indexOffset += localIndices.size
        }
        return SceneMesh(vertices, indices, batches, staticAabbs)
    }
}

private fun pivotRotation(matrix: Matrix4f, x: Float, y: Float, z: Float, angle: Float) {
    matrix.identity().translate(x, y, z).rotateX(angle).translate(-x, -y, -z)
}

class BackgroundRenderSession internal constructor(
    private val state: RendererState
) : AutoCloseable {
    val frame: StateFlow<ImageBitmap?> = state.frame
    val failure: StateFlow<String?> = state.failure
    val lowPerformance: StateFlow<Boolean> = state.lowPerformance

    fun setActive(active: Boolean) {
        if (state.active.getAndSet(active) != active && active) state.ensureThread()
        state.thread?.let(LockSupport::unpark)
    }

    /** Replaces the player appearance; bytes are copied to GPU only on the render thread. */
    fun setPlayerAppearance(appearance: BackgroundPlayerAppearance?) {
        state.appearance.set(appearance)
        state.thread?.let(LockSupport::unpark)
    }

    override fun close() {
        state.close()
    }
}

data class BackgroundPlayerAppearance(
    val skin: PlayerSkinTexture,
    val cape: PlayerSkinTexture? = null
)

object BackgroundRenderer {
    fun openSession(): BackgroundRenderSession = BackgroundRenderSession(RendererState())
}

internal class RendererState {
    private val logger = KotlinLogging.logger {}
    private val running = AtomicBoolean(true)
    val active = AtomicBoolean(false)
    private val terminalFailure = AtomicBoolean(false)
    private val _frame = MutableStateFlow<ImageBitmap?>(null)
    val frame = _frame.asStateFlow()
    private val _failure = MutableStateFlow<String?>(null)
    val failure = _failure.asStateFlow()
    private val _lowPerformance = MutableStateFlow(false)
    val lowPerformance = _lowPerformance.asStateFlow()
    private val performance = FramePerformanceMonitor()
    /** One bounded terrain realization per session; never regenerated during animation. */
    private val sessionSeed = Random.nextLong()
    val appearance = java.util.concurrent.atomic.AtomicReference<BackgroundPlayerAppearance?>(null)
    private val threadLock = Any()
    @Volatile
    var thread: Thread? = null

    fun ensureThread() {
        if (!running.get() || terminalFailure.get() || lowPerformance.value || thread?.isAlive == true) return
        synchronized(threadLock) {
            if (!running.get() || terminalFailure.get() || lowPerformance.value || thread?.isAlive == true) return
            thread = Thread(::renderLoop, "rdi-background-renderer").apply {
                isDaemon = true
                start()
            }
        }
    }

    fun close() {
        running.set(false)
        active.set(false)
        thread?.let {
            LockSupport.unpark(it)
            if (it !== Thread.currentThread()) it.join()
        }
    }

    private fun renderLoop() {
        var window = NULL
        var glfwLease: GlfwRuntime.Lease? = null
        try {
            glfwLease = GlfwRuntime.acquire()
            window = GlfwRuntime.createHiddenWindow("rdi-background")
            check(window != NULL) { "Failed to create the background OpenGL context" }
            glfwMakeContextCurrent(window)
            GL.createCapabilities()
            val renderer = SceneGlRenderer(sessionSeed)
            val framebuffer = OffscreenFramebuffer(RENDER_WIDTH, RENDER_HEIGHT)
            val pixels = BufferUtils.createByteBuffer(RENDER_WIDTH * RENDER_HEIGHT * 4)
            val bitmapBuffer = FrameBitmapBuffer()
            var postProcessor: BackgroundPostProcessor? = null
            try {
                renderer.initialize()
                val initializedPostProcessor = BackgroundPostProcessor(RENDER_WIDTH, RENDER_HEIGHT)
                postProcessor = initializedPostProcessor
                glPixelStorei(GL_PACK_ALIGNMENT, 1)
                var nextFrame = 0L
                val started = System.nanoTime()
                while (running.get()) {
                    if (!active.get()) {
                        LockSupport.park()
                        continue
                    }
                    val now = System.nanoTime()
                    if (nextFrame > now) {
                        LockSupport.parkNanos(nextFrame - now)
                        continue
                    }
                    val elapsed = (now - started).toDouble() / 1_000_000_000.0
                    val frameStarted = System.nanoTime()
                    val frameAppearance = appearance.get()
                    framebuffer.bindForRender()
                    val state = renderer.renderSceneBeforeWater(elapsed, frameAppearance)
                    framebuffer.captureSceneInputs()
                    framebuffer.bindForReflection()
                    renderer.renderReflectionScene(state, frameAppearance)
                    framebuffer.bindForRender()
                    framebuffer.generateReflectionMipmaps()
                    renderer.renderWater(
                        state,
                        framebuffer.sceneColorTexture(),
                        framebuffer.sceneDepthTexture(),
                        framebuffer.reflectionColorTexture()
                    )
                    framebuffer.resolveRawHdr()
                    initializedPostProcessor.process(framebuffer.rawHdrTexture())
                    pixels.clear()
                    glReadPixels(0, 0, RENDER_WIDTH, RENDER_HEIGHT, GL_BGRA, GL_UNSIGNED_BYTE, pixels)
                    _frame.value = bitmapBuffer.update(pixels, RENDER_WIDTH, RENDER_HEIGHT)
                    if (performance.record(System.nanoTime() - frameStarted)) {
                        _lowPerformance.value = true
                        logger.warn { "GPU background renderer is too slow; using the static background" }
                        running.set(false)
                        break
                    }
                    nextFrame = now + 1_000_000_000L / TARGET_FPS
                }
            } finally {
                bitmapBuffer.close()
                postProcessor?.close()
                framebuffer.close()
                renderer.close()
            }
        } catch (error: Throwable) {
            if (running.get()) {
                terminalFailure.set(true)
                _failure.value = "实时背景渲染不可用"
                logger.error(error) { "GPU background renderer failed" }
            }
        } finally {
            GL.setCapabilities(null)
            if (window != NULL) {
                glfwMakeContextCurrent(NULL)
                GlfwRuntime.destroyWindow(window)
            }
            glfwLease?.close()
            synchronized(threadLock) {
                if (thread === Thread.currentThread()) thread = null
            }
        }
    }
}

internal class FramePerformanceMonitor(
    private val warmupFrames: Int = 30,
    private val sampleSize: Int = 60,
    private val thresholdNanos: Long = 45_000_000L
) {
    private var frames = 0
    private val samples = ArrayDeque<Long>()

    fun record(durationNanos: Long): Boolean {
        frames++
        if (frames <= warmupFrames) return false
        samples.addLast(durationNanos)
        if (samples.size > sampleSize) samples.removeFirst()
        return samples.size == sampleSize && samples.average() > thresholdNanos.toDouble()
    }
}

private const val MSAA_SAMPLES = 4

internal const val HDR_COLOR_FORMAT = GL_R11F_G11F_B10F
internal const val FINAL_COLOR_FORMAT = GL_RGBA8
internal const val HDR_COLOR_EXTERNAL_FORMAT = GL_RGB
internal const val HDR_COLOR_EXTERNAL_TYPE = GL_FLOAT

private class OffscreenFramebuffer(private val width: Int, private val height: Int) : AutoCloseable {
    private val msaaFramebuffer = glGenFramebuffers()
    private val msaaColor = glGenRenderbuffers()
    private val msaaDepth = glGenRenderbuffers()
    private val resolveFramebuffer = glGenFramebuffers()
    private val resolveColor = glGenTextures()
    private val sceneFramebuffer = glGenFramebuffers()
    private val sceneColorTexture = glGenTextures()
    private val sceneDepthTexture = glGenTextures()
    private val reflectionFramebuffer = glGenFramebuffers()
    private val reflectionColor = glGenTextures()
    private val reflectionDepth = glGenRenderbuffers()

    init {
        glBindFramebuffer(GL_FRAMEBUFFER, msaaFramebuffer)
        glBindRenderbuffer(GL_RENDERBUFFER, msaaColor)
        glRenderbufferStorageMultisample(GL_RENDERBUFFER, MSAA_SAMPLES, HDR_COLOR_FORMAT, width, height)
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, msaaColor)
        glBindRenderbuffer(GL_RENDERBUFFER, msaaDepth)
        glRenderbufferStorageMultisample(GL_RENDERBUFFER, MSAA_SAMPLES, GL_DEPTH_COMPONENT24, width, height)
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, msaaDepth)
        check(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE) {
            "Background MSAA framebuffer is incomplete"
        }

        glBindFramebuffer(GL_FRAMEBUFFER, resolveFramebuffer)
        glBindTexture(GL_TEXTURE_2D, resolveColor)
        glTexImage2D(
            GL_TEXTURE_2D, 0, HDR_COLOR_FORMAT, width, height, 0,
            HDR_COLOR_EXTERNAL_FORMAT, HDR_COLOR_EXTERNAL_TYPE, 0L
        )
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, resolveColor, 0)
        check(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE) {
            "Background resolve framebuffer is incomplete"
        }

        glBindFramebuffer(GL_FRAMEBUFFER, sceneFramebuffer)
        glBindTexture(GL_TEXTURE_2D, sceneColorTexture)
        glTexImage2D(
            GL_TEXTURE_2D, 0, HDR_COLOR_FORMAT, width, height, 0,
            HDR_COLOR_EXTERNAL_FORMAT, HDR_COLOR_EXTERNAL_TYPE, 0L
        )
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, sceneColorTexture, 0)
        glBindTexture(GL_TEXTURE_2D, sceneDepthTexture)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, width, height, 0, GL_DEPTH_COMPONENT, GL_UNSIGNED_INT, 0L)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, sceneDepthTexture, 0)
        glDrawBuffer(GL_COLOR_ATTACHMENT0)
        check(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE) {
            "Background scene capture framebuffer is incomplete"
        }

        glBindFramebuffer(GL_FRAMEBUFFER, reflectionFramebuffer)
        glBindTexture(GL_TEXTURE_2D, reflectionColor)
        glTexImage2D(
            GL_TEXTURE_2D,
            0,
            HDR_COLOR_FORMAT,
            REFLECTION_WIDTH,
            REFLECTION_HEIGHT,
            0,
            HDR_COLOR_EXTERNAL_FORMAT,
            HDR_COLOR_EXTERNAL_TYPE,
            0L
        )
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, reflectionColor, 0)
        glBindRenderbuffer(GL_RENDERBUFFER, reflectionDepth)
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, REFLECTION_WIDTH, REFLECTION_HEIGHT)
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, reflectionDepth)
        glDrawBuffer(GL_COLOR_ATTACHMENT0)
        check(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE) {
            "Background reflection framebuffer is incomplete"
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }

    fun generateReflectionMipmaps() {
        val previousActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE)
        val previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D)
        try {
            glBindTexture(GL_TEXTURE_2D, reflectionColor)
            glGenerateMipmap(GL_TEXTURE_2D)
        } finally {
            glBindTexture(GL_TEXTURE_2D, previousTexture)
            glActiveTexture(previousActiveTexture)
        }
    }

    fun bindForRender() {
        glBindFramebuffer(GL_FRAMEBUFFER, msaaFramebuffer)
        glViewport(0, 0, width, height)
    }

    fun bindForReflection() {
        glBindFramebuffer(GL_FRAMEBUFFER, reflectionFramebuffer)
        glViewport(0, 0, REFLECTION_WIDTH, REFLECTION_HEIGHT)
    }

    fun captureSceneInputs() {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, msaaFramebuffer)
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, sceneFramebuffer)
        glBlitFramebuffer(
            0, 0, width, height,
            0, 0, width, height,
            GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT,
            GL_NEAREST
        )
    }

    fun sceneColorTexture(): Int = sceneColorTexture

    fun sceneDepthTexture(): Int = sceneDepthTexture

    fun reflectionColorTexture(): Int = reflectionColor

    fun rawHdrTexture(): Int = resolveColor

    fun resolveRawHdr() {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, msaaFramebuffer)
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, resolveFramebuffer)
        glBlitFramebuffer(
            0, 0, width, height,
            0, 0, width, height,
            GL_COLOR_BUFFER_BIT,
            GL_NEAREST
        )
    }

    override fun close() {
        glDeleteTextures(resolveColor)
        glDeleteTextures(sceneColorTexture)
        glDeleteTextures(sceneDepthTexture)
        glDeleteTextures(reflectionColor)
        glDeleteRenderbuffers(reflectionDepth)
        glDeleteRenderbuffers(msaaColor)
        glDeleteRenderbuffers(msaaDepth)
        glDeleteFramebuffers(resolveFramebuffer)
        glDeleteFramebuffers(sceneFramebuffer)
        glDeleteFramebuffers(reflectionFramebuffer)
        glDeleteFramebuffers(msaaFramebuffer)
    }
}

internal class SceneGlRenderer(private val sceneSeed: Long) : AutoCloseable {
    private var program = 0
    private var vertexArray = 0
    private var vertexBuffer = 0
    private var indexBuffer = 0
    private var projectionLocation = -1
    private var viewLocation = -1
    private var boatModelLocation = -1
    private var clipHeightLocation = -1
    private var textureLocation = -1
    private var materialLocation = -1
    private var sunLocation = -1
    private var shadowViewProjectionLocation = -1
    private var shadowMapLocation = -1
    private var shadowTexture = 0
    private var shadowFramebuffer = 0
    private var shadowProgram = 0
    private var shadowProjectionLocation = -1
    private var shadowTextureLocation = -1
    private var shadowMaterialLocation = -1
    private var waterProgram = 0
    private var waterProjectionLocation = -1
    private var waterViewLocation = -1
    private var waterTimeLocation = -1
    private var waterNormalOffsetLocation = -1
    private var waterEyePositionLocation = -1
    private var waterSunDirectionLocation = -1
    private var waterSkyColorLocation = -1
    private var waterHorizonColorLocation = -1
    private var waterHorizonFogStartLocation = -1
    private var waterHorizonFogEndLocation = -1
    private var waterCloudNormalLocation = -1
    private var waterNoiseLocation = -1
    private var waterSceneColorLocation = -1
    private var waterSceneDepthLocation = -1
    private var waterReflectionColorLocation = -1
    private var waterInverseViewProjectionLocation = -1
    private var waterReflectionViewProjectionLocation = -1
    private var skyProgram = 0
    private var skyVertexArray = 0
    private var skyVertexBuffer = 0
    private var skyColorLocation = -1
    private var skyInverseProjectionLocation = -1
    private var skyInverseViewLocation = -1
    private var skySunDirectionLocation = -1
    private var sunProgram = 0
    private var sunVertexArray = 0
    private var sunVertexBuffer = 0
    private var sunProjectionLocation = -1
    private var sunViewLocation = -1
    private var sunOffsetLocation = -1
    private var sunRightLocation = -1
    private var sunUpLocation = -1
    private var sunTextureLocation = -1
    private val materialTextures = IntArray(BlockMaterial.entries.size)
    private var sunTexture = 0
    private var cloudProgram = 0
    private var cloudVertexArray = 0
    private var cloudVertexBuffer = 0
    private var cloudVertexCount = 0
    private var cloudProjectionLocation = -1
    private var cloudViewLocation = -1
    private var cloudOffsetLocation = -1
    private var cloudHeightLocation = -1
    private var cloudSunDirectionLocation = -1
    private var cloudTextureLocation = -1
    private var cloudFogColorLocation = -1
    private var cloudFogStartLocation = -1
    private var cloudFogEndLocation = -1
    private var cloudTexture = 0
    private var waterCloudNormalTexture = 0
    private var waterNoiseTexture = 0
    private var previousCloudX = Int.MIN_VALUE
    private var previousCloudY = Int.MIN_VALUE
    private var previousCloudZ = Int.MIN_VALUE
    private var previousCloudColor: SkyColor? = null
    private var playerProgram = 0
    private var playerVertexArray = 0
    private var playerVertexBuffer = 0
    private var playerVertexCount = 0
    private var playerProjectionLocation = -1
    private var playerViewLocation = -1
    private var playerModelLocation = -1
    private var playerClipHeightLocation = -1
    private var playerSkinLocation = -1
    private val playerBoneLocations = IntArray(PlayerModelPart.entries.size)
    private var playerSkinTexture = 0
    private var playerCapeTexture = 0
    private var playerCapeLocation = -1
    private var playerAppearance: BackgroundPlayerAppearance? = null
    private var playerMeshKey: PlayerMeshKey? = null
    private val projection = Matrix4f()
    private val view = Matrix4f()
    private val mainViewProjection = Matrix4f()
    private val inverseViewProjection = Matrix4f()
    private val reflectionView = Matrix4f()
    private val reflectionViewProjection = Matrix4f()
    private val inverseProjection = Matrix4f()
    private val inverseView = Matrix4f()
    private val cloudView = Matrix4f()
    private val eye = Vector3f()
    private val reflectionEye = Vector3f()
    private val target = Vector3f(CAMERA_TARGET_X, CAMERA_TARGET_Y, CAMERA_TARGET_Z)
    private val reflectionTarget = Vector3f()
    private val worldUp = Vector3f(0f, 1f, 0f)
    private val reflectionUp = Vector3f(0f, -1f, 0f)
    private val matrixBuffer = BufferUtils.createFloatBuffer(16)
    private val playerMatrixBuffer = BufferUtils.createFloatBuffer(16)
    private val playerModel = Matrix4f()
    private val boatModel = Matrix4f()
    private val playerBones = Array(PlayerModelPart.entries.size) { Matrix4f() }
    private val sceneMesh = buildSceneMesh()
    private val staticAabbs = sceneMesh.staticAabbs
    private val waterBatch = sceneMesh.batches.single { it.material == BlockMaterial.WATER }
    private val nonWaterBatches = sceneMesh.batches.filter { it.material != BlockMaterial.WATER }
    private val sceneVertices = sceneMesh.vertices
    private val sceneIndices = sceneMesh.indices
    private val sunVertices = buildSunVertices()
    private val shadowFrame = buildStaticShadowFrame(staticAabbs, BackgroundScene.sun(0f))
    private val shadowViewProjection = shadowFrame.viewProjection
    private var mainCloudCoordinates: CloudCoordinates? = null

    internal fun meshForTesting(): SceneMesh = sceneMesh

    fun initialize() {
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        projectionLocation = glGetUniformLocation(program, "uProjection")
        viewLocation = glGetUniformLocation(program, "uView")
        sunLocation = glGetUniformLocation(program, "uSunDirection")
        boatModelLocation = glGetUniformLocation(program, "uBoatModel")
        clipHeightLocation = glGetUniformLocation(program, "uClipHeight")
        textureLocation = glGetUniformLocation(program, "uTexture")
        materialLocation = glGetUniformLocation(program, "uMaterial")
        shadowViewProjectionLocation = glGetUniformLocation(program, "uShadowViewProjection")
        shadowMapLocation = glGetUniformLocation(program, "uShadowMap")
        vertexArray = glGenVertexArrays()
        vertexBuffer = glGenBuffers()
        glBindVertexArray(vertexArray)
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer)
        val vertices = BufferUtils.createFloatBuffer(sceneVertices.size).put(sceneVertices).flip()
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW)
        val stride = SCENE_VERTEX_FLOATS * Float.SIZE_BYTES
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L)
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3L * Float.SIZE_BYTES)
        glVertexAttribPointer(2, 3, GL_FLOAT, false, stride, 6L * Float.SIZE_BYTES)
        glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, 9L * Float.SIZE_BYTES)
        glVertexAttribPointer(4, 2, GL_FLOAT, false, stride, 10L * Float.SIZE_BYTES)
        glVertexAttribPointer(5, 1, GL_FLOAT, false, stride, 12L * Float.SIZE_BYTES)
        repeat(6) { glEnableVertexAttribArray(it) }
        indexBuffer = glGenBuffers()
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBuffer)
        val indices = BufferUtils.createIntBuffer(sceneIndices.size).put(sceneIndices).flip()
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW)
        glBindVertexArray(0)

        skyProgram = createProgram(SKY_VERTEX_SHADER, SKY_FRAGMENT_SHADER)
        skyColorLocation = glGetUniformLocation(skyProgram, "uSkyColor")
        skyInverseProjectionLocation = glGetUniformLocation(skyProgram, "uInverseProjection")
        skyInverseViewLocation = glGetUniformLocation(skyProgram, "uInverseView")
        skySunDirectionLocation = glGetUniformLocation(skyProgram, "uSunDirection")
        skyVertexArray = glGenVertexArrays()
        skyVertexBuffer = glGenBuffers()
        glBindVertexArray(skyVertexArray)
        glBindBuffer(GL_ARRAY_BUFFER, skyVertexBuffer)
        val skyVertices = BufferUtils.createFloatBuffer(SKY_TRIANGLE.size).put(SKY_TRIANGLE).flip()
        glBufferData(GL_ARRAY_BUFFER, skyVertices, GL_STATIC_DRAW)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.SIZE_BYTES, 0L)
        glEnableVertexAttribArray(0)
        glBindVertexArray(0)

        sunProgram = createProgram(SUN_VERTEX_SHADER, SUN_FRAGMENT_SHADER)
        sunProjectionLocation = glGetUniformLocation(sunProgram, "uProjection")
        sunViewLocation = glGetUniformLocation(sunProgram, "uView")
        sunOffsetLocation = glGetUniformLocation(sunProgram, "uSunOffset")
        sunRightLocation = glGetUniformLocation(sunProgram, "uSunRight")
        sunUpLocation = glGetUniformLocation(sunProgram, "uSunUp")
        sunTextureLocation = glGetUniformLocation(sunProgram, "uSunTexture")
        sunVertexArray = glGenVertexArrays()
        sunVertexBuffer = glGenBuffers()
        glBindVertexArray(sunVertexArray)
        glBindBuffer(GL_ARRAY_BUFFER, sunVertexBuffer)
        val sunBuffer = BufferUtils.createFloatBuffer(sunVertices.size).put(sunVertices).flip()
        glBufferData(GL_ARRAY_BUFFER, sunBuffer, GL_STATIC_DRAW)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.SIZE_BYTES, 0L)
        glEnableVertexAttribArray(0)
        glBindVertexArray(0)

        BlockMaterial.entries.forEachIndexed { index, material ->
            material.texturePath?.let { path ->
                materialTextures[index] = uploadClasspathTexture(path)
            }
        }
        waterCloudNormalTexture = uploadClasspathTexture(
            "assets/complementary/textures/cloud-water.png",
            repeat = true,
            linear = true,
            mipmaps = true
        )
        waterNoiseTexture = uploadClasspathTexture(
            "assets/complementary/textures/noise.png",
            repeat = true,
            linear = true,
            mipmaps = true
        )
        sunTexture = uploadClasspathTexture("assets/minecraft/textures/environment/sun.png", repeat = false)
        cloudTexture = uploadClasspathTexture(
            "assets/minecraft/textures/environment/clouds.png",
            linear = CLOUD_TEXTURE_LINEAR_FILTER
        )

        waterProgram = createProgram(WATER_VERTEX_SHADER, WATER_FRAGMENT_SHADER)
        waterProjectionLocation = glGetUniformLocation(waterProgram, "uProjection")
        waterViewLocation = glGetUniformLocation(waterProgram, "uView")
        waterTimeLocation = glGetUniformLocation(waterProgram, "uTime")
        waterNormalOffsetLocation = glGetUniformLocation(waterProgram, "uWaterNormalOffset")
        waterEyePositionLocation = glGetUniformLocation(waterProgram, "uEyePosition")
        waterSunDirectionLocation = glGetUniformLocation(waterProgram, "uSunDirection")
        waterSkyColorLocation = glGetUniformLocation(waterProgram, "uSkyColor")
        waterHorizonColorLocation = glGetUniformLocation(waterProgram, "uHorizonColor")
        waterHorizonFogStartLocation = glGetUniformLocation(waterProgram, "uHorizonFogStart")
        waterHorizonFogEndLocation = glGetUniformLocation(waterProgram, "uHorizonFogEnd")
        waterCloudNormalLocation = glGetUniformLocation(waterProgram, "uCloudWaterNormal")
        waterNoiseLocation = glGetUniformLocation(waterProgram, "uWaterNoise")
        waterSceneColorLocation = glGetUniformLocation(waterProgram, "uSceneColor")
        waterSceneDepthLocation = glGetUniformLocation(waterProgram, "uSceneDepth")
        waterReflectionColorLocation = glGetUniformLocation(waterProgram, "uReflectionColor")
        waterInverseViewProjectionLocation = glGetUniformLocation(waterProgram, "uInverseViewProjection")
        waterReflectionViewProjectionLocation = glGetUniformLocation(waterProgram, "uReflectionViewProjection")

        cloudProgram = createProgram(CLOUD_VERTEX_SHADER, CLOUD_FRAGMENT_SHADER)
        cloudProjectionLocation = glGetUniformLocation(cloudProgram, "uProjection")
        cloudViewLocation = glGetUniformLocation(cloudProgram, "uView")
        cloudOffsetLocation = glGetUniformLocation(cloudProgram, "uCloudOffset")
        cloudHeightLocation = glGetUniformLocation(cloudProgram, "uCloudHeight")
        cloudTextureLocation = glGetUniformLocation(cloudProgram, "uCloudTexture")
        cloudFogColorLocation = glGetUniformLocation(cloudProgram, "uCloudFogColor")
        cloudFogStartLocation = glGetUniformLocation(cloudProgram, "uCloudFogStart")
        cloudFogEndLocation = glGetUniformLocation(cloudProgram, "uCloudFogEnd")
        cloudSunDirectionLocation = glGetUniformLocation(cloudProgram, "uSunDirection")
        cloudVertexArray = glGenVertexArrays()
        cloudVertexBuffer = glGenBuffers()
        glBindVertexArray(cloudVertexArray)
        glBindBuffer(GL_ARRAY_BUFFER, cloudVertexBuffer)
        val cloudStride = 12 * Float.SIZE_BYTES
        glVertexAttribPointer(0, 3, GL_FLOAT, false, cloudStride, 0L)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, cloudStride, 3L * Float.SIZE_BYTES)
        glVertexAttribPointer(2, 4, GL_FLOAT, false, cloudStride, 5L * Float.SIZE_BYTES)
        glVertexAttribPointer(3, 3, GL_FLOAT, false, cloudStride, 9L * Float.SIZE_BYTES)
        repeat(4) { glEnableVertexAttribArray(it) }
        glBindVertexArray(0)

        glEnable(GL_DEPTH_TEST)
        glDepthFunc(GL_LEQUAL)
        glEnable(GL_BLEND)
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
        glEnable(GL_CULL_FACE)
        glCullFace(GL_BACK)

        playerProgram = createProgram(PLAYER_VERTEX_SHADER, PLAYER_FRAGMENT_SHADER)
        playerProjectionLocation = glGetUniformLocation(playerProgram, "uProjection")
        playerViewLocation = glGetUniformLocation(playerProgram, "uView")
        playerModelLocation = glGetUniformLocation(playerProgram, "uModel")
        playerClipHeightLocation = glGetUniformLocation(playerProgram, "uClipHeight")
        playerSkinLocation = glGetUniformLocation(playerProgram, "uSkin")
        playerCapeLocation = glGetUniformLocation(playerProgram, "uCape")
        playerBoneLocations.indices.forEach { playerBoneLocations[it] = glGetUniformLocation(playerProgram, "uBones[$it]") }

        shadowProgram = createProgram(SHADOW_VERTEX_SHADER, SHADOW_FRAGMENT_SHADER)
        shadowProjectionLocation = glGetUniformLocation(shadowProgram, "uShadowViewProjection")
        shadowTextureLocation = glGetUniformLocation(shadowProgram, "uTexture")
        shadowMaterialLocation = glGetUniformLocation(shadowProgram, "uMaterial")
        shadowTexture = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, shadowTexture)
        glTexImage2D(
            GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, STATIC_SHADOW_MAP_SIZE, STATIC_SHADOW_MAP_SIZE,
            0, GL_DEPTH_COMPONENT, GL_UNSIGNED_INT, 0L
        )
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER)
        glTexParameterfv(GL_TEXTURE_2D, GL_TEXTURE_BORDER_COLOR, floatArrayOf(1f, 1f, 1f, 1f))
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_R_TO_TEXTURE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL)
        shadowFramebuffer = glGenFramebuffers()
        glBindFramebuffer(GL_FRAMEBUFFER, shadowFramebuffer)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, shadowTexture, 0)
        glDrawBuffer(GL_NONE)
        glReadBuffer(GL_NONE)
        check(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE) {
            "Background static shadow framebuffer is incomplete"
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        renderStaticShadowMap()
    }

    /** Renders the immutable harbor geometry once; boat and water are intentionally omitted. */
    private fun renderStaticShadowMap() {
        val previousFramebuffer = glGetInteger(GL_FRAMEBUFFER_BINDING)
        val previousViewport = BufferUtils.createIntBuffer(4)
        glGetIntegerv(GL_VIEWPORT, previousViewport)
        val previousDepthFunc = glGetInteger(GL_DEPTH_FUNC)
        val previousCullMode = glGetInteger(GL_CULL_FACE_MODE)
        val previousPolygonOffsetFactor = glGetFloat(GL_POLYGON_OFFSET_FACTOR)
        val previousPolygonOffsetUnits = glGetFloat(GL_POLYGON_OFFSET_UNITS)
        val previousClearDepth = glGetFloat(GL_DEPTH_CLEAR_VALUE)
        val previousActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE)
        val previousProgram = glGetInteger(GL_CURRENT_PROGRAM)
        val previousVertexArray = glGetInteger(GL_VERTEX_ARRAY_BINDING)
        glActiveTexture(GL_TEXTURE0)
        val previousTexture0 = glGetInteger(GL_TEXTURE_BINDING_2D)
        glActiveTexture(previousActiveTexture)
        val previousDepthTest = glIsEnabled(GL_DEPTH_TEST)
        val previousBlend = glIsEnabled(GL_BLEND)
        val previousCull = glIsEnabled(GL_CULL_FACE)
        val previousPolygonOffset = glIsEnabled(GL_POLYGON_OFFSET_FILL)
        val previousFrontFace = glGetInteger(GL_FRONT_FACE)
        val previousColorMask = BufferUtils.createByteBuffer(4)
        glGetBooleanv(GL_COLOR_WRITEMASK, previousColorMask)
        val previousDepthMask = BufferUtils.createByteBuffer(1)
        glGetBooleanv(GL_DEPTH_WRITEMASK, previousDepthMask)
        try {
            glBindFramebuffer(GL_FRAMEBUFFER, shadowFramebuffer)
            glViewport(0, 0, STATIC_SHADOW_MAP_SIZE, STATIC_SHADOW_MAP_SIZE)
            glClearDepth(1.0)
            glClear(GL_DEPTH_BUFFER_BIT)
            glEnable(GL_DEPTH_TEST)
            glDepthMask(true)
            glDepthFunc(GL_LEQUAL)
            glDisable(GL_BLEND)
            glColorMask(false, false, false, false)
            glEnable(GL_CULL_FACE)
            glCullFace(GL_BACK)
            glFrontFace(GL_CCW)
            glEnable(GL_POLYGON_OFFSET_FILL)
            glPolygonOffset(2f, 4f)
            glUseProgram(shadowProgram)
            uploadMatrix(shadowProjectionLocation, shadowViewProjection)
            glActiveTexture(GL_TEXTURE0)
            glBindVertexArray(vertexArray)
            nonWaterBatches.forEach { batch ->
                glBindTexture(GL_TEXTURE_2D, materialTextures[batch.material.ordinal])
                glUniform1i(shadowTextureLocation, 0)
                glUniform1i(shadowMaterialLocation, batch.material.ordinal)
                glDrawElements(GL_TRIANGLES, batch.indexCount, GL_UNSIGNED_INT, batch.firstIndex.toLong() * Int.SIZE_BYTES)
            }
            glBindVertexArray(0)
            glUseProgram(0)
        } finally {
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, previousTexture0)
            glActiveTexture(previousActiveTexture)
            glBindVertexArray(previousVertexArray)
            glUseProgram(previousProgram)
            glColorMask(
                previousColorMask.get(0).toInt() != 0,
                previousColorMask.get(1).toInt() != 0,
                previousColorMask.get(2).toInt() != 0,
                previousColorMask.get(3).toInt() != 0
            )
            glDepthMask(previousDepthMask.get(0).toInt() != 0)
            glDepthFunc(previousDepthFunc)
            glCullFace(previousCullMode)
            glPolygonOffset(previousPolygonOffsetFactor, previousPolygonOffsetUnits)
            glClearDepth(previousClearDepth.toDouble())
            if (previousPolygonOffset) glEnable(GL_POLYGON_OFFSET_FILL) else glDisable(GL_POLYGON_OFFSET_FILL)
            if (previousCull) glEnable(GL_CULL_FACE) else glDisable(GL_CULL_FACE)
            if (previousBlend) glEnable(GL_BLEND) else glDisable(GL_BLEND)
            if (previousDepthTest) glEnable(GL_DEPTH_TEST) else glDisable(GL_DEPTH_TEST)
            glFrontFace(previousFrontFace)
            glBindFramebuffer(GL_FRAMEBUFFER, previousFramebuffer)
            glViewport(previousViewport.get(0), previousViewport.get(1), previousViewport.get(2), previousViewport.get(3))
        }
    }

    fun renderSceneBeforeWater(time: Double, appearance: BackgroundPlayerAppearance?): BackgroundScene.FrameState {
        val state = BackgroundScene.frameState(time)
        val camera = state.camera
        val horizontal = camera.distance * cos(camera.yawRadians)
        val forward = camera.distance * sin(camera.yawRadians)
        eye.set(horizontal, camera.height, forward)
        projection.identity().perspective(
            Math.toRadians(CAMERA_FOV_DEGREES.toDouble()).toFloat(),
            RENDER_WIDTH.toFloat() / RENDER_HEIGHT,
            CAMERA_NEAR_PLANE,
            CAMERA_FAR_PLANE
        )
        view.identity().lookAt(eye, target, worldUp)
        mainViewProjection.set(projection).mul(view)
        inverseViewProjection.set(mainViewProjection).invert()
        glViewport(0, 0, RENDER_WIDTH, RENDER_HEIGHT)
        glClearColor(NOON_SKY_COLOR.red, NOON_SKY_COLOR.green, NOON_SKY_COLOR.blue, 1f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

        renderScenePass(state, appearance, view, eye, false)
        return state
    }

    fun renderReflectionScene(state: BackgroundScene.FrameState, appearance: BackgroundPlayerAppearance?) {
        reflectionEye.set(eye.x, reflectedY(eye.y), eye.z)
        reflectionTarget.set(target.x, reflectedY(target.y), target.z)
        reflectionView.identity().lookAt(reflectionEye, reflectionTarget, reflectionUp)
        reflectionViewProjection.set(projection).mul(reflectionView)
        glClearColor(NOON_SKY_COLOR.red, NOON_SKY_COLOR.green, NOON_SKY_COLOR.blue, 1f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        renderScenePass(state, appearance, reflectionView, reflectionEye, true)
    }

    private fun renderScenePass(
        state: BackgroundScene.FrameState,
        appearance: BackgroundPlayerAppearance?,
        passView: Matrix4f,
        passEye: Vector3f,
        reflection: Boolean
    ) {
        glDisable(GL_CLIP_DISTANCE0)
        glFrontFace(GL_CCW)
        try {
            val sun = BackgroundScene.sun(0f)
            renderCelestial(sun, passView, passEye)

            glUseProgram(program)
            uploadMatrix(projectionLocation, projection)
            uploadMatrix(viewLocation, passView)
            glActiveTexture(GL_TEXTURE0)
            boatModel.identity()
                .translate(state.boat.x, BOAT_MODEL_BASE_Y + state.boat.y - BOAT_WATERLINE, state.boat.z)
                .rotateY(state.boat.yawRadians)
                .rotateZ(state.boat.rollRadians)
            uploadMatrix(boatModelLocation, boatModel)
            glUniform3f(sunLocation, sun.directionX, sun.directionY, sun.directionZ)
            uploadMatrix(shadowViewProjectionLocation, shadowViewProjection)
            glActiveTexture(GL_TEXTURE5)
            glBindTexture(GL_TEXTURE_2D, shadowTexture)
            glUniform1i(shadowMapLocation, 5)
            glActiveTexture(GL_TEXTURE0)
            glUniform1f(clipHeightLocation, if (reflection) WATER_LEVEL + 0.01f else -10000f)
            if (reflection) glEnable(GL_CLIP_DISTANCE0)
            glBindVertexArray(vertexArray)
            nonWaterBatches.forEach { batch ->
                glBindTexture(GL_TEXTURE_2D, materialTextures[batch.material.ordinal])
                glUniform1i(textureLocation, 0)
                glUniform1i(materialLocation, batch.material.ordinal)
                glDrawElements(GL_TRIANGLES, batch.indexCount, GL_UNSIGNED_INT, batch.firstIndex.toLong() * Int.SIZE_BYTES)
            }
            glBindVertexArray(0)
            glUseProgram(0)
            glActiveTexture(GL_TEXTURE0)

            renderPlayer(appearance, passView)
            glDisable(GL_CLIP_DISTANCE0)
            renderClouds(state.cloudTimeSeconds, passView, reflection, sun)
        } finally {
            glDisable(GL_CLIP_DISTANCE0)
            glFrontFace(GL_CCW)
            glEnable(GL_CULL_FACE)
            glColorMask(true, true, true, true)
            glDepthMask(true)
            glEnable(GL_DEPTH_TEST)
            glEnable(GL_BLEND)
            glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
        }
    }

    fun renderWater(
        state: BackgroundScene.FrameState,
        sceneColorTexture: Int,
        sceneDepthTexture: Int,
        reflectionColorTexture: Int
    ) {
        val sun = BackgroundScene.sun(0f)
        glUseProgram(waterProgram)
        uploadMatrix(waterProjectionLocation, projection)
        uploadMatrix(waterViewLocation, view)
        uploadMatrix(waterInverseViewProjectionLocation, inverseViewProjection)
        uploadMatrix(waterReflectionViewProjectionLocation, reflectionViewProjection)
        glUniform1f(waterTimeLocation, state.waterTimeSeconds)
        glUniform1f(waterNormalOffsetLocation, state.waterNormalOffset)
        glUniform3f(waterEyePositionLocation, eye.x, eye.y, eye.z)
        glUniform3f(waterSunDirectionLocation, sun.directionX, sun.directionY, sun.directionZ)
        glUniform3f(waterSkyColorLocation, NOON_SKY_COLOR.red, NOON_SKY_COLOR.green, NOON_SKY_COLOR.blue)
        glUniform3f(
            waterHorizonColorLocation,
            NOON_WATER_HORIZON_COLOR.red,
            NOON_WATER_HORIZON_COLOR.green,
            NOON_WATER_HORIZON_COLOR.blue
        )
        glUniform1f(waterHorizonFogStartLocation, WATER_HORIZON_FOG_START)
        glUniform1f(waterHorizonFogEndLocation, WATER_HORIZON_FOG_END)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, waterCloudNormalTexture)
        glUniform1i(waterCloudNormalLocation, 0)
        glActiveTexture(GL_TEXTURE1)
        glBindTexture(GL_TEXTURE_2D, waterNoiseTexture)
        glUniform1i(waterNoiseLocation, 1)
        glActiveTexture(GL_TEXTURE2)
        glBindTexture(GL_TEXTURE_2D, sceneColorTexture)
        glUniform1i(waterSceneColorLocation, 2)
        glActiveTexture(GL_TEXTURE3)
        glBindTexture(GL_TEXTURE_2D, sceneDepthTexture)
        glUniform1i(waterSceneDepthLocation, 3)
        glActiveTexture(GL_TEXTURE4)
        glBindTexture(GL_TEXTURE_2D, reflectionColorTexture)
        glUniform1i(waterReflectionColorLocation, 4)
        glDisable(GL_BLEND)
        glDepthMask(false)
        glBindVertexArray(vertexArray)
        glDrawElements(GL_TRIANGLES, waterBatch.indexCount, GL_UNSIGNED_INT, waterBatch.firstIndex.toLong() * Int.SIZE_BYTES)
        glBindVertexArray(0)
        glDepthMask(true)
        glEnable(GL_BLEND)
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
        glActiveTexture(GL_TEXTURE0)
        glUseProgram(0)
    }

    private fun renderPlayer(appearance: BackgroundPlayerAppearance?, passView: Matrix4f) {
        if (appearance == null) {
            releasePlayerTextures()
            return
        }
        ensurePlayerResources(appearance)
        if (playerVertexArray == 0 || playerSkinTexture == 0) return
        playerModel.set(boatModel).translate(0f, PLAYER_SEAT_LOCAL_Y, 0f).scale(PLAYER_VISUAL_SCALE)
        playerBones.forEach { it.identity() }
        pivotRotation(playerBones[PlayerModelPart.LEFT_LEG.ordinal], -1.9f, 12f, -0.1f, -1.0f)
        pivotRotation(playerBones[PlayerModelPart.RIGHT_LEG.ordinal], 1.9f, 12f, -0.1f, -1.0f)
        val armPivot = if (appearance.skin.slim) 5.5f else 6f
        pivotRotation(playerBones[PlayerModelPart.LEFT_ARM.ordinal], -armPivot, 24f, 0f, 0.28f)
        pivotRotation(playerBones[PlayerModelPart.RIGHT_ARM.ordinal], armPivot, 24f, 0f, -0.28f)
        glUseProgram(playerProgram)
        uploadPlayerMatrix(playerProjectionLocation, projection)
        uploadPlayerMatrix(playerViewLocation, passView)
        uploadPlayerMatrix(playerModelLocation, playerModel)
        glUniform1f(playerClipHeightLocation, if (passView === reflectionView) WATER_LEVEL + 0.01f else -10000f)
        playerBones.forEachIndexed { index, matrix -> uploadPlayerMatrix(playerBoneLocations[index], matrix) }
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, playerSkinTexture)
        glUniform1i(playerSkinLocation, 0)
        glActiveTexture(GL_TEXTURE1)
        glBindTexture(GL_TEXTURE_2D, playerCapeTexture)
        glUniform1i(playerCapeLocation, 1)
        glBindVertexArray(playerVertexArray)
        glDrawArrays(GL_TRIANGLES, 0, playerVertexCount)
        glBindVertexArray(0)
        glUseProgram(0)
    }

    private fun ensurePlayerResources(appearance: BackgroundPlayerAppearance) {
        val key = PlayerMeshKey(appearance.skin.slim, appearance.skin.height <= 32, true, appearance.cape != null)
        if (playerAppearance === appearance && playerMeshKey == key) return
        if (playerSkinTexture != 0) glDeleteTextures(playerSkinTexture)
        if (playerCapeTexture != 0) glDeleteTextures(playerCapeTexture)
        playerSkinTexture = uploadTexture(appearance.skin)
        playerCapeTexture = appearance.cape?.let(::uploadTexture) ?: 0
        if (playerMeshKey != key) {
            if (playerVertexBuffer != 0) glDeleteBuffers(playerVertexBuffer)
            if (playerVertexArray != 0) glDeleteVertexArrays(playerVertexArray)
            val mesh = calebxzau.rdi.playermodel.core.buildPlayerMeshData(key.slim, key.legacy, key.showOuterLayer, key.hasCape)
            playerVertexCount = mesh.vertices.size / PLAYER_VERTEX_FLOATS
            playerVertexArray = glGenVertexArrays()
            playerVertexBuffer = glGenBuffers()
            glBindVertexArray(playerVertexArray)
            glBindBuffer(GL_ARRAY_BUFFER, playerVertexBuffer)
            val data = BufferUtils.createFloatBuffer(mesh.vertices.size).put(mesh.vertices).flip()
            glBufferData(GL_ARRAY_BUFFER, data, GL_STATIC_DRAW)
            val stride = PLAYER_VERTEX_FLOATS * Float.SIZE_BYTES
            glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L)
            glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3L * Float.SIZE_BYTES)
            glVertexAttribPointer(2, 3, GL_FLOAT, false, stride, 5L * Float.SIZE_BYTES)
            glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, 8L * Float.SIZE_BYTES)
            glVertexAttribPointer(4, 1, GL_FLOAT, false, stride, 10L * Float.SIZE_BYTES)
            repeat(5) { glEnableVertexAttribArray(it) }
            glBindVertexArray(0)
            playerMeshKey = key
        }
        playerAppearance = appearance
    }

    private fun releasePlayerTextures() {
        if (playerSkinTexture != 0) glDeleteTextures(playerSkinTexture)
        if (playerCapeTexture != 0) glDeleteTextures(playerCapeTexture)
        playerSkinTexture = 0
        playerCapeTexture = 0
        playerAppearance = null
    }

    private fun uploadTexture(
        texture: PlayerSkinTexture,
        repeat: Boolean = false,
        linear: Boolean = false,
        mipmaps: Boolean = false
    ): Int {
        val id = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, id)
        val filter = if (linear) GL_LINEAR else GL_NEAREST
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, if (mipmaps) GL_LINEAR_MIPMAP_LINEAR else filter)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, if (repeat) GL_REPEAT else GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, if (repeat) GL_REPEAT else GL_CLAMP_TO_EDGE)
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
        val pixels = BufferUtils.createByteBuffer(texture.rgba.size).put(texture.rgba).flip()
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, texture.width, texture.height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels)
        if (mipmaps) glGenerateMipmap(GL_TEXTURE_2D)
        return id
    }

    private fun uploadClasspathTexture(
        path: String,
        repeat: Boolean = true,
        linear: Boolean = false,
        mipmaps: Boolean = false
    ): Int {
        val bytes = requireNotNull(javaClass.classLoader.getResourceAsStream(path)) {
            "Missing background texture resource: $path"
        }.use { it.readBytes() }
        val image: BufferedImage = bytes.inputStream().use { ImageIO.read(it) }
            ?: error("Unable to decode background texture resource: $path")
        val rgba = ByteArray(image.width * image.height * 4)
        var offset = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val argb = image.getRGB(x, y)
                rgba[offset++] = (argb shr 16).toByte()
                rgba[offset++] = (argb shr 8).toByte()
                rgba[offset++] = argb.toByte()
                rgba[offset++] = (argb ushr 24).toByte()
            }
        }
        return uploadTexture(PlayerSkinTexture(image.width, image.height, rgba, false), repeat, linear, mipmaps)
    }

    override fun close() {
        materialTextures.forEach { if (it != 0) glDeleteTextures(it) }
        if (shadowTexture != 0) glDeleteTextures(shadowTexture)
        if (shadowFramebuffer != 0) glDeleteFramebuffers(shadowFramebuffer)
        if (shadowProgram != 0) glDeleteProgram(shadowProgram)
        if (waterCloudNormalTexture != 0) glDeleteTextures(waterCloudNormalTexture)
        if (waterNoiseTexture != 0) glDeleteTextures(waterNoiseTexture)
        if (sunTexture != 0) glDeleteTextures(sunTexture)
        if (cloudTexture != 0) glDeleteTextures(cloudTexture)
        releasePlayerTextures()
        if (playerVertexBuffer != 0) glDeleteBuffers(playerVertexBuffer)
        if (playerVertexArray != 0) glDeleteVertexArrays(playerVertexArray)
        if (playerProgram != 0) glDeleteProgram(playerProgram)
        if (cloudVertexBuffer != 0) glDeleteBuffers(cloudVertexBuffer)
        if (cloudVertexArray != 0) glDeleteVertexArrays(cloudVertexArray)
        if (cloudProgram != 0) glDeleteProgram(cloudProgram)
        if (sunVertexBuffer != 0) glDeleteBuffers(sunVertexBuffer)
        if (sunVertexArray != 0) glDeleteVertexArrays(sunVertexArray)
        if (sunProgram != 0) glDeleteProgram(sunProgram)
        if (skyVertexBuffer != 0) glDeleteBuffers(skyVertexBuffer)
        if (skyVertexArray != 0) glDeleteVertexArrays(skyVertexArray)
        if (skyProgram != 0) glDeleteProgram(skyProgram)
        if (vertexBuffer != 0) glDeleteBuffers(vertexBuffer)
        if (indexBuffer != 0) glDeleteBuffers(indexBuffer)
        if (vertexArray != 0) glDeleteVertexArrays(vertexArray)
        if (program != 0) glDeleteProgram(program)
        if (waterProgram != 0) glDeleteProgram(waterProgram)
        vertexBuffer = 0
        indexBuffer = 0
        vertexArray = 0
        program = 0
        waterProgram = 0
        playerSkinTexture = 0
        playerCapeTexture = 0
        playerVertexBuffer = 0
        playerVertexArray = 0
        playerProgram = 0
        materialTextures.fill(0)
        sunTexture = 0
        cloudTexture = 0
        waterCloudNormalTexture = 0
        waterNoiseTexture = 0
        shadowTexture = 0
        shadowFramebuffer = 0
        shadowProgram = 0
    }

    private fun renderCelestial(sun: SunState, passView: Matrix4f, passEye: Vector3f) {
        glDisable(GL_DEPTH_TEST)
        glDepthMask(false)
        glDisable(GL_BLEND)
        glDisable(GL_CULL_FACE)

        inverseProjection.set(projection).invert()
        inverseView.set(passView).invert()
        glUseProgram(skyProgram)
        glUniform3f(skyColorLocation, NOON_SKY_COLOR.red, NOON_SKY_COLOR.green, NOON_SKY_COLOR.blue)
        uploadMatrix(skyInverseProjectionLocation, inverseProjection)
        uploadMatrix(skyInverseViewLocation, inverseView)
        glUniform3f(skySunDirectionLocation, -sun.directionX, -sun.directionY, -sun.directionZ)
        glBindVertexArray(skyVertexArray)
        glDrawArrays(GL_TRIANGLES, 0, 3)
        glBindVertexArray(0)
        glUseProgram(0)

        val basis = BackgroundScene.sunBillboardBasis(passEye.x, passEye.y, passEye.z, sun)
        glUseProgram(sunProgram)
        uploadMatrix(sunProjectionLocation, projection)
        uploadMatrix(sunViewLocation, passView)
        glUniform3f(sunOffsetLocation, sun.offsetX, sun.offsetY, sun.offsetZ)
        glUniform3f(sunRightLocation, basis.rightX, basis.rightY, basis.rightZ)
        glUniform3f(sunUpLocation, basis.upX, basis.upY, basis.upZ)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, sunTexture)
        glUniform1i(sunTextureLocation, 0)
        glEnable(GL_BLEND)
        glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
        glBindVertexArray(sunVertexArray)
        glDrawArrays(GL_TRIANGLES, 0, sunVertices.size / 2)
        glBindVertexArray(0)
        glUseProgram(0)
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)

        glDepthMask(true)
        glEnable(GL_DEPTH_TEST)
        glEnable(GL_BLEND)
        glEnable(GL_CULL_FACE)
    }

    private fun renderClouds(time: Double, passView: Matrix4f, reflection: Boolean, sun: SunState) {
        val coordinates = if (reflection) {
            requireNotNull(mainCloudCoordinates) { "Reflection cloud pass requires the main cloud cache" }
        } else {
            BackgroundScene.cloudCoordinates(time, eye.x.toDouble(), eye.y.toDouble(), eye.z.toDouble()).also {
                mainCloudCoordinates = it
            }
        }
        val cloudColor = NOON_CLOUD_COLOR
        if (coordinates.floorX != previousCloudX ||
            coordinates.floorY != previousCloudY ||
            coordinates.floorZ != previousCloudZ ||
            BackgroundScene.cloudColorChanged(previousCloudColor, cloudColor)
        ) {
            previousCloudX = coordinates.floorX
            previousCloudY = coordinates.floorY
            previousCloudZ = coordinates.floorZ
            previousCloudColor = cloudColor
            val vertices = buildFancyCloudVertices(coordinates, cloudColor)
            glBindBuffer(GL_ARRAY_BUFFER, cloudVertexBuffer)
            val cloudBuffer = BufferUtils.createFloatBuffer(vertices.size).put(vertices).flip()
            glBufferData(GL_ARRAY_BUFFER, cloudBuffer, GL_STATIC_DRAW)
            cloudVertexCount = vertices.size / CLOUD_VERTEX_FLOATS
        }
        cloudView.set(passView).m30(0f).m31(0f).m32(0f)
        glUseProgram(cloudProgram)
        uploadMatrix(cloudProjectionLocation, projection)
        uploadMatrix(cloudViewLocation, cloudView)
        glUniform2f(cloudOffsetLocation, coordinates.fractionX, coordinates.fractionZ)
        glUniform1f(
            cloudHeightLocation,
            if (reflection) reflectionCloudHeight(eye.y, reflectionEye.y, coordinates.fractionY) else coordinates.fractionY
        )
        glUniform3f(cloudFogColorLocation, NOON_CLOUD_FOG_COLOR.red, NOON_CLOUD_FOG_COLOR.green, NOON_CLOUD_FOG_COLOR.blue)
        glUniform1f(cloudFogStartLocation, CLOUD_FOG_START)
        glUniform1f(cloudFogEndLocation, CLOUD_FOG_END)
        glUniform3f(cloudSunDirectionLocation, sun.directionX, sun.directionY, sun.directionZ)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, cloudTexture)
        glUniform1i(cloudTextureLocation, 0)
        glBindVertexArray(cloudVertexArray)

        // Match Minecraft's depth-only cloud prepass before the translucent color pass.
        glFrontFace(GL_CCW)
        glDisable(GL_CULL_FACE)
        glColorMask(false, false, false, false)
        glDepthMask(true)
        glDrawArrays(GL_TRIANGLES, 0, cloudVertexCount)
        glColorMask(true, true, true, true)
        glDepthMask(true)
        glDrawArrays(GL_TRIANGLES, 0, cloudVertexCount)
        glDepthMask(true)

        glBindVertexArray(0)
        glEnable(GL_CULL_FACE)
        glFrontFace(GL_CCW)
        glUseProgram(0)
    }

    private fun uploadMatrix(location: Int, matrix: Matrix4f) {
        matrixBuffer.clear()
        matrix.get(matrixBuffer)
        matrixBuffer.position(0)
        glUniformMatrix4fv(location, false, matrixBuffer)
    }

    private fun uploadPlayerMatrix(location: Int, matrix: Matrix4f) {
        playerMatrixBuffer.clear()
        matrix.get(playerMatrixBuffer)
        playerMatrixBuffer.position(0)
        glUniformMatrix4fv(location, false, playerMatrixBuffer)
    }

    private fun buildSceneMesh(): SceneMesh {
        val values = SceneVertexBuckets()
        data class StaticCube(
            val x: Float,
            val y: Float,
            val z: Float,
            val material: BlockMaterial,
            val sizeX: Float,
            val sizeY: Float,
            val sizeZ: Float,
            val topMaterial: BlockMaterial = material,
            val bottomMaterial: BlockMaterial = material
        )
        val staticCubes = ArrayList<StaticCube>()
        BackgroundScene.harborBlocks().forEach { block ->
            staticCubes += StaticCube(
                block.x, block.y, block.z, block.material,
                block.sizeX, block.sizeY, block.sizeZ, block.topMaterial, block.bottomMaterial
            )
        }
        BackgroundScene.dunes(sceneSeed).forEach { dune ->
            val layers = (dune.height / 0.8f).toInt().coerceIn(2, 7)
            repeat(layers) { layer ->
                val fraction = 1f - layer.toFloat() / layers * 0.38f
                staticCubes += StaticCube(
                    dune.x, WATER_LEVEL + layer * 0.8f + 0.4f, dune.z, BlockMaterial.SAND,
                    dune.radius * fraction, 0.8f, dune.radius * fraction
                )
            }
        }
        BackgroundScene.cacti(sceneSeed).forEach { cactus ->
            staticCubes += StaticCube(
                cactus.x, cactus.groundY + cactus.height * 0.5f, cactus.z, BlockMaterial.CACTUS_SIDE,
                0.42f, cactus.height, 0.42f, BlockMaterial.CACTUS_TOP, BlockMaterial.CACTUS_BOTTOM
            )
            if (cactus.height > 2.3f) {
                staticCubes += StaticCube(
                    cactus.x + 0.32f, cactus.groundY + cactus.height * 0.65f, cactus.z, BlockMaterial.CACTUS_SIDE,
                    0.28f, 0.8f, 0.28f, BlockMaterial.CACTUS_TOP, BlockMaterial.CACTUS_BOTTOM
                )
            }
        }
        val staticAabbs = staticCubes.map { cube ->
            staticAabb(cube.x, cube.y, cube.z, cube.sizeX, cube.sizeY, cube.sizeZ)
        }
        staticCubes.forEachIndexed { index, staticCube ->
            exposedStaticFaces(index, staticAabbs).forEach { quad ->
                val material = when (quad.direction) {
                    StaticFaceDirection.PositiveY -> staticCube.topMaterial
                    StaticFaceDirection.NegativeY -> staticCube.bottomMaterial
                    else -> staticCube.material
                }
                staticFace(
                    values,
                    quad.points,
                    floatArrayOf(quad.direction.normalX, quad.direction.normalY, quad.direction.normalZ),
                    material,
                    0,
                    staticAabbs,
                    staticAabbs[index]
                )
            }
        }
        // Local boat geometry is transformed by the animated boat pose at draw time.
        cube(values, 0f, 2.18f, 0f, BlockMaterial.OAK_PLANKS, 2, 2.7f * BOAT_VISUAL_SCALE, 0.35f * BOAT_VISUAL_SCALE, 1.1f * BOAT_VISUAL_SCALE)
        cube(values, -1.25f * BOAT_VISUAL_SCALE, 2.18f + (2.45f - 2.18f) * BOAT_VISUAL_SCALE, 0f, BlockMaterial.OAK_PLANKS, 2, 0.18f * BOAT_VISUAL_SCALE, 0.7f * BOAT_VISUAL_SCALE, 1.1f * BOAT_VISUAL_SCALE)
        cube(values, 1.25f * BOAT_VISUAL_SCALE, 2.18f + (2.45f - 2.18f) * BOAT_VISUAL_SCALE, 0f, BlockMaterial.OAK_PLANKS, 2, 0.18f * BOAT_VISUAL_SCALE, 0.7f * BOAT_VISUAL_SCALE, 1.1f * BOAT_VISUAL_SCALE)
        cube(values, 0f, 2.18f + (2.48f - 2.18f) * BOAT_VISUAL_SCALE, 0f, BlockMaterial.OAK_PLANKS, 2, 2.2f * BOAT_VISUAL_SCALE, 0.16f * BOAT_VISUAL_SCALE, 0.5f * BOAT_VISUAL_SCALE)
        plane(values, WATER_LEVEL, BlockMaterial.WATER, 1)
        return values.flatten(staticAabbs)
    }

    private fun buildSunVertices(): FloatArray {
        // Local right/up coordinates are expanded into a camera-facing billboard in the shader.
        return floatArrayOf(
            -SUN_BILLBOARD_HALF_SIZE, -SUN_BILLBOARD_HALF_SIZE,
            SUN_BILLBOARD_HALF_SIZE, -SUN_BILLBOARD_HALF_SIZE,
            SUN_BILLBOARD_HALF_SIZE, SUN_BILLBOARD_HALF_SIZE,
            -SUN_BILLBOARD_HALF_SIZE, -SUN_BILLBOARD_HALF_SIZE,
            SUN_BILLBOARD_HALF_SIZE, SUN_BILLBOARD_HALF_SIZE,
            -SUN_BILLBOARD_HALF_SIZE, SUN_BILLBOARD_HALF_SIZE
        )
    }

    private fun plane(values: SceneVertexBuckets, y: Float, material: BlockMaterial, kind: Int) {
        face(values, waterPlaneCornerPositions(WATER_EXTENT, y), floatArrayOf(0f, 1f, 0f), material, kind)
    }

    private fun cube(
        values: SceneVertexBuckets,
        x: Float,
        y: Float,
        z: Float,
        material: BlockMaterial,
        kind: Int,
        sx: Float = 1f,
        sy: Float = 1f,
        sz: Float = sx,
        topMaterial: BlockMaterial = material,
        bottomMaterial: BlockMaterial = material,
        staticAabbs: List<StaticAabb> = emptyList(),
        sourceAabb: StaticAabb? = null
    ) {
        val hx = sx * 0.5f
        val hy = sy * 0.5f
        val hz = sz * 0.5f
        face(values, floatArrayOf(x - hx, y - hy, z + hz, x + hx, y - hy, z + hz, x + hx, y + hy, z + hz, x - hx, y + hy, z + hz), floatArrayOf(0f, 0f, 1f), material, kind, staticAabbs, sourceAabb)
        face(values, floatArrayOf(x + hx, y - hy, z - hz, x - hx, y - hy, z - hz, x - hx, y + hy, z - hz, x + hx, y + hy, z - hz), floatArrayOf(0f, 0f, -1f), material, kind, staticAabbs, sourceAabb)
        face(values, floatArrayOf(x - hx, y - hy, z - hz, x - hx, y - hy, z + hz, x - hx, y + hy, z + hz, x - hx, y + hy, z - hz), floatArrayOf(-1f, 0f, 0f), material, kind, staticAabbs, sourceAabb)
        face(values, floatArrayOf(x + hx, y - hy, z + hz, x + hx, y - hy, z - hz, x + hx, y + hy, z - hz, x + hx, y + hy, z + hz), floatArrayOf(1f, 0f, 0f), material, kind, staticAabbs, sourceAabb)
        face(values, floatArrayOf(x - hx, y + hy, z + hz, x + hx, y + hy, z + hz, x + hx, y + hy, z - hz, x - hx, y + hy, z - hz), floatArrayOf(0f, 1f, 0f), topMaterial, kind, staticAabbs, sourceAabb)
        face(values, floatArrayOf(x - hx, y - hy, z - hz, x + hx, y - hy, z - hz, x + hx, y - hy, z + hz, x - hx, y - hy, z + hz), floatArrayOf(0f, -1f, 0f), bottomMaterial, kind, staticAabbs, sourceAabb)
    }

    private fun face(
        values: SceneVertexBuckets,
        points: FloatArray,
        normal: FloatArray,
        material: BlockMaterial,
        kind: Int,
        staticAabbs: List<StaticAabb> = emptyList(),
        sourceAabb: StaticAabb? = null
    ) {
        val bucket = values[material]
        val indices = intArrayOf(0, 1, 2, 0, 2, 3)
        val baseVertex = bucket.size / SCENE_VERTEX_FLOATS
        indices.forEachIndexed { emittedIndex, index ->
            val x = points[index * 3]
            val y = points[index * 3 + 1]
            val z = points[index * 3 + 2]
            val ao = if (kind == 0 && sourceAabb != null) {
                cornerAmbientOcclusion(sourceAabb, x, y, z, normal[0], normal[1], normal[2], staticAabbs)
            } else 1f
            appendSceneVertex(bucket, x, y, z, normal, material, kind, ao)
            values.appendIndex(material, baseVertex + emittedIndex)
        }
    }

    private fun staticFace(
        values: SceneVertexBuckets,
        points: FloatArray,
        normal: FloatArray,
        material: BlockMaterial,
        kind: Int,
        staticAabbs: List<StaticAabb>,
        sourceAabb: StaticAabb
    ) {
        val bucket = values[material]
        val baseVertex = bucket.size / SCENE_VERTEX_FLOATS
        repeat(4) { index ->
            val point = index * 3
            val x = points[point]
            val y = points[point + 1]
            val z = points[point + 2]
            val ao = if (kind == 0) {
                cornerAmbientOcclusion(sourceAabb, x, y, z, normal[0], normal[1], normal[2], staticAabbs)
            } else 1f
            appendSceneVertex(bucket, x, y, z, normal, material, kind, ao)
        }
        intArrayOf(0, 1, 2, 0, 2, 3).forEach { index -> values.appendIndex(material, baseVertex + index) }
    }

    private fun appendSceneVertex(
        bucket: MutableList<Float>,
        x: Float,
        y: Float,
        z: Float,
        normal: FloatArray,
        material: BlockMaterial,
        kind: Int,
        ao: Float
    ) {
        bucket += x
        bucket += y
        bucket += z
        bucket += normal[0]
        bucket += normal[1]
        bucket += normal[2]
        bucket += material.red
        bucket += material.green
        bucket += material.blue
        bucket += kind.toFloat()
        val uv = faceUv(x, y, z, normal[0], normal[1], normal[2])
        bucket += uv[0]
        bucket += uv[1]
        bucket += ao
    }
}

internal const val SCENE_VERTEX_FLOATS = 13
internal const val CLOUD_TEXTURE_LINEAR_FILTER = false
private const val CLOUD_VERTEX_FLOATS = 12
private const val CLOUD_UV_SCALE = 0.00390625f
private const val CLOUD_FACE_EPSILON = 9.765625E-4f

/** Builds the Minecraft 1.21.1 Fancy cloud mesh (POSITION_TEX_COLOR_NORMAL). */
internal fun buildFancyCloudVertices(coordinates: CloudCoordinates, cloudColor: SkyColor): FloatArray {
    val values = ArrayList<Float>()
    val thickness = CLOUD_THICKNESS
    val xTexture = floor(coordinates.x).toFloat() * CLOUD_UV_SCALE
    val zTexture = floor(coordinates.z).toFloat() * CLOUD_UV_SCALE
    val baseY = floor(coordinates.y / thickness).toFloat() * thickness
    val red = cloudColor.red
    val green = cloudColor.green
    val blue = cloudColor.blue

    for (tileX in CLOUD_TILE_MIN..CLOUD_TILE_MAX) {
        for (tileZ in CLOUD_TILE_MIN..CLOUD_TILE_MAX) {
            val x = tileX * 8f
            val z = tileZ * 8f
            if (baseY > -5f) {
                addCloudQuad(
                    values,
                    floatArrayOf(x, baseY, z + 8f, x + 8f, baseY, z + 8f, x + 8f, baseY, z, x, baseY, z),
                    floatArrayOf(x * CLOUD_UV_SCALE + xTexture, (z + 8f) * CLOUD_UV_SCALE + zTexture),
                    floatArrayOf(0f, -1f, 0f),
                    red, green, blue
                )
            }
            if (baseY <= 5f) {
                val top = baseY + thickness - CLOUD_FACE_EPSILON
                addCloudQuad(
                    values,
                    floatArrayOf(x, top, z + 8f, x + 8f, top, z + 8f, x + 8f, top, z, x, top, z),
                    floatArrayOf(x * CLOUD_UV_SCALE + xTexture, (z + 8f) * CLOUD_UV_SCALE + zTexture),
                    floatArrayOf(0f, 1f, 0f),
                    red, green, blue
                )
            }
            if (tileX > -1) {
                for (slice in 0 until 8) {
                    val sliceX = x + slice
                    addCloudQuad(
                        values,
                        floatArrayOf(sliceX, baseY, z + 8f, sliceX, baseY + thickness, z + 8f, sliceX, baseY + thickness, z, sliceX, baseY, z),
                        floatArrayOf((sliceX + 0.5f) * CLOUD_UV_SCALE + xTexture, (z + 8f) * CLOUD_UV_SCALE + zTexture),
                        floatArrayOf(-1f, 0f, 0f),
                        red, green, blue
                    )
                }
            }
            if (tileX <= 1) {
                for (slice in 0 until 8) {
                    val sliceX = x + slice + 1f - CLOUD_FACE_EPSILON
                    addCloudQuad(
                        values,
                        floatArrayOf(sliceX, baseY, z + 8f, sliceX, baseY + thickness, z + 8f, sliceX, baseY + thickness, z, sliceX, baseY, z),
                        floatArrayOf((x + slice + 0.5f) * CLOUD_UV_SCALE + xTexture, (z + 8f) * CLOUD_UV_SCALE + zTexture),
                        floatArrayOf(1f, 0f, 0f),
                        red, green, blue
                    )
                }
            }
            if (tileZ > -1) {
                for (slice in 0 until 8) {
                    val sliceZ = z + slice
                    addCloudQuad(
                        values,
                        floatArrayOf(x, baseY + thickness, sliceZ, x + 8f, baseY + thickness, sliceZ, x + 8f, baseY, sliceZ, x, baseY, sliceZ),
                        floatArrayOf(x * CLOUD_UV_SCALE + xTexture, (sliceZ + 0.5f) * CLOUD_UV_SCALE + zTexture),
                        floatArrayOf(0f, 0f, -1f),
                        red, green, blue
                    )
                }
            }
            if (tileZ <= 1) {
                for (slice in 0 until 8) {
                    val sliceZ = z + slice + 1f - CLOUD_FACE_EPSILON
                    addCloudQuad(
                        values,
                        floatArrayOf(x, baseY + thickness, sliceZ, x + 8f, baseY + thickness, sliceZ, x + 8f, baseY, sliceZ, x, baseY, sliceZ),
                        floatArrayOf(x * CLOUD_UV_SCALE + xTexture, (z + slice + 0.5f) * CLOUD_UV_SCALE + zTexture),
                        floatArrayOf(0f, 0f, 1f),
                        red, green, blue
                    )
                }
            }
        }
    }
    return values.toFloatArray()
}

private fun addCloudQuad(
    values: MutableList<Float>,
    positions: FloatArray,
    uv: FloatArray,
    normal: FloatArray,
    red: Float,
    green: Float,
    blue: Float
) {
    val indices = intArrayOf(0, 1, 2, 0, 2, 3)
    indices.forEach { index ->
        values += positions[index * 3]
        values += positions[index * 3 + 1]
        values += positions[index * 3 + 2]
        values += uv[0] + (positions[index * 3] - positions[0]) * CLOUD_UV_SCALE
        values += uv[1] + (positions[index * 3 + 2] - positions[2]) * CLOUD_UV_SCALE
        values += red
        values += green
        values += blue
        values += 0.8f
        values += normal[0]
        values += normal[1]
        values += normal[2]
    }
}

/** Projects a face onto its two varying world axes so vertical faces retain texture detail. */
internal fun faceUv(x: Float, y: Float, z: Float, normalX: Float, normalY: Float, normalZ: Float): FloatArray =
    when {
        kotlin.math.abs(normalY) > 0.5f -> floatArrayOf(x * 0.5f, z * 0.5f)
        kotlin.math.abs(normalX) > 0.5f -> floatArrayOf(z * 0.5f, y * 0.5f)
        kotlin.math.abs(normalZ) > 0.5f -> floatArrayOf(x * 0.5f, y * 0.5f)
        else -> floatArrayOf(x * 0.5f, z * 0.5f)
    }

/** Builds the fixed, CPU-only scene mesh for deterministic batch/range tests. */
internal fun buildSceneMesh(seed: Long): SceneMesh = SceneGlRenderer(seed).meshForTesting()

private class FrameBitmapBuffer : AutoCloseable {
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
        val image = SkiaImage.makeRaster(imageInfo, pixels, rowBytes)
        return try {
            image.toComposeImageBitmap()
        } finally {
            image.close()
        }
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

internal fun createProgram(vertexSource: String, fragmentSource: String): Int {
    val vertex = compileShader(GL_VERTEX_SHADER, vertexSource)
    val fragment = compileShader(GL_FRAGMENT_SHADER, fragmentSource)
    val program = glCreateProgram()
    glAttachShader(program, vertex)
    glAttachShader(program, fragment)
    glLinkProgram(program)
    val linked = glGetProgrami(program, GL_LINK_STATUS)
    val log = glGetProgramInfoLog(program)
    glDeleteShader(vertex)
    glDeleteShader(fragment)
    check(linked != 0) { "Background shader link failed: $log" }
    return program
}

private fun compileShader(type: Int, source: String): Int {
    val shader = glCreateShader(type)
    glShaderSource(shader, source)
    glCompileShader(shader)
    check(glGetShaderi(shader, GL_COMPILE_STATUS) != 0) {
        "Background shader compile failed: ${glGetShaderInfoLog(shader)}"
    }
    return shader
}

internal const val SHADOW_VERTEX_SHADER = """
#version 450 core
layout(location = 0) in vec3 aPosition;
layout(location = 3) in float aKind;
layout(location = 4) in vec2 aUv;
uniform mat4 uShadowViewProjection;
flat out int vKind;
out vec2 vUv;
void main() {
    vKind = int(aKind + 0.5);
    vUv = aUv;
    gl_Position = uShadowViewProjection * vec4(aPosition, 1.0);
}
"""

internal const val SHADOW_FRAGMENT_SHADER = """
#version 450 core
flat in int vKind;
in vec2 vUv;
uniform sampler2D uTexture;
uniform int uMaterial;
out vec4 color;
void main() {
    if (vKind != 0) discard;
    vec2 uv = fract(vUv);
    if (uMaterial == 7) uv = vec2(uv.x, uv.y / 3.0);
    vec4 texel = texture(uTexture, uv);
    bool cutout = (uMaterial >= 4 && uMaterial <= 6) || uMaterial == 7;
    if (cutout && texel.a < 0.1) discard;
    color = vec4(1.0);
}
"""

internal const val VERTEX_SHADER = """
#version 450 core
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec3 aColor;
layout(location = 3) in float aKind;
layout(location = 4) in vec2 aUv;
layout(location = 5) in float aAmbientOcclusion;
uniform mat4 uProjection;
uniform mat4 uView;
uniform mat4 uBoatModel;
uniform float uClipHeight;
uniform mat4 uShadowViewProjection;
out vec3 vNormal;
out vec3 vColor;
out vec2 vUv;
out vec3 vWorldPosition;
out vec4 vShadowClipPosition;
out float vAmbientOcclusion;
void main() {
    vec3 position = aPosition;
    vec3 normal = aNormal;
    if (aKind > 1.5 && aKind < 2.5) {
        position = (uBoatModel * vec4(position, 1.0)).xyz;
        normal = mat3(uBoatModel) * normal;
    }
    vec4 worldPosition = vec4(position, 1.0);
    gl_ClipDistance[0] = worldPosition.y - uClipHeight;
    gl_Position = uProjection * uView * worldPosition;
    vNormal = normal;
    vColor = aColor;
    vUv = aUv;
    vWorldPosition = position;
    vShadowClipPosition = uShadowViewProjection * worldPosition;
    vAmbientOcclusion = aAmbientOcclusion;
}
"""

internal const val FRAGMENT_SHADER = """
#version 450 core
in vec3 vNormal;
in vec3 vColor;
in vec2 vUv;
in vec3 vWorldPosition;
in vec4 vShadowClipPosition;
in float vAmbientOcclusion;
uniform vec3 uSunDirection;
uniform sampler2D uTexture;
uniform sampler2DShadow uShadowMap;
uniform int uMaterial;
out vec4 color;
void main() {
    float light = max(dot(normalize(vNormal), normalize(-uSunDirection)), 0.0);
    vec2 uv = fract(vUv);
    if (uMaterial == 7) uv = vec2(uv.x, uv.y / 3.0);
    vec4 texel = texture(uTexture, uv);
    bool cutout = (uMaterial >= 4 && uMaterial <= 6) || uMaterial == 7;
    if (cutout && texel.a < 0.1) discard;
    vec3 shadowCoord = vShadowClipPosition.xyz / vShadowClipPosition.w * 0.5 + 0.5;
    float shadowFactor = 1.0;
    vec3 lightDirection = normalize(-uSunDirection);
    float ndotl = max(dot(normalize(vNormal), lightDirection), 0.0);
    float shadowBias = max(0.0008 * (1.0 - ndotl), 0.00025);
    if (vShadowClipPosition.w > 0.0 && shadowCoord.z > 0.0 && shadowCoord.z < 1.0 &&
        all(greaterThanEqual(shadowCoord.xy, vec2(0.0))) && all(lessThanEqual(shadowCoord.xy, vec2(1.0)))) {
        vec2 texelSize = 1.0 / vec2(textureSize(uShadowMap, 0));
        shadowFactor = 0.0;
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                shadowFactor += texture(uShadowMap, vec3(shadowCoord.xy + vec2(x, y) * texelSize, shadowCoord.z - shadowBias));
            }
        }
        shadowFactor /= 9.0;
    }
    vec3 ambientLight = vec3(0.46, 0.48, 0.52) * mix(1.0, vAmbientOcclusion, 0.55);
    float directShadow = mix(0.22, 1.0, shadowFactor);
    vec3 directLight = vec3(1.10, 0.90, 0.68) * light * directShadow;
    vec3 litColor = vColor * texel.rgb * (ambientLight + directLight);
    if (uMaterial == 7) {
        float lanternBrightness = max(texel.r, max(texel.g, texel.b));
        float emissionMask = smoothstep(0.25, 0.75, lanternBrightness);
        litColor += vColor * vec3(1.8, 0.72, 0.16) * emissionMask;
    }
    color = vec4(litColor, texel.a);
}
"""

internal const val NOON_SKY_FUNCTIONS = """
float pow2(float value) {
    return value * value;
}

float sqrt1(float value) {
    return value * (2.0 - value);
}

float sqrt3(float value) {
    value = 1.0 - value;
    value *= value;
    value *= value;
    value *= value;
    return 1.0 - value;
}

float smoothstep1(float value) {
    return value * value * (3.0 - 2.0 * value);
}

vec3 noonSkyColor(vec3 worldRay, vec3 sunDirection, vec3 skyColor) {
    float VdotU = dot(worldRay, vec3(0.0, 1.0, 0.0));
    float VdotS = dot(worldRay, normalize(sunDirection));
    float VdotSM1 = pow2(max(VdotS, 0.0));
    float VdotSM2 = pow2(VdotSM1);
    vec3 skyColorSqrt = sqrt(skyColor);
    vec3 noonUpSkyColor = pow(skyColorSqrt, vec3(2.9)) * vec3(0.85, 0.92, 0.81);
    vec3 noonMiddleSkyColor = pow(skyColorSqrt, vec3(1.5)) * 1.3 + noonUpSkyColor * 0.65;
    vec3 noonDownSkyColor = skyColorSqrt * 0.9 + noonUpSkyColor * 0.25;
    float VdotUmax0 = max(VdotU, 0.0);
    float VdotUM1 = pow2(1.0 - VdotUmax0);
    VdotUM1 = pow(VdotUM1, 1.0 - VdotSM2 * 0.4);
    vec3 finalSky = mix(noonUpSkyColor, noonMiddleSkyColor * (1.0 + VdotSM2 * 0.3), VdotUM1);
    float VdotUM3 = min(max(-VdotU + 0.08, 0.0) / 0.35, 1.0);
    VdotUM3 = smoothstep1(VdotUM3);
    vec3 scatteredGroundMixer = vec3(pow2(VdotUM3), sqrt1(VdotUM3), sqrt3(VdotUM3));
    scatteredGroundMixer = mix(vec3(VdotUM3), scatteredGroundMixer, 0.75);
    finalSky = mix(finalSky, noonDownSkyColor, scatteredGroundMixer);
    if (VdotS > 0.0) {
        float glareScatter = 3.0 * (2.0 - clamp(VdotS * 1000.0, 0.0, 1.0));
        float VdotSM4 = pow(abs(VdotS), glareScatter);
        float visfactor = 0.075;
        float glare = visfactor / (1.0 - (1.0 - visfactor) * VdotSM4) - visfactor;
        glare *= 0.7;
        vec3 glareColor = vec3(1.5, 0.7, 0.3) + vec3(0.0, 0.5, 0.5);
        finalSky += glareColor * glare;
    }
    return finalSky;
}
"""

internal const val WATER_VERTEX_SHADER = """
#version 450 core
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec3 aColor;
layout(location = 3) in float aKind;
uniform mat4 uProjection;
uniform mat4 uView;
uniform mat4 uReflectionViewProjection;
uniform float uTime;
out vec3 vNormal;
out vec3 vColor;
out vec3 vWorldPosition;
out vec4 vReflectionClipPosition;
void main() {
    vec3 position = aPosition;
    position.y += sin(position.x * 0.8 + uTime * 1.4) * 0.07
        + cos(position.z * 0.5 + uTime) * 0.04;
    vWorldPosition = position;
    vNormal = aNormal;
    vColor = aColor;
    vec4 worldPosition = vec4(position, 1.0);
    gl_Position = uProjection * uView * worldPosition;
    vReflectionClipPosition = uReflectionViewProjection * worldPosition;
}
"""

internal const val WATER_FRAGMENT_SHADER = """
#version 450 core
in vec3 vNormal;
in vec3 vColor;
in vec3 vWorldPosition;
in vec4 vReflectionClipPosition;
uniform float uWaterNormalOffset;
uniform vec3 uEyePosition;
uniform vec3 uSunDirection;
uniform vec3 uSkyColor;
uniform mat4 uInverseViewProjection;
uniform vec3 uHorizonColor;
uniform float uHorizonFogStart;
uniform float uHorizonFogEnd;
uniform sampler2D uCloudWaterNormal;
uniform sampler2D uWaterNoise;
uniform sampler2D uSceneColor;
uniform sampler2D uSceneDepth;
uniform sampler2D uReflectionColor;
out vec4 color;

${NOON_SKY_FUNCTIONS}

vec3 reconstructWorldPosition(vec2 uv, float depth) {
    vec4 clipPosition = uInverseViewProjection * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return clipPosition.xyz / clipPosition.w;
}

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

vec3 ggxSunHighlight(vec3 normal, vec3 viewDirection, vec3 lightDirection) {
    float NdotV = max(dot(normal, viewDirection), 0.001);
    float NdotL = max(dot(normal, lightDirection), 0.0);
    if (NdotL <= 0.0) return vec3(0.0);
    vec3 halfDirection = normalize(viewDirection + lightDirection);
    float NdotH = max(dot(normal, halfDirection), 0.0);
    float VdotH = max(dot(viewDirection, halfDirection), 0.0);
    float roughness = 0.12;
    float alpha = roughness * roughness;
    float alphaSquared = alpha * alpha;
    float denominator = NdotH * NdotH * (alphaSquared - 1.0) + 1.0;
    float distribution = alphaSquared / (3.14159265 * denominator * denominator);
    float k = (roughness + 1.0) * (roughness + 1.0) / 8.0;
    float geometryV = NdotV / (NdotV * (1.0 - k) + k);
    float geometryL = NdotL / (NdotL * (1.0 - k) + k);
    float geometry = geometryV * geometryL;
    vec3 f0 = vec3(0.02);
    vec3 fresnel = f0 + (1.0 - f0) * pow(1.0 - VdotH, 5.0);
    vec3 highlight = distribution * geometry * fresnel / (4.0 * NdotV * NdotL)
        * vec3(1.0, 0.84, 0.62) * 0.08;
    return min(highlight, vec3(1.8));
}

void main() {
    const float BASE_WATER_OPACITY = 0.65;
    float waterDistance = length(vWorldPosition.xz - uEyePosition.xz);
    vec3 baseWater = vColor;
    float waterNoise = texture(uWaterNoise, vWorldPosition.xz * 0.018).g;
    baseWater *= exp((waterNoise - 0.5) * 0.16);
    float nearWater = 1.0 - smoothstep(18.0, 110.0, waterDistance);
    baseWater *= mix(vec3(1.0), vec3(0.92, 0.98, 1.04), nearWater * 0.35);
    vec3 viewDirection = normalize(uEyePosition - vWorldPosition);
    float viewEdge = 1.0 - clamp(dot(vec3(0.0, 1.0, 0.0), viewDirection), 0.0, 1.0);

    vec2 waterPos = 0.032 * (vWorldPosition.xz + vWorldPosition.y * 2.0);
    waterPos *= 2.5;
    vec2 wind = vec2(0.0, -uWaterNormalOffset);
    vec2 medium = texture(uCloudWaterNormal, waterPos + wind).rg * 2.0 - 1.0;
    vec2 small = texture(uCloudWaterNormal, waterPos * 4.0 - wind * 2.0).rg * 2.0 - 1.0;
    vec2 big = texture(uCloudWaterNormal, waterPos * 0.25 - wind * 0.5).rg * 2.0 - 1.0;
    vec2 extraBig = texture(uCloudWaterNormal, waterPos * 0.05 - wind * 0.05).rg * 2.0 - 1.0;
    vec2 normalXY = (medium * 1.10 + small * 0.35 + big * 1.55 + extraBig * 0.85)
        * (0.27 * (1.0 - 0.60 * viewEdge));
    float normalLength = min(length(normalXY), 0.98);
    normalXY = normalLength > 0.0 ? normalize(normalXY) * normalLength : vec2(0.0);
    vec3 surfaceNormal = normalize(vec3(normalXY.x, sqrt(max(1.0 - dot(normalXY, normalXY), 0.0)), normalXY.y));

    float NdotV = clamp(dot(surfaceNormal, viewDirection), 0.0, 1.0);
    float fresnel = 0.02 + 0.98 * pow(1.0 - NdotV, 5.0);

    vec2 screenUv = gl_FragCoord.xy / vec2(textureSize(uSceneColor, 0));
    float surfaceDepth = gl_FragCoord.z;
    vec2 refractionOffset = normalXY * 0.012;
    vec2 candidateUv = clamp(screenUv + refractionOffset, vec2(0.001), vec2(0.999));
    float candidateDepth = texture(uSceneDepth, candidateUv).r;
    bool candidateSky = candidateDepth >= 0.999;
    vec3 candidateWorld = reconstructWorldPosition(candidateUv, candidateDepth);
    bool candidateBehind = candidateDepth > surfaceDepth + 0.0001 && candidateWorld.y <= vWorldPosition.y + 0.15;
    vec2 transmissionUv = (candidateSky || candidateBehind) ? candidateUv : screenUv;
    float sceneDepth = texture(uSceneDepth, transmissionUv).r;
    vec3 sceneWorld = reconstructWorldPosition(transmissionUv, sceneDepth);
    bool realGeometry = sceneDepth < 0.999 && sceneDepth > surfaceDepth + 0.0001 && sceneWorld.y <= vWorldPosition.y;
    float thickness = sceneDepth >= 0.999
        ? 24.0
        : realGeometry
            ? clamp(distance(sceneWorld, vWorldPosition), 0.0, 24.0)
            : 0.05;
    vec3 absorption = exp(-vec3(0.22, 0.10, 0.035) * thickness);
    vec3 sceneTransmission = texture(uSceneColor, transmissionUv).rgb;
    vec3 tintedWater = mix(baseWater, sceneTransmission, absorption);
    float waterAlpha = mix(BASE_WATER_OPACITY, 1.0, fresnel);
    tintedWater = mix(sceneTransmission, tintedWater, waterAlpha);

    float foamMask = realGeometry
        ? (1.0 - smoothstep(0.12, 1.35, thickness)) * smoothstep(0.38, 0.68, waterNoise)
        : 0.0;

    vec3 reflectedDirection = reflect(-viewDirection, surfaceNormal);
    vec3 skyReflection = noonSkyColor(reflectedDirection, normalize(-uSunDirection), uSkyColor);
    float reflectionW = vReflectionClipPosition.w;
    vec2 reflectionUv = vReflectionClipPosition.xy / vReflectionClipPosition.w * 0.5 + 0.5;
    bool reflectionInBounds = vReflectionClipPosition.w > 0.0 &&
        reflectionUv.x >= 0.0 && reflectionUv.x <= 1.0 &&
        reflectionUv.y >= 0.0 && reflectionUv.y <= 1.0;
    vec3 planarReflection = skyReflection;
    if (reflectionInBounds) {
        vec2 sampledReflectionUv = clamp(reflectionUv + normalXY * 0.012, vec2(0.001), vec2(0.999));
        float distanceLod = clamp((waterDistance - 24.0) / 88.0 * 2.5, 0.0, 2.5);
        float edgeLod = viewEdge * 0.65;
        float reflectionLod = clamp(distanceLod + edgeLod, 0.0, 3.5);
        planarReflection = textureLod(uReflectionColor, sampledReflectionUv, reflectionLod).rgb;
    }
    float reflectionStrength = clamp(0.06 + fresnel * 0.92, 0.0, 0.92);
    vec3 result = mix(tintedWater, planarReflection, reflectionStrength);
    result = mix(result, vec3(0.82, 0.96, 0.98), foamMask * 0.42);
    float solarFade = (1.0 - smoothstep(80.0, 180.0, waterDistance)) * (1.0 - 0.35 * viewEdge);
    result += ggxSunHighlight(surfaceNormal, viewDirection, normalize(-uSunDirection)) * solarFade;

    float horizon = smoothstep(uHorizonFogStart, uHorizonFogEnd, waterDistance);
    result = mix(result, uHorizonColor, horizon);
    color = vec4(result, 1.0);
}
"""

private const val PLAYER_VERTEX_FLOATS = 11
internal const val PLAYER_VERTEX_SHADER = """
#version 450 core
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec2 aUv;
layout(location = 2) in vec3 aNormal;
layout(location = 3) in float aPart;
layout(location = 4) in float aTexture;
uniform mat4 uProjection;
uniform mat4 uView;
uniform mat4 uModel;
uniform mat4 uBones[7];
uniform float uClipHeight;
out vec2 vUv;
out vec3 vNormal;
flat out int vTexture;
void main() {
    mat4 bone = uBones[int(aPart + 0.5)];
    vec4 worldPosition = uModel * bone * vec4(aPosition, 1.0);
    vNormal = mat3(uModel * bone) * aNormal;
    vUv = aUv;
    vTexture = int(aTexture + 0.5);
    gl_ClipDistance[0] = worldPosition.y - uClipHeight;
    gl_Position = uProjection * uView * worldPosition;
}
"""

private const val PLAYER_FRAGMENT_SHADER = """
#version 450 core
in vec2 vUv;
in vec3 vNormal;
flat in int vTexture;
uniform sampler2D uSkin;
uniform sampler2D uCape;
out vec4 color;
void main() {
    vec4 skin = vTexture == 0
        ? texture(uSkin, vUv / vec2(textureSize(uSkin, 0)))
        : texture(uCape, vUv / vec2(textureSize(uCape, 0)));
    if (skin.a < 0.05) discard;
    float light = 0.68 + max(dot(normalize(vNormal), normalize(vec3(0.35, 0.85, 0.75))), 0.0) * 0.32;
    color = vec4(skin.rgb * light, skin.a);
}
"""

private val SKY_TRIANGLE = floatArrayOf(
    -1f, -1f,
    3f, -1f,
    -1f, 3f
)

internal const val SKY_VERTEX_SHADER = """
#version 450 core
layout(location = 0) in vec2 aPosition;
out vec2 vNdc;
void main() {
    vNdc = aPosition;
    gl_Position = vec4(aPosition, 1.0, 1.0);
}
"""

internal const val SKY_FRAGMENT_SHADER = """
#version 450 core
in vec2 vNdc;
uniform vec3 uSkyColor;
uniform mat4 uInverseProjection;
uniform mat4 uInverseView;
uniform vec3 uSunDirection;
out vec4 color;

${NOON_SKY_FUNCTIONS}

float Bayer2(vec2 coordinate) {
    coordinate = 0.5 * floor(coordinate);
    return fract(1.5 * fract(coordinate.y) + coordinate.x);
}

float Bayer4(vec2 coordinate) {
    return 0.25 * Bayer2(0.5 * coordinate) + Bayer2(coordinate);
}

float Bayer8(vec2 coordinate) {
    return 0.25 * Bayer4(0.5 * coordinate) + Bayer2(coordinate);
}

void main() {
    vec4 viewRay = uInverseProjection * vec4(vNdc, 1.0, 1.0);
    vec3 worldRay = normalize(mat3(uInverseView) * (viewRay.xyz / viewRay.w));
    vec3 finalSky = noonSkyColor(worldRay, normalize(uSunDirection), uSkyColor);

    finalSky += (Bayer8(gl_FragCoord.xy) - 0.5) / 128.0;
    color = vec4(finalSky, 1.0);
}
"""

internal val SUN_VERTEX_SHADER = """
#version 450 core
layout(location = 0) in vec2 aPosition;
uniform mat4 uProjection;
uniform mat4 uView;
uniform vec3 uSunOffset;
uniform vec3 uSunRight;
uniform vec3 uSunUp;
out vec2 vUv;
void main() {
    vec3 position = uSunOffset + aPosition.x * uSunRight + aPosition.y * uSunUp;
    gl_Position = uProjection * uView * vec4(position, 1.0);
    vUv = aPosition / (2.0 * ${SUN_BILLBOARD_HALF_SIZE}) + 0.5;
}
"""

internal const val SUN_FRAGMENT_SHADER = """
#version 450 core
in vec2 vUv;
uniform sampler2D uSunTexture;
out vec4 color;
void main() {
    vec2 centered = vUv - 0.5;
    vec2 discUv = centered * 1.8 + 0.5;
    bool insideDisc = all(greaterThanEqual(discUv, vec2(0.0))) && all(lessThanEqual(discUv, vec2(1.0)));
    vec4 disc = insideDisc ? texture(uSunTexture, discUv) : vec4(0.0);
    float squareDistance = max(abs(centered.x), abs(centered.y));
    float discCoverage = 1.0 - smoothstep(0.20, 0.27, squareDistance);
    float discDetail = max(disc.r, max(disc.g, disc.b));
    vec3 discColor = vec3(1.0, 0.88, 0.68) + disc.rgb * 0.35 + vec3(discDetail * 0.15);
    float radial = max(1.0 - length(centered) * 2.0, 0.0);
    float halo = pow(radial, 3.0) * 0.65;
    vec3 hdrSun = discColor * discCoverage * 2.0 + vec3(1.0, 0.72, 0.42) * halo;
    float alpha = max(discCoverage, halo);
    if (alpha < 0.01) discard;
    color = vec4(hdrSun, alpha);
}
"""

internal val CLOUD_VERTEX_SHADER = """
#version 450 core
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec2 aUv;
layout(location = 2) in vec4 aColor;
layout(location = 3) in vec3 aNormal;
uniform mat4 uProjection;
uniform mat4 uView;
uniform vec2 uCloudOffset;
uniform float uCloudHeight;
out vec2 vUv;
out vec4 vColor;
out vec3 vNormal;
out float vViewDistance;
void main() {
    vec3 position = vec3(
        (aPosition.x - uCloudOffset.x) * ${CLOUD_HORIZONTAL_SCALE},
        aPosition.y + uCloudHeight,
        (aPosition.z - uCloudOffset.y) * ${CLOUD_HORIZONTAL_SCALE}
    );
    vec4 viewPosition = uView * vec4(position, 1.0);
    gl_Position = uProjection * viewPosition;
    vViewDistance = length(viewPosition.xyz);
    vUv = aUv;
    vColor = aColor;
    vNormal = aNormal;
}
"""

internal const val CLOUD_FRAGMENT_SHADER = """
#version 450 core
in vec2 vUv;
in vec4 vColor;
in vec3 vNormal;
in float vViewDistance;
uniform sampler2D uCloudTexture;
uniform vec3 uSunDirection;
uniform vec3 uCloudFogColor;
uniform float uCloudFogStart;
uniform float uCloudFogEnd;
out vec4 color;
void main() {
    vec4 texel = texture(uCloudTexture, vUv);
    if (texel.a < 0.1) discard;
    vec4 cloudColor = texel * vColor;
    vec3 normal = normalize(vNormal);
    vec3 lightDirection = normalize(-uSunDirection);
    float ndotl = dot(normal, lightDirection);
    float wrapped = clamp((ndotl + 0.55) / 1.55, 0.0, 1.0);
    float transmission = max(dot(-normal, lightDirection), 0.0) * 0.18;
    vec3 coolAmbient = vec3(0.82, 0.86, 0.92);
    vec3 warmSun = vec3(1.0, 0.84, 0.66);
    float ambient = 0.94 + 0.12 * wrapped;
    float direct = 0.50 * max(ndotl, 0.0);
    vec3 lighting = coolAmbient * ambient
        + warmSun * (direct + transmission);
    vec3 litRgb = cloudColor.rgb * lighting;
    float fogValue = vViewDistance < uCloudFogEnd
        ? smoothstep(uCloudFogStart, uCloudFogEnd, vViewDistance)
        : 1.0;
    color = vec4(mix(litRgb, uCloudFogColor, fogValue), cloudColor.a);
}
"""
