package calebxzau.rdi.client.modcatalog

import calebxzau.rdi.client.modcatalog.CatalogException
import calebxzau.rdi.client.modcatalog.CatalogNetworkPolicy
import calebxzau.rdi.client.modcatalog.CatalogSort
import calebxzau.rdi.client.modcatalog.CatalogTarget
import calebxzau.rdi.client.modcatalog.CurseForgeAdapter
import calebxzau.rdi.client.modcatalog.CurseForgeConfig
import calebxzau.rdi.client.modcatalog.EnvironmentRequirement
import calebxzau.rdi.client.modcatalog.ModrinthAdapter
import calebxzau.rdi.client.modcatalog.ModrinthConfig
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.ModLoader
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlatformAdapterTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `modrinth search maps raw values`() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("1.21.1", request.url.parameters.getAll("facets")?.single()?.substringAfter("versions:")?.substringBefore('"'))
            respond(
                content = """{"hits":[{"slug":"jei","title":"JEI","description":"items","project_type":"mod","downloads":42,"project_id":"abc","date_modified":"2026-01-01T00:00:00Z","client_side":"required","server_side":"unsupported"}],"total_hits":1}""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val adapter = ModrinthAdapter(
            HttpClient(engine),
            ModrinthConfig(),
            CatalogNetworkPolicy(preferMirror = false),
            json
        )
        val page = adapter.search(
            "jei",
            CatalogTarget(McVersion.V211, ModLoader.neoforge),
            CatalogSort.RELEVANCE,
            0,
            20
        )
        assertEquals(42L, page.items.single().downloadCount)
        assertEquals(EnvironmentRequirement.UNSUPPORTED, page.items.single().environment.server)
    }

    @Test
    fun `curseforge exact slug lookup uses one filtered search request`() = runBlocking {
        var requestCount = 0
        val engine = MockEngine { request ->
            requestCount++
            assertEquals("jei", request.url.parameters["slug"])
            assertEquals(null, request.url.parameters["searchFilter"])
            assertEquals("1.21.1", request.url.parameters["gameVersion"])
            assertEquals("6", request.url.parameters["modLoaderType"])
            respond(
                content = """{"data":[{"id":238222,"name":"JEI","slug":"jei","classId":6}],"pagination":{"index":0,"resultCount":1,"totalCount":1}}""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val adapter = CurseForgeAdapter(
            HttpClient(engine),
            CurseForgeConfig(apiKey = "secret"),
            CatalogNetworkPolicy(preferMirror = false),
            json
        )

        val project = adapter.resolveSlugs(
            listOf("jei"),
            CatalogTarget(McVersion.V211, ModLoader.neoforge)
        ).found["jei"]

        assertEquals("238222", project?.ref?.projectId)
        assertEquals(1, requestCount)
    }

    @Test
    fun `modrinth resolves multiple slugs in one request and filters target`() = runBlocking {
        var requestCount = 0
        val engine = MockEngine { request ->
            requestCount++
            assertEquals("/v2/projects", request.url.encodedPath)
            respond(
                content = """[
                    {"id":"2","slug":"second","title":"Second","game_versions":["1.21.1"],"loaders":["neoforge"]},
                    {"id":"1","slug":"first","title":"First","game_versions":["1.21.1"],"loaders":["neoforge"]},
                    {"id":"3","slug":"old","title":"Old","game_versions":["1.20.1"],"loaders":["forge"]}
                ]""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val adapter = ModrinthAdapter(
            HttpClient(engine),
            ModrinthConfig(),
            CatalogNetworkPolicy(preferMirror = false),
            json
        )

        val result = adapter.resolveSlugs(
            listOf("first", "second", "old"),
            CatalogTarget(McVersion.V211, ModLoader.neoforge)
        )

        assertEquals(1, requestCount)
        assertEquals(setOf("first", "second"), result.found.keys)
        assertEquals(setOf("old"), result.missing)
    }

    @Test
    fun `curseforge key reaches mirror and invalid mirror falls back to official`() = runBlocking {
        val requests = mutableListOf<Pair<String, String?>>()
        val engine = MockEngine { request ->
            requests += request.url.host to request.headers["x-api-key"]
            if (request.url.host == "mirror.test") {
                respond(
                    "<html>bad mirror</html>",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString())
                )
            } else {
                respond(
                    """{"data":[],"pagination":{"index":0,"resultCount":0,"totalCount":0}}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            }
        }
        val adapter = CurseForgeAdapter(
            HttpClient(engine),
            CurseForgeConfig(apiKey = "secret"),
            CatalogNetworkPolicy(preferMirror = true, mirrorBaseUrl = "https://mirror.test"),
            json
        )
        adapter.search(
            "jei",
            CatalogTarget(McVersion.V211, ModLoader.neoforge),
            CatalogSort.RELEVANCE,
            0,
            20
        )
        assertEquals(listOf("mirror.test", "api.curseforge.com"), requests.map { it.first })
        assertTrue(requests.all { it.second == "secret" })
    }

    @Test
    fun `official retry does not retry mirror`() = runBlocking {
        val hosts = mutableListOf<String>()
        var officialRequests = 0
        val engine = MockEngine { request ->
            hosts += request.url.host
            when (request.url.host) {
                "mirror.test" -> respond(
                    """{"error":"unavailable"}""",
                    HttpStatusCode.ServiceUnavailable,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )

                else -> {
                    officialRequests++
                    respond(
                        if (officialRequests == 1) """{"error":"unavailable"}"""
                        else """{"data":[],"pagination":{"index":0,"resultCount":0,"totalCount":0}}""",
                        if (officialRequests == 1) HttpStatusCode.ServiceUnavailable else HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            }
        }
        val adapter = CurseForgeAdapter(
            HttpClient(engine),
            CurseForgeConfig(apiKey = "secret"),
            CatalogNetworkPolicy(preferMirror = true, mirrorBaseUrl = "https://mirror.test"),
            json
        )

        adapter.search(
            "jei",
            CatalogTarget(McVersion.V211, ModLoader.neoforge),
            CatalogSort.RELEVANCE,
            0,
            20
        )

        assertEquals(listOf("mirror.test", "api.curseforge.com", "api.curseforge.com"), hosts)
    }

    @Test
    fun `invalid official response is not retried`() = runBlocking {
        var requestCount = 0
        val engine = MockEngine {
            requestCount++
            respond(
                "<html>invalid</html>",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString())
            )
        }
        val adapter = ModrinthAdapter(
            HttpClient(engine),
            ModrinthConfig(),
            CatalogNetworkPolicy(preferMirror = false),
            json
        )
        var failure: Throwable? = null

        try {
            adapter.search(
                "jei",
                CatalogTarget(McVersion.V211, ModLoader.neoforge),
                CatalogSort.RELEVANCE,
                0,
                20
            )
        } catch (cause: Throwable) {
            failure = cause
        }

        assertTrue(failure is CatalogException.InvalidResponse)
        assertEquals(1, requestCount)
    }
}
