package calebxzau.rdi.bgrenderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f

class BackgroundSceneTest {
    @Test
    fun reflectionUsesHalfResolutionAndMirrorsAroundWater() {
        assertEquals(640, REFLECTION_WIDTH)
        assertEquals(360, REFLECTION_HEIGHT)
        assertEquals(0.1f, CAMERA_NEAR_PLANE)
        assertEquals(5.0f, reflectedY(reflectedY(5.0f)), absoluteTolerance = 0.000001f)
        assertTrue(reflectedY(5.0f) < WATER_LEVEL)
        assertEquals(
            5.63f,
            reflectionCloudHeight(5f, reflectedY(5f), 0.33f),
            absoluteTolerance = 0.000001f
        )
    }

    @Test
    fun dunesAreStablePerSeedAndBoundedAwayFromBoat() {
        val first = BackgroundScene.dunes(17L)
        assertEquals(first, BackgroundScene.dunes(17L))
        assertTrue(first != BackgroundScene.dunes(18L))
        assertEquals(24, first.size)
        assertTrue(first.all { it.x in -58f..58f && it.z in -66f..-44f && it.radius in 3.5f..8f && it.height in 2f..6.5f })
        val cacti = BackgroundScene.cacti(17L)
        assertEquals(16, cacti.size)
        assertEquals(cacti, BackgroundScene.cacti(17L))
        assertTrue(cacti != BackgroundScene.cacti(18L))
        assertTrue(cacti.all { it.height in 1.8f..4.2f })
        assertTrue(cacti.none { it.x in -24f..24f && it.z > -24f })
        assertTrue(cacti.any { it.x == -57f && it.z == -16f && it.groundY == 5.35f })
    }

    @Test
    fun harborLayoutBuildsThreeDepthLayersAndLandmarksWithinBudget() {
        val blocks = BackgroundScene.harborBlocks()
        assertEquals(blocks, BackgroundScene.harborBlocks())
        HarborGroup.entries.forEach { group -> assertTrue(blocks.any { it.group == group }) }
        val foreground = blocks.filter { it.group == HarborGroup.Foreground }
        assertTrue(foreground.minOf { it.x - it.sizeX / 2f } <= -60f)
        assertTrue(foreground.maxOf { it.x + it.sizeX / 2f } >= 60f)
        assertTrue(foreground.all { it.z > -22f })
        val firstTerrace = foreground.filter { it.material == BlockMaterial.SAND }.take(3)
        assertEquals(listOf(28f, 23.3f, 18.6f), firstTerrace.map { it.sizeX })
        assertEquals(listOf(-48f, -47.9f, -46f), firstTerrace.map { it.x })
        assertEquals(listOf(-12f, -12.1f, -11.3f), firstTerrace.map { it.z })
        assertTrue(firstTerrace.map { it.x }.distinct().size == 3)
        assertTrue(firstTerrace.map { it.z }.distinct().size == 3)
        assertTrue(blocks.filter { it.group == HarborGroup.Midground }.all { it.z in -45f..-22f })
        assertTrue(blocks.filter { it.group == HarborGroup.Background }.all { it.z < -42f })
        val corridor = blocks.filter { it.material == BlockMaterial.SAND && it.group in setOf(HarborGroup.Foreground, HarborGroup.Midground) && it.z > -24f }
        assertTrue(corridor.none { it.x + it.sizeX / 2f >= -24f && it.x - it.sizeX / 2f <= 24f && it.z + it.sizeZ / 2f >= -24f && it.z - it.sizeZ / 2f <= -8f })
        fun maxTop(group: HarborGroup) = blocks.filter { it.group == group }.maxOf { it.y + it.sizeY / 2f }
        assertTrue(maxTop(HarborGroup.RightLighthouse) > maxTop(HarborGroup.LeftPavilion))
        assertTrue(maxTop(HarborGroup.RightLighthouse) > maxTop(HarborGroup.LeftShelter))
        assertEquals(21, blocks.count { it.group == HarborGroup.LeftDock })
        assertEquals(21, blocks.count { it.group == HarborGroup.RightDock })
        assertEquals(16, blocks.count { it.group == HarborGroup.LeftShelter })
        assertEquals(16, blocks.count { it.group == HarborGroup.RightShelter })
        assertEquals(23, blocks.count { it.group == HarborGroup.LeftPavilion })
        assertEquals(39, blocks.count { it.group == HarborGroup.CenterMarket })
        assertEquals(22, blocks.count { it.group == HarborGroup.RightLighthouse })
        val lighthouseBlocks = blocks.filter { it.group == HarborGroup.RightLighthouse }
        assertTrue(lighthouseBlocks.all { it.x in 15f..21f })
        val lighthouseSupports = lighthouseBlocks.filter { it.material == BlockMaterial.SAND && it.sizeY == 1f }
        assertEquals(listOf(4.35f, 5.35f, 6.35f, 7.35f), lighthouseSupports.map { it.y + it.sizeY / 2f })
        lighthouseSupports.zipWithNext().forEach { (lower, upper) ->
            assertEquals(lower.y + lower.sizeY / 2f, upper.y - upper.sizeY / 2f)
        }
        val lighthouseBase = lighthouseBlocks.first { it.material == BlockMaterial.OAK_PLANKS && it.sizeX == 6f && it.sizeY == 1.2f && it.sizeZ == 6f }
        assertEquals(lighthouseBase.y - lighthouseBase.sizeY / 2f, lighthouseSupports.last().y + lighthouseSupports.last().sizeY / 2f)
        assertTrue(blocks.any { it.group == HarborGroup.LeftShelter && it.material == BlockMaterial.OAK_PLANKS && it.sizeY == 3.5f })
        assertTrue(blocks.any { it.group == HarborGroup.CenterMarket && it.material == BlockMaterial.OAK_PLANKS && it.sizeY == 3.2f })
        assertTrue(blocks.any { it.group == HarborGroup.RightLighthouse && it.material == BlockMaterial.OAK_PLANKS && it.sizeY == 7.2f })
        val mesh = buildSceneMesh(123L)
        assertEquals(13_182, mesh.vertices.size / SCENE_VERTEX_FLOATS)
        assertTrue(mesh.vertices.size / SCENE_VERTEX_FLOATS < SCENE_VERTEX_BUDGET)
        val kinds = mesh.vertices.asSequence().chunked(SCENE_VERTEX_FLOATS).map { it[9] }.toList()
        assertTrue(kinds.all { it == 0f || it == 1f || it == 2f })
        assertEquals(144, kinds.count { it == 2f })
        assertEquals(6, kinds.count { it == 1f })
    }

