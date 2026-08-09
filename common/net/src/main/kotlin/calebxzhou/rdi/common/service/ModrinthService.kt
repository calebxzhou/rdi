package calebxzhou.rdi.common.service

import calebxzhou.mykotutils.log.Loggers
import calebxzhou.mykotutils.std.openChineseZip
import calebxzhou.mykotutils.std.sha1
import calebxzhou.rdi.common.exception.ModpackError
import calebxzhou.rdi.common.model.*
import calebxzhou.rdi.common.net.json
import calebxzhou.rdi.common.net.ktorClient
import calebxzhou.rdi.common.serdesJson
import calebxzhou.rdi.common.service.ModService.buildIconUrls
import calebxzhou.rdi.common.service.ModService.modLogo
import calebxzhou.rdi.common.service.ModService.ofMirrorUrl
import calebxzhou.rdi.common.service.ModService.readModMeta
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import java.io.File
import java.util.jar.JarFile

object ModrinthService {
    private val lgr by Loggers
    const val OFFICIAL_URL = "https://api.modrinth.com/v2"
    const val V3_OFFICIAL_URL = "https://api.modrinth.com/v3"
    data class LoadedModpack(
        val index: ModrinthModpackIndex,
        val file: File,
        val mods: List<Mod>,
        val mcVersion: McVersion,
        val modloader: ModLoader
    )

