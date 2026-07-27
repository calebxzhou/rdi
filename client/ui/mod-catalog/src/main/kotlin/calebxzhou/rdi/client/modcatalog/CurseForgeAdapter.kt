package calebxzhou.rdi.client.modcatalog

import calebxzhou.rdi.common.model.ModLoader
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Instant

internal class CurseForgeAdapter(
    httpClient: io.ktor.client.HttpClient,
    private val config: CurseForgeConfig,
    networkPolicy: CatalogNetworkPolicy,
    private val json: Json,
    onWarning: (Throwable) -> Unit = {}
) : PlatformAdapter {
    override val platform = ModPlatform.CURSEFORGE
    private val allowedDownloadHosts = setOf(
        "edge.forgecdn.net",
        "media.forgecdn.net",
        "mediafilez.forgecdn.net",
        URI(networkPolicy.mirrorBaseUrl).host.lowercase()
    )
    private val transport = CatalogHttpTransport(
        httpClient = httpClient,
        platform = platform,
        officialBaseUrl = config.baseUrl,
        mirrorBaseUrl = networkPolicy.mirrorBase(platform),
        defaultHeaders = mapOf(API_KEY_HEADER to config.apiKey),
        preferMirror = networkPolicy.preferMirror,
        json = json,
        onWarning = onWarning
    )

    override suspend fun search(
        query: String,
        target: CatalogTarget,
        sort: CatalogSort,
        offset: Int,
        limit: Int
    ): SourcePage {
        val response = get<CfSearchResponse>(
            "mods/search",
            buildMap {
                put("gameId", MINECRAFT_GAME_ID.toString())
                put("classId", MOD_CLASS_ID.toString())
                query.trim().takeIf(String::isNotBlank)?.let { put("searchFilter", it) }
                put("gameVersion", target.minecraftVersion.mcVer)
                put("modLoaderType", target.loader.curseForgeType().toString())
                sort.curseForgeField()?.let { put("sortField", it.toString()) }
                put("sortOrder", "desc")
                put("index", offset.toString())
                put("pageSize", limit.toString())
            }
        )
        val items = response.data.map { it.toSource() }
        val total = response.pagination?.totalCount?.toLong()
        val next = (offset + items.size).takeIf { items.isNotEmpty() && (total == null || it < total) }
        return SourcePage(items, next, total)
    }

    override suspend fun getProjects(ids: Set<String>): AdapterResult<CatalogProjectSource> {
        if (ids.isEmpty()) return AdapterResult(emptyMap(), emptySet())
        val numericIds = ids.mapNotNull(String::toIntOrNull)
        val projects = numericIds.chunked(BATCH_SIZE).flatMap { chunk ->
            post<CfDataResponse<List<CfProjectDto>>>(
                "mods",
                json.encodeToString(CfProjectRequest(chunk))
            ).data
        }.filter { it.classId == null || it.classId == MOD_CLASS_ID }
            .associate { it.id.toString() to it.toSource() }
        return AdapterResult(projects, ids - projects.keys)
    }

    override suspend fun getDetails(ref: CatalogProjectRef): CatalogModDetailsSource {
        require(ref.platform == platform)
        val project = get<CfDataResponse<CfProjectDto>>("mods/${ref.projectId}").data
        val description = get<CfDataResponse<String>>("mods/${ref.projectId}/description").data
        return CatalogModDetailsSource(
            project = project.toSource(),
            description = description,
            authors = project.authors.mapNotNull(CfAuthorDto::name),
            categories = project.categories.mapNotNull(CfCategoryDto::name),
            sourceUrl = project.links?.websiteUrl ?: "https://www.curseforge.com/minecraft/mc-mods/${project.slug}",
            issuesUrl = project.links?.issuesUrl,
            wikiUrl = project.links?.wikiUrl
        )
    }

    override suspend fun listFiles(
        project: CatalogProjectRef,
        target: CatalogTarget,
        channels: Set<ReleaseChannel>,
        offset: Int,
        limit: Int
    ): Pair<List<CatalogFile>, Int?> {
        require(project.platform == platform)
        val response = get<CfFileListResponse>(
            "mods/${project.projectId}/files",
            mapOf(
                "gameVersion" to target.minecraftVersion.mcVer,
                "modLoaderType" to target.loader.curseForgeType().toString(),
                "index" to offset.toString(),
                "pageSize" to limit.toString()
            )
        )
        val items = response.data.map { it.toFile() }
            .filter { it.channel in channels }
            .sortedByDescending(CatalogFile::publishedAt)
        val pagination = response.pagination
        val next = pagination?.let {
            (it.index + it.resultCount).takeIf { nextOffset -> it.resultCount > 0 && nextOffset < it.totalCount }
        }
        return items to next
    }

    override suspend fun getFiles(ids: Set<String>): AdapterResult<CatalogFile> {
        if (ids.isEmpty()) return AdapterResult(emptyMap(), emptySet())
        val numericIds = ids.mapNotNull(String::toIntOrNull)
        val files = numericIds.chunked(BATCH_SIZE).flatMap { chunk ->
            post<CfDataResponse<List<CfFileDto>>>(
                "mods/files",
                json.encodeToString(CfFileRequest(chunk))
            ).data
        }.associate { it.id.toString() to it.toFile() }
        return AdapterResult(files, ids - files.keys)
    }

    override suspend fun matchLocalFiles(files: List<LocalFileHashes>): Map<Path, CatalogFile> {
        val byFingerprint = files.groupBy(LocalFileHashes::curseForgeFingerprint)
        val matches = linkedMapOf<Path, CatalogFile>()
        byFingerprint.keys.chunked(FINGERPRINT_BATCH_SIZE).forEach { fingerprints ->
            val response = post<CfDataResponse<CfFingerprintDataDto>>(
                "fingerprints/$MINECRAFT_GAME_ID",
                json.encodeToString(CfFingerprintRequest(fingerprints))
            )
            response.data.exactMatches.forEach { match ->
                val fingerprint = match.file.fileFingerprint ?: return@forEach
                val mapped = match.file.toFile()
                byFingerprint[fingerprint].orEmpty().forEach { matches[it.path] = mapped }
            }
        }
        return matches
    }

    override suspend fun getChangelog(file: CatalogFileRef): String {
        require(file.platform == platform)
        val project = getFiles(setOf(file.fileId)).found[file.fileId]?.project
            ?: throw CatalogException.InvalidResponse(platform)
        return get<CfDataResponse<String>>("mods/${project.projectId}/files/${file.fileId}/changelog").data
    }

    override suspend fun resolveDownload(file: CatalogFile): ResolvedDownload {
        require(file.ref.platform == platform)
        val endpoint = transport.get(
            "mods/${file.project.projectId}/files/${file.ref.fileId}/download-url",
            allowedStatuses = setOf(HttpStatusCode.Forbidden, HttpStatusCode.NotFound)
        ).let { response ->
            if (response.status.value in 200..299) {
                response.decode<CfDataResponse<String?>>(platform, json).data?.trim()?.takeIf(String::isNotBlank)
            } else {
                null
            }
        }
        val url = (endpoint ?: deriveDownloadUrl(file)).validatedDownloadUrl(platform)
        return ResolvedDownload(url, downloadHeadersFor(url), file.fileName, file.digests)
    }

    private fun CfProjectDto.toSource() = CatalogProjectSource(
        ref = CatalogProjectRef(platform, id.toString()),
        slug = slug,
        name = name,
        summary = summary.orEmpty().trim(),
        iconUrl = logo?.url ?: logo?.thumbnailUrl,
        downloadCount = downloadCount,
        updatedAt = dateModified.toInstant(),
        environment = EnvironmentCompatibility()
    )

    private fun CfFileDto.toFile(): CatalogFile {
        val normalizedName = fileName?.trim()?.takeIf(String::isNotBlank) ?: throw invalidResponse()
        if (id <= 0 || modId <= 0) throw invalidResponse()
        val ref = CatalogFileRef(platform, id.toString())
        val project = CatalogProjectRef(platform, modId.toString())
        val digests = buildSet {
            hashes.firstOrNull { it.algo == SHA1_ALGORITHM }
                ?.value
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let { add(CatalogDigest(CatalogDigestAlgorithm.SHA1, it.lowercase())) }
            fileFingerprint?.let {
                add(CatalogDigest(CatalogDigestAlgorithm.CURSEFORGE_MURMUR2, it.toString()))
            }
        }
        if (digests.isEmpty()) throw invalidResponse()
        return CatalogFile(
            ref = ref,
            project = project,
            displayName = displayName?.trim()?.takeIf(String::isNotBlank) ?: normalizedName,
            versionNumber = null,
            fileName = normalizedName,
            channel = releaseType.toReleaseChannel(),
            publishedAt = fileDate.toInstant(),
            fileSize = fileLength ?: fileSizeOnDisk ?: 0,
            minecraftVersions = gameVersions.filter { it.firstOrNull()?.isDigit() == true }.toSet(),
            loaders = gameVersions.mapNotNull(String::toModLoader).toSet(),
            environment = EnvironmentCompatibility(),
            digests = digests,
            dependencies = dependencies.mapNotNull { it.toDomain() }
        )
    }

    private fun CfDependencyDto.toDomain(): CatalogDependency? {
        val projectId = modId?.takeIf { it > 0 } ?: return null
        val requirement = when (relationType) {
            1 -> DependencyRequirement.EMBEDDED
            2 -> DependencyRequirement.OPTIONAL
            3 -> DependencyRequirement.REQUIRED
            4 -> DependencyRequirement.TOOL
            5 -> DependencyRequirement.INCOMPATIBLE
            6 -> DependencyRequirement.INCLUDED
            else -> return null
        }
        return CatalogDependency(
            DependencyTarget.Project(CatalogProjectRef(platform, projectId.toString())),
            requirement
        )
    }

    private fun deriveDownloadUrl(file: CatalogFile): String {
        val id = file.ref.fileId
        if (id.length <= 4 || id.any { !it.isDigit() }) throw CatalogException.DownloadUnavailable(file.ref)
        val encodedName = URLEncoder.encode(file.fileName, StandardCharsets.UTF_8).replace("+", "%20")
        return "https://mediafilez.forgecdn.net/files/${id.take(4).toInt()}/${id.drop(4).toInt()}/$encodedName"
    }

    private fun downloadHeadersFor(url: String): Map<String, String> {
        val host = try {
            URI(url).host?.lowercase()
        } catch (cause: Exception) {
            throw CatalogException.InvalidResponse(platform, cause)
        } ?: throw CatalogException.InvalidResponse(platform)
        val allowed = host in allowedDownloadHosts || host.endsWith(".forgecdn.net")
        return if (allowed) mapOf(API_KEY_HEADER to config.apiKey) else emptyMap()
    }

    private suspend inline fun <reified T> get(
        path: String,
        parameters: Map<String, String> = emptyMap()
    ): T = transport.get(path, parameters).decode(platform, json)

    private suspend inline fun <reified T> post(path: String, body: String): T =
        transport.post(path, body).decode(platform, json)

    private fun invalidResponse() = CatalogException.InvalidResponse(platform)

    private fun String?.toInstant(): Instant = try {
        this?.let(Instant::parse) ?: Instant.EPOCH
    } catch (cause: Exception) {
        throw CatalogException.InvalidResponse(platform, cause)
    }

    private fun Int?.toReleaseChannel(): ReleaseChannel = when (this) {
        1 -> ReleaseChannel.RELEASE
        2 -> ReleaseChannel.BETA
        3 -> ReleaseChannel.ALPHA
        else -> throw invalidResponse()
    }

    companion object {
        private const val API_KEY_HEADER = "x-api-key"
        private const val MINECRAFT_GAME_ID = 432
        private const val MOD_CLASS_ID = 6
        private const val BATCH_SIZE = 50
        private const val FINGERPRINT_BATCH_SIZE = 1_000
        private const val SHA1_ALGORITHM = 1
    }
}

