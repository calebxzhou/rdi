package calebxzau.rdi.server.modpack

import calebxzhou.rdi.common.exception.RequestError
import calebxzau.rdi.common.model.ContentPlatform
import calebxzau.rdi.common.model.ContentType
import calebxzau.rdi.common.model.Modpack2Content
import calebxzau.rdi.common.model.Modpack2ContentKeyDto
import calebxzau.rdi.common.model.Modpack2ContentSourceDto
import calebxzau.rdi.common.model.Modpack2VersionManifestDto
import calebxzau.rdi.common.model.Modpack2RawFile
import calebxzau.rdi.common.model.Modpack2ManifestFormat
import calebxzhou.rdi.common.model.CurseForgePackManifest
import calebxzhou.rdi.common.model.ModrinthModpackIndex
import calebxzhou.rdi.common.model.ModLoader
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.master.service.host2.validateGameAndLoader
import calebxzau.rdi.common.model.Modpack2Loader
import calebxzhou.rdi.common.serdesJson
import calebxzhou.rdi.common.model.ModrinthVersionInfo
import calebxzhou.rdi.common.net.downloadFileFrom
import calebxzhou.rdi.common.net.httpRequest
import calebxzhou.rdi.common.service.CurseForgeService
import calebxzhou.rdi.common.service.ModrinthService
import calebxzhou.rdi.common.util.sha256
import calebxzhou.rdi.common.util.sha1
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** Resolves every external content reference and verifies its declared identity. */
object Modpack2ContentValidator {
    suspend fun validateAll(
        contents: List<Modpack2Content>,
        manifest: Modpack2VersionManifestDto? = null,
        rawFiles: List<Modpack2RawFile> = emptyList(),
        expectedMc: Int? = null,
        expectedLoader: Modpack2Loader? = null,
    ) {
        contents.forEach { content ->
            val binding = manifest?.bindings
                ?.firstOrNull { binding -> binding.contentKey.matches(content) }
            val rawSource = binding?.source as? Modpack2ContentSourceDto.RawSource
            val raw = rawSource?.let { source -> rawFiles.firstOrNull { it.root == source.root && it.path == source.path } }
            val shadow = rawSource != null && manifest?.let { isManifestShadow(content, it) } == true
            when (content.platform) {
                ContentPlatform.CurseForge -> validateCurseForge(content, raw, shadow, expectedMc, expectedLoader)
                ContentPlatform.Modrinth -> validateModrinth(content, raw, shadow, expectedMc, expectedLoader)
                ContentPlatform.GitHub -> validateGitHub(content, raw, shadow)
            }
        }
    }

    private suspend fun validateCurseForge(
        content: Modpack2Content,
        raw: Modpack2RawFile?,
        shadow: Boolean,
        expectedMc: Int?,
        expectedLoader: Modpack2Loader?,
    ) {
        val (projectId, fileId) = parseCurseForgeIds(content.projectId, content.fileId)
        val file = remoteRequest("无法验证CurseForge内容${content.projectId}/${content.fileId}") {
            CurseForgeService.getModFileInfo(projectId, fileId)
        } ?: throw RequestError("CurseForge不存在${content.projectId}/${content.fileId}对应文件")
        if (file.modId != 0 && file.modId != projectId) {
            throw RequestError("CurseForge文件不属于所选项目")
        }
        validateCurseForgeEnvironment(file.gameVersions, expectedMc, expectedLoader, content.type, content.slug)
        if (file.fileFingerprint.toString() != content.hash) {
            throw RequestError("CurseForge内容指纹不匹配: ${content.slug}")
        }
        val remoteSize = file.fileLength ?: file.fileSizeOnDisk
            ?: throw RequestError("CurseForge内容缺少文件大小: ${content.slug}")
        if (remoteSize <= 0L || content.fileSize <= 0L || remoteSize != content.fileSize) {
            throw RequestError("CurseForge内容文件大小不匹配: ${content.slug}")
        }
        raw?.takeUnless { shadow }?.let { expected ->
            val remoteSha1 = file.hashes.firstOrNull { it.algo == 1 }?.value
            if (!remoteSha1.equals(expected.sha1, ignoreCase = true) || remoteSize != expected.size ||
                file.fileName?.substringAfterLast('/') != expected.path.substringAfterLast('/')
            ) throw RequestError("CurseForge Raw来源与远端文件不匹配: ${content.slug}")
        }
    }