    @Test
    fun staticSceneUsesMinecraftCornerAoAndKeepsDynamicGeometryUnshaded() {
        val source = staticAabb(0f, 0f, 0f, 1f, 1f, 1f)
        val corner = floatArrayOf(0.5f, 0.5f, 0.5f)
        assertEquals(1f, cornerAmbientOcclusion(source, corner[0], corner[1], corner[2], 0f, 1f, 0f, listOf(source)))

        val sideA = staticAabb(1.05f, 0f, 0f, 1.1f, 1f, 1f)
        assertEquals(
            0.86f,
            cornerAmbientOcclusion(source, corner[0], corner[1], corner[2], 0f, 1f, 0f, listOf(source, sideA)),
            absoluteTolerance = 0.000001f
        )
        val boxBehindFace = staticAabb(0f, -1f, 0f, 2f, 1f, 2f)
        assertEquals(
            1f,
            cornerAmbientOcclusion(source, corner[0], corner[1], corner[2], 0f, 1f, 0f, listOf(source, boxBehindFace)),
            absoluteTolerance = 0.000001f
        )
        val sideB = staticAabb(0f, 0f, 1.05f, 2f, 1f, 1.1f)
        assertEquals(
            0.58f,
            cornerAmbientOcclusion(source, corner[0], corner[1], corner[2], 0f, 1f, 0f, listOf(source, sideA, sideB)),
            absoluteTolerance = 0.000001f
        )

        val mesh = buildSceneMesh(123L)
        val vertices = mesh.vertices.asSequence().chunked(SCENE_VERTEX_FLOATS).toList()
        assertTrue(vertices.all { it[12] in 0.58f..1f })
        assertTrue(vertices.any { it[12] < 1f })
        assertTrue(vertices.filter { it[9] == 1f || it[9] == 2f }.all { it[12] == 1f })
    }

