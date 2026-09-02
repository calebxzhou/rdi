package calebxzhou.rdi.common.model

import calebxzhou.rdi.common.serdesJson
import calebxzau.rdi.common.model.ClientContentVo
import calebxzau.rdi.common.model.ContentOrigin
import calebxzau.rdi.common.model.ContentPlatform
import calebxzau.rdi.common.model.ContentSide
import calebxzau.rdi.common.model.ContentType
import calebxzau.rdi.common.model.ContentVo
import calebxzhou.rdi.model.Role
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Host2Test {
    @Test
    fun `host2 availability requires ready pack known status and membership for whitelist`() {
        assertTrue(isHost2Available(Host2PackStatus.Ok, HostStatus.STOPPED, false, false))
        assertTrue(isHost2Available(Host2PackStatus.Ok, HostStatus.PLAYABLE, true, true))
        assertTrue(isHost2Available(Host2PackStatus.Ok, HostStatus.PLAYABLE, false, false))
        assertFalse(isHost2Available(Host2PackStatus.Busy, HostStatus.PLAYABLE, false, false))
        assertFalse(isHost2Available(Host2PackStatus.Ok, HostStatus.UNKNOWN, false, false))
        assertFalse(isHost2Available(Host2PackStatus.Ok, HostStatus.PLAYABLE, true, false))
    }

    @Test
    fun `pack sources use stable discriminators and round trip`() {
        val modpack2 = PackSource.Modpack2(
            UUID.fromString("019c9c18-778d-7000-8000-000000000001")
        )
        val modpack2Json = serdesJson.encodeToString<PackSource>(modpack2)

        assertTrue(modpack2Json.contains("\"type\":\"modpack2\""))
        assertEquals(modpack2, serdesJson.decodeFromString<PackSource>(modpack2Json))
    }

    @Test
    fun `legacy pack source discriminator is rejected`() {
        assertFailsWith<Throwable> {
            serdesJson.decodeFromString<PackSource>(
                "{\"type\":\"legacy\",\"modpackId\":\"0123456789abcdef01234567\",\"versionName\":\"v1.2.3\"}"
            )
        }
    }

    @Test
    fun `host detail round trips the pack status and content revisions`() {
        val hostId = UUID.fromString("019c9c18-778d-7000-8000-000000000002")
        val ownerId = UUID.fromString("01234567-89ab-cdef-0123-456700000000")
        val detail = Host2.DetailVo(
            id = hostId,
            name = "新版房间",
            iconUrl = "https://cdn.modrinth.com/data/example/icon.png",
            ownerId = ownerId,
            packSource = PackSource.Modpack2(
                UUID.fromString("019c9c18-778d-7000-8000-000000000003")
            ),
            packStatus = Host2PackStatus.Ok,
            activeContentRevision = 3,
            pendingContentRevision = 4,
            pack = Host2PackInfo(
                name = "测试整合包",
                versionName = "1.0.0",
                mcVersion = McVersion.V211,
                modLoader = ModLoader.neoforge,
                modpackId = UUID.fromString("019c9c18-778d-7000-8000-000000000004"),
            ),
            port = 30000,
            whitelist = true,
            status = HostStatus.STOPPED,
            role = Role.OWNER,
        )

        val encoded = serdesJson.encodeToString(detail)
        assertTrue(encoded.contains("\"packStatus\":\"Ok\""))
        assertTrue(encoded.contains("\"activeContentRevision\":3"))
        assertTrue(encoded.contains("\"pendingContentRevision\":4"))
        assertTrue(encoded.contains("\"modpackId\":\"019c9c18-778d-7000-8000-000000000004\""))
        assertEquals(detail, serdesJson.decodeFromString<Host2.DetailVo>(encoded))
    }

    @Test
    fun `management contents retain enabled while client manifest omits it`() {
        val managedContent = ContentVo(
            origin = ContentOrigin.Extra,
            platform = ContentPlatform.Modrinth,
            type = ContentType.Mod,
            projectId = "project",
            fileId = "file",
            slug = "example-mod",
            hash = "0123456789abcdef",
            side = ContentSide.Client,
            fileSize = 42,
            enabled = false,
        )
        val contents = Host2.ContentsVo(
            activeRevision = 7,
            pendingRevision = 8,
            active = listOf(managedContent),
            pending = listOf(managedContent),
        )
        val contentsEncoded = serdesJson.encodeToString(contents)
        assertTrue(contentsEncoded.contains("\"enabled\":false"))
        assertEquals(contents, serdesJson.decodeFromString<Host2.ContentsVo>(contentsEncoded))

        val content = ClientContentVo(
            origin = ContentOrigin.Extra,
            platform = ContentPlatform.Modrinth,
            type = ContentType.Mod,
            projectId = "project",
            fileId = "file",
            slug = "example-mod",
            hash = "0123456789abcdef",
            side = ContentSide.Client,
            fileSize = 42,
        )
        val manifest = Host2.ClientManifest(
            packSource = PackSource.Modpack2(UUID.fromString("019c9c18-778d-7000-8000-000000000003")),
            activeContentRevision = 7,
            mcVersion = McVersion.V211,
            modLoader = ModLoader.neoforge,
            contents = listOf(content),
        )

        val encoded = serdesJson.encodeToString(manifest)
        assertFalse(encoded.contains("\"enabled\""))
        assertEquals(manifest, serdesJson.decodeFromString<Host2.ClientManifest>(encoded))
    }

    @Test
    fun `response round trips error code and current revision`() {
        val response = Response<String>(
            code = -1,
            msg = "revision conflict",
            errorCode = Host2ErrorCode.REVISION_CONFLICT,
            currentRevision = 12,
        )

        val encoded = serdesJson.encodeToString(response)
        assertTrue(encoded.contains("\"errorCode\":\"revision_conflict\""))
        assertTrue(encoded.contains("\"currentRevision\":12"))
        assertEquals(response, serdesJson.decodeFromString<Response<String>>(encoded))
    }
}
