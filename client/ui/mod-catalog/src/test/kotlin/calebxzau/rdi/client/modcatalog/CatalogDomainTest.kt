package calebxzau.rdi.client.modcatalog

import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.ModLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
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
    fun `file environment keeps known fields and falls back per unknown field`() {
        val source = source(ModPlatform.CURSEFORGE, "project", "project").copy(
            environment = EnvironmentCompatibility(
                client = EnvironmentRequirement.REQUIRED,
                server = EnvironmentRequirement.UNSUPPORTED,
            )
        )
        val file = file("file", "project").copy(
            environment = EnvironmentCompatibility(
                client = EnvironmentRequirement.UNKNOWN,
                server = EnvironmentRequirement.REQUIRED,
            )
        )

        assertEquals(
            EnvironmentCompatibility(
                client = EnvironmentRequirement.REQUIRED,
                server = EnvironmentRequirement.REQUIRED,
            ),
            file.effectiveEnvironment(source)
        )
    }

    @Test
    fun `ordinary file query treats release and beta equally`() {
        val request = CatalogFileRequest(sampleMod(), CatalogTarget(McVersion.V211, ModLoader.neoforge))
        assertEquals(setOf(ReleaseChannel.RELEASE, ReleaseChannel.BETA), request.channels)
    }

    @Test
    fun `repeated and overlapping file lookups only request uncached ids`() = runBlocking {
        val first = file("1", "project")
        val second = file("2", "project")
        val gate = CompletableDeferred<Unit>()
        val adapter = FakeAdapter(
            ModPlatform.MODRINTH,
            files = mapOf("1" to first, "2" to second),
            fileRequestGate = gate
        )
        val catalog = testCatalog(mapOf(ModPlatform.MODRINTH to adapter), FakeIdentityIndex())

        val one = async { catalog.getFiles(setOf(first.ref, second.ref)).getOrThrow() }
        adapter.fileRequestStarted.await()
        val two = async { catalog.getFiles(setOf(second.ref)).getOrThrow() }
        gate.complete(Unit)
        one.await()
        two.await()
        catalog.getFiles(setOf(first.ref)).getOrThrow()

        assertEquals(listOf(setOf("1", "2")), adapter.fileRequests)
    }

    @Test
    fun `missing files expire while failures remain retryable`() = runBlocking {
        val clock = MutableClock(Instant.EPOCH)
        val adapter = FakeAdapter(ModPlatform.MODRINTH)
        val catalog = testCatalog(
            mapOf(ModPlatform.MODRINTH to adapter),
            FakeIdentityIndex(),
            cachePolicy = CatalogCachePolicy(negativeTtl = Duration.ofSeconds(1)),
            clock = clock,
            onWarning = {}
        )
        val ref = CatalogFileRef(ModPlatform.MODRINTH, "missing")

        catalog.getFiles(setOf(ref)).getOrThrow()
        catalog.getFiles(setOf(ref)).getOrThrow()
        clock.advance(Duration.ofSeconds(2))
        catalog.getFiles(setOf(ref)).getOrThrow()
        adapter.fileFailure = IllegalStateException("offline")
        catalog.getFiles(setOf(CatalogFileRef(ModPlatform.MODRINTH, "failed")))
        adapter.fileFailure = null
        catalog.getFiles(setOf(CatalogFileRef(ModPlatform.MODRINTH, "failed"))).getOrThrow()

        assertEquals(listOf(setOf("missing"), setOf("missing"), setOf("failed"), setOf("failed")), adapter.fileRequests)
    }

    @Test
    fun `file lookup cancellation is propagated`() = runBlocking {
        val adapter = FakeAdapter(ModPlatform.MODRINTH).also {
            it.fileFailure = CancellationException("cancelled")
        }
        val catalog = testCatalog(mapOf(ModPlatform.MODRINTH to adapter), FakeIdentityIndex())

        assertFailsWith<CancellationException> {
            catalog.getFiles(setOf(CatalogFileRef(ModPlatform.MODRINTH, "file")))
        }
    }

    @Test
    fun `modrinth file list caches complete result across channels and pages`() = runBlocking {
        val release = file("release", "project", publishedAt = Instant.parse("2026-03-01T00:00:00Z"))
        val beta = file("beta", "project", channel = ReleaseChannel.BETA, publishedAt = Instant.parse("2026-02-01T00:00:00Z"))
        val alpha = file("alpha", "project", channel = ReleaseChannel.ALPHA, publishedAt = Instant.parse("2026-01-01T00:00:00Z"))
        val adapter = FakeAdapter(ModPlatform.MODRINTH, files = mapOf("release" to release, "beta" to beta, "alpha" to alpha))
        val catalog = testCatalog(mapOf(ModPlatform.MODRINTH to adapter), FakeIdentityIndex())
        val mod = listOf(source(ModPlatform.MODRINTH, "project", "project")).toCatalogModForTest()
        val target = CatalogTarget(McVersion.V211, ModLoader.neoforge)

        val first = catalog.listFiles(CatalogFileRequest(mod, target, setOf(ReleaseChannel.RELEASE, ReleaseChannel.BETA), 1)).getOrThrow().value
        catalog.listFiles(CatalogFileRequest(mod, target, setOf(ReleaseChannel.ALPHA), 1)).getOrThrow()
        catalog.listFiles(CatalogFileRequest(mod, target, setOf(ReleaseChannel.RELEASE, ReleaseChannel.BETA), 1, first.nextCursor)).getOrThrow()
        catalog.getFiles(setOf(release.ref, beta.ref, alpha.ref)).getOrThrow()

        assertEquals(1, adapter.fileListRequests)
        assertEquals(emptyList(), adapter.fileRequests)
    }

    @Test
    fun `curseforge pages keep separate cache entries`() = runBlocking {
        val files = (1..3).associate { id ->
            id.toString() to file(id.toString(), "project").copy(
                ref = CatalogFileRef(ModPlatform.CURSEFORGE, id.toString()),
                project = CatalogProjectRef(ModPlatform.CURSEFORGE, "project")
            )
        }
        val adapter = FakeAdapter(ModPlatform.CURSEFORGE, files = files)
        val catalog = testCatalog(mapOf(ModPlatform.CURSEFORGE to adapter), FakeIdentityIndex())
        val mod = listOf(source(ModPlatform.CURSEFORGE, "project", "project")).toCatalogModForTest()
        val target = CatalogTarget(McVersion.V211, ModLoader.neoforge)

        val first = catalog.listFiles(CatalogFileRequest(mod, target, pageSize = 1)).getOrThrow().value
        catalog.listFiles(CatalogFileRequest(mod, target, pageSize = 1, cursor = first.nextCursor)).getOrThrow()
        catalog.listFiles(CatalogFileRequest(mod, target, pageSize = 1)).getOrThrow()

        assertEquals(2, adapter.fileListRequests)
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
    fun `non-mod project does not merge into mcmod identity`() = runBlocking {
        val identity = CatalogIdentityRecord(
            mcmodId = 459,
            name = "Shared",
            nameCn = null,
            intro = null,
            logoUrl = null,
            projects = listOf(
                CatalogIdentityProject(ModPlatform.MODRINTH, "mod-slug", null),
                CatalogIdentityProject(ModPlatform.MODRINTH, "resource-slug", null),
            )
        )
        val mod = source(ModPlatform.MODRINTH, "mod-id", "mod-slug")
        val resource = source(ModPlatform.MODRINTH, "resource-id", "resource-slug")
            .copy(contentType = CatalogContentType.RESOURCE_PACK)
        val catalog = testCatalog(
            mapOf(
                ModPlatform.MODRINTH to FakeAdapter(
                    ModPlatform.MODRINTH,
                    projectSources = mapOf("mod-id" to mod, "resource-id" to resource),
                )
            ),
            FakeIdentityIndex(
                records = mapOf(
                    "MODRINTH:mod-slug" to identity,
                    "MODRINTH:resource-slug" to identity,
                )
            )
        )

        val result = catalog.getMods(
            setOf(mod.ref, resource.ref)
        ).getOrThrow().value

        assertEquals("mcmod:459", result.getValue(mod.ref).identity.stableKey)
        assertEquals("modrinth:resource-id", result.getValue(resource.ref).identity.stableKey)
        assertEquals("RESOURCE-SLUG", result.getValue(resource.ref).name)
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
    fun `project dependency skips incompatible latest curseforge file`() = runBlocking {
        val release = curseForgeFile("release", "dependency", ReleaseChannel.RELEASE, 1)
        val alpha = curseForgeFile("alpha", "dependency", ReleaseChannel.ALPHA, 2)
        val olderAlpha = (0 until 50).map { index ->
            curseForgeFile("older-alpha-$index", "dependency", ReleaseChannel.ALPHA, -index.toLong())
        }
        val files = listOf(alpha, release) + olderAlpha
        val adapter = FakeAdapter(
            ModPlatform.CURSEFORGE,
            files = files.associateBy { it.ref.fileId }
        )
        val root = file(
            "root",
            "root-project",
            dependencies = listOf(
                CatalogDependency(
                    DependencyTarget.Project(release.project),
                    DependencyRequirement.REQUIRED
                )
            )
        )
        val graph = testCatalog(mapOf(ModPlatform.CURSEFORGE to adapter), FakeIdentityIndex())
            .resolveDependencies(DependencyRequest(listOf(root), CatalogTarget(McVersion.V211, ModLoader.neoforge)))
            .getOrThrow().value

        assertEquals(setOf(root.ref, release.ref), graph.nodes.keys)
        assertTrue(graph.unresolved.isEmpty())
        assertEquals(1, adapter.fileListRequests)
    }

    @Test
    fun `project dependency continues curseforge pages`() = runBlocking {
        val files = (0..50).map { index ->
            curseForgeFile("alpha-$index", "dependency", ReleaseChannel.ALPHA, (51 - index).toLong())
        } + curseForgeFile("release", "dependency", ReleaseChannel.RELEASE, 0)
        val adapter = FakeAdapter(
            ModPlatform.CURSEFORGE,
            files = files.associateBy { it.ref.fileId }
        )
        val root = file(
            "root",
            "root-project",
            dependencies = listOf(
                CatalogDependency(
                    DependencyTarget.Project(files.first().project),
                    DependencyRequirement.REQUIRED
                )
            )
        )

        val graph = testCatalog(mapOf(ModPlatform.CURSEFORGE to adapter), FakeIdentityIndex())
            .resolveDependencies(DependencyRequest(listOf(root), CatalogTarget(McVersion.V211, ModLoader.neoforge)))
            .getOrThrow().value

        assertEquals(setOf(root.ref, files.last().ref), graph.nodes.keys)
        assertEquals(2, adapter.fileListRequests)
    }

    @Test
    fun `project dependency reports later page failure`() = runBlocking {
        val files = (0..50).map { index ->
            curseForgeFile("alpha-$index", "dependency", ReleaseChannel.ALPHA, (51 - index).toLong())
        }
        val adapter = FakeAdapter(
            ModPlatform.CURSEFORGE,
            files = files.associateBy { it.ref.fileId }
        ).also { it.fileListFailureAtOffset = 50 }
        val root = file(
            "root",
            "root-project",
            dependencies = listOf(
                CatalogDependency(
                    DependencyTarget.Project(files.first().project),
                    DependencyRequirement.REQUIRED
                )
            )
        )

        val outcome = testCatalog(
            mapOf(ModPlatform.CURSEFORGE to adapter),
            FakeIdentityIndex(),
            onWarning = {}
        )
            .resolveDependencies(DependencyRequest(listOf(root), CatalogTarget(McVersion.V211, ModLoader.neoforge)))
            .getOrThrow()

        assertEquals(1, outcome.value.unresolved.size)
        assertTrue(outcome.issues.any { it is CatalogIssue.SourceFailed })
    }

    @Test
    fun `project dependency with no compatible files is unresolved`() = runBlocking {
        val alpha = curseForgeFile("alpha", "dependency", ReleaseChannel.ALPHA, 1)
        val adapter = FakeAdapter(
            ModPlatform.CURSEFORGE,
            files = mapOf(alpha.ref.fileId to alpha)
        )
        val root = file(
            "root",
            "root-project",
            dependencies = listOf(
                CatalogDependency(
                    DependencyTarget.Project(alpha.project),
                    DependencyRequirement.REQUIRED
                )
            )
        )

        val outcome = testCatalog(mapOf(ModPlatform.CURSEFORGE to adapter), FakeIdentityIndex())
            .resolveDependencies(DependencyRequest(listOf(root), CatalogTarget(McVersion.V211, ModLoader.neoforge)))
            .getOrThrow()

        assertEquals(1, outcome.value.unresolved.size)
        assertTrue(outcome.issues.isEmpty())
        assertEquals(1, adapter.fileListRequests)
    }

    @Test
    fun `modrinth project dependency selects compatible file from complete list`() = runBlocking {
        val alpha = file(
            "alpha",
            "dependency",
            channel = ReleaseChannel.ALPHA,
            publishedAt = Instant.parse("2026-02-01T00:00:00Z")
        )
        val release = file(
            "release",
            "dependency",
            channel = ReleaseChannel.RELEASE,
            publishedAt = Instant.parse("2026-01-01T00:00:00Z")
        )
        val adapter = FakeAdapter(
            ModPlatform.MODRINTH,
            files = mapOf(alpha.ref.fileId to alpha, release.ref.fileId to release)
        )
        val root = file(
            "root",
            "root-project",
            dependencies = listOf(
                CatalogDependency(
                    DependencyTarget.Project(release.project),
                    DependencyRequirement.REQUIRED
                )
            )
        )

        val graph = testCatalog(mapOf(ModPlatform.MODRINTH to adapter), FakeIdentityIndex())
            .resolveDependencies(DependencyRequest(listOf(root), CatalogTarget(McVersion.V211, ModLoader.neoforge)))
            .getOrThrow().value

        assertEquals(setOf(root.ref, release.ref), graph.nodes.keys)
        assertEquals(1, adapter.fileListRequests)
    }

    @Test
    fun `project dependency cancellation propagates`() = runBlocking<Unit> {
        val dependency = curseForgeFile("dependency", "dependency", ReleaseChannel.RELEASE, 1)
        val adapter = FakeAdapter(
            ModPlatform.CURSEFORGE,
            files = mapOf(dependency.ref.fileId to dependency)
        ).also { it.fileListFailure = CancellationException("cancelled") }
        val root = file(
            "root",
            "root-project",
            dependencies = listOf(
                CatalogDependency(
                    DependencyTarget.Project(dependency.project),
                    DependencyRequirement.REQUIRED
                )
            )
        )

        assertFailsWith<CancellationException> {
            testCatalog(mapOf(ModPlatform.CURSEFORGE to adapter), FakeIdentityIndex())
                .resolveDependencies(DependencyRequest(listOf(root), CatalogTarget(McVersion.V211, ModLoader.neoforge)))
        }
    }

    @Test
    fun `project dependency rejects non advancing page`() = runBlocking {
        listOf(0, -1).forEach { invalidOffset ->
            val dependencyFile = curseForgeFile("alpha-$invalidOffset", "dependency", ReleaseChannel.ALPHA, 1)
            val adapter = FakeAdapter(
                ModPlatform.CURSEFORGE,
                files = mapOf(dependencyFile.ref.fileId to dependencyFile)
            ).also { it.fileListNextOffset = { _, _ -> invalidOffset } }
            val warnings = mutableListOf<Throwable>()
            val root = file(
                "root-$invalidOffset",
                "root-project-$invalidOffset",
                dependencies = listOf(
                    CatalogDependency(
                        DependencyTarget.Project(dependencyFile.project),
                        DependencyRequirement.REQUIRED
                    )
                )
            )

            val outcome = testCatalog(
                mapOf(ModPlatform.CURSEFORGE to adapter),
                FakeIdentityIndex(),
                onWarning = warnings::add
            )
                .resolveDependencies(
                    DependencyRequest(listOf(root), CatalogTarget(McVersion.V211, ModLoader.neoforge))
                )
                .getOrThrow()

            assertEquals(1, outcome.value.unresolved.size)
            assertTrue(outcome.issues.any { it is CatalogIssue.SourceFailed })
            assertEquals(1, warnings.size)
            assertTrue(warnings.single() is CatalogException.InvalidResponse)
        }
    }

    @Test
    fun `dependency resolver selects requirements at every depth`() = runBlocking {
        val grandchild = file("grandchild", "grandchild-project")
        val required = file(
            "required",
            "required-project",
            dependencies = listOf(
                CatalogDependency(
                    DependencyTarget.File(grandchild.ref),
                    DependencyRequirement.OPTIONAL
                )
            )
        )
        val optional = file("optional", "optional-project")
        val incompatible = file("incompatible", "incompatible-project")
        val embedded = file("embedded", "embedded-project")
        val tool = file("tool", "tool-project")
        val included = file("included", "included-project")
        val root = file(
            "root",
            "root-project",
            dependencies = listOf(
                CatalogDependency(DependencyTarget.File(required.ref), DependencyRequirement.REQUIRED),
                CatalogDependency(DependencyTarget.File(optional.ref), DependencyRequirement.OPTIONAL),
                CatalogDependency(DependencyTarget.File(incompatible.ref), DependencyRequirement.INCOMPATIBLE),
                CatalogDependency(DependencyTarget.File(embedded.ref), DependencyRequirement.EMBEDDED),
                CatalogDependency(DependencyTarget.File(tool.ref), DependencyRequirement.TOOL),
                CatalogDependency(DependencyTarget.File(included.ref), DependencyRequirement.INCLUDED)
            )
        )
        val adapter = FakeAdapter(
            ModPlatform.MODRINTH,
            files = listOf(root, required, grandchild, optional, incompatible, embedded, tool, included)
                .associateBy { it.ref.fileId }
        )
        val catalog = testCatalog(mapOf(ModPlatform.MODRINTH to adapter), FakeIdentityIndex())

        val graph = catalog.resolveDependencies(
            DependencyRequest(
                listOf(root),
                CatalogTarget(McVersion.V211, ModLoader.neoforge),
                requirements = setOf(DependencyRequirement.REQUIRED)
            )
        ).getOrThrow().value

        assertEquals(setOf(root.ref, required.ref), graph.nodes.keys)
        assertEquals(listOf(required.ref), graph.edges.map { (it.to as DependencyTarget.File).ref })
        assertEquals(listOf(setOf(required.ref.fileId)), adapter.fileRequests)
        assertTrue(graph.unresolved.isEmpty())
        assertTrue(graph.cycles.isEmpty())
    }

    @Test
    fun `dependency resolver can explicitly select optional requirements`() = runBlocking {
        val optional = file("optional", "optional-project")
        val root = file(
            "root",
            "root-project",
            dependencies = listOf(
                CatalogDependency(DependencyTarget.File(optional.ref), DependencyRequirement.OPTIONAL)
            )
        )
        val adapter = FakeAdapter(
            ModPlatform.MODRINTH,
            files = mapOf(root.ref.fileId to root, optional.ref.fileId to optional)
        )
        val catalog = testCatalog(mapOf(ModPlatform.MODRINTH to adapter), FakeIdentityIndex())

        val graph = catalog.resolveDependencies(
            DependencyRequest(
                listOf(root),
                CatalogTarget(McVersion.V211, ModLoader.neoforge),
                requirements = setOf(DependencyRequirement.OPTIONAL)
            )
        ).getOrThrow().value

        assertEquals(setOf(root.ref, optional.ref), graph.nodes.keys)
        assertEquals(listOf(setOf(optional.ref.fileId)), adapter.fileRequests)
    }

    @Test
    fun `legacy dependency request resolves optional dependencies by default`() = runBlocking {
        val optional = file("optional", "optional-project")
        val root = file(
            "root",
            "root-project",
            dependencies = listOf(
                CatalogDependency(DependencyTarget.File(optional.ref), DependencyRequirement.OPTIONAL)
            )
        )
        val adapter = FakeAdapter(
            ModPlatform.MODRINTH,
            files = mapOf(root.ref.fileId to root, optional.ref.fileId to optional)
        )
        val catalog = testCatalog(mapOf(ModPlatform.MODRINTH to adapter), FakeIdentityIndex())

        val graph = catalog.resolveDependencies(
            DependencyRequest(listOf(root), CatalogTarget(McVersion.V211, ModLoader.neoforge))
        ).getOrThrow().value

        assertEquals(setOf(root.ref, optional.ref), graph.nodes.keys)
        assertEquals(listOf(setOf(optional.ref.fileId)), adapter.fileRequests)
        assertEquals(DependencyRequirement.OPTIONAL, graph.edges.single().requirement)
    }

    @Test
    fun `dependency resolver with no requirements keeps roots without requests`() = runBlocking {
        val dependency = file("dependency", "dependency-project")
        val root = file(
            "root",
            "root-project",
            dependencies = listOf(
                CatalogDependency(DependencyTarget.File(dependency.ref), DependencyRequirement.REQUIRED)
            )
        )
        val adapter = FakeAdapter(
            ModPlatform.MODRINTH,
            files = mapOf(root.ref.fileId to root, dependency.ref.fileId to dependency)
        )
        val catalog = testCatalog(mapOf(ModPlatform.MODRINTH to adapter), FakeIdentityIndex())

        val graph = catalog.resolveDependencies(
            DependencyRequest(
                listOf(root),
                CatalogTarget(McVersion.V211, ModLoader.neoforge),
                requirements = emptySet()
            )
        ).getOrThrow().value

        assertEquals(setOf(root.ref), graph.nodes.keys)
        assertTrue(graph.edges.isEmpty())
        assertTrue(adapter.fileRequests.isEmpty())
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
        cachePolicy: CatalogCachePolicy = CatalogCachePolicy(),
        clock: Clock = Clock.systemUTC(),
        onWarning: (Throwable) -> Unit = { throw AssertionError(it) }
    ) = DefaultModCatalog(
        adapters = adapters,
        identityIndex = index,
        cachePolicy = cachePolicy,
        clock = clock,
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
    private val projectSources: Map<String, CatalogProjectSource> = emptyMap(),
    private val files: Map<String, CatalogFile> = emptyMap(),
    private val fileRequestGate: CompletableDeferred<Unit>? = null,
    private val searchFailure: Throwable? = null,
    private val searchFailureAtOffset: Int = 0
) : PlatformAdapter {
    val searchQueries = mutableListOf<String>()
    val slugRequests = mutableListOf<List<String>>()
    val fileRequests = mutableListOf<Set<String>>()
    val fileRequestStarted = CompletableDeferred<Unit>()
    var fileListRequests = 0
    var fileFailure: Throwable? = null
    var fileListFailure: Throwable? = null
    var fileListFailureAtOffset: Int? = null
    var fileListNextOffset: ((Int, Int) -> Int?)? = null

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

    override suspend fun getProjects(ids: Set<String>) = AdapterResult(
        projectSources.filterKeys { it in ids },
        ids - projectSources.keys,
    )

    override suspend fun getDetails(ref: CatalogProjectRef) = error("unused")

    override suspend fun listFiles(
        project: CatalogProjectRef,
        target: CatalogTarget,
        offset: Int,
        limit: Int
    ): AdapterFileList {
        fileListRequests++
        fileListFailure?.let { throw it }
        if (fileListFailureAtOffset == offset) throw IllegalStateException("file list failed at $offset")
        val items = files.values.asSequence()
        .filter { it.project == project }
        .sortedByDescending(CatalogFile::publishedAt)
        .toList()
        if (platform == ModPlatform.MODRINTH) return AdapterFileList.Complete(items)
        val page = items.drop(offset).take(limit)
        val nextOffset = fileListNextOffset?.invoke(offset, page.size)
            ?: (offset + page.size).takeIf { page.isNotEmpty() && it < items.size }
        return AdapterFileList.Page(page, nextOffset)
    }

    override suspend fun getFiles(ids: Set<String>): AdapterResult<CatalogFile> {
        fileRequests += ids
        fileRequestStarted.complete(Unit)
        fileRequestGate?.await()
        fileFailure?.let { throw it }
        return AdapterResult(files.filterKeys { it in ids }, ids - files.keys)
    }

    override suspend fun matchFiles(files: List<CatalogFileHashes>): Map<String, CatalogFile> = emptyMap()

    override suspend fun matchLocalFiles(files: List<LocalFileHashes>) = emptyMap<Path, CatalogFile>()

    override suspend fun getChangelog(file: CatalogFileRef) = ""

    override suspend fun resolveDownload(file: CatalogFile) = error("unused")
}

private class MutableClock(private var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneId.of("UTC")
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = now
    fun advance(duration: Duration) {
        now = now.plus(duration)
    }
}

private fun source(platform: ModPlatform, id: String, slug: String) = CatalogProjectSource(
    ref = CatalogProjectRef(platform, id),
    slug = slug,
    name = slug.uppercase(),
    summary = slug,
    iconUrl = null,
    downloadCount = id.toLongOrNull() ?: id.hashCode().toLong(),
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

private fun curseForgeFile(
    id: String,
    projectId: String,
    channel: ReleaseChannel,
    publishedAtSeconds: Long
) = file(id, projectId, channel = channel, publishedAt = Instant.EPOCH.plusSeconds(publishedAtSeconds)).copy(
    ref = CatalogFileRef(ModPlatform.CURSEFORGE, id),
    project = CatalogProjectRef(ModPlatform.CURSEFORGE, projectId)
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