    @Test
    fun staticShadowFrameContainsEveryProductionAabbCorner() {
        val mesh = buildSceneMesh(123L)
        val frame = buildStaticShadowFrame(mesh.staticAabbs, BackgroundScene.sun(0f))
        mesh.staticAabbs.flatMap { it.corners().asList() }.forEach { corner ->
            val clip = Vector4f(corner, 1f).mul(frame.viewProjection)
            assertTrue(clip.w > 0f)
            assertTrue(clip.x / clip.w in -1f..1f)
            assertTrue(clip.y / clip.w in -1f..1f)
            assertTrue(clip.z / clip.w in -1f..1f)
        }
        assertEquals(frame.viewProjection, buildStaticShadowViewProjection(mesh.staticAabbs, BackgroundScene.sun(0f)))
    }

    @Test
    fun cameraStaysInNarrowFrontFacingArcAndReturnsToSamePose() {
        val start = BackgroundScene.camera(0.0)
        val quarter = BackgroundScene.camera(CAMERA_CYCLE_SECONDS / 4.0)
        val half = BackgroundScene.camera(CAMERA_CYCLE_SECONDS / 2.0)
        val threeQuarter = BackgroundScene.camera(CAMERA_CYCLE_SECONDS * 3.0 / 4.0)
        val repeat = BackgroundScene.camera(CAMERA_CYCLE_SECONDS)
        val center = CAMERA_CENTER_YAW_RADIANS

        assertEquals(center, start.yawRadians)
        assertEquals(center + CAMERA_SWING_RADIANS, quarter.yawRadians, absoluteTolerance = 0.000001f)
        assertEquals(center, half.yawRadians, absoluteTolerance = 0.000001f)
        assertEquals(center - CAMERA_SWING_RADIANS, threeQuarter.yawRadians, absoluteTolerance = 0.000001f)
        listOf(start, quarter, half, threeQuarter, repeat).forEach { pose ->
            assertTrue(pose.yawRadians in center - CAMERA_SWING_RADIANS..center + CAMERA_SWING_RADIANS)
        }
        assertEquals(start, repeat)
        val beforeBoundary = BackgroundScene.camera(CAMERA_CYCLE_SECONDS - 0.001)
        val afterBoundary = BackgroundScene.camera(CAMERA_CYCLE_SECONDS + 0.001)
        assertTrue(abs(beforeBoundary.yawRadians - afterBoundary.yawRadians) < 0.001f)
        assertEquals(50f, start.distance)
        assertEquals(8f, start.height)
        assertEquals(CAMERA_TARGET_X, 0f)
        assertEquals(CAMERA_TARGET_Y, 5f)
        assertEquals(CAMERA_TARGET_Z, -25f)
        assertEquals(CAMERA_FOV_DEGREES, 60f)
    }

    @Test
    fun boatAndPlayerStayInBoundedWaterAnchor() {
        val poses = listOf(
            0.0,
            BOAT_SLIDE_PERIOD_SECONDS / 4.0,
            BOAT_SLIDE_PERIOD_SECONDS / 2.0,
            BOAT_SLIDE_PERIOD_SECONDS * 3.0 / 4.0
        ).map(BackgroundScene::boat)
        assertEquals(-14f, poses.first().x)
        assertEquals(-14f, BOAT_CENTER_X)
        assertEquals(1.25f, BOAT_VISUAL_SCALE)
        assertEquals(0.08f, PLAYER_VISUAL_SCALE)
        assertTrue(poses.all { it.x in BOAT_CENTER_X - BOAT_SLIDE_DISTANCE..BOAT_CENTER_X + BOAT_SLIDE_DISTANCE && it.y > WATER_LEVEL })
        poses.forEach { boat ->
            val anchor = BackgroundScene.playerAnchor(boat)
            assertEquals(boat.x, anchor.x)
            assertEquals(boat.z, anchor.z)
            assertTrue(anchor.y > WATER_LEVEL)
        }
    }

