package calebxzau.rdi.client.modcatalog

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.nio.file.Path
import java.time.Clock

interface ModCatalog : AutoCloseable {
    suspend fun getMetadata(
        refs: Set<CatalogSlugRef>
    ): Result<Map<CatalogSlugRef, CatalogModMetadata>>

    suspend fun searchMetadata(
        query: String,
        limit: Int
    ): Result<List<CatalogModMetadata>>

    suspend fun search(request: CatalogSearchRequest): Result<CatalogOutcome<CatalogSearchPage>>

    suspend fun getMods(
        refs: Set<CatalogProjectRef>
    ): Result<CatalogOutcome<Map<CatalogProjectRef, CatalogMod>>>

    suspend fun getFiles(
        refs: Set<CatalogFileRef>
    ): Result<CatalogOutcome<Map<CatalogFileRef, CatalogFile>>>

    suspend fun getDetails(
        request: CatalogDetailsRequest
    ): Result<CatalogOutcome<CatalogModDetails>>

    suspend fun listFiles(
        request: CatalogFileRequest
    ): Result<CatalogOutcome<CatalogFilePage>>

    suspend fun matchFiles(
        hashes: List<CatalogFileHashes>
    ): Result<Map<String, CatalogFile>>

    suspend fun matchLocalFiles(
        paths: List<Path>
    ): Result<CatalogOutcome<LocalFileMatchReport>>

    suspend fun findUpdates(
        request: UpdateRequest
    ): Result<CatalogOutcome<UpdateReport>>

    suspend fun resolveDependencies(
        request: DependencyRequest
    ): Result<CatalogOutcome<DependencyGraph>>

    suspend fun getChangelog(file: CatalogFileRef): Result<String>

    suspend fun resolveDownload(file: CatalogFile): Result<ResolvedDownload>
}

data class CurseForgeConfig(
    val apiKey: String = DEFAULT_CURSEFORGE_API_KEY,
    val baseUrl: String = "https://api.curseforge.com/v1"
)

data class ModrinthConfig(
    val userAgent: String = "rdi5-mod-catalog",
    val baseUrl: String = "https://api.modrinth.com/v2"
)

data class CatalogNetworkPolicy(
    val preferMirror: Boolean = true,
    val mirrorBaseUrl: String = "https://mod.mcimirror.top",
    val preferMirrorProvider: () -> Boolean = { preferMirror }
) {
    internal fun mirrorBase(platform: ModPlatform): String = when (platform) {
        ModPlatform.CURSEFORGE -> "${mirrorBaseUrl.trimEnd('/')}/curseforge/v1"
        ModPlatform.MODRINTH -> "${mirrorBaseUrl.trimEnd('/')}/modrinth/v2"
    }
}

fun createModCatalog(
    httpClient: HttpClient,
    identityDatabaseMaterializationDir: Path,
    curseForgeConfig: CurseForgeConfig = CurseForgeConfig(),
    modrinthConfig: ModrinthConfig = ModrinthConfig(),
    networkPolicy: CatalogNetworkPolicy = CatalogNetworkPolicy(),
    cachePolicy: CatalogCachePolicy = CatalogCachePolicy(),
    clock: Clock = Clock.systemUTC(),
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    onWarning: (Throwable) -> Unit = {}
): ModCatalog = DefaultModCatalog.create(
    httpClient = httpClient,
    identityDatabaseMaterializationDir = identityDatabaseMaterializationDir,
    curseForgeConfig = curseForgeConfig,
    modrinthConfig = modrinthConfig,
    networkPolicy = networkPolicy,
    cachePolicy = cachePolicy,
    clock = clock,
    ioDispatcher = ioDispatcher,
    onWarning = onWarning
)

private val DEFAULT_CURSEFORGE_API_KEY: String = byteArrayOf(
    36, 50, 97, 36, 49, 48, 36, 55, 87, 87, 86, 49, 87, 69, 76, 99, 119, 88, 56, 88,
    112, 55, 100, 54, 56, 77, 72, 115, 46, 53, 103, 114, 84, 121, 90, 86, 97, 54,
    83, 121, 110, 121, 101, 83, 121, 77, 104, 49, 114, 115, 69, 56, 57, 110, 73,
    97, 48, 57, 122, 79
).decodeToString()
