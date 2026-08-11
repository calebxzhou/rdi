package calebxzau.rdi.client.modcatalog

import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.ModLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CatalogDomainTest {
    @Test
    fun `identity keys never depend on names`() {
        assertEquals("mcmod:459", CatalogIdentity.Mcmod(459).stableKey)
        assertEquals(
            "modrinth:AABB",
            CatalogIdentity.Unmapped(CatalogProjectRef(ModPlatform.MODRINTH, "AABB")).stableKey
        )
    }

    @Test
    fun `target only accepts configured loader`() {
        assertEquals(ModLoader.neoforge, CatalogTarget(McVersion.V211, ModLoader.neoforge).loader)
        assertFailsWith<IllegalArgumentException> {
            CatalogTarget(McVersion.V211, ModLoader.forge)
        }
    }

    @Test
    fun `ordinary file query treats release and beta equally`() {
        val request = CatalogFileRequest(sampleMod(), CatalogTarget(McVersion.V211, ModLoader.neoforge))
        assertEquals(setOf(ReleaseChannel.RELEASE, ReleaseChannel.BETA), request.channels)
    }

    @Test
    fun `catalog icon candidates prefer primary platform then mcmod`() {
        val curseForge = source(ModPlatform.CURSEFORGE, "1", "cf").copy(iconUrl = "https://cf/icon.png")
        val modrinth = source(ModPlatform.MODRINTH, "2", "mr").copy(iconUrl = "https://mr/icon.png")
        val mod = listOf(curseForge, modrinth).toCatalogModForTest().copy(
            identity = CatalogIdentity.Unmapped(modrinth.ref),
            primaryRef = modrinth.ref,
            mcmodIconUrl = "https://mcmod/icon.png"
        )

        assertEquals(listOf("https://mr/icon.png", "https://cf/icon.png"), mod.platformIconUrls)
        assertEquals(
            listOf("https://mr/icon.png", "https://cf/icon.png", "https://mcmod/icon.png"),
            mod.iconUrls
        )
    }

    @Test
    fun `metadata lookup uses database project localization`() = runBlocking {
        val ref = CatalogSlugRef(ModPlatform.MODRINTH, "jei")
        val record = CatalogIdentityRecord(
            mcmodId = 459,
            name = "Just Enough Items",
            nameCn = "JEI物品管理器",
            intro = "查看物品及配方",
            logoUrl = "https://mcmod/icon.png",
            projects = listOf(CatalogIdentityProject(ModPlatform.MODRINTH, "jei", "JEI"))
        )
        val catalog = testCatalog(emptyMap(), FakeIdentityIndex(mapOf("MODRINTH:jei" to record)))

        val metadata = catalog.getMetadata(setOf(ref)).getOrThrow().getValue(ref)

        assertEquals(459, metadata.mcmodId)
        assertEquals("JEI", metadata.nameCn)
        assertEquals("查看物品及配方", metadata.intro)
    }

    @Test
    fun `installed release can update to newer beta`() = runBlocking {
        val installed = file(
            "release",
            "project",
            channel = ReleaseChannel.RELEASE,
            publishedAt = Instant.parse("2026-01-01T00:00:00Z")
        )
        val beta = file(
            "beta",
            "project",
            channel = ReleaseChannel.BETA,
            publishedAt = Instant.parse("2026-02-01T00:00:00Z")
        )
        val catalog = testCatalog(
            mapOf(
                ModPlatform.MODRINTH to FakeAdapter(
                    ModPlatform.MODRINTH,
                    files = linkedMapOf(beta.ref.fileId to beta, installed.ref.fileId to installed)
                )
            ),
            FakeIdentityIndex()
        )

        val report = catalog.findUpdates(
            UpdateRequest(listOf(InstalledCatalogFile(installed, CatalogTarget(McVersion.V211, ModLoader.neoforge))))
        ).getOrThrow().value

        assertEquals(beta.ref, report.updates.single().latest.ref)
    }

    @Test
    fun `one source read calculates sha1 and curseforge fingerprint`() = runBlocking {
        val file = Files.createTempFile("rdi-catalog-hash", ".jar")
        try {
            Files.write(file, "a b\ncd\t".toByteArray())
            val hashes = hashLocalFile(file, Dispatchers.IO)
            assertEquals("69f48e5e37225fc9b7db2b4e5cb60023905edbc9", hashes.sha1)
            assertEquals(3_376_380_438L, hashes.curseForgeFingerprint)
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `opaque search cursor prevents cross page duplicates`() = runBlocking {
        val shared = CatalogIdentityRecord(
            mcmodId = 1,
            name = "A",
            nameCn = null,
            intro = null,
            logoUrl = null,
            projects = listOf(
                CatalogIdentityProject(ModPlatform.MODRINTH, "a", null),
                CatalogIdentityProject(ModPlatform.CURSEFORGE, "a", null)
            )
        )
        val index = FakeIdentityIndex(mapOf("MODRINTH:a" to shared, "CURSEFORGE:a" to shared))
        val catalog = testCatalog(
            adapters = mapOf(
                ModPlatform.MODRINTH to FakeAdapter(
                    ModPlatform.MODRINTH,
                    searchItems = listOf(source(ModPlatform.MODRINTH, "1", "a"), source(ModPlatform.MODRINTH, "2", "b"))
                ),
                ModPlatform.CURSEFORGE to FakeAdapter(
                    ModPlatform.CURSEFORGE,
                    searchItems = listOf(source(ModPlatform.CURSEFORGE, "10", "a"), source(ModPlatform.CURSEFORGE, "30", "c"))
                )
            ),
            index = index
        )
        val first = catalog.search(searchRequest()).getOrThrow().value
        val second = catalog.search(searchRequest(first.nextCursor)).getOrThrow().value
        val identities = (first.items + second.items).map { it.identity.stableKey }
        assertEquals(identities.distinct(), identities)
        assertTrue("mcmod:1" in identities)
    }

    @Test
    fun `query resolves ranked local identity before remote fallback`() = runBlocking {
        val record = CatalogIdentityRecord(
            mcmodId = 459,
            name = "Just Enough Items",
            nameCn = "JEI物品管理器",
            intro = "查看物品及配方",
            logoUrl = null,
            projects = listOf(
                CatalogIdentityProject(ModPlatform.MODRINTH, "jei", null),
                CatalogIdentityProject(ModPlatform.MODRINTH, "jei-alias", null),
                CatalogIdentityProject(ModPlatform.CURSEFORGE, "jei", null),
                CatalogIdentityProject(ModPlatform.CURSEFORGE, "jei-alias", null)
            )
        )
        val modrinth = FakeAdapter(
            ModPlatform.MODRINTH,
            slugSources = mapOf("jei" to source(ModPlatform.MODRINTH, "1", "jei"))
        )
        val curseForge = FakeAdapter(
            ModPlatform.CURSEFORGE,
            slugSources = mapOf("jei" to source(ModPlatform.CURSEFORGE, "2", "jei"))
        )
        val catalog = testCatalog(
            mapOf(ModPlatform.MODRINTH to modrinth, ModPlatform.CURSEFORGE to curseForge),
            FakeIdentityIndex(searchRecords = listOf(record))
        )

        val page = catalog.search(searchRequest(query = "物品管理器")).getOrThrow().value

        assertEquals(listOf(listOf("jei", "jei-alias")), modrinth.slugRequests)
        assertEquals(listOf(listOf("jei")), curseForge.slugRequests)
        assertEquals(listOf("Just Enough Items"), modrinth.searchQueries)
        assertEquals(listOf("Just Enough Items"), curseForge.searchQueries)
        assertEquals("mcmod:459", page.items.single().identity.stableKey)
        assertEquals(2, page.items.single().sources.size)
        assertEquals(null, page.nextCursor)
    }

    @Test
    fun `curseforge tries aliases in order within three request budget`() = runBlocking {
        val records = (1..4).map { id ->
            CatalogIdentityRecord(
                mcmodId = id,
                name = "Mod$id",
                nameCn = null,
                intro = null,
                logoUrl = null,
                projects = listOf(CatalogIdentityProject(ModPlatform.CURSEFORGE, "slug$id", null))
            )
        }
        val adapter = FakeAdapter(
            ModPlatform.CURSEFORGE,
            slugSources = mapOf("slug2" to source(ModPlatform.CURSEFORGE, "2", "slug2"))
        )
        val catalog = testCatalog(
            mapOf(ModPlatform.CURSEFORGE to adapter),
            FakeIdentityIndex(searchRecords = records)
        )

        catalog.search(searchRequest(query = "模组")).getOrThrow()

        assertEquals(listOf(listOf("slug1"), listOf("slug2"), listOf("slug3")), adapter.slugRequests)
    }

    @Test
    fun `missing slug is negatively cached for repeated search`() = runBlocking {
        val record = CatalogIdentityRecord(
            mcmodId = 1,
            name = "Missing",
            nameCn = null,
            intro = null,
            logoUrl = null,
            projects = listOf(CatalogIdentityProject(ModPlatform.MODRINTH, "missing", null))
        )
        val adapter = FakeAdapter(ModPlatform.MODRINTH)
        val catalog = testCatalog(
            mapOf(ModPlatform.MODRINTH to adapter),
            FakeIdentityIndex(searchRecords = listOf(record))
        )

        catalog.search(searchRequest(query = "missing")).getOrThrow()
        catalog.search(searchRequest(query = "missing")).getOrThrow()

        assertEquals(listOf(listOf("missing")), adapter.slugRequests)
        assertEquals(listOf("missing", "missing"), adapter.searchQueries)
    }

    @Test
    fun `local overflow is paged before remote fallback`() = runBlocking {
        val records = (1..3).map { id ->
            CatalogIdentityRecord(
                mcmodId = id,
                name = "Local$id",
                nameCn = null,
                intro = null,
                logoUrl = null,
                projects = listOf(CatalogIdentityProject(ModPlatform.MODRINTH, "local$id", null))
            )
        }
        val adapter = FakeAdapter(
            ModPlatform.MODRINTH,
            slugSources = records.associate { record ->
                val slug = record.projects.single().slug
                slug to source(ModPlatform.MODRINTH, record.mcmodId.toString(), slug)
            }
        )
        val catalog = testCatalog(
            mapOf(ModPlatform.MODRINTH to adapter),
            FakeIdentityIndex(searchRecords = records)
        )

        val first = catalog.search(searchRequest(query = "本地")).getOrThrow().value
        val second = catalog.search(searchRequest(first.nextCursor, query = "本地")).getOrThrow().value

        assertEquals(listOf("mcmod:1", "mcmod:2"), first.items.map { it.identity.stableKey })
        assertEquals(listOf("mcmod:3"), second.items.map { it.identity.stableKey })
        assertEquals(listOf("Local1"), adapter.searchQueries)
        assertEquals(null, second.nextCursor)
    }

    @Test
    fun `failed platform is not retained as next page work`() = runBlocking {
        val modrinth = FakeAdapter(ModPlatform.MODRINTH, searchFailure = IllegalStateException("offline"))
        val curseForge = FakeAdapter(
            ModPlatform.CURSEFORGE,
            searchItems = listOf(source(ModPlatform.CURSEFORGE, "1", "available"))
        )
        val catalog = testCatalog(
            mapOf(ModPlatform.MODRINTH to modrinth, ModPlatform.CURSEFORGE to curseForge),
            FakeIdentityIndex(),
            onWarning = {}
        )

        val page = catalog.search(searchRequest(query = "available")).getOrThrow()

        assertEquals(1, page.value.items.size)
        assertEquals(null, page.value.nextCursor)
        assertTrue(page.issues.any { it is CatalogIssue.SourceFailed })
    }

    @Test
    fun `later page failure closes cursor without discarding shown results`() = runBlocking {
        val adapter = FakeAdapter(
            ModPlatform.MODRINTH,
            searchItems = listOf(
                source(ModPlatform.MODRINTH, "1", "a"),
                source(ModPlatform.MODRINTH, "2", "b"),
                source(ModPlatform.MODRINTH, "3", "c")
            ),
            searchFailure = IllegalStateException("offline"),
            searchFailureAtOffset = 2
        )
        val catalog = testCatalog(
            mapOf(ModPlatform.MODRINTH to adapter),
            FakeIdentityIndex(),
            onWarning = {}
        )

        val first = catalog.search(searchRequest(query = "remote")).getOrThrow().value
        val second = catalog.search(searchRequest(first.nextCursor, query = "remote")).getOrThrow()

        assertEquals(2, first.items.size)
        assertEquals(emptyList(), second.value.items)
        assertEquals(null, second.value.nextCursor)
        assertTrue(second.issues.any { it is CatalogIssue.SourceFailed })
    }

    @Test
    fun `remote result grouping batches identity lookup once`() = runBlocking {
        val index = FakeIdentityIndex()
        val catalog = testCatalog(
            mapOf(
                ModPlatform.MODRINTH to FakeAdapter(
                    ModPlatform.MODRINTH,
                    searchItems = listOf(
                        source(ModPlatform.MODRINTH, "1", "a"),
                        source(ModPlatform.MODRINTH, "2", "b")
                    )
                )
            ),
            index
        )

        catalog.search(searchRequest(query = "unknown")).getOrThrow()

        assertEquals(1, index.findAllCalls)
    }

    @Test
    fun `dependency resolver reports cycles`() = runBlocking {
        val root = file("root", "root-project")
        val child = file(
            "child",
            "child-project",
            listOf(CatalogDependency(DependencyTarget.File(root.ref), DependencyRequirement.REQUIRED))
        )
        val rootWithDependency = root.copy(
            dependencies = listOf(CatalogDependency(DependencyTarget.File(child.ref), DependencyRequirement.REQUIRED))
        )
        val adapter = FakeAdapter(
            ModPlatform.MODRINTH,
            files = mapOf(root.ref.fileId to rootWithDependency, child.ref.fileId to child)
        )
        val catalog = testCatalog(mapOf(ModPlatform.MODRINTH to adapter), FakeIdentityIndex())
        val graph = catalog.resolveDependencies(
            DependencyRequest(listOf(rootWithDependency), CatalogTarget(McVersion.V211, ModLoader.neoforge))
        ).getOrThrow().value
        assertEquals(2, graph.nodes.size)
        assertTrue(graph.cycles.isNotEmpty())
    }

    @Test
    fun `cancellation is propagated instead of wrapped in result`() = runBlocking {
        val catalog = testCatalog(
            mapOf(
                ModPlatform.MODRINTH to FakeAdapter(
                    ModPlatform.MODRINTH,
                    searchFailure = CancellationException("cancelled")
                )
            ),
            FakeIdentityIndex()
        )
        var propagated = false

        try {
            catalog.search(searchRequest())
        } catch (_: CancellationException) {
            propagated = true
        }

        assertTrue(propagated)
    }

    private fun testCatalog(
        adapters: Map<ModPlatform, PlatformAdapter>,
        index: CatalogIdentityIndex,
        onWarning: (Throwable) -> Unit = { throw AssertionError(it) }
    ) = DefaultModCatalog(
        adapters = adapters,
        identityIndex = index,
        cachePolicy = CatalogCachePolicy(),
        clock = Clock.systemUTC(),
        ioDispatcher = Dispatchers.IO,
        onWarning = onWarning
    )

    private fun searchRequest(
        cursor: CatalogSearchCursor? = null,
        query: String = "a"
    ) = CatalogSearchRequest(
        query = query,
        target = CatalogTarget(McVersion.V211, ModLoader.neoforge),
        pageSize = 2,
        cursor = cursor
    )

    private fun sampleMod(): CatalogMod {
        val source = source(ModPlatform.MODRINTH, "1", "a")
        return listOf(source).toCatalogModForTest()
    }
}

private class FakeIdentityIndex(
    private val records: Map<String, CatalogIdentityRecord> = emptyMap(),
    private val searchRecords: List<CatalogIdentityRecord> = emptyList(),
    private val searchFailure: Throwable? = null
) : CatalogIdentityIndex {
    override val unavailableCause: Throwable? = null
    var findAllCalls = 0

    override suspend fun find(platform: ModPlatform, slug: String): CatalogIdentityRecord? =
        records["${platform.name}:$slug"]

    override suspend fun findAll(refs: Set<CatalogSlugRef>): Map<CatalogSlugRef, CatalogIdentityRecord> =
        refs.mapNotNull { ref ->
            records["${ref.platform.name}:${ref.slug}"]?.let { ref to it }
        }.toMap().also { findAllCalls++ }

    override suspend fun search(query: String, offset: Int, limit: Int): List<CatalogIdentityRecord> {
        searchFailure?.let { throw it }
        return searchRecords.drop(offset).take(limit)
    }

    override fun close() = Unit
}

private class FakeAdapter(
    override val platform: ModPlatform,
    private val searchItems: List<CatalogProjectSource> = emptyList(),
    private val slugSources: Map<String, CatalogProjectSource> = emptyMap(),
    private val files: Map<String, CatalogFile> = emptyMap(),
    private val searchFailure: Throwable? = null,
    private val searchFailureAtOffset: Int = 0
) : PlatformAdapter {
    val searchQueries = mutableListOf<String>()
    val slugRequests = mutableListOf<List<String>>()

    override suspend fun search(
        query: String,
        target: CatalogTarget,
        sort: CatalogSort,
        offset: Int,
        limit: Int
    ): SourcePage {
        searchQueries += query
        searchFailure?.takeIf { offset >= searchFailureAtOffset }?.let { throw it }
        val items = searchItems.drop(offset).take(limit)
        return SourcePage(
            items = items,
            nextOffset = (offset + items.size).takeIf { items.isNotEmpty() && it < searchItems.size },
            totalCount = searchItems.size.toLong()
        )
    }

    override suspend fun resolveSlugs(slugs: List<String>, target: CatalogTarget): SlugResolution {
        slugRequests += slugs
        val found = slugs.mapNotNull { slug ->
            slugSources[normalizeProjectSlug(slug)]?.let { normalizeProjectSlug(slug) to it }
        }.toMap()
        return SlugResolution(found, slugs.map(::normalizeProjectSlug).toSet() - found.keys)
    }

    override suspend fun getProjects(ids: Set<String>) = AdapterResult<CatalogProjectSource>(emptyMap(), ids)

    override suspend fun getDetails(ref: CatalogProjectRef) = error("unused")

    override suspend fun listFiles(
        project: CatalogProjectRef,
        target: CatalogTarget,
        channels: Set<ReleaseChannel>,
        offset: Int,
        limit: Int
    ) = files.values.asSequence()
        .filter { it.project == project && it.channel in channels }
        .sortedByDescending(CatalogFile::publishedAt)
        .drop(offset)
        .take(limit)
        .toList() to null

    override suspend fun getFiles(ids: Set<String>): AdapterResult<CatalogFile> =
        AdapterResult(files.filterKeys { it in ids }, ids - files.keys)

    override suspend fun matchLocalFiles(files: List<LocalFileHashes>) = emptyMap<Path, CatalogFile>()

    override suspend fun getChangelog(file: CatalogFileRef) = ""

    override suspend fun resolveDownload(file: CatalogFile) = error("unused")
}

private fun source(platform: ModPlatform, id: String, slug: String) = CatalogProjectSource(
    ref = CatalogProjectRef(platform, id),
    slug = slug,
    name = slug.uppercase(),
    summary = slug,
    iconUrl = null,
    downloadCount = id.toLong(),
    updatedAt = Instant.EPOCH,
    environment = EnvironmentCompatibility()
)

private fun file(
    id: String,
    projectId: String,
    dependencies: List<CatalogDependency> = emptyList(),
    channel: ReleaseChannel = ReleaseChannel.RELEASE,
    publishedAt: Instant = Instant.EPOCH
) = CatalogFile(
    ref = CatalogFileRef(ModPlatform.MODRINTH, id),
    project = CatalogProjectRef(ModPlatform.MODRINTH, projectId),
    displayName = id,
    versionNumber = id,
    fileName = "$id.jar",
    channel = channel,
    publishedAt = publishedAt,
    fileSize = 1,
    minecraftVersions = setOf("1.21.1"),
    loaders = setOf(ModLoader.neoforge),
    environment = EnvironmentCompatibility(),
    digests = setOf(CatalogDigest(CatalogDigestAlgorithm.SHA1, "0".repeat(40))),
    dependencies = dependencies
)

private fun List<CatalogProjectSource>.toCatalogModForTest(): CatalogMod {
    val primary = first()
    return CatalogMod(
        identity = CatalogIdentity.Unmapped(primary.ref),
        primaryRef = primary.ref,
        sources = this,
        name = primary.name,
        nameCn = null,
        summary = primary.summary,
        mcmodIconUrl = null,
        downloadCount = sumOf(CatalogProjectSource::downloadCount),
        updatedAt = maxOf(CatalogProjectSource::updatedAt),
        environment = primary.environment
    )
}