    private suspend fun validateModrinth(
        content: Modpack2Content,
        raw: Modpack2RawFile?,
        shadow: Boolean,
        expectedMc: Int?,
        expectedLoader: Modpack2Loader?,
    ) {
        val version = remoteRequest("无法验证Modrinth内容${content.projectId}/${content.fileId}") {
            ModrinthService.mrreq("version/${content.fileId}").body<ModrinthVersionInfo>()
        }
        if (version.projectId != content.projectId) {
            throw RequestError("Modrinth版本不属于所选项目")
        }
        expectedMc?.let {
            val expectedVersion = McVersion.fromMinor(it.toString())?.mcVer
                ?: throw RequestError("不支持的MC版本")
            if (version.gameVersions.isNotEmpty() &&
                version.gameVersions.none { gameVersion -> gameVersion.equals(expectedVersion, ignoreCase = true) }
            ) {
                throw RequestError("Modrinth文件不支持整合包MC版本: ${content.slug}")
            }
        }
        expectedLoader?.let { loader ->
            val name = when (loader) {
                Modpack2Loader.Forge -> ModLoader.forge.name
                Modpack2Loader.NeoForge -> ModLoader.neoforge.name
            }
            if (content.type == calebxzau.rdi.common.model.ContentType.Mod &&
                version.loaders.none { it.equals(name, ignoreCase = true) }
            ) {
                throw RequestError("Modrinth文件不支持整合包ModLoader: ${content.slug}")
            }
        }
        val targetPath = content.targetPath
            ?: throw RequestError("Modrinth内容缺少目标路径: ${content.slug}")
        val matchingFiles = version.files.filter {
            it.hashes["sha1"].equals(content.hash, ignoreCase = true)
        }
        if (matchingFiles.size != 1) {
            throw RequestError("Modrinth版本中找不到匹配hash的文件: ${content.slug}")
        }
        val file = matchingFiles.single()
        val remoteSize = file.size ?: throw RequestError("Modrinth内容缺少文件大小: ${content.slug}")
        if (remoteSize <= 0L || content.fileSize <= 0L || remoteSize != content.fileSize) {
            throw RequestError("Modrinth内容文件大小不匹配: ${content.slug}")
        }
        if (file.filename.substringAfterLast('/').substringAfterLast('\\') != targetPath.substringAfterLast('/')) {
            throw RequestError("Modrinth内容文件名与目标路径不匹配: ${content.slug}")
        }
        raw?.takeUnless { shadow }?.let { expected ->
            if (!file.hashes["sha1"].equals(expected.sha1, ignoreCase = true) ||
                file.size != expected.size || file.filename.substringAfterLast('/') != expected.path.substringAfterLast('/')
            ) throw RequestError("Modrinth Raw来源与远端文件不匹配: ${content.slug}")
        }
    }

    private suspend fun validateGitHub(content: Modpack2Content, raw: Modpack2RawFile?, shadow: Boolean) {
        val (owner, repository) = parseGitHubProjectId(content.projectId)
        val (tag, assetName) = parseGitHubFileId(content.fileId)
        val encodedTag = URLEncoder.encode(tag, StandardCharsets.UTF_8).replace("+", "%20")
        val release = remoteRequest("无法验证GitHub内容${content.projectId}/${content.fileId}") {
            val response = httpRequest {
                url("https://api.github.com/repos/$owner/$repository/releases/tags/$encodedTag")
                header(HttpHeaders.Accept, "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
            }
            if (!response.status.isSuccess()) {
                throw RequestError("GitHub不存在${content.slug}对应Release")
            }
            response.body<GithubReleaseResponse>()
        }
        val asset = release.assets.firstOrNull { it.name == assetName }
            ?: throw RequestError("GitHub Release中找不到$assetName")

        val temporaryPath = remoteRequest("无法准备GitHub内容${content.projectId}/${content.fileId}校验文件") {
            Files.createTempFile("modpack2-github-", ".download")
        }
        try {
            remoteRequest("无法下载GitHub内容${content.projectId}/${content.fileId}") {
                temporaryPath.downloadFileFrom(
                    url = asset.downloadUrl,
                    knownSize = asset.size,
                    validator = { path ->
                        val matchesContent = path.toFile().sha256.equals(content.hash, ignoreCase = true)
                        val matchesRaw = raw == null || shadow || (
                            path.toFile().sha1.equals(raw.sha1, ignoreCase = true) &&
                                Files.size(path) == raw.size &&
                                asset.name == raw.path.substringAfterLast('/')
                            )
                        if (matchesContent && matchesRaw) {
                            Result.success(Unit)
                        } else {
                            Result.failure(RequestError("GitHub内容SHA-256不匹配: ${content.slug}"))
                        }
                    },
                ) {}.getOrThrow()
            }
        } finally {
            Files.deleteIfExists(temporaryPath)
        }
    }

    private suspend fun <T> remoteRequest(message: String, block: suspend () -> T): T = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: RequestError) {
        throw error
    } catch (error: Throwable) {
        throw RequestError(message, error)
    }

