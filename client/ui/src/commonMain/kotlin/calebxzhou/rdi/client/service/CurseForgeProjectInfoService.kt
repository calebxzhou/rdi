package calebxzhou.rdi.client.service

import calebxzhou.rdi.client.model.ModrinthProjectCategoryVo
import calebxzhou.rdi.client.model.ModrinthProjectGalleryVo
import calebxzhou.rdi.client.model.ModrinthProjectInfoVo
import calebxzhou.rdi.client.model.ModrinthProjectVersionDependencyVo
import calebxzhou.rdi.client.model.ModrinthProjectVersionFileVo
import calebxzhou.rdi.client.model.ModrinthProjectVersionVo
import calebxzhou.rdi.client.model.RemoteModSource
import calebxzhou.rdi.common.model.CurseForgeFile
import calebxzhou.rdi.common.model.CurseForgeModInfo
import calebxzhou.rdi.common.service.CurseForgeService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.abs

object CurseForgeProjectInfoService {
    suspend fun loadProjectInfo(
        projectId: String,
        mcVersion: String? = null,
        loader: String? = null
    ): ModrinthProjectInfoVo = coroutineScope {
        val modId = projectId.toIntOrNull() ?: error("CurseForge项目ID无效")
        val project = CurseForgeService.getModsInfo(listOf(modId)).firstOrNull()
            ?: error("找不到CurseForge项目")
        val description = async { CurseForgeService.getModDescription(modId) }
        val files = async {
            loadAvailableFiles(modId, mcVersion, loader)
        }
        project.toModrinthProjectInfoVo(
            description = description.await().takeIf(String::isNotBlank) ?: project.summary.orEmpty(),
            files = files.await()
        )
    }

    suspend fun loadProjectVersions(
        projectId: String,
        mcVersion: String,
        loader: String
    ): List<ModrinthProjectVersionVo> {
        val modId = projectId.toIntOrNull() ?: error("CurseForge项目ID无效")
        return loadAvailableFiles(modId, mcVersion, loader).toModrinthProjectVersionVos(modId)
    }

    private suspend fun loadAvailableFiles(
        modId: Int,
        mcVersion: String?,
        loader: String?
    ): List<CurseForgeFile> =
        CurseForgeService.getModFiles(
            modId = modId,
            mcVersion = mcVersion,
            loader = loader,
            limit = 50
        ).data
            .filter { it.isAvailable }
            .sortedByDescending { it.fileDate.orEmpty() }
}

private suspend fun CurseForgeModInfo.toModrinthProjectInfoVo(
    description: String,
    files: List<CurseForgeFile>
): ModrinthProjectInfoVo = coroutineScope {
    val versions = files.toModrinthProjectVersionVos(id)
    val fallbackSummary = summary?.takeIf(String::isNotBlank) ?: "暂无简介"
    val localizedSummary = RemoteModLocalization.introByCurseForgeSlug(slug, fallbackSummary)
    val localizedDescription = RemoteModLocalization.introByCurseForgeSlug(
        slug = slug,
        fallback = description.takeIf(String::isNotBlank) ?: fallbackSummary
    )
    val sourceUrl = links?.websiteUrl?.takeIf(String::isNotBlank)
        ?: "https://www.curseforge.com/minecraft/mc-mods/$slug"
    ModrinthProjectInfoVo(
        source = RemoteModSource.CURSEFORGE,
        projectId = id.toString(),
        slug = slug,
        title = RemoteModLocalization.titleByCurseForgeSlug(slug, name),
        summary = localizedSummary,
        description = localizedDescription,
        downloadsText = (downloadCount ?: 0).toCompactCountText(),
        followsText = thumbsUpCount?.toSeparatedCountText() ?: "0",
        iconUrl = logo?.thumbnailUrl ?: logo?.url,
        categories = categories.mapNotNull { category ->
            val categoryId = category.slug ?: category.name ?: return@mapNotNull null
            ModrinthProjectCategoryVo(categoryId, category.name ?: categoryId.toModrinthProjectCategoryLabel())
        }.take(6),
        gallery = screenshots.mapNotNull { screenshot ->
            val url = screenshot.url ?: screenshot.thumbnailUrl ?: return@mapNotNull null
            ModrinthProjectGalleryVo(url, screenshot.url, false, screenshot.title)
        },
        gameVersions = files.flatMap { it.gameVersions }.filter(String::isMinecraftVersion).distinct(),
        loaders = files.flatMap { it.gameVersions }.mapNotNull(String::toRemoteLoaderId).distinct(),
        versionIds = versions.map { it.id },
        versions = versions,
        sourceUrl = sourceUrl
    )
}