    @Test
    fun frameStateKeepsIndependentPeriodicAnimationsContinuousAtCameraBoundary() {
        val before = BackgroundScene.frameState(CAMERA_CYCLE_SECONDS - 0.001)
        val after = BackgroundScene.frameState(CAMERA_CYCLE_SECONDS + 0.001)
        assertTrue(abs(before.camera.yawRadians - after.camera.yawRadians) < 0.001f)
        assertTrue(abs(before.boat.x - after.boat.x) < 0.01f)
        assertTrue(abs(before.waterTimeSeconds - after.waterTimeSeconds) < 0.01f)
        assertTrue(abs(before.waterNormalOffset - after.waterNormalOffset) < 0.01f)
        val cloudBefore = BackgroundScene.cloudCoordinates(CAMERA_CYCLE_SECONDS - 0.001, CAMERA_DISTANCE.toDouble(), CAMERA_BASE_HEIGHT.toDouble(), 0.0)
        val cloudAfter = BackgroundScene.cloudCoordinates(CAMERA_CYCLE_SECONDS + 0.001, CAMERA_DISTANCE.toDouble(), CAMERA_BASE_HEIGHT.toDouble(), 0.0)
        assertTrue(abs(cloudBefore.fractionX - cloudAfter.fractionX) < 0.01f)
    }

    @Test
    fun waterIsShaderOnlyWhileOtherMaterialsKeepBundledTextures() {
        assertNull(BlockMaterial.WATER.texturePath)
        assertEquals(0.08f, BlockMaterial.WATER.red)
        assertEquals(0.30f, BlockMaterial.WATER.green)
        assertEquals(0.58f, BlockMaterial.WATER.blue)
        assertTrue(BlockMaterial.entries.filter { it != BlockMaterial.WATER }.all { it.texturePath != null })
    }

    @Test
    fun waterNormalOffsetHasStablePeriodAndContinuousWrap() {
        assertEquals(0f, BackgroundScene.waterNormalOffset(0.0), absoluteTolerance = 0.000001f)
        assertTrue(BackgroundScene.waterNormalOffset(1.0e15) in 0f..WATER_NORMAL_SCROLL_PERIOD.toFloat())
        val periodSeconds = WATER_NORMAL_SCROLL_PERIOD / WATER_NORMAL_SCROLL_SPEED
        assertEquals(
            BackgroundScene.waterNormalOffset(0.0),
            BackgroundScene.waterNormalOffset(periodSeconds),
            absoluteTolerance = 0.0001f
        )
        val before = BackgroundScene.waterNormalOffset(periodSeconds - 0.001)
        val after = BackgroundScene.waterNormalOffset(periodSeconds + 0.001)
        assertTrue(before > WATER_NORMAL_SCROLL_PERIOD.toFloat() - 0.01f)
        assertTrue(after < 0.01f)
    }

    @Test
    fun faceUvUsesBothVaryingAxesForEachFaceOrientation() {
        val front = listOf(
            faceUv(-1f, 0f, 2f, 0f, 0f, 1f),
            faceUv(1f, 3f, 2f, 0f, 0f, 1f)
        )
        val side = listOf(
            faceUv(2f, 0f, -1f, 1f, 0f, 0f),
            faceUv(2f, 3f, 1f, 1f, 0f, 0f)
        )
        assertTrue(front[0][0] != front[1][0] && front[0][1] != front[1][1])
        assertTrue(side[0][0] != side[1][0] && side[0][1] != side[1][1])
        assertEquals(faceUv(0f, 1f, -1f, 0f, 1f, 0f).toList(), faceUv(0f, 1f, -1f, 0f, -1f, 0f).toList())
    }

    @Test
    fun sceneMaterialBatchesAreContiguousAndCoverEveryVertex() {
        val mesh = buildSceneMesh(123L)
        assertEquals(0, mesh.vertices.size % SCENE_VERTEX_FLOATS)
        assertEquals(mesh.vertices.size / SCENE_VERTEX_FLOATS, mesh.batches.sumOf { it.vertexCount })
        assertEquals(BlockMaterial.entries.toList(), mesh.batches.map { it.material })
        var nextVertex = 0
        mesh.batches.forEach { batch ->
            assertEquals(nextVertex, batch.firstVertex)
            assertTrue(batch.vertexCount > 0)
            assertTrue(batch.firstVertex + batch.vertexCount <= mesh.vertices.size / SCENE_VERTEX_FLOATS)
            nextVertex += batch.vertexCount
        }
        assertEquals(mesh.vertices.size / SCENE_VERTEX_FLOATS, nextVertex)
    }

