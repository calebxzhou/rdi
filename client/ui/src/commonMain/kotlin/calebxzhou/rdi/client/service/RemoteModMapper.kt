package calebxzhou.rdi.client.service

import calebxzhou.rdi.client.model.ModrinthProjectCardVo
import calebxzhou.rdi.client.model.ModrinthProjectSearchResult
import calebxzhou.rdi.client.model.RemoteModCardVo
import calebxzhou.rdi.client.model.RemoteModSearchResult
import calebxzhou.rdi.client.model.RemoteModSource

fun ModrinthProjectSearchResult.toRemoteModSearchResult(): RemoteModSearchResult =
    RemoteModSearchResult(
        mods = projects.map(ModrinthProjectCardVo::toRemoteModCardVo),
        offset = offset,
        limit = limit,
        totalHits = totalHits
    )

fun ModrinthProjectCardVo.toRemoteModCardVo(): RemoteModCardVo {
    val loaderIds = loaders.map(String::lowercase).toSet()
    return RemoteModCardVo(
        source = RemoteModSource.MODRINTH,
        projectId = projectId,
        slug = slug,
        title = RemoteModLocalization.titleByModrinthSlug(slug, title),
        author = author,
        summary = RemoteModLocalization.introByModrinthSlug(slug, description),
        iconUrl = iconUrl,
        downloadsText = downloadsText,
        followsText = followsText,
        modifiedText = modifiedText,
        categories = categories.filterNot { it.id.lowercase() in loaderIds },
        gameVersions = gameVersions,
        loaders = loaders,
        clientSide = clientSide,
        serverSide = serverSide
    )
}
