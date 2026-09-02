package calebxzau.rdi.playermodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerModelRendererTest {
    @Test
    fun `detectSlimSkin recognizes the standard transparent slim markers`() {
        val pixels = opaqueRgba(64, 64)
        for (y in 20..31) setAlpha(pixels, 64, 54, y, 0)
        for (y in 52..63) setAlpha(pixels, 64, 46, y, 0)

        assertTrue(detectSlimSkin(64, 64, pixels))
    }

    @Test
    fun `detectSlimSkin keeps legacy skins classic`() {
        assertFalse(detectSlimSkin(64, 32, opaqueRgba(64, 32)))
    }

    @Test
    fun `render size preserves aspect ratio within the requested cap`() {
        assertEquals(PlayerModelRenderSize(200, 100), constrainedPlayerModelRenderSize(800, 400, 200))
        assertEquals(PlayerModelRenderSize(128, 96), constrainedPlayerModelRenderSize(128, 96, 256))
    }

    @Test
    fun `modern mesh contains outer layer and cape geometry`() {
        val mesh = buildPlayerMesh(slim = true, legacy = false, showOuterLayer = true, hasCape = true)

        assertEquals(5.5f, mesh.armPivotX)
        assertEquals(432, mesh.skinVertexCount)
        assertEquals(36, mesh.capeVertexCount)
        assertEquals((432 + 36) * 11, mesh.vertices.size)
    }

    @Test
    fun `legacy mesh only adds the hat outer layer`() {
        val mesh = buildPlayerMesh(slim = false, legacy = true, showOuterLayer = true, hasCape = false)

        assertEquals(6f, mesh.armPivotX)
        assertEquals(252, mesh.skinVertexCount)
        assertEquals(0, mesh.capeVertexCount)
    }

    private fun opaqueRgba(width: Int, height: Int): ByteArray = ByteArray(width * height * 4).also { rgba ->
        for (index in 3 until rgba.size step 4) rgba[index] = 0xFF.toByte()
    }

    private fun setAlpha(rgba: ByteArray, width: Int, x: Int, y: Int, alpha: Int) {
        rgba[(y * width + x) * 4 + 3] = alpha.toByte()
    }
}