    @Test
    fun infiniteWaterCoversConservativeFarPlaneCornersWithOneQuad() {
        assertEquals(38f, CLOUD_HEIGHT)
        assertEquals(8f, CLOUD_HORIZONTAL_SCALE)
        assertEquals(3f, CLOUD_THICKNESS)
        assertEquals(-6, CLOUD_TILE_MIN)
        assertEquals(6, CLOUD_TILE_MAX)
        assertEquals(400f, CAMERA_FAR_PLANE)
        assertEquals(260f, WATER_HORIZON_FOG_START)
        assertEquals(360f, WATER_HORIZON_FOG_END)
        assertTrue(WATER_HORIZON_FOG_START < WATER_HORIZON_FOG_END)
        assertEquals(640f, WATER_EXTENT)
        assertTrue(WATER_EXTENT >= CAMERA_DISTANCE + CAMERA_FAR_PLANE * 1.25f)
        val mesh = buildSceneMesh(123L)
        val waterBatch = mesh.batches.single { it.material == BlockMaterial.WATER }
        assertEquals(6, waterBatch.vertexCount)
    }

    @Test
    fun cameraMovesVerySlowlyBetweenAdjacentSamples() {
        val start = BackgroundScene.camera(0f)
        val nearby = BackgroundScene.camera(0.1f)
        assertTrue(abs(nearby.yawRadians - start.yawRadians) < 0.002f)
        assertTrue(abs(nearby.height - start.height) < 0.01f)
        assertEquals(50f, start.distance)
        assertEquals(8f, start.height)
        assertEquals(0.25f, CAMERA_HEIGHT_SWING)
        assertEquals(60f, CAMERA_FOV_DEGREES)
    }

    @Test
    fun renderContractUses720pSixteenToNineFrameAtTargetFps() {
        assertEquals(RENDER_WIDTH, 1280)
        assertEquals(RENDER_HEIGHT, 720)
        assertEquals(RENDER_WIDTH * 9, RENDER_HEIGHT * 16)
        assertEquals(TARGET_FPS, 20)
    }

    @Test
    fun waterPlaneNormalPointsUp() {
        val points = waterPlaneCornerPositions(1f, WATER_LEVEL)
        val ax = points[3] - points[0]
        val az = points[5] - points[2]
        val bx = points[6] - points[0]
        val bz = points[8] - points[2]
        val crossY = az * bx - ax * bz
        assertTrue(crossY > 0f)
    }

    @Test
    fun sunStaysFixedAtNoonAndItsLightingDirectionIsNormalized() {
        val first = BackgroundScene.sun(0f)
        val later = BackgroundScene.sun(60f)
        assertEquals(first, later)
        assertEquals(0f, first.offsetX)
        assertEquals(26.7f, first.offsetY)
        assertEquals(-6f, first.offsetZ)
        val directionX = CAMERA_TARGET_X - first.offsetX
        val directionY = CAMERA_TARGET_Y - first.offsetY
        val directionZ = CAMERA_TARGET_Z - first.offsetZ
        val expectedDirectionLength = sqrt(directionX * directionX + directionY * directionY + directionZ * directionZ)
        assertEquals(directionX / expectedDirectionLength, first.directionX, absoluteTolerance = 0.000001f)
        assertEquals(directionY / expectedDirectionLength, first.directionY, absoluteTolerance = 0.000001f)
        assertEquals(directionZ / expectedDirectionLength, first.directionZ, absoluteTolerance = 0.000001f)
        val directionLength = sqrt(
            first.directionX * first.directionX +
                first.directionY * first.directionY +
                first.directionZ * first.directionZ
        )
        assertTrue(abs(directionLength - 1f) < 0.0001f)
        val horizontalDirectionLength = sqrt(first.directionX * first.directionX + first.directionZ * first.directionZ)
        val lightingElevationDegrees = Math.toDegrees(
            atan2((-first.directionY).toDouble(), horizontalDirectionLength.toDouble())
        ).toFloat()
        assertEquals(48.8f, lightingElevationDegrees, absoluteTolerance = 0.2f)
    }

