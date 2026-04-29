package calebxzhou.rdi.client.service

import calebxzhou.rdi.client.model.ModrinthProjectCategoryVo
import calebxzhou.rdi.client.model.ModrinthProjectGalleryVo
import calebxzhou.rdi.client.model.ModrinthProjectInfoVo
import calebxzhou.rdi.client.model.ModrinthProjectVersionDependencyVo
import calebxzhou.rdi.client.model.ModrinthProjectVersionFileVo
import calebxzhou.rdi.client.model.ModrinthProjectVersionVo
import calebxzhou.rdi.common.model.ModrinthDependency
import calebxzhou.rdi.common.model.ModrinthV3GalleryItem
import calebxzhou.rdi.common.model.ModrinthV3Project
import calebxzhou.rdi.common.model.ModrinthV3Version
import calebxzhou.rdi.common.model.ModrinthV3VersionFile
import calebxzhou.rdi.common.service.ModrinthService
import kotlin.math.abs

object ModrinthProjectInfoService {
    suspend fun loadProjectInfo(
        projectId: String,
        mcVersion: String? = null,
        loader: String? = null
    ): ModrinthProjectInfoVo {
        val project = ModrinthService.getProjectDetailV3(projectId)
        val versions = ModrinthService.getProjectVersionsV3(
            projectIdOrSlug = project.id,
            gameVersions = mcVersion?.trim()?.takeIf(String::isNotBlank)?.let { listOf(it) } ?: emptyList(),
            loaders = loader?.trim()?.takeIf(String::isNotBlank)?.let { listOf(it) } ?: emptyList(),
            includeChangelog = true
        )
        return project.toModrinthProjectInfoVo(versions)
    }

    suspend fun loadProjectVersions(
        projectId: String,
        mcVersion: String? = null,
        loader: String? = null,
        includeChangelog: Boolean = true
    ): List<ModrinthProjectVersionVo> =
        ModrinthService.getProjectVersionsV3(
            projectIdOrSlug = projectId,
            gameVersions = mcVersion?.trim()?.takeIf(String::isNotBlank)?.let { listOf(it) } ?: emptyList(),
            loaders = loader?.trim()?.takeIf(String::isNotBlank)?.let { listOf(it) } ?: emptyList(),
            includeChangelog = includeChangelog
        ).map(ModrinthV3Version::toModrinthProjectVersionVo)
}

private fun ModrinthV3Project.toModrinthProjectInfoVo(versions: List<ModrinthV3Version>): ModrinthProjectInfoVo {
    val selectedCategories = (categories + loaders).distinct().take(6)
    val versionOrder = this.versions.withIndex().associate { it.value to it.index }
    return ModrinthProjectInfoVo(
        projectId = id,
        slug = slug,
        title = RemoteModLocalization.titleByModrinthSlug(slug, name),
        summary = RemoteModLocalization.introByModrinthSlug(slug, summary?.takeIf(String::isNotBlank) ?: "暂无简介"),
        description = RemoteModLocalization.introByModrinthSlug(slug, description?.takeIf(String::isNotBlank) ?: "暂无描述"),
        downloadsText = downloads.toCompactCountText(),
        followsText = followers.toSeparatedCountText(),
        iconUrl = iconUrl,
        categories = selectedCategories.map { ModrinthProjectCategoryVo(it, it.toModrinthProjectCategoryLabel()) },
        gallery = gallery.sortedWith(compareByDescending<ModrinthV3GalleryItem> { it.featured }.thenBy { it.ordering })
            .map { ModrinthProjectGalleryVo(it.url, it.rawUrl, it.featured, it.name) },
        gameVersions = gameVersions,
        loaders = loaders,
        versionIds = this.versions,
        versions = versions
            .sortedWith(compareBy<ModrinthV3Version> { versionOrder[it.id] ?: Int.MAX_VALUE }.thenByDescending { it.datePublished })
            .map(ModrinthV3Version::toModrinthProjectVersionVo)
    )
}

private fun ModrinthV3Version.toModrinthProjectVersionVo(): ModrinthProjectVersionVo {
    val mappedFiles = files.map(ModrinthV3VersionFile::toModrinthProjectVersionFileVo)
    val primaryFile = mappedFiles.firstOrNull { it.primary } ?: mappedFiles.firstOrNull()
    return ModrinthProjectVersionVo(
        id = id,
        name = name,
        versionNumber = versionNumber,
        changelog = changelog?.takeIf(String::isNotBlank) ?: "暂无更新日志",
        publishedText = datePublished.toRelativeTimeText(),
        rawPublished = datePublished,
        downloadsText = downloads.toCompactCountText(),
        versionType = versionType,
        loaders = loaders,
        gameVersions = gameVersions,
        environment = environment,
        minecraftJavaServer = minecraftJavaServer,
        dependencies = dependencies.map(ModrinthDependency::toModrinthProjectVersionDependencyVo),
        primaryFile = primaryFile,
        files = mappedFiles
    )
}

private fun ModrinthDependency.toModrinthProjectVersionDependencyVo(): ModrinthProjectVersionDependencyVo =
    ModrinthProjectVersionDependencyVo(
        versionId = versionId,
        projectId = projectId,
        dependencyType = dependencyType
    )

private fun ModrinthV3VersionFile.toModrinthProjectVersionFileVo(): ModrinthProjectVersionFileVo =
    ModrinthProjectVersionFileVo(
        fileId = id,
        filename = filename,
        url = url,
        size = size,
        sizeText = size?.toFileSizeText() ?: "未知大小",
        sha1 = hashes.sha1,
        sha512 = hashes.sha512,
        fileType = fileType,
        primary = primary
)

private fun Long.toFileSizeText(): String {
    val units = listOf("B", "KB", "MB", "GB")
    var value = abs(toDouble())
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex += 1
    }
    val prefix = if (this < 0) "-" else ""
    val text = if (unitIndex == 0) value.toLong().toString() else value.toStringWithOneDecimal()
    return "$prefix$text${units[unitIndex]}"
}

private fun Double.toStringWithOneDecimal(): String {
    val scaled = kotlin.math.round(this * 10).toInt()
    val whole = scaled / 10
    val fraction = scaled % 10
    return if (fraction == 0) whole.toString() else "$whole.$fraction"
}
