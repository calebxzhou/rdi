package calebxzhou.rdi.client.service

import calebxzhou.rdi.client.model.ModrinthProjectCategoryVo
import calebxzhou.rdi.client.model.ModrinthProjectVersionVo
import calebxzhou.rdi.client.model.RemoteModCardVo
import calebxzhou.rdi.client.model.RemoteModSource
import calebxzhou.rdi.common.model.ModrinthProject
import calebxzhou.rdi.common.service.CurseForgeService
import calebxzhou.rdi.common.service.ModrinthService
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

object RemoteModDependencyService {
    suspend fun loadRequiredDependencyCards(versions: List<ModrinthProjectVersionVo>): List<RemoteModCardVo> =
        coroutineScope {
            val requiredDependencies = versions
                .flatMap { it.dependencies }
                .filter { it.required }

            val modrinthIds = requiredDependencies
                .filter { it.source == RemoteModSource.MODRINTH }
                .mapNotNull { it.projectId?.trim()?.takeIf(String::isNotBlank) }
                .distinct()
            val curseForgeIds = requiredDependencies
                .filter { it.source == RemoteModSource.CURSEFORGE }
                .mapNotNull { it.projectId?.trim()?.toIntOrNull() }
                .distinct()

            val modrinthCards = async {
                if (modrinthIds.isEmpty()) {
                    emptyList()
                } else {
                    ModrinthService.getMultipleProjects(modrinthIds)
                        .map(ModrinthProject::toRemoteModCardVo)
                }
            }
            val curseForgeCards = async {
                if (curseForgeIds.isEmpty()) {
                    emptyList()
                } else {
                    CurseForgeService.getModsInfo(curseForgeIds)
                        .map { it.toRemoteModCardVo() }
                }
            }

            (modrinthCards.await() + curseForgeCards.await())
                .distinctBy { "${it.source}:${it.projectId}" }
        }
}

private fun ModrinthProject.toRemoteModCardVo(): RemoteModCardVo {
    val loaderIds = loaders.map(String::lowercase).toSet()
    val categoryVos = (categories + loaders)
        .distinct()
        .map { ModrinthProjectCategoryVo(it, it.toModrinthProjectCategoryLabel()) }
    return RemoteModCardVo(
        source = RemoteModSource.MODRINTH,
        projectId = id,
        slug = slug,
        title = RemoteModLocalization.titleByModrinthSlug(slug, title),
        author = "Modrinth",
        summary = RemoteModLocalization.introByModrinthSlug(slug, description?.takeIf(String::isNotBlank) ?: "暂无简介"),
        iconUrl = iconUrl,
        downloadsText = downloads.toCompactCountText(),
        followsText = followers.toSeparatedCountText(),
        modifiedText = updated.toRelativeTimeText(),
        categories = categoryVos.filterNot { it.id.lowercase() in loaderIds }.take(4),
        gameVersions = gameVersions,
        loaders = loaders,
        clientSide = clientSide,
        serverSide = serverSide
    )
}
