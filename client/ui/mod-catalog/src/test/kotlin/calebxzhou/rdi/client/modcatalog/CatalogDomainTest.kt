package calebxzhou.rdi.client.modcatalog

import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.ModLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
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
    fun `exact full pinyin resolves only the best local identity`() = runBlocking {
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
            searchItems = listOf(source(ModPlatform.MODRINTH, "1", "jei"))
        )
        val curseForge = FakeAdapter(
            ModPlatform.CURSEFORGE,
            searchItems = listOf(source(ModPlatform.CURSEFORGE, "2", "jei"))
        )
        val catalog = testCatalog(
            mapOf(ModPlatform.MODRINTH to modrinth, ModPlatform.CURSEFORGE to curseForge),
            FakeIdentityIndex(fullPinyinRecords = mapOf("jeiwupinguanliqi" to record))
        )

        val page = catalog.search(searchRequest(query = "jeiwupinguanliqi")).getOrThrow().value

        assertEquals(listOf("jei"), modrinth.searchQueries)
        assertEquals(listOf("jei"), curseForge.searchQueries)
        assertEquals("mcmod:459", page.items.single().identity.stableKey)
        assertEquals(2, page.items.single().sources.size)
        assertEquals(null, page.nextCursor)
    }

    @Test
    fun `ordinary query skips local identity expansion`() = runBlocking {
        val adapter = FakeAdapter(
            ModPlatform.MODRINTH,
            searchItems = listOf(source(ModPlatform.MODRINTH, "1", "jei"))
        )
        val catalog = testCatalog(
            mapOf(ModPlatform.MODRINTH to adapter),
            FakeIdentityIndex(searchFailure = AssertionError("ordinary search must not query local candidates"))
        )

        val page = catalog.search(searchRequest(query = "jei")).getOrThrow().value

        assertEquals(listOf("jei"), adapter.searchQueries)
        assertEquals("modrinth:1", page.items.single().identity.stableKey)
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
        index: CatalogIdentityIndex
    ) = DefaultModCatalog(
        adapters = adapters,
        identityIndex = index,
        cachePolicy = CatalogCachePolicy(),
        clock = Clock.systemUTC(),
        ioDispatcher = Dispatchers.IO,
        onWarning = { throw AssertionError(it) }
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
    private val fullPinyinRecords: Map<String, CatalogIdentityRecord> = emptyMap(),
    private val searchFailure: Throwable? = null
) : CatalogIdentityIndex {
    override val unavailableCause: Throwable? = null

    override suspend fun find(platform: ModPlatform, slug: String): CatalogIdentityRecord? =
        records["${platform.name}:$slug"]

    override suspend fun findAll(refs: Set<CatalogSlugRef>): Map<CatalogSlugRef, CatalogIdentityRecord> =
        refs.mapNotNull { ref ->
            records["${ref.platform.name}:${ref.slug}"]?.let { ref to it }
        }.toMap()

    override suspend fun search(query: String, offset: Int, limit: Int): List<CatalogIdentityRecord> {
        searchFailure?.let { throw it }
        return emptyList()
    }

    override suspend fun findExactFullPinyin(query: String): CatalogIdentityRecord? = fullPinyinRecords[query]

    override fun close() = Unit
}

private class FakeAdapter(
    override val platform: ModPlatform,
    private val searchItems: List<CatalogProjectSource> = emptyList(),
    private val files: Map<String, CatalogFile> = emptyMap(),
    private val searchFailure: Throwable? = null
) : PlatformAdapter {
    val searchQueries = mutableListOf<String>()

    override suspend fun search(
        query: String,
        target: CatalogTarget,
        sort: CatalogSort,
        offset: Int,
        limit: Int
    ): SourcePage {
        searchQueries += query
        searchFailure?.let { throw it }
        return SourcePage(
            items = if (offset == 0) searchItems.take(limit) else emptyList(),
            nextOffset = null,
            totalCount = searchItems.size.toLong()
        )
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

    override suspend fun matchLocalFiles(files: List<LocalFileHashes>) = emptyMap<java.nio.file.Path, CatalogFile>()

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
