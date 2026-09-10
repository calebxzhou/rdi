package calebxzhou.rdi.master.service

import com.mongodb.kotlin.client.coroutine.FindFlow
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.DistinctFlow
import calebxzhou.rdi.common.serdesJson
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.testing.testApplication
import io.ktor.server.routing.routing
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.FlowCollector
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.bson.conversions.Bson
import org.bson.types.ObjectId

class ModpackRoutesTest {
    private lateinit var collection: MongoCollection<calebxzhou.rdi.common.model.Modpack>

    @BeforeTest
    fun setup() {
        collection = mockk(relaxed = true)
        ModpackService.testDbcl = collection
        val flow = mockk<FindFlow<calebxzhou.rdi.common.model.Modpack>>()
        every { collection.find(any<Bson>()) } returns flow
        coEvery { flow.collect(any()) } coAnswers {
            // Public list/search routes are exercised with no records; this
            // keeps the harness independent of PlayerService/Mongo startup.
        }
        val distinctFlow = mockk<DistinctFlow<ObjectId>>()
        every { collection.distinct<ObjectId>(any(), any<Bson>()) } returns distinctFlow
        coEvery { distinctFlow.collect(any()) } coAnswers { }
    }

    @AfterTest
    fun cleanup() {
        ModpackService.testDbcl = null
    }

    @Test
    fun `public modpack list route returns response envelope`() = testApplication {
        application { routing { modpackRoutes() } }
        val response = client.get("/modpack")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":0"))
    }

    @Test
    fun `public search route returns empty response for unknown name`() = testApplication {
        application { routing { modpackRoutes() } }
        val response = client.get("/modpack/search/no-such-pack")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"data\":[]"))
    }

    @Test
    fun `missing route returns requested missing ids in response envelope`() = testApplication {
        application {
            install(ContentNegotiation) { json(serdesJson) }
            routing { modpackRoutes() }
        }
        val missingId = ObjectId()
        val response = client.post("/modpack/missing") {
            contentType(ContentType.Application.Json)
            setBody("[\"${missingId}\"]")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"data\":[\"${missingId}\"]"))
    }

    @Test
    fun `legacy new modpack multipart upload route is unavailable`() = testApplication {
        application { routing { modpackRoutes() } }

        val response = client.post("/modpack")

        assertTrue(
            response.status == HttpStatusCode.NotFound ||
                response.status == HttpStatusCode.MethodNotAllowed
        )
    }

    @Test
    fun `legacy new version multipart upload route is unavailable`() = testApplication {
        application { routing { modpackRoutes() } }
        val modpackId = ObjectId()

        val response = client.post("/modpack/$modpackId/version/1.0")

        assertTrue(
            response.status == HttpStatusCode.NotFound ||
                response.status == HttpStatusCode.MethodNotAllowed
        )
    }
}
