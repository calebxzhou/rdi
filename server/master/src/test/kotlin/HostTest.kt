import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.master.service.host.HostContext
import calebxzhou.rdi.model.Role
import org.bson.types.ObjectId

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
}
