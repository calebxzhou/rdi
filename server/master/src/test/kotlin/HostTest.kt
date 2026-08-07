import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.ModLoader
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.master.service.host.HostContext
import calebxzhou.rdi.master.service.host.HostInstallService
import calebxzhou.rdi.model.Role
import org.bson.types.ObjectId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HostTest {

    private fun testHost(): Host = Host(
        _id = ObjectId("696312b0e61232912c744968"),
        name = "TestHost",
        ownerId = ObjectId(),
        modpackId = ObjectId(),
        port = 25565,
        difficulty = 1,
        gameMode = 0,
        levelType = "default",
        members = emptyList()
    )

    private fun testContext(host: Host): HostContext {
        val player = RAccount(ObjectId(), "tester", "pwd", "12345")
        val member = Host.Member(player._id, Role.ADMIN)
        return HostContext(host, player, member, null)
    }

    @Test
    fun resolveHostCreateVersion_usesLastVersionForLatest() {
        val modpackId = ObjectId()
        val first = Modpack.Version(
            time = 1L,
            modpackId = modpackId,
            name = "1.0",
            changelog = "",
            status = Modpack.Status.OK
        )
        val latest = Modpack.Version(
            time = 2L,
            modpackId = modpackId,
            name = "2.0",
            changelog = "",
            status = Modpack.Status.OK
        )
        val modpack = Modpack(
            _id = modpackId,
            name = "test",
            authorId = ObjectId(),
            modloader = ModLoader.neoforge,
            mcVer = McVersion.V211,
            versions = mutableListOf(first, latest)
        )

        assertEquals(
            "2.0",
            HostInstallService.resolveHostCreateVersion(modpack, "latest").name
        )
    }

    @Test
    fun resolveHostCreateVersion_keepsExactVersionCompatibility() {
        val modpackId = ObjectId()
        val version = Modpack.Version(
            time = 1L,
            modpackId = modpackId,
            name = "1.0",
            changelog = "",
            status = Modpack.Status.OK
        )
        val modpack = Modpack(
            _id = modpackId,
            name = "test",
            authorId = ObjectId(),
            modloader = ModLoader.neoforge,
            mcVer = McVersion.V211,
            versions = mutableListOf(version)
        )

        assertEquals(
            "1.0",
            HostInstallService.resolveHostCreateVersion(modpack, "1.0").name
        )
    }

    @Test
    fun resolveHostCreateVersion_rejectsEmptyLatestPack() {
        val modpack = Modpack(
            name = "test",
            authorId = ObjectId(),
            modloader = ModLoader.neoforge,
            mcVer = McVersion.V211
        )

        assertFailsWith<RequestError> {
            HostInstallService.resolveHostCreateVersion(modpack, "latest")
        }
    }
}
