package calebxzhou.rdi.client.service

import calebxzhou.rdi.client.model.ModrinthProjectCardVo
import calebxzhou.rdi.client.model.ModrinthProjectCategoryVo
import calebxzhou.rdi.client.model.ModrinthProjectSearchResult
import calebxzhou.rdi.client.model.RemoteModCardVo
import calebxzhou.rdi.client.model.RemoteModSearchResult
import calebxzhou.rdi.client.model.RemoteModSource
import calebxzhou.rdi.common.model.CurseForgeModInfo

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

fun CurseForgeModInfo.toRemoteModCardVo(): RemoteModCardVo {
    val loaders = latestFiles
        .flatMap { it.gameVersions }
        .mapNotNull(String::toRemoteLoaderId)
        .distinct()
    val gameVersions = latestFiles
        .flatMap { it.gameVersions }
        .filter(String::isMinecraftVersion)
        .distinct()
    return RemoteModCardVo(
        source = RemoteModSource.CURSEFORGE,
        projectId = id.toString(),
        slug = slug,
        title = RemoteModLocalization.titleByCurseForgeSlug(slug, name),
        author = authors.firstOrNull()?.name ?: "CurseForge",
        summary = RemoteModLocalization.introByCurseForgeSlug(
            slug = slug,
            fallback = summary?.takeIf(String::isNotBlank) ?: "暂无简介"
        ),
        iconUrl = logo?.thumbnailUrl ?: logo?.url,
        downloadsText = (downloadCount ?: 0).toCompactCountText(),
        followsText = thumbsUpCount?.toSeparatedCountText(),
        modifiedText = dateModified?.toRelativeTimeText(),
        categories = categories.mapNotNull { category ->
            val categoryId = category.slug ?: category.name ?: return@mapNotNull null
            ModrinthProjectCategoryVo(categoryId, category.name ?: categoryId.toModrinthProjectCategoryLabel())
        }.take(4),
        gameVersions = gameVersions,
        loaders = loaders,
        clientSide = null,
        serverSide = null
    )
}