private suspend fun List<CurseForgeFile>.toModrinthProjectVersionVos(modId: Int): List<ModrinthProjectVersionVo> = coroutineScope {
    val sourceFiles = this@toModrinthProjectVersionVos
    val fileChangelogs = sourceFiles.map { file ->
        async {
            file.id to runCatching {
                CurseForgeService.getModFileChangelog(modId, file.id)
            }.getOrDefault("")
        }
    }.awaitAll().toMap()
    val fileDownloadUrls = sourceFiles.map { file ->
        async {
            file.id to runCatching {
                CurseForgeService.getModFileDownloadUrl(modId, file.id)
            }.getOrNull()
        }
    }.awaitAll().toMap()
    sourceFiles.map { file ->
        file.toModrinthProjectVersionVo(
            changelog = fileChangelogs[file.id].orEmpty(),
            downloadUrl = fileDownloadUrls[file.id]
        )
    }
}

private fun CurseForgeFile.toModrinthProjectVersionVo(
    changelog: String,
    downloadUrl: String?
): ModrinthProjectVersionVo {
    val loaders = gameVersions.mapNotNull(String::toRemoteLoaderId).distinct()
    val mappedFile = toModrinthProjectVersionFileVo(downloadUrl)
    return ModrinthProjectVersionVo(
        id = id.toString(),
        name = displayName?.takeIf(String::isNotBlank) ?: fileName ?: "CurseForge文件$id",
        versionNumber = displayName?.takeIf(String::isNotBlank) ?: fileName ?: id.toString(),
        changelog = changelog.takeIf(String::isNotBlank) ?: "暂无更新日志",
        publishedText = fileDate?.toRelativeTimeText() ?: "未知",
        rawPublished = fileDate.orEmpty(),
        downloadsText = (downloadCount ?: 0).toCompactCountText(),
        versionType = releaseType.toReleaseTypeText(),
        loaders = loaders,
        gameVersions = gameVersions.filter(String::isMinecraftVersion).distinct(),
        environment = null,
        minecraftJavaServer = null,
        dependencies = dependencies.mapNotNull { dependency ->
            val projectId = dependency.modId?.toString() ?: return@mapNotNull null
            val relationLabel = dependency.relationType.toDependencyRelationLabel()
            ModrinthProjectVersionDependencyVo(
                versionId = null,
                projectId = projectId,
                dependencyType = relationLabel,
                source = RemoteModSource.CURSEFORGE,
                relationLabel = relationLabel,
                required = dependency.relationType == 3
            )
        },
        primaryFile = mappedFile,
        files = listOf(mappedFile)
    )
}

private fun CurseForgeFile.toModrinthProjectVersionFileVo(downloadUrl: String?): ModrinthProjectVersionFileVo =
    ModrinthProjectVersionFileVo(
        fileId = id.toString(),
        filename = fileName ?: displayName ?: "curseforge-$id.jar",
        url = downloadUrl?.takeIf(String::isNotBlank) ?: this.downloadUrl?.takeIf(String::isNotBlank) ?: realDownloadUrl,
        size = fileLength,
        sizeText = fileLength?.toFileSizeText() ?: "未知大小",
        sha1 = hashes.firstOrNull { it.algo == 1 }?.value,
        sha512 = null,
        murmur2 = fileFingerprint.toString(),
        fileType = releaseType.toReleaseTypeText(),
        primary = true
    )

internal fun String.isMinecraftVersion(): Boolean =
    matches(Regex("""\d+\.\d+(\.\d+)?"""))

internal fun String.toRemoteLoaderId(): String? =
    when (lowercase()) {
        "forge" -> "forge"
        "fabric" -> "fabric"
        "quilt" -> "quilt"
        "neoforge" -> "neoforge"
        else -> null
    }

private fun Int?.toReleaseTypeText(): String? =
    when (this) {
        1 -> "release"
        2 -> "beta"
        3 -> "alpha"
        else -> null
    }

private fun Int?.toDependencyRelationLabel(): String =
    when (this) {
        1 -> "embedded"
        2 -> "optional"
        3 -> "required"
        4 -> "tool"
        5 -> "incompatible"
        6 -> "include"
        else -> "dependency"
    }

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
