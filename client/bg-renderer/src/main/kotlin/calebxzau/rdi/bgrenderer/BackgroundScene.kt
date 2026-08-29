package calebxzau.rdi.bgrenderer

import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.floor
import kotlin.random.Random

enum class BlockMaterial(val red: Float, val green: Float, val blue: Float, val texturePath: String?) {
    SAND(1.0f, 0.94f, 0.72f, "assets/minecraft/textures/block/sand.png"),
    OAK_PLANKS(0.88f, 0.68f, 0.44f, "assets/minecraft/textures/block/oak_planks.png"),
    OAK_LOG(0.88f, 0.68f, 0.44f, "assets/minecraft/textures/block/oak_log.png"),
    OAK_LOG_TOP(0.95f, 0.78f, 0.56f, "assets/minecraft/textures/block/oak_log_top.png"),
    CACTUS_SIDE(0.70f, 0.92f, 0.58f, "assets/minecraft/textures/block/cactus_side.png"),
    CACTUS_TOP(0.74f, 0.96f, 0.62f, "assets/minecraft/textures/block/cactus_top.png"),
    CACTUS_BOTTOM(0.62f, 0.84f, 0.48f, "assets/minecraft/textures/block/cactus_bottom.png"),
    LANTERN(1.0f, 0.86f, 0.54f, "assets/minecraft/textures/block/lantern.png"),
    WATER(0.08f, 0.30f, 0.58f, null)
}

data class CameraPose(val yawRadians: Float, val height: Float, val distance: Float)

data class DesertDune(val x: Float, val z: Float, val radius: Float, val height: Float)
data class Cactus(val x: Float, val z: Float, val height: Float, val groundY: Float = 1.2f)
data class BoatPose(val x: Float, val y: Float, val z: Float, val yawRadians: Float, val rollRadians: Float)
data class PlayerAnchor(val x: Float, val y: Float, val z: Float)

data class SunState(
    val offsetX: Float,
    val offsetY: Float,
    val offsetZ: Float,
    val directionX: Float,
    val directionY: Float,
    val directionZ: Float
)

data class SkyColor(val red: Float, val green: Float, val blue: Float)

data class SunBillboardBasis(
    val rightX: Float,
    val rightY: Float,
    val rightZ: Float,
    val upX: Float,
    val upY: Float,
    val upZ: Float
)

const val WATER_LEVEL = 2.35f
const val TARGET_FPS = 20
const val RENDER_WIDTH = 1280
const val RENDER_HEIGHT = 720
const val CLOUD_HEIGHT = 38f
const val CLOUD_HORIZONTAL_SCALE = 8f
const val CLOUD_THICKNESS = 3f
const val CLOUD_TILE_MIN = -6
const val CLOUD_TILE_MAX = 6
const val CLOUD_FOG_START = 300f
const val CLOUD_FOG_END = 400f
const val CLOUD_COORDINATE_WRAP = 2048
const val CLOUD_TEXTURE_COORDINATE_SCALE = 0.00390625f
const val CLOUD_TIME_SPEED = 0.6
const val CLOUD_COLOR_REBUILD_THRESHOLD = 2.0e-4f
const val CAMERA_CYCLE_SECONDS = 60.0
const val CAMERA_DISTANCE = 50f
const val CAMERA_BASE_HEIGHT = 8f
const val CAMERA_HEIGHT_SWING = 0.25f
const val CAMERA_TARGET_X = 0f
const val CAMERA_TARGET_Y = 5f
const val CAMERA_TARGET_Z = -25f
const val CAMERA_FOV_DEGREES = 60f
const val CAMERA_CENTER_YAW_RADIANS = 1.5707964f
const val CAMERA_SWING_RADIANS = 0.045f
const val CAMERA_FAR_PLANE = 400f
const val CAMERA_NEAR_PLANE = 0.1f
internal const val SUN_BILLBOARD_HALF_SIZE = 4.45f
internal const val SCENE_VERTEX_BUDGET = 14_000
const val REFLECTION_WIDTH = RENDER_WIDTH / 2
const val REFLECTION_HEIGHT = RENDER_HEIGHT / 2
const val BOAT_SLIDE_PERIOD_SECONDS = 18.0
const val BOAT_CENTER_X = -7f
const val BOAT_CENTER_Z = 25f
internal const val BOAT_VISUAL_SCALE = 1.25f
internal const val PLAYER_VISUAL_SCALE = 0.08f
const val BOAT_SLIDE_DISTANCE = 1.2f
const val BOAT_WATERLINE = WATER_LEVEL + 0.12f
const val BOAT_MODEL_BASE_Y = WATER_LEVEL - 2.18f
const val PLAYER_SEAT_LOCAL_Y = 1.65f
const val WATER_ANIMATION_PERIOD_SECONDS = 31.41592653589793
const val WATER_NORMAL_SCROLL_SPEED = 1.10 * 0.018 * 2.5
const val WATER_NORMAL_SCROLL_PERIOD = 20.0
const val WATER_EXTENT = 640f
const val WATER_HORIZON_FOG_START = 260f
const val WATER_HORIZON_FOG_END = 360f

