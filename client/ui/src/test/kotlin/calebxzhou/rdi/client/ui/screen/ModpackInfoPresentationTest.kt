package calebxzhou.rdi.client.ui.screen

import calebxzhou.rdi.common.model.Modpack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.bson.types.ObjectId

class ModpackInfoPresentationTest {
    @Test
    fun `author sees every version in original order`() {
        val versions = versions(
            version("building", Modpack.Status.BUILDING),
            version("ok", Modpack.Status.OK),
            version("failed", Modpack.Status.FAIL),
        )

        assertEquals(versions, visibleModpackVersions(versions, isAuthor = true, playerId = ObjectId()))
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
            visibleModpackVersions(versions, isAuthor = false, playerId = ObjectId()),
        )
    }

    @Test
    fun `non-author with no available versions gets an empty result`() {
        val versions = versions(
            version("waiting", Modpack.Status.WAIT),
            version("failed", Modpack.Status.FAIL),
        )

        assertEquals(emptyList(), visibleModpackVersions(versions, isAuthor = false, playerId = ObjectId()))
    }

    @Test
    fun `uploader sees their own non-ok version but not another uploader version`() {
        val uploaderId = ObjectId()
        val otherUploaderId = ObjectId()
        val versions = versions(
            version("own-failed", Modpack.Status.FAIL, uploaderId),
            version("other-failed", Modpack.Status.FAIL, otherUploaderId),
            version("public", Modpack.Status.OK, otherUploaderId),
        )

        assertEquals(
            listOf(versions[0], versions[2]),
            visibleModpackVersions(versions, isAuthor = false, playerId = uploaderId),
        )
    }

    @Test
    fun `legacy version without uploader is not visible as uploader owned`() {
        val playerId = ObjectId()
        val versions = versions(
            version("legacy-failed", Modpack.Status.FAIL),
            version("public", Modpack.Status.OK),
        )

        assertEquals(
            listOf(versions[1]),
            visibleModpackVersions(versions, isAuthor = false, playerId = playerId),
        )
    }

    @Test
    fun `version management permission is exact to uploader author and dav`() {
        val authorId = ObjectId()
        val uploaderId = ObjectId()
        val otherId = ObjectId()
        val pack = Modpack.DetailVo(
            _id = ObjectId(MODPACK_ID),
            name = "Pack",
            authorId = authorId,
            modCount = 0,
            modloader = calebxzhou.rdi.common.model.ModLoader.neoforge,
            mcVer = calebxzhou.rdi.common.model.McVersion.V211,
        )
        val version = version("target", Modpack.Status.FAIL, uploaderId)

        assertTrue(canManageModpackVersion(pack, version, uploaderId, isDav = false))
        assertTrue(canManageModpackVersion(pack, version, authorId, isDav = false))
        assertTrue(canManageModpackVersion(pack, version, otherId, isDav = true))
        assertFalse(canManageModpackVersion(pack, version, otherId, isDav = false))
    }

    private companion object {
        const val MODPACK_ID = "66a000000000000000000001"

        fun versions(vararg versions: Modpack.Version): List<Modpack.Version> = versions.toList()

        fun version(name: String, status: Modpack.Status, uploaderId: ObjectId? = null) = Modpack.Version(
            time = 1L,
            modpackId = ObjectId(MODPACK_ID),
            name = name,
            changelog = "",
            status = status,
            uploaderId = uploaderId,
        )
    }
}
