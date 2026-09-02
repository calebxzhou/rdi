package calebxzau.rdi.common.model

import calebxzhou.rdi.common.model.Response
import calebxzhou.rdi.common.serdesJson
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class Modpack2PageSerializationTest {
    @Test
    fun ownedPageRoundTripsInsideResponse() {
        val modpackId = UUID.randomUUID()
        val versionId = UUID.randomUUID()
        val response = Response(
            code = 0,
            msg = "ok",
            data = Modpack2Page(
                items = listOf(
                    Modpack2OwnedVo(
                        id = modpackId,
                        name = "Pack",
                        intro = "Intro",
                        mc = 20,
                        loader = Modpack2Loader.Forge,
                        iconUrl = "https://example.com/icon.png",
                        categories = listOf(ModpackCategory.Other, ModpackCategory.Adventure),
                        currentVersionId = versionId,
                        currentVersionName = "1.0.0",
                        currentVersionStatus = Modpack2VersionStatus.Ok,
                    ),
                ),
                total = 1,
                offset = 0,
                limit = 50,
                hasMore = false,
            ),
        )
        val encoded = serdesJson.encodeToString<Response<Modpack2Page<Modpack2OwnedVo>>>(response)
        assertEquals(response, serdesJson.decodeFromString<Response<Modpack2Page<Modpack2OwnedVo>>>(encoded))
    }
}