private fun ModLoader.curseForgeType(): Int = when (this) {
    ModLoader.forge, ModLoader.cleanroom -> 1
    ModLoader.neoforge -> 6
}

private fun CatalogSort.curseForgeField(): Int? = when (this) {
    CatalogSort.RELEVANCE -> null
    CatalogSort.DOWNLOADS -> 6
    CatalogSort.UPDATED -> 3
}

private fun String.toModLoader(): ModLoader? = when (lowercase()) {
    "forge" -> ModLoader.forge
    "neoforge" -> ModLoader.neoforge
    else -> null
}

@Serializable
private data class CfDataResponse<T>(val data: T)

@Serializable
private data class CfProjectRequest(val modIds: List<Int>, val filterPcOnly: Boolean = true)

@Serializable
private data class CfFileRequest(val fileIds: List<Int>)

@Serializable
private data class CfFingerprintRequest(val fingerprints: List<Long>)

@Serializable
private data class CfProjectDto(
    val id: Int,
    val name: String,
    val slug: String,
    val summary: String? = null,
    val downloadCount: Long = 0,
    val classId: Int? = null,
    val logo: CfLogoDto? = null,
    val dateModified: String? = null,
    val authors: List<CfAuthorDto> = emptyList(),
    val categories: List<CfCategoryDto> = emptyList(),
    val links: CfLinksDto? = null
)

