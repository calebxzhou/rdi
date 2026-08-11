package calebxzhou.rdi.client.service

import calebxzau.rdi.client.ui.imageBitmapFromArgb
import calebxzhou.rdi.client.model.toUiMod
import calebxzhou.rdi.common.model.Mod
import kotlin.test.Test
import kotlin.test.assertEquals

class ModIconColorAnalyzerTest {
    @Test
    fun `classifies the chromatic order used by icon mode`() {
        val samples = listOf(
            0xFFFF0000.toInt() to ModIconColorBand.RED,
            0xFFFF8000.toInt() to ModIconColorBand.ORANGE,
            0xFFFFFF00.toInt() to ModIconColorBand.YELLOW,
            0xFF00FF00.toInt() to ModIconColorBand.GREEN,
            0xFF00FFFF.toInt() to ModIconColorBand.CYAN,
            0xFF0000FF.toInt() to ModIconColorBand.BLUE,
            0xFF8000FF.toInt() to ModIconColorBand.PURPLE,
            0xFFFF00AA.toInt() to ModIconColorBand.PINK
        )

        samples.forEach { (argb, expected) ->
            val bitmap = imageBitmapFromArgb(IntArray(40 * 40) { argb }, 40, 40)

            val result = classifyModIconColor(bitmap).getOrThrow()

            assertEquals(ModIconColorCategory.CHROMATIC, result.category)
            assertEquals(expected, result.band)
        }
    }

    @Test
    fun `samples the centered square and ignores transparent pixels`() {
        val pixels = IntArray(64 * 64) { 0x4000FF00 }
        for (y in 16 until 48) {
            for (x in 16 until 48) {
                pixels[y * 64 + x] = 0xFF0000FF.toInt()
            }
        }

        val bitmap = imageBitmapFromArgb(pixels, 64, 64)

        assertEquals(
            ModIconColorBand.BLUE,
            classifyModIconColor(bitmap).getOrThrow().band
        )
    }

    @Test
    fun `samples the full centered square before applying the stride`() {
        val pixels = IntArray(64 * 64) { 0xFFFF0000.toInt() }
        for (y in 16 until 48) {
            for (x in 16 until 48) {
                pixels[y * 64 + x] = 0xFF0000FF.toInt()
            }
        }

        val bitmap = imageBitmapFromArgb(pixels, 64, 64)

        assertEquals(
            ModIconColorBand.RED,
            classifyModIconColor(bitmap).getOrThrow().band
        )
    }

    @Test
    fun `uses achromatic for an image with no visible chromatic pixels`() {
        val bitmap = imageBitmapFromArgb(IntArray(32 * 32) { 0xFF777777.toInt() }, 32, 32)

        assertEquals(
            ModIconColorCategory.ACHROMATIC,
            classifyModIconColor(bitmap).getOrThrow().category
        )
    }

    @Test
    fun `uses missing when no icon was resolved`() {
        assertEquals(
            ModIconColorCategory.MISSING,
            classifyModIconColor(null).getOrThrow().category
        )
    }

    @Test
    fun `uses missing when every sampled pixel is transparent`() {
        val bitmap = imageBitmapFromArgb(IntArray(32 * 32) { 0x00000000 }, 32, 32)

        assertEquals(
            ModIconColorCategory.MISSING,
            classifyModIconColor(bitmap).getOrThrow().category
        )
    }

    @Test
    fun `sorts color buckets before primary names`() {
        val mods = listOf(
            createMod("zulu"),
            createMod("alpha"),
            createMod("gray"),
            createMod("missing"),
            createMod("bravo")
        )
        val colors = mapOf(
            mods[0].key to chromatic(ModIconColorBand.RED, hue = 0f),
            mods[1].key to chromatic(ModIconColorBand.RED, hue = 0f),
            mods[2].key to achromatic(value = 0.5f),
            mods[3].key to missing(),
            mods[4].key to chromatic(ModIconColorBand.ORANGE, hue = 30f)
        )

        assertEquals(
            listOf("alpha", "zulu", "bravo", "gray", "missing"),
            sortModsByIconColor(mods, colors).map { it.primaryName }
        )
    }

    @Test
    fun `sorts achromatic icons by brightness`() {
        val mods = listOf(createMod("white"), createMod("black"), createMod("gray"))
        val colors = mapOf(
            mods[0].key to achromatic(value = 1f),
            mods[1].key to achromatic(value = 0f),
            mods[2].key to achromatic(value = 0.5f)
        )

        assertEquals(
            listOf("white", "gray", "black"),
            sortModsByIconColor(mods, colors).map { it.primaryName }
        )
    }

    @Test
    fun `sorts within a hue band by hue then saturation and value`() {
        val mods = listOf(
            createMod("value-high"),
            createMod("saturation-high"),
            createMod("hue-high"),
            createMod("hue-low"),
            createMod("saturation-low"),
            createMod("value-low")
        )
        val colors = mapOf(
            mods[0].key to chromatic(ModIconColorBand.RED, hue = 10f, saturation = 1f, value = 1f),
            mods[1].key to chromatic(ModIconColorBand.RED, hue = 10f, saturation = 1f, value = 0.5f),
            mods[2].key to chromatic(ModIconColorBand.RED, hue = 20f),
            mods[3].key to chromatic(ModIconColorBand.RED, hue = 5f),
            mods[4].key to chromatic(ModIconColorBand.RED, hue = 10f, saturation = 0.5f),
            mods[5].key to chromatic(ModIconColorBand.RED, hue = 10f, saturation = 1f, value = 0.25f)
        )

        assertEquals(
            listOf("hue-low", "value-high", "saturation-high", "value-low", "saturation-low", "hue-high"),
            sortModsByIconColor(mods, colors).map { it.primaryName }
        )
    }

    private fun chromatic(
        band: ModIconColorBand,
        hue: Float,
        saturation: Float = 1f,
        value: Float = 1f
    ) = ModIconColorSortKey(
        category = ModIconColorCategory.CHROMATIC,
        band = band,
        hue = hue,
        saturation = saturation,
        value = value
    )

    private fun achromatic(value: Float) = ModIconColorSortKey(
        category = ModIconColorCategory.ACHROMATIC,
        band = ModIconColorBand.NONE,
        hue = 0f,
        saturation = 0f,
        value = value
    )

    private fun missing() = ModIconColorSortKey(
        category = ModIconColorCategory.MISSING,
        band = ModIconColorBand.NONE,
        hue = 0f,
        saturation = 0f,
        value = 0f
    )

    private fun createMod(name: String) = Mod(
        platform = "mr",
        projectId = name,
        slug = name,
        fileId = "file",
        hash = "hash"
    ).toUiMod(Mod.CardVo(name = name))
}
