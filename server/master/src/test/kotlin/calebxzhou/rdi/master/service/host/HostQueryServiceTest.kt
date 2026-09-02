package calebxzhou.rdi.master.service.host

import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.common.model.HostStatus
import calebxzhou.rdi.model.Role
import org.bson.types.ObjectId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostQueryServiceTest {
    private val requesterId = ObjectId("00112233445566778899aabb")
    private val otherPlayerId = ObjectId("aabbccddeeff001122334455")

    @Test
    fun `availability includes owners and members regardless of status`() {
        assertTrue(isPlayable(host(ownerId = requesterId), HostStatus.STOPPED))
        assertTrue(
            isPlayable(
                host(members = listOf(Host.Member(otherPlayerId, Role.MEMBER))),
                HostStatus.STOPPED,
                otherPlayerId,
            )
        )
        assertFalse(isPlayable(host(name = "公共房间"), HostStatus.STOPPED))
    }

    @Test
    fun `availability includes playable non-whitelist hosts only`() {
        assertTrue(isPlayable(host(), HostStatus.PLAYABLE))
        assertFalse(isPlayable(host(whitelist = true), HostStatus.PLAYABLE))
        assertFalse(isPlayable(host(name = "公共房间", whitelist = true), HostStatus.PLAYABLE))
        assertFalse(isPlayable(host(), HostStatus.STOPPED))
    }

    @Test
    fun `listing candidate carries the already resolved status`() {
        val candidate = candidate("running-room", playable = true)

        assertEquals(HostStatus.PLAYABLE, candidate.status)
    }

    @Test
    fun `my and list selections are mutually exclusive and list paginates after filtering`() {
        val candidates = listOf(
            candidate("available-1", playable = true),
            candidate("unavailable-1", playable = false),
            candidate("available-2", playable = true),
            candidate("unavailable-2", playable = false),
            candidate("unavailable-3", playable = false),
        )

        val available = HostQueryService.selectLegacyHosts(candidates, myOnly = true, page = 99, pageSize = 1)
        val unavailablePage0 = HostQueryService.selectLegacyHosts(candidates, myOnly = false, page = 0, pageSize = 2)
        val unavailablePage1 = HostQueryService.selectLegacyHosts(candidates, myOnly = false, page = 1, pageSize = 2)

        assertEquals(listOf("available-1", "available-2"), available.map { it.host.name })
        assertEquals(listOf("unavailable-1", "unavailable-2"), unavailablePage0.map { it.host.name })
        assertEquals(listOf("unavailable-3"), unavailablePage1.map { it.host.name })
        assertTrue(available.map { it.host._id }.intersect(unavailablePage0.map { it.host._id }.toSet()).isEmpty())
        assertEquals(
            candidates.map { it.host._id }.toSet(),
            (available + unavailablePage0 + unavailablePage1).map { it.host._id }.toSet(),
        )
    }

    private fun candidate(name: String, playable: Boolean) = HostQueryService.HostListingCandidate(
        host = host(name = name),
        status = if (playable) HostStatus.PLAYABLE else HostStatus.STOPPED,
        isMember = false,
        role = null,
        playable = playable,
    )

    private fun isPlayable(
        host: Host,
        status: HostStatus,
        requesterId: ObjectId = this.requesterId,
    ) = with(HostQueryService) { host.isPlayableFor(requesterId, status) }

    private fun host(
        name: String = "普通房间",
        ownerId: ObjectId = otherPlayerId,
        whitelist: Boolean = false,
        members: List<Host.Member> = emptyList(),
    ) = Host(
        name = name,
        ownerId = ownerId,
        modpackId = ObjectId("11223344556677889900aabb"),
        port = 50000,
        difficulty = 2,
        gameMode = 0,
        levelType = "default",
        whitelist = whitelist,
        members = members,
    )
}