    @Test
    fun sunBillboardBasisRemainsOrthonormalAroundCameraOrbit() {
        val sun = BackgroundScene.sun(0f)
        repeat(8) { step ->
            val angle = step * Math.PI / 4.0
            val basis = BackgroundScene.sunBillboardBasis(
                (CAMERA_DISTANCE * cos(angle)).toFloat(),
                CAMERA_BASE_HEIGHT,
                (CAMERA_DISTANCE * sin(angle)).toFloat(),
                sun
            )
            val rightLength = sqrt(basis.rightX * basis.rightX + basis.rightY * basis.rightY + basis.rightZ * basis.rightZ)
            val upLength = sqrt(basis.upX * basis.upX + basis.upY * basis.upY + basis.upZ * basis.upZ)
            val dot = basis.rightX * basis.upX + basis.rightY * basis.upY + basis.rightZ * basis.upZ
            val cameraFacingX = (CAMERA_DISTANCE * cos(angle)).toFloat() - sun.offsetX
            val cameraFacingY = CAMERA_BASE_HEIGHT - sun.offsetY
            val cameraFacingZ = (CAMERA_DISTANCE * sin(angle)).toFloat() - sun.offsetZ
            val cameraFacingLength = sqrt(
                cameraFacingX * cameraFacingX + cameraFacingY * cameraFacingY + cameraFacingZ * cameraFacingZ
            )
            val normalFacingCamera =
                basis.rightY * basis.upZ - basis.rightZ * basis.upY
            val normalFacingCameraY = basis.rightZ * basis.upX - basis.rightX * basis.upZ
            val normalFacingCameraZ = basis.rightX * basis.upY - basis.rightY * basis.upX
            val facingDot = (
                normalFacingCamera * cameraFacingX +
                    normalFacingCameraY * cameraFacingY +
                    normalFacingCameraZ * cameraFacingZ
                ) / cameraFacingLength
            assertTrue(abs(rightLength - 1f) < 0.0001f)
            assertTrue(abs(upLength - 1f) < 0.0001f)
            assertTrue(abs(dot) < 0.0001f)
            assertTrue(facingDot > 0.999f)
        }

        val projection = Matrix4f().perspective(
            Math.toRadians(CAMERA_FOV_DEGREES.toDouble()).toFloat(),
            RENDER_WIDTH.toFloat() / RENDER_HEIGHT,
            CAMERA_NEAR_PLANE,
            CAMERA_FAR_PLANE
        )
        val target = Vector3f(CAMERA_TARGET_X, CAMERA_TARGET_Y, CAMERA_TARGET_Z)
        val worldUp = Vector3f(0f, 1f, 0f)
        val size = SUN_BILLBOARD_HALF_SIZE
        listOf(
            0.0,
            CAMERA_CYCLE_SECONDS / 4.0,
            CAMERA_CYCLE_SECONDS / 2.0,
            CAMERA_CYCLE_SECONDS * 3.0 / 4.0,
            CAMERA_CYCLE_SECONDS
        ).forEach { time ->
            val pose = BackgroundScene.camera(time)
            val eye = Vector3f(
                pose.distance * cos(pose.yawRadians),
                pose.height,
                pose.distance * sin(pose.yawRadians)
            )
            val view = Matrix4f().lookAt(eye, target, worldUp)
            val viewProjection = Matrix4f(projection).mul(view)
            val basis = BackgroundScene.sunBillboardBasis(eye.x, eye.y, eye.z, sun)
            listOf(-size to -size, size to -size, size to size, -size to size).forEach { (right, up) ->
                val clip = Vector4f(
                    sun.offsetX + right * basis.rightX + up * basis.upX,
                    sun.offsetY + right * basis.rightY + up * basis.upY,
                    sun.offsetZ + right * basis.rightZ + up * basis.upZ,
                    1f
                ).mul(viewProjection)
                assertTrue(clip.w > 0f)
                assertTrue(clip.x / clip.w in -1f..1f)
                assertTrue(clip.y / clip.w in -1f..1f)
            }
            fun projectedY(localUp: Float): Float {
                val clip = Vector4f(
                    sun.offsetX + localUp * basis.upX,
                    sun.offsetY + localUp * basis.upY,
                    sun.offsetZ + localUp * basis.upZ,
                    1f
                ).mul(viewProjection)
                return clip.y / clip.w
            }
            val pixelDiameter = abs(projectedY(SUN_BILLBOARD_HALF_SIZE * 0.54f) - projectedY(-SUN_BILLBOARD_HALF_SIZE * 0.54f)) * RENDER_HEIGHT / 2f
            assertTrue(pixelDiameter in 50f..70f, "Projected sun disc diameter was $pixelDiameter pixels at t=$time")
        }
    }

