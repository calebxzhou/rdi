package calebxzhou.rdi.client.service

import calebxzhou.mykotutils.std.humanFileSize
import calebxzhou.rdi.common.DIR
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.net.downloadFileFrom
import calebxzhou.rdi.common.net.httpRequest
import calebxzhou.rdi.common.service.ModService
import calebxzhou.rdi.common.util.sha1
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.net.URI
import java.util.jar.JarFile

data class GithubRepoRef(
    val owner: String,
    val name: String
) {
    val projectId get() = "$owner/$name"
}

data class GithubRelease(
    val tagName: String,
    val name: String,
    val publishedAt: String,
    val assets: List<GithubReleaseAsset>
)

data class GithubReleaseAsset(
    val releaseTag: String,
    val releaseName: String,
    val releasePublishedAt: String,
    val name: String,
    val size: Long,
    val downloadUrl: String
) {
    val key get() = "$releaseTag:$name:$downloadUrl"
    val sizeText get() = size.takeIf { it > 0 }?.humanFileSize ?: "--"
}

object GithubExtraModService {
    private val cacheDir: File
        get() = DIR.resolve("pack-proc").resolve("github-extra-mod-cache").apply { mkdirs() }

    suspend fun fetchReleases(repoUrl: String): Result<Pair<GithubRepoRef, List<GithubRelease>>> = runCatching {
        val repo = parseRepo(repoUrl)
        val response = httpRequest {
            url {
                protocol = URLProtocol.HTTPS
                host = "api.github.com"
                encodedPath = "/repos/${repo.owner}/${repo.name}/releases"
            }
            header("Accept", "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException(response.toGithubErrorMessage(repo.projectId))
        }
        val releases = response.body<List<GithubReleaseResp>>()
            .map { release ->
                GithubRelease(
                    tagName = release.tagName,
                    name = release.name.ifBlank { release.tagName },
                    publishedAt = release.publishedAt.orEmpty(),
                    assets = release.assets
                        .filter {
                            it.name.endsWith(".jar", ignoreCase = true) &&
                                it.browserDownloadUrl.isNotBlank()
                        }
                        .map { asset ->
                            GithubReleaseAsset(
                                releaseTag = release.tagName,
                                releaseName = release.name.ifBlank { release.tagName },
                                releasePublishedAt = release.publishedAt.orEmpty(),
                                name = asset.name,
                                size = asset.size,
                                downloadUrl = asset.browserDownloadUrl
                            )
                        }
                )
            }
            .filter { it.assets.isNotEmpty() }
        if (releases.isEmpty()) {
            throw IllegalStateException("该仓库没有包含jar文件的Release")
        }
        repo to releases
    }

    suspend fun buildExtraModFromAsset(
        repo: GithubRepoRef,
        asset: GithubReleaseAsset,
        side: Mod.Side,
        onProgress: (String) -> Unit
    ): Result<Mod> = runCatching {
        val jarFile = downloadAsset(repo, asset, onProgress)
        val modId = readModId(jarFile)
        val hash = withContext(Dispatchers.IO) { jarFile.toPath().sha1.lowercase() }
        Mod(
            platform = "github",
            projectId = repo.projectId,
            slug = modId,
            fileId = "${asset.releaseTag}/${asset.name}",
            hash = hash,
            side = side,
            downloadUrls = listOf(asset.downloadUrl)
        )
    }

    fun parseRepo(repoUrl: String): GithubRepoRef {
        val raw = repoUrl.trim().trimEnd('/')
        if (raw.isBlank()) error("GitHub repo URL不能为空")
        val path = runCatching {
            val uri = URI(raw)
            if (uri.host?.equals("github.com", ignoreCase = true) != true) {
                error("只支持github.com仓库链接")
            }
            uri.path.orEmpty()
        }.getOrElse {
            raw.removePrefix("github.com")
        }
        val parts = path.trim('/').split('/').filter(String::isNotBlank)
        if (parts.size < 2) error("GitHub repo URL格式应为https://github.com/owner/repo")
        return GithubRepoRef(
            owner = parts[0],
            name = parts[1].removeSuffix(".git")
        )
    }

    private suspend fun downloadAsset(
        repo: GithubRepoRef,
        asset: GithubReleaseAsset,
        onProgress: (String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val target = cacheDir.resolve("${repo.projectId}_${asset.releaseTag}_${asset.name}".toSafeFileName())
        if (target.exists() && (asset.size <= 0 || target.length() == asset.size)) {
            onProgress("已复用临时文件 ${asset.name}")
            return@withContext target
        }
        onProgress("下载GitHub文件 ${asset.name}")
        target.toPath().downloadFileFrom(
            url = asset.downloadUrl,
            knownSize = asset.size,
            validator = { path ->
                if (asset.size <= 0 || path.toFile().length() == asset.size) {
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("下载文件大小不匹配: ${asset.name}"))
                }
            }
        ) { progress ->
            val downloaded = progress.bytesDownloaded.takeIf { it >= 0 }?.humanFileSize ?: "0B"
            val total = progress.totalBytes.takeIf { it > 0 }?.humanFileSize ?: asset.sizeText
            onProgress("下载${asset.name} $downloaded/$total")
        }.getOrElse { throw it }
        target
    }

    private suspend fun readModId(jarFile: File): String = withContext(Dispatchers.IO) {
        JarFile(jarFile).use { jar ->
            ModService.run {
                jar.readModMeta()?.primaryModId
                    ?.trim()
                    ?.lowercase()
                    ?.takeIf(String::isNotBlank)
            }
        } ?: throw IllegalStateException("无法从${jarFile.name}读取Mod ID")
    }

    private suspend fun io.ktor.client.statement.HttpResponse.toGithubErrorMessage(projectId: String): String {
        val statusText = "${status.value} ${status.description}"
        return when (status) {
            HttpStatusCode.NotFound -> "无法读取${projectId}的Release，确认仓库存在且是公开仓库"
            HttpStatusCode.Forbidden -> {
                val remaining = headers["X-RateLimit-Remaining"]?.toIntOrNull()
                if (remaining == 0) "GitHub API请求次数已用完，稍后再试" else "GitHub拒绝请求: $statusText"
            }
            else -> "读取GitHub Release失败: $statusText ${bodyAsText().take(200)}"
        }
    }

    private fun String.toSafeFileName(): String =
        replace(Regex("""[\\/:*?"<>|]+"""), "_")
            .replace(Regex("""\s+"""), "_")
            .take(180)
}

@Serializable
private data class GithubReleaseResp(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GithubAssetResp> = emptyList()
)

@Serializable
private data class GithubAssetResp(
    val name: String = "",
    val size: Long = 0,
    @SerialName("browser_download_url") val browserDownloadUrl: String = ""
)
