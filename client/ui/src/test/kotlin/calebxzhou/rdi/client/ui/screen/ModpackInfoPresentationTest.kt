package calebxzhou.rdi.client.ui.screen

import calebxzhou.rdi.common.model.Modpack
import kotlin.test.Test
import kotlin.test.assertEquals
import org.bson.types.ObjectId

class ModpackInfoPresentationTest {
    @Test
    fun `author sees every version in original order`() {
        val versions = versions(
            version("building", Modpack.Status.BUILDING),
            version("ok", Modpack.Status.OK),
            version("failed", Modpack.Status.FAIL),
        )

        assertEquals(versions, visibleModpackVersions(versions, isAuthor = true))
    }

    @Test
    fun `non-author sees only available versions in original order`() {
        val versions = versions(
            version("waiting", Modpack.Status.WAIT),
            version("ok-1", Modpack.Status.OK),
            version("failed", Modpack.Status.FAIL),
            version("ok-2", Modpack.Status.OK),
        )

        assertEquals(
            listOf(versions[1], versions[3]),
            visibleModpackVersions(versions, isAuthor = false),
        )
    }

    @Test
    fun `non-author with no available versions gets an empty result`() {
        val versions = versions(
            version("waiting", Modpack.Status.WAIT),
            version("failed", Modpack.Status.FAIL),
        )

        assertEquals(emptyList(), visibleModpackVersions(versions, isAuthor = false))
    }

    private companion object {
        const val MODPACK_ID = "66a000000000000000000001"

        fun versions(vararg versions: Modpack.Version): List<Modpack.Version> = versions.toList()

        fun version(name: String, status: Modpack.Status) = Modpack.Version(
            time = 1L,
            modpackId = ObjectId(MODPACK_ID),
            name = name,
            changelog = "",
            status = status,
        )
    }
}
