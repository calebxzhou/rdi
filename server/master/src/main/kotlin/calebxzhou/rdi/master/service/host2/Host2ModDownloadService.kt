package calebxzhou.rdi.master.service.host2

import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.ModLoader
import calebxzhou.rdi.common.model.ModrinthVersionInfo
import calebxzhou.rdi.common.net.downloadFileFrom
import calebxzhou.rdi.common.net.httpRequest
import calebxzhou.rdi.common.service.CurseForgeService
import calebxzhou.rdi.common.service.ModrinthService
import calebxzhou.rdi.common.service.murmur2
import calebxzhou.rdi.common.util.sha1
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class Host2ModDownloadService {
    suspend fun download(mod: Mod, mcVersion: McVersion, modLoader: ModLoader, targetDir: File): File {
        validateMod(mod)
        targetDir.mkdirs()
        val target = targetDir.resolve(mod.host2FileName)
        when (mod.platform.lowercase()) {
            "cf" -> downloadCurseForge(mod, mcVersion, modLoader, target)
            "mr" -> downloadModrinth(mod, mcVersion, modLoader, target)
            "github" -> downloadGithub(mod, target)
            else -> throw RequestError("不支持的Mod平台: ${mod.platform}")
        }
        return target
    }

    private suspend fun downloadCurseForge(
        mod: Mod,
        mcVersion: McVersion,
        modLoader: ModLoader,
        target: File
    ) {
        val projectId = mod.projectId.toIntOrNull() ?: throw RequestError("CurseForge projectId无效")
        val fileId = mod.fileId.toIntOrNull() ?: throw RequestError("CurseForge fileId无效")
        val file = CurseForgeService.getModFileInfo(projectId, fileId)
            ?: throw RequestError("CurseForge不存在${mod.slug}对应文件")
        if (file.modId != 0 && file.modId != projectId) throw RequestError("CurseForge文件不属于所选项目")
        if (file.fileFingerprint.toString() != mod.hash) throw RequestError("CurseForge Mod hash不匹配: ${mod.slug}")
        if (file.gameVersions.isNotEmpty() && mcVersion.mcVer !in file.gameVersions) {
            throw RequestError("${mod.slug}不支持MC${mcVersion.mcVer}")
        }
        val loaderNames = when (modLoader) {
            ModLoader.forge -> setOf("forge")
            ModLoader.neoforge -> setOf("neoforge")
            ModLoader.cleanroom -> setOf("cleanroom", "forge")
        }
        val declaredLoaders = file.gameVersions.map(String::lowercase).toSet()
        if (declaredLoaders.any { it in ALL_LOADER_NAMES } && declaredLoaders.none { it in loaderNames }) {
            throw RequestError("${mod.slug}不支持${modLoader.name}")
        }
        target.toPath().downloadFileFrom(
            url = file.realDownloadUrl,
            knownSize = file.fileLength ?: 0L,
            urlHeadersProvider = CurseForgeService::downloadHeadersFor,
            validator = { path ->
                val actual = path.murmur2
                if (actual == file.fileFingerprint) Result.success(Unit)
                else Result.failure(RequestError("CurseForge Mod hash校验失败: ${mod.slug}"))
            }
        ) {}.getOrThrow()
    }

    private suspend fun downloadModrinth(
        mod: Mod,
        mcVersion: McVersion,
        modLoader: ModLoader,
        target: File
    ) {
        val version = runCatching {
            ModrinthService.mrreq("version/${mod.fileId}").body<ModrinthVersionInfo>()
        }.getOrElse { throw RequestError("Modrinth不存在${mod.slug}对应版本") }
        if (version.projectId != mod.projectId) throw RequestError("Modrinth版本不属于所选项目")
        if (version.gameVersions.isNotEmpty() && mcVersion.mcVer !in version.gameVersions) {
            throw RequestError("${mod.slug}不支持MC${mcVersion.mcVer}")
        }
        val expectedLoader = when (modLoader) {
            ModLoader.forge -> "forge"
            ModLoader.neoforge -> "neoforge"
            ModLoader.cleanroom -> "forge"
        }
        if (version.loaders.isNotEmpty() && version.loaders.none { it.equals(expectedLoader, true) }) {
            throw RequestError("${mod.slug}不支持${modLoader.name}")
        }
        val file = version.files.firstOrNull { it.hashes["sha1"].equals(mod.hash, true) }
            ?: throw RequestError("Modrinth版本中找不到匹配hash的文件: ${mod.slug}")
        target.toPath().downloadFileFrom(
            url = file.url,
            knownSize = file.size ?: 0L,
            validator = { path ->
                if (path.sha1.equals(mod.hash, true)) Result.success(Unit)
                else Result.failure(RequestError("Modrinth Mod hash校验失败: ${mod.slug}"))
            }
        ) {}.getOrThrow()
    }

    private suspend fun downloadGithub(mod: Mod, target: File) {
        val projectParts = mod.projectId.split('/')
        if (projectParts.size != 2 || projectParts.any(String::isBlank)) throw RequestError("GitHub projectId无效")
        val fileParts = mod.fileId.split('/', limit = 2)
        if (fileParts.size != 2 || fileParts.any(String::isBlank)) throw RequestError("GitHub fileId无效")
        val tag = URLEncoder.encode(fileParts[0], StandardCharsets.UTF_8).replace("+", "%20")
        val response = httpRequest {
            url("https://api.github.com/repos/${projectParts[0]}/${projectParts[1]}/releases/tags/$tag")
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
        }
        if (!response.status.isSuccess()) throw RequestError("GitHub不存在${mod.slug}对应Release")
        val asset = response.body<GithubReleaseResponse>().assets.firstOrNull { it.name == fileParts[1] }
            ?: throw RequestError("GitHub Release中找不到${fileParts[1]}")
        target.toPath().downloadFileFrom(
            url = asset.downloadUrl,
            knownSize = asset.size,
            validator = { path ->
                if (path.sha1.equals(mod.hash, true)) Result.success(Unit)
                else Result.failure(RequestError("GitHub Mod hash校验失败: ${mod.slug}"))
            }
        ) {}.getOrThrow()
    }

    private fun validateMod(mod: Mod) {
        if (mod.projectId.isBlank() || mod.fileId.isBlank() || mod.slug.isBlank() || mod.hash.isBlank()) {
            throw RequestError("Mod来源信息不完整")
        }
        if (mod.hash.length !in 1..128 || !HOST2_HASH.matches(mod.hash)) {
            throw RequestError("Mod hash无效")
        }
        if (mod.side == Mod.Side.CLIENT) throw RequestError("不能向server添加客户端专用Mod: ${mod.slug}")
    }
}

val Mod.host2FileName: String
    get() = "${slug.toHost2FileSlug()}_${hash.lowercase()}.jar"

private fun String.toHost2FileSlug(): String =
    trim().lowercase()
        .replace(Regex("[^a-z0-9._-]+"), "_")
        .trim('_', '.', '-')
        .take(100)
        .ifBlank { throw RequestError("Mod slug无效") }

@Serializable
private data class GithubReleaseResponse(
    val assets: List<GithubReleaseAssetResponse> = emptyList()
)

@Serializable
private data class GithubReleaseAssetResponse(
    val name: String,
    val size: Long = 0,
    @SerialName("browser_download_url") val downloadUrl: String
)

private val ALL_LOADER_NAMES = setOf("forge", "neoforge", "cleanroom", "fabric", "quilt")
private val HOST2_HASH = Regex("[A-Fa-f0-9]+")