/** Mirrors a world-space height around the water plane for the planar reflection camera. */
fun reflectedY(y: Float): Float = 2f * WATER_LEVEL - y

/** Converts a cloud layer offset relative to the main eye into reflection-eye space. */
fun reflectionCloudHeight(mainEyeY: Float, reflectedEyeY: Float, fractionY: Float): Float =
    fractionY + mainEyeY - reflectedEyeY

val NOON_SKY_COLOR = SkyColor(0.14f, 0.40f, 0.78f)
val NOON_WATER_HORIZON_COLOR = SkyColor(0.34f, 0.62f, 0.84f)
val NOON_CLOUD_FOG_COLOR = SkyColor(0.86f, 0.90f, 0.96f)
val NOON_CLOUD_COLOR = SkyColor(1.0f, 1.0f, 1.0f)

data class CloudCoordinates(
    val x: Double,
    val y: Double,
    val z: Double,
    val fractionX: Float,
    val fractionY: Float,
    val fractionZ: Float,
    val floorX: Int,
    val floorY: Int,
    val floorZ: Int
)

internal enum class HarborGroup {
    Foreground, Midground, Background,
    LeftDock, RightDock, LeftShelter, RightShelter,
    LeftPavilion, CenterMarket, RightLighthouse
}

internal data class HarborBlock(
    val group: HarborGroup,
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

/** Deterministic, deliberately small scene data that can be checked without a GPU. */
object BackgroundScene {
    internal fun harborBlocks(): List<HarborBlock> = buildList {
        fun addTerrace(
            group: HarborGroup,
            cx: Float,
            cz: Float,
            width: Float,
            depth: Float,
            layers: Int,
            layerHeight: Float,
            shrinkX: Float,
            shrinkZ: Float,
            driftX: Float,
            driftZ: Float,
            zigzagX: Float,
            zigzagZ: Float
        ) {
            repeat(layers) { layer ->
                val alternating = if (layer % 2 == 0) 1f else -1f
                val offsetX = if (layer == 0) 0f else layer * driftX + alternating * zigzagX
                val offsetZ = if (layer == 0) 0f else layer * driftZ + alternating * zigzagZ
                val sizeX = width - layer * shrinkX - (layer % 3) * 0.7f
                val sizeZ = depth - layer * shrinkZ - ((layer + 1) % 3) * 0.5f
                check(sizeX > 0f && sizeZ > 0f)
                add(HarborBlock(group, cx + offsetX, WATER_LEVEL + layerHeight * (layer + 0.5f), cz + offsetZ, BlockMaterial.SAND, sizeX, layerHeight, sizeZ))
            }
        }
        fun addPier(group: HarborGroup, centerX: Float) {
            listOf(-13f, -11f, -9f, -7f, -5f, -3f).forEach { z -> add(HarborBlock(group, centerX, WATER_LEVEL + 0.15f, z, BlockMaterial.OAK_PLANKS, 6f, 0.3f, 2f)) }
            listOf(-13f, -9f, -5f, -2f).forEach { z ->
                listOf(centerX - 2.6f, centerX + 2.6f).forEach { x -> add(HarborBlock(group, x, 1.65f, z, BlockMaterial.OAK_LOG, 0.28f, 3f, 0.28f)) }
            }
            listOf(centerX - 2.6f, centerX + 2.6f).forEach { x -> add(HarborBlock(group, x, 3.55f, -2f, BlockMaterial.LANTERN, 0.3f, 0.3f, 0.3f)) }
            listOf(centerX - 2.8f, centerX + 2.8f).forEach { x -> add(HarborBlock(group, x, 3.05f, -8f, BlockMaterial.OAK_LOG, 0.2f, 1f, 10f)) }
            add(HarborBlock(group, centerX, 3.05f, -13.9f, BlockMaterial.OAK_LOG, 5.8f, 1f, 0.2f))
            listOf(centerX - 1.5f, centerX + 1.5f).forEach { x -> add(HarborBlock(group, x, 3f, -5f, BlockMaterial.OAK_PLANKS, 1.2f, 1f, 1.2f)) }
        }
        fun addShelter(group: HarborGroup, cx: Float, baseY: Float, cz: Float) {
            add(HarborBlock(group, cx, baseY + 0.15f, cz, BlockMaterial.OAK_PLANKS, 10f, 0.3f, 7f))
            listOf(-4f to -2.5f, -4f to 2.5f, 4f to -2.5f, 4f to 2.5f).forEach { (dx, dz) -> add(HarborBlock(group, cx + dx, baseY + 2f, cz + dz, BlockMaterial.OAK_LOG, 0.3f, 4f, 0.3f)) }
            listOf(-1.8f, 1.8f).forEach { dz -> add(HarborBlock(group, cx, baseY + 4.25f, cz + dz, BlockMaterial.OAK_PLANKS, 12f, 0.35f, 4.2f)) }
            listOf(-3f, 3f).forEach { dx -> add(HarborBlock(group, cx + dx, baseY + 2.6f, cz - 2.7f, BlockMaterial.LANTERN, 0.3f, 0.3f, 0.3f)) }
            add(HarborBlock(group, cx, baseY + 2f, cz - 3.15f, BlockMaterial.OAK_PLANKS, 7.2f, 3.5f, 0.3f))
            listOf(cx - 4.15f, cx + 4.15f).forEach { x -> add(HarborBlock(group, x, baseY + 1.5f, cz, BlockMaterial.OAK_PLANKS, 0.3f, 2.7f, 5.4f)) }
            listOf(cx - 2.5f, cx + 2.5f).forEach { x -> add(HarborBlock(group, x, baseY + 1f, cz + 3f, BlockMaterial.OAK_LOG, 4f, 0.7f, 0.25f)) }
            listOf(cx - 2.5f, cx + 2.2f).forEach { x -> add(HarborBlock(group, x, baseY + 0.75f, cz + 1.8f, BlockMaterial.OAK_PLANKS, 1.4f, 1.2f, 1.4f)) }
        }

        addTerrace(HarborGroup.Foreground, -48f, -12f, 28f, 12f, 3, 1f, 4f, 2f, .7f, .2f, .6f, .3f)
        addTerrace(HarborGroup.Foreground, -62f, -9f, 18f, 10f, 2, 1f, 3f, 2f, -.5f, .4f, .4f, -.2f)
        addTerrace(HarborGroup.Foreground, 46f, -12f, 30f, 12f, 3, 1f, 4f, 2f, -.6f, .25f, .7f, .2f)
        addTerrace(HarborGroup.Foreground, 62f, -10f, 18f, 10f, 2, 1f, 3f, 2f, .45f, .35f, -.5f, .25f)
        addTerrace(HarborGroup.Midground, -50f, -30f, 26f, 16f, 5, 1f, 3.5f, 2f, .8f, .35f, .7f, -.25f)
        addTerrace(HarborGroup.Midground, -24f, -34f, 22f, 18f, 4, 1f, 3f, 2f, -.65f, .4f, .6f, .2f)
        addTerrace(HarborGroup.Midground, 5f, -36f, 30f, 18f, 4, 1f, 3f, 2f, .35f, .45f, .8f, -.3f)
        addTerrace(HarborGroup.Midground, 34f, -32f, 25f, 17f, 5, 1f, 3f, 2f, -.7f, .3f, .5f, .35f)
        addTerrace(HarborGroup.Midground, 57f, -30f, 18f, 15f, 4, 1f, 2.5f, 2f, .55f, .4f, .6f, -.25f)
        addTerrace(HarborGroup.Background, -54f, -54f, 34f, 22f, 8, 1.1f, 3.2f, 2f, .9f, .45f, .8f, .3f)
        addTerrace(HarborGroup.Background, -24f, -60f, 34f, 24f, 7, 1.1f, 3.2f, 2f, -.75f, .5f, .7f, -.35f)
        addTerrace(HarborGroup.Background, 10f, -64f, 40f, 24f, 6, 1.1f, 3.5f, 2f, .55f, .55f, .9f, .25f)
        addTerrace(HarborGroup.Background, 42f, -56f, 34f, 22f, 8, 1.1f, 3.2f, 2f, -.85f, .45f, .6f, -.3f)
        addTerrace(HarborGroup.Background, 64f, -50f, 22f, 18f, 6, 1.1f, 2.8f, 2f, .65f, .5f, .7f, .3f)

        // Phase2B foreground shoulders.
        addTerrace(HarborGroup.Foreground, -44f, -18f, 8f, 4f, 3, 0.8f, 1.4f, 0.9f, 0.35f, 0.10f, 0.30f, 0.15f)
        addTerrace(HarborGroup.Foreground, -33f, -19f, 10f, 5f, 3, 0.8f, 1.8f, 1.0f, 0.35f, 0.12f, 0.30f, 0.16f)
        addTerrace(HarborGroup.Foreground, 26f, -19f, 3f, 5f, 3, 0.8f, 0.5f, 1.0f, -0.12f, 0.12f, 0.10f, 0.16f)

        // Phase2B midground banks.
        addTerrace(HarborGroup.Midground, -37f, -31f, 10f, 8f, 4, 0.9f, 1.8f, 1.2f, 0.45f, 0.18f, 0.35f, 0.20f)
        addTerrace(HarborGroup.Midground, -28f, -34f, 6f, 7f, 4, 0.9f, 1.0f, 1.0f, -0.30f, 0.22f, 0.20f, 0.18f)
        addTerrace(HarborGroup.Midground, 26f, -34f, 3f, 7f, 4, 0.9f, 0.5f, 1.0f, 0.22f, 0.20f, 0.10f, 0.18f)
        addTerrace(HarborGroup.Midground, 37f, -31f, 10f, 8f, 4, 0.9f, 1.8f, 1.2f, -0.45f, 0.18f, 0.35f, 0.20f)

        // Phase2B background silhouette: side peaks above the existing banks, center lower.
        addTerrace(HarborGroup.Background, -50f, -54f, 20f, 16f, 10, 1.05f, 1.5f, 1.1f, 0.55f, 0.30f, 0.45f, 0.22f)
        addTerrace(HarborGroup.Background, 48f, -54f, 18f, 16f, 10, 1.05f, 1.35f, 1.1f, -0.50f, 0.30f, 0.40f, 0.22f)
        addTerrace(HarborGroup.Background, 0f, -66f, 18f, 12f, 8, 1.0f, 1.7f, 1.1f, 0.30f, 0.25f, 0.35f, 0.18f)

        addPier(HarborGroup.LeftDock, -43f)
        addPier(HarborGroup.RightDock, 43f)
        addShelter(HarborGroup.LeftShelter, -47f, 5.35f, -16f)
        addShelter(HarborGroup.RightShelter, 47f, 5.35f, -16f)

        add(HarborBlock(HarborGroup.LeftPavilion, -34f, 7.65f, -30f, BlockMaterial.OAK_PLANKS, 18f, 0.3f, 10f))
        listOf(-41f, -27f).forEach { x -> listOf(-33.5f, -26.5f).forEach { z -> add(HarborBlock(HarborGroup.LeftPavilion, x, 10f, z, BlockMaterial.OAK_LOG, 0.35f, 5f, 0.35f)) } }
        listOf(-34f).forEach { x -> listOf(-34f, -30f, -26f).forEach { z -> add(HarborBlock(HarborGroup.LeftPavilion, x, 12.7f, z, BlockMaterial.OAK_PLANKS, 20f, 0.4f, 4.3f)) } }
        listOf(-41f, -27f).forEach { x -> listOf(-33.5f, -26.5f).forEach { z -> add(HarborBlock(HarborGroup.LeftPavilion, x, 10.6f, z, BlockMaterial.LANTERN, 0.3f, 0.3f, 0.3f)) } }
        listOf(-39f, -34f, -29f).forEach { x -> add(HarborBlock(HarborGroup.LeftPavilion, x, 10f, -34.6f, BlockMaterial.OAK_PLANKS, 4.5f, 4.2f, 0.3f)) }
        listOf(-41.2f, -26.8f).forEach { x -> add(HarborBlock(HarborGroup.LeftPavilion, x, 9f, -30f, BlockMaterial.OAK_PLANKS, 0.3f, 2.4f, 6.5f)) }
        listOf(-39f, -34f, -29f).forEach { x -> add(HarborBlock(HarborGroup.LeftPavilion, x, 8.45f, -25.3f, BlockMaterial.OAK_LOG, 4f, 0.7f, 0.25f)) }
        listOf(-38f, -35f, -31f).forEach { x -> add(HarborBlock(HarborGroup.LeftPavilion, x, 8.35f, -28f, BlockMaterial.OAK_PLANKS, 1.5f, 1.4f, 1.5f)) }

        listOf(-12f, 0f, 12f).forEach { cx ->
            val baseY = 6.35f
            add(HarborBlock(HarborGroup.CenterMarket, cx, baseY + 0.15f, -31f, BlockMaterial.OAK_PLANKS, 8f, 0.3f, 6f))
            listOf(-3f to -2f, -3f to 2f, 3f to -2f, 3f to 2f).forEach { (dx, dz) -> add(HarborBlock(HarborGroup.CenterMarket, cx + dx, baseY + 2f, -31f + dz, BlockMaterial.OAK_LOG, 0.25f, 4f, 0.25f)) }
            add(HarborBlock(HarborGroup.CenterMarket, cx, baseY + 4.25f, -31f, BlockMaterial.OAK_PLANKS, 9f, 0.35f, 7f))
            listOf(-2.5f, 2.5f).forEach { dx -> add(HarborBlock(HarborGroup.CenterMarket, cx + dx, baseY + 2.6f, -28.8f, BlockMaterial.LANTERN, 0.25f, 0.25f, 0.25f)) }
            add(HarborBlock(HarborGroup.CenterMarket, cx, baseY + 2f, -34.1f, BlockMaterial.OAK_PLANKS, 6f, 3.2f, 0.3f))
            listOf(cx - 3.2f, cx + 3.2f).forEach { x -> add(HarborBlock(HarborGroup.CenterMarket, x, baseY + 1.5f, -31f, BlockMaterial.OAK_PLANKS, 0.3f, 2.6f, 4.5f)) }
            add(HarborBlock(HarborGroup.CenterMarket, cx, baseY + 1f, -27.8f, BlockMaterial.OAK_PLANKS, 5.5f, 1.2f, 0.7f))
            add(HarborBlock(HarborGroup.CenterMarket, cx - 2f, baseY + 0.8f, -29f, BlockMaterial.OAK_PLANKS, 1.4f, 1.3f, 1.4f))
        }

        add(HarborBlock(HarborGroup.RightLighthouse, 18.5f, 3.85f, -34f, BlockMaterial.SAND, 8f, 1f, 9f))
        add(HarborBlock(HarborGroup.RightLighthouse, 18.3f, 4.85f, -34f, BlockMaterial.SAND, 7.5f, 1f, 8.5f))
        add(HarborBlock(HarborGroup.RightLighthouse, 18.1f, 5.85f, -34f, BlockMaterial.SAND, 7f, 1f, 8f))
        add(HarborBlock(HarborGroup.RightLighthouse, 18f, 6.85f, -34f, BlockMaterial.SAND, 6.5f, 1f, 7.5f))
        add(HarborBlock(HarborGroup.RightLighthouse, 18f, 7.95f, -34f, BlockMaterial.OAK_PLANKS, 6f, 1.2f, 6f))
        listOf(16f, 20f).forEach { x -> listOf(-36f, -32f).forEach { z -> add(HarborBlock(HarborGroup.RightLighthouse, x, 12f, z, BlockMaterial.OAK_LOG, 0.45f, 8f, 0.45f)) } }
        add(HarborBlock(HarborGroup.RightLighthouse, 18f, 12.4f, -34f, BlockMaterial.OAK_PLANKS, 7f, 0.35f, 7f))
        add(HarborBlock(HarborGroup.RightLighthouse, 18f, 16.2f, -34f, BlockMaterial.OAK_PLANKS, 9f, 0.4f, 9f))
        add(HarborBlock(HarborGroup.RightLighthouse, 18f, 18f, -34f, BlockMaterial.OAK_LOG_TOP, 6f, 0.45f, 6f))
        add(HarborBlock(HarborGroup.RightLighthouse, 18f, 17f, -34f, BlockMaterial.LANTERN, 1.2f, 1.2f, 1.2f))
        listOf(15f, 21f).forEach { x -> listOf(-37f, -31f).forEach { z -> add(HarborBlock(HarborGroup.RightLighthouse, x, 16.8f, z, BlockMaterial.LANTERN, 0.3f, 0.3f, 0.3f)) } }
        add(HarborBlock(HarborGroup.RightLighthouse, 18f, 12f, -34f, BlockMaterial.OAK_PLANKS, 3.6f, 7.2f, 3.6f))
        listOf(15f, 21f).forEach { x -> add(HarborBlock(HarborGroup.RightLighthouse, x, 16.9f, -34f, BlockMaterial.OAK_LOG, 0.3f, 1f, 7.5f)) }
        listOf(-37f, -31f).forEach { z -> add(HarborBlock(HarborGroup.RightLighthouse, 18f, 16.9f, z, BlockMaterial.OAK_LOG, 6.3f, 1f, 0.3f)) }
    }

    /** Generates a bounded far shoreline. The protected center keeps the boat unobscured. */
    fun dunes(seed: Long): List<DesertDune> {
        val random = Random(seed)
        return buildList {
            repeat(24) {
                val x = random.nextFloat() * 116f - 58f
                val z = -44f - random.nextFloat() * 22f
                val radius = 3.5f + random.nextFloat() * 4.5f
                val height = 2f + random.nextFloat() * 4.5f
                // The far bank is intentionally behind the water and boat view.
                add(DesertDune(x, z, radius, height))
            }
        }
    }

    fun cacti(seed: Long): List<Cactus> {
        val random = Random(seed xor 0x5deece66dL)
        val anchors = listOf(
                -57f to (-16f to 5.35f), -32f to (-13f to 5.35f), 32f to (-13f to 5.35f), 58f to (-16f to 5.35f),
                -55f to (-30f to 7.35f), -43f to (-33f to 7.35f), -18f to (-35f to 6.35f), 22f to (-31f to 7.35f),
                56f to (-38f to 6.35f), -60f to (-52f to 11.15f), -48f to (-56f to 10f), -28f to (-59f to 10f),
                -12f to (-61f to 9f), 22f to (-60f to 9f), 42f to (-55f to 11f), 58f to (-50f to 8f)
            )
        return anchors.map { (x, zAndGround) ->
            Cactus(x, zAndGround.first, 1.8f + random.nextFloat() * 2.4f, zAndGround.second)
        }
    }

    fun camera(timeSeconds: Float): CameraPose = camera(timeSeconds.toDouble())

    /** Smoothly sweeps a narrow arc around the front-facing shoreline view. */
    fun camera(timeSeconds: Double): CameraPose {
        val phase = positiveModulo(timeSeconds, CAMERA_CYCLE_SECONDS) / CAMERA_CYCLE_SECONDS
        val swing = kotlin.math.sin(phase * Math.PI * 2.0).toFloat() * CAMERA_SWING_RADIANS
        return CameraPose(
            yawRadians = CAMERA_CENTER_YAW_RADIANS + swing,
            height = CAMERA_BASE_HEIGHT + sin((phase * Math.PI * 2.0)).toFloat() * CAMERA_HEIGHT_SWING,
            distance = CAMERA_DISTANCE
        )
    }

    fun boat(timeSeconds: Float): BoatPose = boat(timeSeconds.toDouble())

    fun boat(timeSeconds: Double): BoatPose {
        val phase = positiveModulo(timeSeconds, BOAT_SLIDE_PERIOD_SECONDS) / BOAT_SLIDE_PERIOD_SECONDS
        val wave = kotlin.math.sin(phase * Math.PI * 2.0)
        return BoatPose(
            x = BOAT_CENTER_X + wave.toFloat() * BOAT_SLIDE_DISTANCE,
            y = BOAT_WATERLINE + (wave * 0.08).toFloat(),
            z = BOAT_CENTER_Z,
            yawRadians = (wave * 0.035).toFloat(),
            rollRadians = (kotlin.math.cos(phase * Math.PI * 2.0) * 0.018).toFloat()
        )
    }

    fun playerAnchor(boat: BoatPose): PlayerAnchor = PlayerAnchor(boat.x, boat.y + 0.05f, boat.z)

    data class FrameState(
        val camera: CameraPose,
        val boat: BoatPose,
        val waterTimeSeconds: Float,
        val waterNormalOffset: Float,
        val cloudTimeSeconds: Double
    )

    fun frameState(elapsedSeconds: Double): FrameState = FrameState(
        camera = camera(elapsedSeconds),
        boat = boat(elapsedSeconds),
        waterTimeSeconds = positiveModulo(elapsedSeconds, WATER_ANIMATION_PERIOD_SECONDS).toFloat(),
        waterNormalOffset = waterNormalOffset(elapsedSeconds),
        cloudTimeSeconds = elapsedSeconds
    )

    /** Stable, repeating scroll offset for the Complementary water normal layers. */
    fun waterNormalOffset(elapsedSeconds: Double): Float =
        positiveModulo(elapsedSeconds * WATER_NORMAL_SCROLL_SPEED, WATER_NORMAL_SCROLL_PERIOD).toFloat()

    private fun positiveModulo(value: Double, period: Double): Double = ((value % period) + period) % period

    /** Fixed, high noon sun. The light direction points from the sun at the scene target. */
    @Suppress("UNUSED_PARAMETER")
    fun sun(timeSeconds: Float): SunState {
        val x = 0f
        val y = 26.7f
        val z = -6f
        val directionX = CAMERA_TARGET_X - x
        val directionY = CAMERA_TARGET_Y - y
        val directionZ = CAMERA_TARGET_Z - z
        val length = sqrt(directionX * directionX + directionY * directionY + directionZ * directionZ)
        return SunState(x, y, z, directionX / length, directionY / length, directionZ / length)
    }

    /** Camera-facing basis for the sun quad; avoids edge-on disappearance during camera orbit. */
    fun sunBillboardBasis(eyeX: Float, eyeY: Float, eyeZ: Float, sun: SunState): SunBillboardBasis {
        var normalX = eyeX - sun.offsetX
        var normalY = eyeY - sun.offsetY
        var normalZ = eyeZ - sun.offsetZ
        val normalLength = sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ)
        normalX /= normalLength
        normalY /= normalLength
        normalZ /= normalLength

        // Cross with world-up except near the pole, where Z-up keeps the basis stable.
        val nearPole = kotlin.math.abs(normalY) > 0.99f
        val referenceUpY = if (nearPole) 0f else 1f
        val referenceUpZ = if (nearPole) 1f else 0f
        val rightX = referenceUpY * normalZ - referenceUpZ * normalY
        val rightY = referenceUpZ * normalX
        val rightZ = -referenceUpY * normalX
        val rightLength = sqrt(rightX * rightX + rightY * rightY + rightZ * rightZ)
        val normalizedRightX = rightX / rightLength
        val normalizedRightY = rightY / rightLength
        val normalizedRightZ = rightZ / rightLength
        val upX = normalY * normalizedRightZ - normalZ * normalizedRightY
        val upY = normalZ * normalizedRightX - normalX * normalizedRightZ
        val upZ = normalX * normalizedRightY - normalY * normalizedRightX
        return SunBillboardBasis(
            rightX = normalizedRightX,
            rightY = normalizedRightY,
            rightZ = normalizedRightZ,
            upX = upX,
            upY = upY,
            upZ = upZ
        )
    }

    /** Minecraft 1.21.1's cloud coordinates before the view-stack transform. */
    fun cloudCoordinates(
        timeSeconds: Double,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
        cloudHeight: Float = CLOUD_HEIGHT
    ): CloudCoordinates {
        val cloudTime = timeSeconds * CLOUD_TIME_SPEED
        var x = (cameraX + cloudTime) / CLOUD_HORIZONTAL_SCALE
        val y = cloudHeight - cameraY + 0.33
        var z = cameraZ / CLOUD_HORIZONTAL_SCALE + 0.33
        x -= floor(x / CLOUD_COORDINATE_WRAP) * CLOUD_COORDINATE_WRAP
        z -= floor(z / CLOUD_COORDINATE_WRAP) * CLOUD_COORDINATE_WRAP
        val floorX = floor(x).toInt()
        val floorY = floor(y / CLOUD_THICKNESS).toInt()
        val floorZ = floor(z).toInt()
        return CloudCoordinates(
            x = x,
            y = y,
            z = z,
            fractionX = (x - floorX).toFloat(),
            fractionY = (((y / CLOUD_THICKNESS) - floorY) * CLOUD_THICKNESS).toFloat(),
            fractionZ = (z - floorZ).toFloat(),
            floorX = floorX,
            floorY = floorY,
            floorZ = floorZ
        )
    }

    fun cloudColorChanged(previous: SkyColor?, current: SkyColor): Boolean {
        if (previous == null) return true
        val red = previous.red - current.red
        val green = previous.green - current.green
        val blue = previous.blue - current.blue
        return red * red + green * green + blue * blue > CLOUD_COLOR_REBUILD_THRESHOLD
    }
}

/** Counter-clockwise when viewed from above, matching the +Y normal. */
internal fun waterPlaneCornerPositions(size: Float, y: Float): FloatArray = floatArrayOf(
    -size, y, size,
    size, y, size,
    size, y, -size,
    -size, y, -size
)
