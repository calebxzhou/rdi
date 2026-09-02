package calebxzau.rdi.playermodel.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.math.sqrt

class PlayerModelCoreTest {
    @Test
    fun detectsClassicAndSlimTransparentArmRegions() {
        val classic = ByteArray(64 * 64 * 4) { 0xff.toByte() }
        val slim = classic.copyOf()
        for (y in 20..31) slim[(y * 64 + 54) * 4 + 3] = 0
        for (y in 52..63) slim[(y * 64 + 46) * 4 + 3] = 0
        assertFalse(detectSlimSkin(64, 64, classic))
        assertTrue(detectSlimSkin(64, 64, slim))
    }

    @Test
    fun meshCountsAndUvLayoutAreStable() {
        val mesh = buildPlayerMeshData(slim = true, legacy = false, showOuterLayer = true, hasCape = true)
        assertEquals(0, mesh.vertices.size % 11)
        assertTrue(mesh.skinVertexCount > 0)
        assertEquals(36, mesh.capeVertexCount)
        assertEquals(mesh.skinVertexCount + mesh.capeVertexCount, mesh.vertices.size / 11)
        val uvs = mesh.vertices.asSequence().chunked(11).map { it[3] to it[4] }.toList()
        assertTrue(uvs.all { it.first in 0f..64f && it.second in 0f..64f })
    }

    @Test
    fun everyCuboidFaceHasFrontFacingNonDegenerateTriangles() {
        val mesh = buildPlayerMeshData(slim = false, legacy = false, showOuterLayer = true, hasCape = true)
        val vertices = mesh.vertices.asSequence().chunked(11).toList()
        assertEquals(0, vertices.size % 6)
        vertices.chunked(6).forEach { face ->
            val normal = face[0].normal()
            face.forEach { vertex ->
                val vertexNormal = vertex.normal()
                assertEquals(normal[0], vertexNormal[0])
                assertEquals(normal[1], vertexNormal[1])
                assertEquals(normal[2], vertexNormal[2])
            }
            listOf(Triple(0, 1, 2), Triple(3, 4, 5)).forEach { (first, second, third) ->
                val cross = cross(face[first].position(), face[second].position(), face[third].position())
                assertTrue(length(cross) > 0.001f)
                assertTrue(dot(cross, normal) > 0.001f)
            }
        }
    }

    @Test
    fun headBaseAndHatKeepTheirTopAndBottomUvRectangles() {
        val mesh = buildPlayerMeshData(slim = false, legacy = false, showOuterLayer = true, hasCape = false)
        val vertices = mesh.vertices.asSequence().chunked(11).toList()
        val headFaces = vertices.chunked(6).filter { face -> face.all { it[8] == PlayerModelPart.HEAD.ordinal.toFloat() } }
        val topUvSets = headFaces.filter { it[0][6] == 1f }.map { it.map { vertex -> vertex.uv() }.toSet() }
        val bottomUvSets = headFaces.filter { it[0][6] == -1f }.map { it.map { vertex -> vertex.uv() }.toSet() }
        assertTrue(topUvSets.contains(setOf(8f to 0f, 16f to 0f, 8f to 8f, 16f to 8f)))
        assertTrue(topUvSets.contains(setOf(40f to 0f, 48f to 0f, 40f to 8f, 48f to 8f)))
        assertTrue(bottomUvSets.contains(setOf(16f to 0f, 24f to 0f, 16f to 8f, 24f to 8f)))
        assertTrue(bottomUvSets.contains(setOf(48f to 0f, 56f to 0f, 48f to 8f, 56f to 8f)))
    }

    private fun List<Float>.position() = floatArrayOf(this[0], this[1], this[2])
    private fun List<Float>.normal() = floatArrayOf(this[5], this[6], this[7])
    private fun List<Float>.uv() = this[3] to this[4]

    private fun cross(a: FloatArray, b: FloatArray, c: FloatArray): FloatArray {
        val ab = floatArrayOf(b[0] - a[0], b[1] - a[1], b[2] - a[2])
        val ac = floatArrayOf(c[0] - a[0], c[1] - a[1], c[2] - a[2])
        return floatArrayOf(
            ab[1] * ac[2] - ab[2] * ac[1],
            ab[2] * ac[0] - ab[0] * ac[2],
            ab[0] * ac[1] - ab[1] * ac[0]
        )
    }

    private fun dot(a: FloatArray, b: FloatArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    private fun length(value: FloatArray) = sqrt(dot(value, value))
}
