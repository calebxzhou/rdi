package calebxzhou.rdi.master.service.host2

import calebxzau.rdi.common.model.ContentInput
import calebxzau.rdi.common.model.ContentOrigin
import calebxzau.rdi.common.model.ContentPlatform
import calebxzau.rdi.common.model.ContentSide
import calebxzau.rdi.common.model.ContentType
import calebxzau.rdi.common.model.ContentVo
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.CurseForgeFile
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
import calebxzhou.rdi.common.util.sha256
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
import java.nio.file.Files

class Host2ModDownloadService {
    suspend fun resolveExtra(input: ContentInput, mcVersion: McVersion, modLoader: ModLoader): ContentVo =
        resolveContent(
            ContentVo(
                origin = ContentOrigin.Extra,
                platform = input.platform,
                type = input.type,
                projectId = input.projectId.trim(),
                fileId = input.fileId.trim(),
                slug = input.projectId.trim(),
                hash = "pending",
                targetPath = input.targetPath,
                side = input.side,
                required = true,
                fileSize = 0,
                enabled = true,
            ),
            mcVersion,
            modLoader,
            verifyDeclaredHash = false,
        )

    suspend fun resolveContent(
        content: ContentVo,
        mcVersion: McVersion,
        modLoader: ModLoader,
        verifyDeclaredHash: Boolean = true,
    ): ContentVo = when (content.platform) {
        ContentPlatform.CurseForge -> resolveCurseForgeContent(
            content,
            mcVersion,
            modLoader,
            verifyDeclaredHash,
        )

        ContentPlatform.Modrinth -> resolveModrinthContent(
            content,
            mcVersion,
            modLoader,
            verifyDeclaredHash,
        )

        ContentPlatform.GitHub -> resolveGithubContent(content, verifyDeclaredHash)
    }

    suspend fun downloadContent(content: ContentVo, target: File) {
        target.parentFile.mkdirs()
        when (content.platform) {
            ContentPlatform.CurseForge -> {
                val projectId = content.projectId.toIntOrNull() ?: throw RequestError("CurseForge projectId无效")
                val fileId = content.fileId.toIntOrNull() ?: throw RequestError("CurseForge fileId无效")
                val file = CurseForgeService.getModFileInfo(projectId, fileId)
                    ?: throw RequestError("CurseForge内容不存在: ${content.slug}")
                target.toPath().downloadFileFrom(
                    url = file.realDownloadUrl,
                    knownSize = content.fileSize,
                    urlHeadersProvider = CurseForgeService::downloadHeadersFor,
                    validator = { path ->
                        if (path.murmur2.toString() == content.hash && Files.size(path) == content.fileSize) {
                            Result.success(Unit)
                        } else Result.failure(RequestError("CurseForge内容校验失败: ${content.slug}"))
                    },
                ) {}.getOrThrow()
            }

            ContentPlatform.Modrinth -> {
                val version = ModrinthService.mrreq("version/${content.fileId}").body<ModrinthVersionInfo>()
                val file = version.files.firstOrNull { it.hashes["sha1"].equals(content.hash, true) }
                    ?: throw RequestError("Modrinth内容文件不存在: ${content.slug}")
                target.toPath().downloadFileFrom(
                    url = file.url,
                    knownSize = content.fileSize,
                    validator = { path ->
                        if (path.sha1.equals(content.hash, true) && Files.size(path) == content.fileSize) {
                            Result.success(Unit)
                        } else Result.failure(RequestError("Modrinth内容校验失败: ${content.slug}"))
                    },
                ) {}.getOrThrow()
            }

            ContentPlatform.GitHub -> {
                val asset = githubAsset(content.projectId, content.fileId)
                target.toPath().downloadFileFrom(
                    url = asset.downloadUrl,
                    knownSize = content.fileSize,
                    validator = { path ->
                        if (path.toFile().sha256.equals(content.hash, true) && Files.size(path) == content.fileSize) {
                            Result.success(Unit)
                        } else Result.failure(RequestError("GitHub内容校验失败: ${content.slug}"))
                    },
                ) {}.getOrThrow()
            }
        }
    }

    suspend fun download(mod: Mod, mcVersion: McVersion, modLoader: ModLoader, targetDir: File): File =
        downloadInternal(mod, mcVersion, modLoader, targetDir, preparedCurseForgeFiles = null)

    /** Download a group while resolving all CurseForge files through one batch request. */
    suspend fun downloadAll(
        mods: List<Mod>,
        mcVersion: McVersion,
        modLoader: ModLoader,
        targetDir: File
    ): List<File> {
        mods.forEach(::validateMod)
        targetDir.mkdirs()
        val fileIds = mods.asSequence()
            .filter { it.platform.equals("cf", ignoreCase = true) }
            .mapNotNull { it.fileId.toIntOrNull() }
            .distinct()
            .toList()
        val preparedCurseForgeFiles = if (fileIds.isEmpty()) {
            emptyMap()
        } else {
            CurseForgeService.getModFilesInfo(fileIds).associateBy { it.id }
        }
        return mods.map { mod ->
            downloadInternal(mod, mcVersion, modLoader, targetDir, preparedCurseForgeFiles)
        }
    }

