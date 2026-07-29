package calebxzhou.rdi.client.service

import calebxzhou.rdi.client.modcatalog.CatalogSlugRef
import calebxzhou.rdi.client.modcatalog.ModPlatform
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.service.ModrinthService

object ModrinthCardResolver : ModCardResolver {
    override val platform: String = "mr"

    override suspend fun resolve(
        mods: List<Mod>,
        context: ModCardResolveContext
    ): Map<String, Mod.CardVo> {
        val targetMods = mods.filter { it.platform.equals(platform, ignoreCase = true) }
        if (targetMods.isEmpty()) return emptyMap()

        val projectIds = targetMods.map { it.projectKey() }.distinct()
        val projects = context.modrinthProjects ?: ModrinthService.getMultipleProjects(projectIds)
        val projectIdToFile = targetMods.associateBy(
            keySelector = { it.projectKey() },
            valueTransform = { it.file }
        )

        val refs = projects.map { CatalogSlugRef(ModPlatform.MODRINTH, it.slug) }.toSet()
        val metadata = context.modCatalog.getMetadataOrEmpty(refs)
        return projects.associate { project ->
            val ref = CatalogSlugRef(ModPlatform.MODRINTH, project.slug)
            project.id to project.toUiCardVo(metadata[ref], projectIdToFile[project.id])
        }
    }
}