    @Test
    fun skyColorStaysFixed() {
        assertEquals(SkyColor(0.14f, 0.40f, 0.78f), NOON_SKY_COLOR)
        assertEquals(SkyColor(0.34f, 0.62f, 0.84f), NOON_WATER_HORIZON_COLOR)
        assertEquals(SkyColor(0.86f, 0.90f, 0.96f), NOON_CLOUD_FOG_COLOR)
        assertEquals(300f, CLOUD_FOG_START)
        assertEquals(400f, CLOUD_FOG_END)
        assertTrue(CLOUD_FOG_START < CLOUD_FOG_END)
        assertTrue(CLOUD_FOG_START != WATER_HORIZON_FOG_START)
        assertTrue(CLOUD_FOG_END != WATER_HORIZON_FOG_END)
    }

    @Test
    fun cloudCoordinatesMatchMinecraftTimeScaleAnd2048Wrap() {
        val start = BackgroundScene.cloudCoordinates(0.0, 0.0, 0.0, 0.0)
        val afterSecond = BackgroundScene.cloudCoordinates(1.0, 0.0, 0.0, 0.0)
        assertEquals(0.33, start.z, absoluteTolerance = 0.000001)
        assertEquals(CLOUD_TIME_SPEED / CLOUD_HORIZONTAL_SCALE, afterSecond.x, absoluteTolerance = 0.000001)
        val wrapped = BackgroundScene.cloudCoordinates(CLOUD_COORDINATE_WRAP * CLOUD_HORIZONTAL_SCALE / CLOUD_TIME_SPEED, 0.0, 0.0, 0.0)
        assertEquals(start.x, wrapped.x, absoluteTolerance = 0.000001)
        assertEquals(start.z, wrapped.z, absoluteTolerance = 0.000001)
        assertEquals(12, start.floorY)
        assertEquals(2.33f, start.fractionY, absoluteTolerance = 0.0001f)
    }

    @Test
    fun cloudColorRebuildUsesMinecraftThreshold() {
        assertTrue(BackgroundScene.cloudColorChanged(null, NOON_CLOUD_COLOR))
        assertTrue(!BackgroundScene.cloudColorChanged(NOON_CLOUD_COLOR, SkyColor(1.0001f, 1f, 1f)))
        assertTrue(BackgroundScene.cloudColorChanged(NOON_CLOUD_COLOR, SkyColor(1.02f, 1f, 1f)))
    }

    @Test
    fun fancyCloudMeshUsesConfiguredCoverageAndVertexFormatWhenTopFacesAreVisible() {
        val coordinates = BackgroundScene.cloudCoordinates(0.0, 25.0, CLOUD_HEIGHT.toDouble() - 4.0, 0.0)
        val vertices = buildFancyCloudVertices(coordinates, NOON_CLOUD_COLOR)
        assertEquals(0, vertices.size % 12)
        assertTrue(vertices.isNotEmpty())
        assertEquals(20748, vertices.size / 12)
        assertTrue(vertices.asList().windowed(12, 12).all { it[8] == 0.8f })
    }

