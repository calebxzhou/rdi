package calebxzhou.rdi.client.service

import calebxzau.rdi.client.modcatalog.getMetadataOrEmpty
import calebxzau.rdi.client.modcatalog.toCatalogSlugRef
import calebxzhou.rdi.client.model.UiMod
import calebxzhou.rdi.common.model.Mod

object LocalModCardResolver {
    suspend fun resolve(mods: List<UiMod>, context: ModCardResolveContext): Map<String, Mod.CardVo> {
        val refs = mods.mapNotNull { it.mod.toCatalogSlugRef() }.toSet()
        val metadata = context.modCatalog.getMetadataOrEmpty(refs)
        return mods.mapNotNull { uiMod ->
            val mod = uiMod.mod
            uiMod.toLocalCardVo(mod.toCatalogSlugRef()?.let(metadata::get))?.let { mod.projectKey() to it }
        }.toMap()
    }
}