@Serializable
private data class CfLogoDto(val thumbnailUrl: String? = null, val url: String? = null)

@Serializable
private data class CfAuthorDto(val name: String? = null)

@Serializable
private data class CfCategoryDto(val name: String? = null)

@Serializable
private data class CfLinksDto(
    val websiteUrl: String? = null,
    val wikiUrl: String? = null,
    val issuesUrl: String? = null
)

@Serializable
private data class CfSearchResponse(
    val data: List<CfProjectDto> = emptyList(),
    val pagination: CfPaginationDto? = null
)

@Serializable
private data class CfFileListResponse(
    val data: List<CfFileDto> = emptyList(),
    val pagination: CfPaginationDto? = null
)

@Serializable
private data class CfPaginationDto(
    val index: Int = 0,
    val resultCount: Int = 0,
    val totalCount: Int = 0
)

@Serializable
private data class CfFileDto(
    val id: Int,
    val modId: Int,
    val displayName: String? = null,
    val fileName: String? = null,
    val releaseType: Int? = null,
    val hashes: List<CfFileHashDto> = emptyList(),
    val fileDate: String? = null,
    val fileLength: Long? = null,
    val fileSizeOnDisk: Long? = null,
    val gameVersions: List<String> = emptyList(),
    val dependencies: List<CfDependencyDto> = emptyList(),
    val fileFingerprint: Long? = null
)

@Serializable
private data class CfFileHashDto(val value: String? = null, val algo: Int? = null)

@Serializable
private data class CfDependencyDto(val modId: Long? = null, val relationType: Int? = null)

@Serializable
private data class CfFingerprintDataDto(
    val exactMatches: List<CfFingerprintMatchDto> = emptyList()
)

@Serializable
private data class CfFingerprintMatchDto(val file: CfFileDto)
