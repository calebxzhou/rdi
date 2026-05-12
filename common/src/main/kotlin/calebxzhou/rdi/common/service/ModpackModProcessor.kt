
package calebxzhou.rdi.common.service

import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.normalizedSlug

object ModpackModProcessor {
    private val removedSlugs = setOf(
        "powerful-dummy",
        "spark",
        "essential-mod",
        "default-server-properties",
        "skybox-loader-forge"
    )

    private val clientSideSlugs = setOf(
        "status-effect-bars-reforged",
        "mafglib",
        "flighthud-reborn",
        "i18nupdatemod",
        "modern-ui",
        "controllable"
    )

    private val bothSideSlugs = setOf(
        "loot-beams-refork",
        "particular-reforged",
        "inventory-profiles-next",
        "inventory-tweaks-refoxed",
        "just-enough-resources-jer",
        "radiant-gear",
        "fusion-connected-textures"
    )

    fun processMods(mods: List<Mod>): MutableList<Mod> = mods.mapNotNull { mod ->
        val slug = mod.normalizedSlug
        when {
            slug.contains("backup") || slug in removedSlugs -> null
            slug in clientSideSlugs -> mod.copyKeepingTransients(Mod.Side.CLIENT)
            slug in bothSideSlugs -> mod.copyKeepingTransients(Mod.Side.BOTH)
            else -> mod.copyKeepingTransients(mod.side)
        }
    }.toMutableList()

    private fun Mod.copyKeepingTransients(side: Mod.Side): Mod =
        copy(side = side, downloadUrls = downloadUrls.toList()).also {
            it.vo = vo?.copy(side = side)
            it.file = file
        }
}