    suspend fun loadModpack(
        modpackFile: File,
        resolveModrinthSlugs: suspend (Set<String>) -> Map<String, String> = { emptyMap() }
    ): Result<LoadedModpack> = runCatching {
        if (!modpackFile.exists()) {
            throw ModpackError("找不到整合包文件: ${modpackFile.path}")
        }
        val index = if (modpackFile.isDirectory) {
            if (!hasOverridesDir(modpackFile)) {
                throw ModpackError("整合包缺少目录：overrides")
            }
            val indexFile = modpackFile.walkTopDown()
                .firstOrNull { it.isFile && it.name == "modrinth.index.json" }
                ?: throw ModpackError("整合包缺少文件：modrinth.index.json")
            val indexJson = indexFile.readText(Charsets.UTF_8)
            runCatching {
                serdesJson.decodeFromString<ModrinthModpackIndex>(indexJson)
            }.getOrElse { err ->
                throw ModpackError("modrinth.index.json 解析失败: ${err.message}")
            }
        } else {
            modpackFile.openChineseZip().use { zip ->
                val indexEntry = zip.entries().asSequence().firstOrNull {
                    !it.isDirectory && it.name.substringAfterLast('/') == "modrinth.index.json"
                } ?: throw ModpackError("整合包缺少文件：modrinth.index.json")
                val rootPrefix = indexEntry.name.substringBeforeLast('/', missingDelimiterValue = "")
                    .let { if (it.isBlank()) "" else "$it/" }
                if (!hasOverridesDir(zip.entries().asSequence().toList().map { it.name }, rootPrefix)) {
                    throw ModpackError("整合包缺少目录：overrides")
                }
                val indexJson = zip.getInputStream(indexEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                runCatching {
                    serdesJson.decodeFromString<ModrinthModpackIndex>(indexJson)
                }.getOrElse { err ->
                    throw ModpackError("modrinth.index.json 解析失败: ${err.message}")
                }
            }
        }

        if (!index.game.equals("minecraft", ignoreCase = true)) {
            throw ModpackError("不支持的游戏类型: ${index.game}")
        }
        if (index.formatVersion <= 0) {
            throw ModpackError("不支持的整合包格式版本: ${index.formatVersion}")
        }
        val mcVersion = index.dependencies["minecraft"]?.trim().orEmpty()
        if (mcVersion.isBlank()) {
            throw ModpackError("整合包缺少MC版本")
        }
        val parsedMcVersion = McVersion.from(mcVersion)
        if (parsedMcVersion == null || !parsedMcVersion.enabled) {
            throw ModpackError("不支持的MC版本: $mcVersion")
        }
        val loaderKey = index.dependencies.keys.firstOrNull { ModLoader.from(it) != null } ?: throw ModpackError("不支持的Mod加载器: 未知")
        val parsedModloader = ModLoader.from(loaderKey) ?: throw ModpackError("不支持的Mod加载器: $loaderKey")
        val fileEntries = index.files.associateBy { it.hashes.sha1 }
        val hashVersions = getVersionsFromHashes(fileEntries.keys.toList())
        val projectIds = hashVersions.values.map { it.projectId }.distinct()
        val projects = getMultipleProjects(projectIds)
        val projectMap = projects.associateBy { it.id }

        val matchedMrMods = fileEntries.mapNotNull { (sha1, entry) ->
            val version = hashVersions[sha1] ?: return@mapNotNull null
            val project = projectMap[version.projectId]
            val slug = project?.slug?.takeIf { it.isNotBlank() }
                ?: entry.path.substringAfterLast('/').substringBeforeLast('.')
                    .ifBlank { version.projectId }
            val side = project?.toModSide() ?: Mod.Side.UNKNOWN
            Mod(
                platform = "mr",
                projectId = version.projectId,
                slug = slug,
                fileId = version.id,
                hash = entry.hashes.sha1,
                side = side,
                downloadUrls = entry.downloads
            )
        }
        //有些mod mr没有 但是下载url里有cf file id 可以取出来去CF拿
        val unmatchedEntries = fileEntries.filterKeys { it !in hashVersions.keys }
        val cfFileIdByHash = unmatchedEntries.mapNotNull { (sha1, entry) ->
            val fileId = entry.downloads.firstNotNullOfOrNull { parseCurseForgeFileId(it) }
            if (fileId == null) null else sha1 to fileId
        }.toMap()
        val cfFiles = CurseForgeService.getModFilesInfo(cfFileIdByHash.values.distinct())
        val cfFileMap = cfFiles.associateBy { it.id }
        val cfModIds = cfFiles.map { it.modId }.distinct()
        val cfModInfos = CurseForgeService.getModsInfo(cfModIds)
        val cfModInfoMap = cfModInfos.associateBy { it.id }

        // Some CF files are also on Modrinth but with different binary/hash.
        // Resolve side info by CF slug -> MR slug mapping, then batch query MR projects.
        val cfSlugToMrSlug = resolveModrinthSlugs(cfModInfos.map { it.slug }.toSet())
        val mrCandidates = buildSet {
            cfModInfos.forEach { info ->
                cfSlugToMrSlug[info.slug]?.takeIf { it.isNotBlank() }?.let { add(it) }
                info.slug.takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }.toList()
        val mrProjectBySlug = if (mrCandidates.isEmpty()) {
            emptyMap()
        } else {
            getMultipleProjects(mrCandidates).associateBy { it.slug.trim().lowercase() }
        }

        val matchedCfMods = cfFileIdByHash.mapNotNull { (sha1, fileId) ->
            val entry = unmatchedEntries[sha1] ?: return@mapNotNull null
            val cfFile = cfFileMap[fileId] ?: return@mapNotNull null
            val modInfo = cfModInfoMap[cfFile.modId]
            val slug = modInfo?.slug ?: let {
                lgr.warn { "找不到cf mod信息：${cfFile.id} ${cfFile.displayName}" }
                return@mapNotNull null
            }
            val mrProject = cfSlugToMrSlug[slug]
                ?.takeIf { it.isNotBlank() }
                ?.let { mrProjectBySlug[it.trim().lowercase()] }
                ?: mrProjectBySlug[slug.trim().lowercase()]
            val side = mrProject?.toModSide() ?: Mod.Side.UNKNOWN
            Mod(
                platform = "cf",
                projectId = modInfo.id.toString(),
                slug = slug,
                fileId = cfFile.id.toString(),
                hash = cfFile.fileFingerprint.toString(),
                side = side,
                downloadUrls = entry.downloads
            )
        }

        val mods = matchedMrMods + matchedCfMods

        LoadedModpack(
            index = index,
            file = modpackFile,
            mods = mods,
            mcVersion = parsedMcVersion,
            modloader = parsedModloader
        )
    }

    private fun hasOverridesDir(rootDir: File): Boolean {
        return rootDir.walkTopDown().any { it.isDirectory && it.name.equals("overrides", ignoreCase = true) }
    }

    private fun hasOverridesDir(entryNames: List<String>, rootPrefix: String): Boolean {
        val overridesPath = rootPrefix + "overrides"
        val overridesPrefix = "$overridesPath/"
        return entryNames.any { rawName ->
            val normalized = rawName.replace('\\', '/').trimStart('/')
            normalized == overridesPath || normalized.startsWith(overridesPrefix)
        }
    }

    private fun ModrinthProject.toModSide(): Mod.Side {
        if (serverSide == "unsupported") return Mod.Side.CLIENT
        if (clientSide == "unsupported") return Mod.Side.SERVER
        return Mod.Side.BOTH
    }

    private fun parseCurseForgeFileId(url: String): Int? {
        val match = Regex("/files/(\\d+)/(\\d+)/").find(url) ?: return null
        val idPart1 = match.groupValues.getOrNull(1) ?: return null
        val idPart2 = match.groupValues.getOrNull(2) ?: return null
        val paddedPart2 = idPart2.padStart(3, '0')
        return (idPart1 + paddedPart2).toIntOrNull()
    }

    fun ModrinthProject.toCardVo(modFile: File? = null): Mod.CardVo {
        val icons = buildIconUrls(iconUrl)
        val resolvedName = (title ?: slug).ifBlank { slug }
        val localMeta = modFile?.readLocalModCardMeta()
        val introText = description?.takeIf { it.isNotBlank() }?.trim()
            ?: localMeta?.description
            ?: "暂无介绍"

        return Mod.CardVo(
            name = resolvedName,
            nameCn = null,
            intro = introText,
            iconData = localMeta?.iconBytes,
            iconUrls = icons,
            side = Mod.Side.BOTH
        )
    }

    private data class LocalModCardMeta(
        val iconBytes: ByteArray? = null,
        val description: String? = null
    )

    private fun File.readLocalModCardMeta(): LocalModCardMeta = runCatching {
        JarFile(this).use { jar ->
            LocalModCardMeta(
                iconBytes = jar.modLogo,
                description = jar.readModMeta()?.description
            )
        }
    }.getOrDefault(LocalModCardMeta())
    suspend fun mrreq(
        path: String,
        method: HttpMethod = HttpMethod.Get,
        params: Map<String, Any>? = null,
        body: Any? = null
    ): HttpResponse {
        suspend fun doRequest(base: String) = ktorClient.request {
            url("${base}/${path}")
            json()
            body?.let { setBody(it) }
            params?.forEach { parameter(it.key, it.value) }
            this.method = method
        }

        if (!ModService.preferMirror) {
            return doRequest(OFFICIAL_URL)
        }

        val mirrorResult = runCatching<HttpResponse> { doRequest(OFFICIAL_URL.ofMirrorUrl) }
        val mirrorResponse = mirrorResult.getOrNull()
        if (mirrorResponse != null && mirrorResponse.status.isSuccess()) {
            return mirrorResponse
        } else {
            val bodyAsText = mirrorResponse?.bodyAsText()
            lgr.warn { "Modrinth mirror fail，${mirrorResponse?.status},$bodyAsText" }
        }

        mirrorResult.exceptionOrNull()?.let {
            lgr.warn { "Modrinth mirror request failed, falling back to official API: ${it.message}" }
        }
        val officialResponse = doRequest(OFFICIAL_URL)
        return officialResponse
    }

    suspend fun mrreqV3(
        path: String,
        method: HttpMethod = HttpMethod.Get,
        params: Map<String, Any>? = null,
        body: Any? = null
    ): HttpResponse {
        suspend fun doRequest(base: String) = ktorClient.request {
            url("${base}/${path}")
            json()
            body?.let { setBody(it) }
            params?.forEach { parameter(it.key, it.value) }
            this.method = method
        }

        if (!ModService.preferMirror) {
            return doRequest(V3_OFFICIAL_URL)
        }

        val mirrorResult = runCatching<HttpResponse> { doRequest(V3_OFFICIAL_URL.ofMirrorUrl) }
        val mirrorResponse = mirrorResult.getOrNull()
        if (mirrorResponse != null && mirrorResponse.status.isSuccess()) {
            return mirrorResponse
        } else {
            val bodyAsText = mirrorResponse?.bodyAsText()
            lgr.warn { "Modrinth v3 mirror fail，${mirrorResponse?.status},$bodyAsText" }
        }

        mirrorResult.exceptionOrNull()?.let {
            lgr.warn { "Modrinth v3 mirror request failed, falling back to official API: ${it.message}" }
        }
        return doRequest(V3_OFFICIAL_URL)
    }

    suspend fun getProjectDetailV3(projectIdOrSlug: String): ModrinthV3Project {
        val normalizedId = projectIdOrSlug.trim()
        require(normalizedId.isNotBlank()) { "projectId不能为空" }
        return mrreqV3("project/$normalizedId").body()
    }

    suspend fun getVersionsV3(ids: List<String>): List<ModrinthV3Version> {
        val normalizedIds = ids.map { it.trim() }.filter(String::isNotBlank).distinct()
        if (normalizedIds.isEmpty()) return emptyList()
        return mrreqV3(
            path = "versions",
            params = mapOf("ids" to Json.encodeToString(normalizedIds))
        ).body()
    }

    suspend fun getProjectVersionsV3(
        projectIdOrSlug: String,
        gameVersions: List<String> = emptyList(),
        loaders: List<String> = emptyList(),
        includeChangelog: Boolean = true
    ): List<ModrinthV3Version> {
        val normalizedId = projectIdOrSlug.trim()
        require(normalizedId.isNotBlank()) { "projectId不能为空" }
        val params = buildMap<String, Any> {
            val normalizedGameVersions = gameVersions.map { it.trim() }.filter(String::isNotBlank).distinct()
            val normalizedLoaders = loaders.map { it.trim() }.filter(String::isNotBlank).distinct()
            if (normalizedGameVersions.isNotEmpty()) {
                put("game_versions", Json.encodeToString(normalizedGameVersions))
            }
            if (normalizedLoaders.isNotEmpty()) {
                put("loaders", Json.encodeToString(normalizedLoaders))
            }
            put("include_changelog", includeChangelog)
        }
        return mrreqV3(
            path = "project/$normalizedId/version",
            params = params
        ).body()
    }

    suspend fun getMultipleProjects(idSlugs: List<String>): List<ModrinthProject> {
        val normalizedIds = idSlugs.asSequence()
            .distinct()
            .toList()

        val chunkSize = 100
        val projects = mutableListOf<ModrinthProject>()

        normalizedIds.chunked(chunkSize).forEach { chunk ->
            val response = mrreq("projects", params = mapOf("ids" to Json.encodeToString(chunk)))
                .body<List<ModrinthProject>>()
            projects += response

            if (response.size != chunk.size) {
                val missing = chunk.toSet() - response.map { it.id }.toSet() - response.map { it.slug }.toSet()
                if (missing.isNotEmpty()) {
                    lgr.debug { "Modrinth: ${missing.size} ids from chunk unmatched: ${missing.joinToString()}" }
                }
            }
        }

        lgr.info { "Modrinth: fetched ${projects.size} projects for ${normalizedIds.size} requested ids" }

        return projects
    }

    suspend fun searchProjects(
        query: String? = null,
        facets: List<List<String>> = emptyList(),
        index: ModrinthSearchIndex = ModrinthSearchIndex.RELEVANCE,
        offset: Int = 0,
        limit: Int = 10
    ): ModrinthSearchResponse {
        require(offset >= 0) { "offset不能小于0" }
        require(limit in 1..100) { "limit必须在1..100之间" }

        val params = buildMap<String, Any> {
            query?.trim()?.ifBlank { null }?.let { put("query", it) }
            if (facets.isNotEmpty()) {
                put("facets", Json.encodeToString(facets))
            }
            put("index", index.apiValue)
            put("offset", offset)
            put("limit", limit)
        }

        return mrreq(
            path = "search",
            params = params
        ).body()
    }

    suspend fun getProjectVersions(
        projectIdOrSlug: String,
        gameVersions: List<String> = emptyList(),
        loaders: List<String> = emptyList()
    ): List<ModrinthVersionInfo> {
        val params = buildMap<String, Any> {
            if (gameVersions.isNotEmpty()) {
                put("game_versions", Json.encodeToString(gameVersions))
            }
            if (loaders.isNotEmpty()) {
                put("loaders", Json.encodeToString(loaders))
            }
        }
        return mrreq(
            path = "project/${projectIdOrSlug.trim()}/version",
            params = params.ifEmpty { null }
        ).body()
    }

    suspend fun List<File>.mapModrinthVersions(): Map<String, ModrinthVersionInfo> {
        val hashes = map { it.sha1 }
        val response = mrreq(
            "version_files",
            method = HttpMethod.Post,
            body = ModrinthVersionLookupRequest(hashes = hashes, algorithm = "sha1")
        )
            .body<Map<String, ModrinthVersionInfo>>()

        val missing = hashes.filter { it !in response }
        if (missing.isNotEmpty()) {
            lgr.info { "Modrinth: ${response.size} matches, ${missing.size} hashes unmatched" }
        } else {
            lgr.info { "Modrinth: matched all ${response.size} hashes" }
        }

        return response
    }

    suspend fun getVersionsFromHashes(
        hashes: List<String>,
        algorithm: String = "sha1"
    ): Map<String, ModrinthVersionInfo> {
        val response = mrreq(
            "version_files",
            method = HttpMethod.Post,
            body = ModrinthVersionLookupRequest(hashes = hashes, algorithm = algorithm)
        )
            .body<Map<String, ModrinthVersionInfo>>()

        val missing = hashes.filter { it !in response }
        if (missing.isNotEmpty()) {
            lgr.info { "Modrinth: ${response.size} matches, ${missing.size} hashes unmatched" }
        } else {
            lgr.info { "Modrinth: matched all ${response.size} hashes" }
        }

        return response
    }
}

