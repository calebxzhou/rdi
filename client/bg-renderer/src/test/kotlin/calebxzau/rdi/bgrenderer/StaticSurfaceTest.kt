package calebxzau.rdi.bgrenderer

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StaticSurfaceTest {
    @Test
    fun oneBoxEmitsSixWoundQuadsWithMatchingNormals() {
        val boxes = listOf(staticAabb(0f, 0f, 0f, 2f, 3f, 4f))
        val faces = exposedStaticFaces(0, boxes)
        assertEquals(6, faces.size)
        assertEquals(StaticFaceDirection.entries.toList(), faces.map { it.direction })
        faces.forEach { face ->
            assertEquals(12, face.points.size)
            val ax = face.points[3] - face.points[0]
            val ay = face.points[4] - face.points[1]
            val az = face.points[5] - face.points[2]
            val bx = face.points[6] - face.points[0]
            val by = face.points[7] - face.points[1]
            val bz = face.points[8] - face.points[2]
            val crossX = ay * bz - az * by
            val crossY = az * bx - ax * bz
            val crossZ = ax * by - ay * bx
            val dot = crossX * face.direction.normalX + crossY * face.direction.normalY + crossZ * face.direction.normalZ
            assertTrue(dot > 0f)
        }
    }

    @Test
    fun disjointBoxDoesNotChangeAnyFace() {
        val boxes = listOf(staticAabb(0f, 0f, 0f, 2f, 2f, 2f), staticAabb(5f, 5f, 5f, 1f, 1f, 1f))
        assertEquals(6, exposedStaticFaces(0, boxes).size)
    }

    @Test
    fun adjacentBoxesRemoveBothSidesOfSharedSurface() {
        val boxes = listOf(staticAabb(0f, 0f, 0f, 2f, 1f, 2f), staticAabb(0f, 1f, 0f, 2f, 1f, 2f))
        assertTrue(exposedStaticFaces(0, boxes).none { it.direction == StaticFaceDirection.PositiveY })
        assertTrue(exposedStaticFaces(1, boxes).none { it.direction == StaticFaceDirection.NegativeY })
        assertEquals(5, exposedStaticFaces(0, boxes).size)
        assertEquals(5, exposedStaticFaces(1, boxes).size)
    }

    @Test
    fun partialTopOccluderLeavesFourNonOverlappingFragmentsWithCorrectArea() {
        val boxes = listOf(staticAabb(0f, 0f, 0f, 4f, 1f, 4f), staticAabb(0f, 1f, 0f, 2f, 1f, 2f))
        val sourceTop = exposedStaticFaces(0, boxes).filter { it.direction == StaticFaceDirection.PositiveY }
        assertEquals(4, sourceTop.size)
        val area = sourceTop.sumOf { quad ->
            val ax = quad.points[3] - quad.points[0]
            val az = quad.points[5] - quad.points[2]
            val bx = quad.points[6] - quad.points[0]
            val bz = quad.points[8] - quad.points[2]
            abs(ax * bz - az * bx).toDouble()
        }
        assertEquals(12.0, area, absoluteTolerance = 0.000001)
    }

    @Test
    fun boxBehindFacePlaneDoesNotOcclude() {
        val boxes = listOf(staticAabb(0f, 0f, 0f, 2f, 2f, 2f), staticAabb(0f, -0.5f, 0f, 1f, 0.5f, 1f))
        assertEquals(6, exposedStaticFaces(0, boxes).size)
    }

    @Test
    fun duplicateBoxAndCoplanarOverlapBelongToLowerDescriptorIndex() {
        val duplicate = listOf(staticAabb(0f, 0f, 0f, 2f, 2f, 2f), staticAabb(0f, 0f, 0f, 2f, 2f, 2f))
        assertEquals(6, exposedStaticFaces(0, duplicate).size)
        assertTrue(exposedStaticFaces(1, duplicate).isEmpty())

        val coplanar = listOf(staticAabb(0f, 0f, 0f, 4f, 2f, 4f), staticAabb(1f, 0f, 0f, 2f, 2f, 2f))
        val lower = exposedStaticFaces(0, coplanar).filter { it.direction == StaticFaceDirection.PositiveY }
        val higher = exposedStaticFaces(1, coplanar).filter { it.direction == StaticFaceDirection.PositiveY }
        assertEquals(1, lower.size)
        assertEquals(0, higher.size)
    }

    @Test
    fun repeatedBuildHasByteStablePointOrderAndSharedEdgesHaveStableUv() {
        val boxes = listOf(staticAabb(0f, 0f, 0f, 4f, 1f, 4f), staticAabb(0.5f, 1f, 0.5f, 1f, 1f, 1f))
        val first = exposedStaticFaces(0, boxes)
        val second = exposedStaticFaces(0, boxes)
        assertEquals(first.map { it.direction }, second.map { it.direction })
        first.zip(second).forEach { (a, b) -> assertTrue(a.points.contentEquals(b.points)) }

        val top = first.filter { it.direction == StaticFaceDirection.PositiveY }
        val uvByPoint = top.flatMap { quad ->
            quad.points.asList().chunked(3).map { point ->
                point.joinToString(",") to faceUv(point[0], point[1], point[2], 0f, 1f, 0f).toList()
            }
        }.groupBy({ it.first }, { it.second })
        assertTrue(uvByPoint.values.all { uvs -> uvs.distinct().size == 1 })
    }
}
