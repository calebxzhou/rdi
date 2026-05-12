package calebxzhou.rdi.common.service

import calebxzhou.rdi.common.model.Mod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ModpackModProcessorTest {
    @Test
    fun removesKnownBadModsAndBackupMods() {
        val mods = listOf(
            mod("world-backups"),
            mod("spark"),
            mod("essential-mod"),
            mod("modern-ui")
        )

        val processed = ModpackModProcessor.processMods(mods)

        assertFalse(processed.any { it.slug == "world-backups" })
        assertFalse(processed.any { it.slug == "spark" })
        assertFalse(processed.any { it.slug == "essential-mod" })
        assertEquals(listOf("modern-ui"), processed.map(Mod::slug))
    }

    @Test
    fun overridesKnownModSides() {
        val mods = listOf(
            mod("loot-beams-refork", Mod.Side.CLIENT),
            mod("modern-ui", Mod.Side.BOTH)
        )

        val processed = ModpackModProcessor.processMods(mods)

        assertEquals(Mod.Side.BOTH, processed.first { it.slug == "loot-beams-refork" }.side)
        assertEquals(Mod.Side.CLIENT, processed.first { it.slug == "modern-ui" }.side)
    }

    private fun mod(slug: String, side: Mod.Side = Mod.Side.BOTH): Mod = Mod(
        platform = "mr",
        projectId = slug,
        slug = slug,
        fileId = "file-$slug",
        hash = "hash-$slug",
        side = side
    )
}