    @Test
    fun fancyCloudMeshOmitsTopFacesAlongTheRuntimeCameraTrack() {
        val times = listOf(
            0.0,
            CAMERA_CYCLE_SECONDS / 4.0,
            CAMERA_CYCLE_SECONDS / 2.0,
            CAMERA_CYCLE_SECONDS * 3.0 / 4.0,
            CAMERA_CYCLE_SECONDS
        )
        times.forEach { time ->
            val pose = BackgroundScene.camera(time)
            val yaw = pose.yawRadians.toDouble()
            val coordinates = BackgroundScene.cloudCoordinates(
                time,
                pose.distance.toDouble() * cos(yaw),
                pose.height.toDouble(),
                pose.distance.toDouble() * sin(yaw)
            )
            val vertices = buildFancyCloudVertices(coordinates, NOON_CLOUD_COLOR)
            assertEquals(19734, vertices.size / 12)
            assertTrue(vertices.asList().windowed(12, 12).none { vertex ->
                vertex[9] == 0f && vertex[10] == 1f && vertex[11] == 0f
            })
        }
    }

    @Test
    fun fancyCloudMeshPreservesVanillaUvNormalsAndHeightBoundary() {
        val coordinates = BackgroundScene.cloudCoordinates(0.0, 0.0, 0.0, 0.0)
        val bottomOnly = buildFancyCloudVertices(coordinates, NOON_CLOUD_COLOR)
        val first = bottomOnly.take(12)
        val firstTile = CLOUD_TILE_MIN * 8f
        assertEquals(firstTile * CLOUD_TEXTURE_COORDINATE_SCALE, first[3], absoluteTolerance = 0.000001f)
        assertEquals((firstTile + 8f) * CLOUD_TEXTURE_COORDINATE_SCALE, first[4], absoluteTolerance = 0.000001f)
        assertEquals(0f, first[9])
        assertEquals(-1f, first[10])
        assertEquals(0f, first[11])
        assertTrue(bottomOnly.asList().windowed(12, 12).all { vertex ->
            vertex[5] == 1.0f && vertex[6] == 1.0f && vertex[7] == 1.0f && vertex[8] == 0.8f
        })

        val topCoordinates = BackgroundScene.cloudCoordinates(0.0, 0.0, CLOUD_HEIGHT.toDouble() - 4.0, 0.0)
        val withTop = buildFancyCloudVertices(topCoordinates, NOON_CLOUD_COLOR)
        val topBaseY = topCoordinates.floorY * CLOUD_THICKNESS
        assertTrue(withTop.asList().windowed(12, 12).any { vertex ->
            vertex[9] == 0f && vertex[10] == 1f && vertex[11] == 0f &&
                vertex[5] == 1.0f && vertex[6] == 1.0f && vertex[7] == 1.0f
        })
        assertTrue(withTop.asList().windowed(12, 12).any { vertex ->
            kotlin.math.abs(vertex[9]) == 1f && vertex[5] == 1.0f && vertex[6] == 1.0f && vertex[7] == 1.0f
        })
        assertTrue(withTop.asList().windowed(12, 12).any { vertex ->
            vertex[9] == 0f && vertex[10] == -1f && vertex[11] == 0f &&
                vertex[5] == 1.0f && vertex[6] == 1.0f && vertex[7] == 1.0f
        })
        assertTrue(withTop.asList().windowed(12, 12).any { vertex ->
            kotlin.math.abs(vertex[11]) == 1f && vertex[5] == 1.0f && vertex[6] == 1.0f && vertex[7] == 1.0f
        })
        assertTrue(withTop.asList().windowed(12, 12).any { vertex ->
            abs(vertex[1] - (topBaseY + CLOUD_THICKNESS - 0.0009765625f)) < 0.000001f &&
                vertex[9] == 0f && vertex[10] == 1f && vertex[11] == 0f
        })
    }

    @Test
    fun performanceMonitorIgnoresWarmupAndSingleSpikes() {
        val monitor = FramePerformanceMonitor(warmupFrames = 30, sampleSize = 60)
        repeat(30) { assertTrue(!monitor.record(100_000_000L)) }
        repeat(59) { assertTrue(!monitor.record(10_000_000L)) }
        assertTrue(!monitor.record(100_000_000L))
    }

    @Test
    fun performanceMonitorDetectsSustainedSlowFrames() {
        val monitor = FramePerformanceMonitor(warmupFrames = 30, sampleSize = 60)
        repeat(30) { monitor.record(10_000_000L) }
        repeat(59) { assertTrue(!monitor.record(50_000_000L)) }
        assertTrue(monitor.record(50_000_000L))
    }
}
