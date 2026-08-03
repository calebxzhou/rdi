package calebxzau.rdi.client.blessingskin

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlessingSkinClientTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `search maps two provider pages and encodes query`() = runBlocking {
        val requests = mutableListOf<Url>()
        val engine = MockEngine { request ->
            requests += request.url
            val page = request.url.parameters["page"]
            respond(
                content = if (page == "1") {
                    pageJson(currentPage = 1, lastPage = 2, tid = 1, type = "alex")
                } else {
                    pageJson(currentPage = 2, lastPage = 2, tid = 2, type = "steve")
                },
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = BlessingSkinClient(
            "https://skin.test/",
            HttpClient(engine),
            json,
            pageDelayMillis = 0
        )

        val result = client.search(
            BlessingTextureSearch(keyword = "红&蓝"),
            page = 1
        ).getOrThrow()

        assertEquals(listOf(1, 2), result.items.map { it.id })
        assertEquals(BlessingTextureType.ALEX, result.items[0].type)
        assertEquals(BlessingTextureType.STEVE, result.items[1].type)
        assertEquals(2, result.nextPage)
        assertEquals("skin", requests[0].parameters["filter"])
        assertEquals("红&蓝", requests[0].parameters["keyword"])
        assertTrue(requests[0].encodedQuery.contains("%26"))
    }

    @Test
    fun `last provider page does not expose another page`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = pageJson(currentPage = 1, lastPage = 1, tid = 1, type = "cape"),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = BlessingSkinClient(
            "https://skin.test",
            HttpClient(engine),
            json,
            pageDelayMillis = 0
        )

        val result = client.search(BlessingTextureSearch(), 1).getOrThrow()

        assertNull(result.nextPage)
        assertEquals(BlessingTextureType.CAPE, result.items.single().type)
    }

    @Test
    fun `resolve maps alex as slim and creates texture url`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """{"tid":7,"name":"Alex","type":"alex","hash":"abc123","uploader":9,"public":true,"likes":4}""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = BlessingSkinClient("https://skin.test", HttpClient(engine), json, 0)

        val result = client.resolve(7).getOrThrow()

        assertEquals(BlessingTextureType.ALEX, result.type)
        assertEquals("https://skin.test/textures/abc123", result.textureUrl)
        assertEquals("https://skin.test/preview/7?height=150&png=", result.previewUrl)
    }

    @Test
    fun `non success response is returned as typed failure`() = runBlocking {
        val engine = MockEngine {
            respond("not found", HttpStatusCode.NotFound)
        }
        val client = BlessingSkinClient("https://skin.test", HttpClient(engine), json, 0)

        val failure = client.resolve(7).exceptionOrNull()

        val error = assertIs<BlessingSkinException.HttpFailure>(failure)
        assertEquals(404, error.statusCode)
        assertEquals("not found", error.responseBody)
    }

    @Test
    fun `unknown texture type is returned as invalid response`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """{"tid":7,"name":"Unknown","type":"elytra","hash":"abc123","uploader":9,"public":true,"likes":4}""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = BlessingSkinClient("https://skin.test", HttpClient(engine), json, 0)

        val failure = client.resolve(7).exceptionOrNull()

        assertIs<BlessingSkinException.InvalidResponse>(failure)
    }

    private fun pageJson(currentPage: Int, lastPage: Int, tid: Int, type: String): String =
        """{"current_page":$currentPage,"last_page":$lastPage,"data":[{"tid":$tid,"name":"texture-$tid","type":"$type","uploader":9,"public":true,"likes":4}]}"""
}