    private suspend fun downloadInternal(
        mod: Mod,
        mcVersion: McVersion,
        modLoader: ModLoader,
        targetDir: File,
        preparedCurseForgeFiles: Map<Int, CurseForgeFile>?
    ): File {
        validateMod(mod)
        targetDir.mkdirs()
        val target = targetDir.resolve(mod.host2FileName)
        when (mod.platform.lowercase()) {
            "cf" -> downloadCurseForge(
                mod,
                mcVersion,
                modLoader,
                target,
                preparedCurseForgeFiles
            )
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
        target: File,
        preparedCurseForgeFiles: Map<Int, CurseForgeFile>?
    ) {
        val projectId = mod.projectId.toIntOrNull() ?: throw RequestError("CurseForge projectId无效")
        val fileId = mod.fileId.toIntOrNull() ?: throw RequestError("CurseForge fileId无效")
        val file = preparedCurseForgeFiles?.get(fileId)
            ?: if (preparedCurseForgeFiles == null) {
                CurseForgeService.getModFileInfo(projectId, fileId)
            } else {
                null
            }
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
        val asset = githubAsset(mod.projectId, mod.fileId)
        target.toPath().downloadFileFrom(
            url = asset.downloadUrl,
            knownSize = asset.size,
            validator = { path ->
                if (path.toFile().sha256.equals(mod.hash, true)) Result.success(Unit)
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
        if (mod.platform.equals("github", ignoreCase = true) && !GITHUB_SHA256.matches(mod.hash)) {
            throw RequestError("GitHub Mod必须使用SHA-256 hash")
        }
        if (mod.side == Mod.Side.CLIENT) throw RequestError("不能向server添加客户端专用Mod: ${mod.slug}")
    }

    private suspend fun resolveCurseForgeContent(
        content: ContentVo,
        mcVersion: McVersion,
        modLoader: ModLoader,
        verifyDeclaredHash: Boolean,
    ): ContentVo {
        val projectId = content.projectId.toIntOrNull() ?: throw RequestError("CurseForge projectId无效")
        val fileId = content.fileId.toIntOrNull() ?: throw RequestError("CurseForge fileId无效")
        val file = CurseForgeService.getModFileInfo(projectId, fileId)
            ?: throw RequestError("CurseForge内容不存在")
        if (file.modId != 0 && file.modId != projectId) throw RequestError("CurseForge文件不属于所选项目")
        if (verifyDeclaredHash && file.fileFingerprint.toString() != content.hash) {
            throw RequestError("CurseForge内容hash不匹配: ${content.slug}")
        }
        validateGameAndLoader(file.gameVersions, mcVersion, modLoader, content.slug)
        val fileName = file.fileName?.takeIf(String::isNotBlank) ?: throw RequestError("CurseForge文件名缺失")
        return content.copy(
            slug = content.slug.takeIf { verifyDeclaredHash } ?: fileName.substringBeforeLast('.'),
            hash = file.fileFingerprint.toString(),
            targetPath = content.targetPath ?: defaultTarget(content.type, fileName),
            fileSize = file.fileLength ?: throw RequestError("CurseForge文件大小缺失"),
        ).validatedTarget()
    }

    private suspend fun resolveModrinthContent(
        content: ContentVo,
        mcVersion: McVersion,
        modLoader: ModLoader,
        verifyDeclaredHash: Boolean,
    ): ContentVo {
        val version = runCatching {
            ModrinthService.mrreq("version/${content.fileId}").body<ModrinthVersionInfo>()
        }.getOrElse { throw RequestError("Modrinth内容版本不存在", it) }
        if (version.projectId != content.projectId) throw RequestError("Modrinth版本不属于所选项目")
        validateGameAndLoader(version.gameVersions + version.loaders, mcVersion, modLoader, content.slug)
        val file = if (verifyDeclaredHash) {
            version.files.firstOrNull { it.hashes["sha1"].equals(content.hash, true) }
        } else {
            version.files.firstOrNull { it.primary } ?: version.files.firstOrNull()
        } ?: throw RequestError("Modrinth内容文件不存在")
        val sha1 = file.hashes["sha1"] ?: throw RequestError("Modrinth内容缺少SHA-1")
        return content.copy(
            slug = content.slug.takeIf { verifyDeclaredHash } ?: file.filename.substringBeforeLast('.'),
            hash = sha1,
            targetPath = content.targetPath ?: defaultTarget(content.type, file.filename),
            fileSize = file.size ?: throw RequestError("Modrinth文件大小缺失"),
        ).validatedTarget()
    }

    private suspend fun resolveGithubContent(content: ContentVo, verifyDeclaredHash: Boolean): ContentVo {
        val asset = githubAsset(content.projectId, content.fileId)
        if (verifyDeclaredHash && !GITHUB_SHA256.matches(content.hash)) {
            throw RequestError("GitHub内容必须使用SHA-256")
        }
        val temporary = Files.createTempFile("host2-github-", ".download").toFile()
        val hash = try {
            temporary.toPath().downloadFileFrom(
                url = asset.downloadUrl,
                knownSize = asset.size,
            ) {}.getOrThrow()
            temporary.sha256.lowercase()
        } finally {
            Files.deleteIfExists(temporary.toPath())
        }
        if (verifyDeclaredHash && !hash.equals(content.hash, true)) {
            throw RequestError("GitHub内容SHA-256不匹配: ${content.slug}")
        }
        return content.copy(
            slug = content.slug.takeIf { verifyDeclaredHash } ?: asset.name.substringBeforeLast('.'),
            hash = hash,
            targetPath = content.targetPath ?: defaultTarget(content.type, asset.name),
            fileSize = asset.size,
        ).validatedTarget()
    }

    private suspend fun githubAsset(projectId: String, fileId: String): GithubReleaseAssetResponse {
        val projectParts = projectId.split('/')
        if (projectParts.size != 2 || projectParts.any(String::isBlank)) throw RequestError("GitHub projectId无效")
        val fileParts = fileId.split('/', limit = 2)
        if (fileParts.size != 2 || fileParts.any(String::isBlank)) throw RequestError("GitHub fileId无效")
        val tag = URLEncoder.encode(fileParts[0], StandardCharsets.UTF_8).replace("+", "%20")
        val response = httpRequest {
            url("https://api.github.com/repos/${projectParts[0]}/${projectParts[1]}/releases/tags/$tag")
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
        }
        if (!response.status.isSuccess()) throw RequestError("GitHub Release不存在")
        return response.body<GithubReleaseResponse>().assets.firstOrNull { it.name == fileParts[1] }
            ?: throw RequestError("GitHub Release中找不到${fileParts[1]}")
    }
}

internal fun validateGameAndLoader(
    declarations: List<String>,
    mcVersion: McVersion,
    modLoader: ModLoader,
    slug: String,
) {
    if (declarations.isNotEmpty() && declarations.any { it.firstOrNull()?.isDigit() == true } &&
        declarations.none { it == mcVersion.mcVer }
    ) throw RequestError("${slug}不支持MC${mcVersion.mcVer}")
    val expected = if (modLoader == ModLoader.neoforge) "neoforge" else "forge"
    val normalized = declarations.map(String::lowercase)
    if (normalized.any { it in ALL_LOADER_NAMES } && expected !in normalized) {
        throw RequestError("${slug}不支持${modLoader.name}")
    }
}

private fun defaultTarget(type: ContentType, fileName: String): String = when (type) {
    ContentType.Mod -> "mods/$fileName"
    ContentType.ShaderPack -> "shaderpacks/$fileName"
    ContentType.ResPack -> "resourcepacks/$fileName"
    ContentType.DataPack, ContentType.Other -> throw RequestError("此内容类型必须指定目标路径")
}

internal fun ContentVo.validatedTarget(): ContentVo {
    val path = targetPath?.trim()?.replace('\\', '/') ?: throw RequestError("内容目标路径不能为空")
    val segments = path.split('/')
    if (path.startsWith('/') || path.length > 512 || segments.any { it.isBlank() || it == "." || it == ".." } ||
        path.endsWith(".disabled", true)
    ) throw RequestError("内容目标路径无效")
    val normalized = path.lowercase()
    val first = segments.first().lowercase()
    if (normalized in HOST2_RESERVED_FILES || first in HOST2_RESERVED_DIRECTORIES ||
        first == "world" && (segments.size < 3 || !segments[1].equals("datapacks", true)) ||
        path.endsWith(".exe", true) || path.endsWith(".jar", true) && first != "mods"
    ) throw RequestError("内容目标路径属于平台保留位置")
    return copy(targetPath = path)
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
private val GITHUB_SHA256 = Regex("[A-Fa-f0-9]{64}")
private val HOST2_RESERVED_FILES = setOf(
    "server.properties",
    "eula.txt",
    ".rdi-host2-deployment.json",
)
private val HOST2_RESERVED_DIRECTORIES = setOf(
    "libraries",
    "logs",
    "cache",
    "crash-reports",
    ".staging",
    ".backup",
    ".deleting",
)
