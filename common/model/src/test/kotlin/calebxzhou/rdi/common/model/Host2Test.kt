package calebxzhou.rdi.common.model

import calebxzhou.rdi.common.serdesJson
import calebxzhou.rdi.model.Role
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class Host2Test {
    @Test
    fun `detail round trips UUID fields`() {
        val hostId = UUID.fromString("019c9c18-778d-7000-8000-000000000001")
        val ownerId = UUID.fromString("01234567-89ab-cdef-0123-456700000000")
        val detail = Host2.DetailVo(
            id = hostId,
            name = "新版房间",
            iconUrl = "https://cdn.modrinth.com/data/example/icon.png",
            ownerId = ownerId,
            mcVersion = McVersion.V211,
            modLoader = ModLoader.neoforge,
            port = 30000,
            whitelist = true,
            setupStatus = Host2SetupStatus.READY,
            status = HostStatus.STOPPED,
            role = Role.OWNER,
            members = listOf(Host2.Member(ownerId, Host2MemberRole.ADMIN))
        )

        assertEquals(detail, serdesJson.decodeFromString<Host2.DetailVo>(serdesJson.encodeToString(detail)))
    }

    @Test
    fun `server pack manifest does not require download URL`() {
        val dto = Host2.ServerPackUploadDto(
            listOf(Mod("mr", "project", "slug", "file", "0123456789abcdef", Mod.Side.BOTH))
        )

        val decoded = serdesJson.decodeFromString<Host2.ServerPackUploadDto>(serdesJson.encodeToString(dto))
        assertEquals(emptyList(), decoded.mods.single().downloadUrls)
    }
}
