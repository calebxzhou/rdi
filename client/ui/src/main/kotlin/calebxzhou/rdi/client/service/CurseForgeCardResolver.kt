package calebxzhou.rdi.client.service

import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.service.CurseForgeService

object CurseForgeCardResolver : ModCardResolver {
    override val platform: String = "cf"

    override suspend fun resolve(
        mods: List<Mod>,
        context: ModCardResolveContext
    ): Map<String, Mod.CardVo> {
        val targetMods = mods.filter { it.platform.equals(platform, ignoreCase = true) }
        if (targetMods.isEmpty()) return emptyMap()

        val projectIds = targetMods.mapNotNull { it.projectId.toIntOrNull() }.distinct()
        if (projectIds.isEmpty()) return emptyMap()

        val projectIdToFile = targetMods.associateBy(
            keySelector = { it.projectKey() },
            valueTransform = { it.file }
        )

        return CurseForgeService.getModsInfo(projectIds).associate { info ->
            val projectKey = info.id.toString()
            projectKey to info.toUiCardVo(projectIdToFile[projectKey])
        }
    }
}