    internal fun parseCurseForgeIds(projectId: String, fileId: String): Pair<Int, Int> {
        val project = projectId.toIntOrNull()?.takeIf { it > 0 }
            ?: throw RequestError("CurseForge projectId无效")
        val file = fileId.toIntOrNull()?.takeIf { it > 0 }
            ?: throw RequestError("CurseForge fileId无效")
        return project to file
    }

    internal fun parseGitHubProjectId(projectId: String): Pair<String, String> =
        parseGitHubPair(projectId, "GitHub projectId")

    internal fun parseGitHubFileId(fileId: String): Pair<String, String> =
        parseGitHubPair(fileId, "GitHub fileId")

    private fun parseGitHubPair(value: String, field: String): Pair<String, String> {
        val parts = value.split('/', limit = 3)
        if (parts.size != 2 || parts.any(String::isBlank)) {
            throw RequestError("${field}无效")
        }
        return parts[0] to parts[1]
    }

    private fun Modpack2ContentKeyDto.matches(content: Modpack2Content): Boolean =
        platform == content.platform && type == content.type && projectId == content.projectId &&
            fileId == content.fileId && hash.equals(content.hash, ignoreCase = true) &&
            targetPath == content.targetPath && side == content.side

    private fun isManifestShadow(
        content: Modpack2Content,
        manifest: Modpack2VersionManifestDto,
    ): Boolean = runCatching {
        when (manifest.format) {
            Modpack2ManifestFormat.CurseForge -> {
                if (content.platform != ContentPlatform.CurseForge) false else {
                    val parsed = serdesJson.decodeFromString<CurseForgePackManifest>(manifest.manifestJson)
                    parsed.files.any { it.projectId.toString() == content.projectId && it.fileId.toString() == content.fileId }
                }
            }
            Modpack2ManifestFormat.Modrinth -> {
                if (content.platform != ContentPlatform.Modrinth) false else {
                    val parsed = serdesJson.decodeFromString<ModrinthModpackIndex>(manifest.manifestJson)
                    parsed.files.any {
                        it.path.replace('\\', '/') == content.targetPath &&
                            it.hashes.sha1.equals(content.hash, ignoreCase = true)
                    }
                }
            }
        }
    }.getOrDefault(false)

    private fun Modpack2Loader.toModLoader() = when (this) {
        Modpack2Loader.Forge -> ModLoader.forge
        Modpack2Loader.NeoForge -> ModLoader.neoforge
    }

    internal fun validateCurseForgeEnvironment(
        declarations: List<String>,
        expectedMc: Int?,
        expectedLoader: Modpack2Loader?,
        contentType: ContentType,
        slug: String,
    ) {
        expectedMc ?: return
        val mcVersion = McVersion.fromMinor(expectedMc.toString())
            ?: throw RequestError("不支持的MC版本")
        if (declarations.any { it.firstOrNull()?.isDigit() == true } &&
            declarations.none { it.equals(mcVersion.mcVer, ignoreCase = true) }
        ) throw RequestError("CurseForge文件不支持整合包MC版本: $slug")
        if (contentType == ContentType.Mod) {
            val loader = expectedLoader ?: throw RequestError("Mod内容缺少目标ModLoader: $slug")
            validateGameAndLoader(declarations, mcVersion, loader.toModLoader(), slug)
        }
    }

    @Serializable
    private data class GithubReleaseResponse(
        val assets: List<GithubReleaseAssetResponse> = emptyList(),
    )

    @Serializable
    private data class GithubReleaseAssetResponse(
        val name: String,
        val size: Long = 0,
        @SerialName("browser_download_url") val downloadUrl: String,
    )
}
