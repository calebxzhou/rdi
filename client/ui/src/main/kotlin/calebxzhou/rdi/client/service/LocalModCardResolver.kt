package calebxzhou.rdi.client.service

import calebxzhou.rdi.common.model.Mod

object LocalModCardResolver {
    suspend fun resolve(mods: List<Mod>, context: ModCardResolveContext): Map<String, Mod.CardVo> {
        val refs = mods.mapNotNull(Mod::toCatalogSlugRef).toSet()
        val metadata = context.modCatalog.getMetadataOrEmpty(refs)
        return mods.mapNotNull { mod ->
            mod.toLocalCardVo(mod.toCatalogSlugRef()?.let(metadata::get))?.let { mod.projectKey() to it }
        }.toMap()
    }
}
