package calebxzhou.rdi.client.service

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

        return projects.associate { project ->
            project.id to project.toUiCardVo(projectIdToFile[project.id])
        }
    }
}
