package calebxzhou.rdi.master.service.host2

import calebxzhou.rdi.common.model.Host2PackStatus
import calebxzhou.rdi.common.model.HostStatus
import calebxzhou.rdi.common.model.PackSource
import calebxzhou.rdi.model.Role
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Host2ListingTest {
    @Test
    fun `my and list candidates are mutually exclusive and list paginates after filtering`() {
        val candidates = listOf(
            candidate("available-1", available = true),
            candidate("unavailable-1", available = false),
            candidate("available-2", available = true),
            candidate("unavailable-2", available = false),
            candidate("unavailable-3", available = false),
        )

        val available = selectHost2Candidates(candidates, myOnly = true, page = 99, pageSize = 1)
        val unavailablePage0 = selectHost2Candidates(candidates, myOnly = false, page = 0, pageSize = 2)
        val unavailablePage1 = selectHost2Candidates(candidates, myOnly = false, page = 1, pageSize = 2)

        assertEquals(listOf("available-1", "available-2"), available.map { it.record.name })
        assertEquals(listOf("unavailable-1", "unavailable-2"), unavailablePage0.map { it.record.name })
        assertEquals(listOf("unavailable-3"), unavailablePage1.map { it.record.name })
        assertTrue(available.map { it.record.id }.intersect(unavailablePage0.map { it.record.id }.toSet()).isEmpty())
        assertEquals(
            candidates.map { it.record.id }.toSet(),
            (available + unavailablePage0 + unavailablePage1).map { it.record.id }.toSet(),
        )
    }

    @Test
    fun `runtime snapshot conversion preserves session playability`() {
        assertEquals(HostStatus.PLAYABLE, Host2RuntimeService.resolveHost2Status(HostStatus.STARTED, true))
        assertEquals(HostStatus.STARTED, Host2RuntimeService.resolveHost2Status(HostStatus.STARTED, false))
        assertEquals(HostStatus.STOPPED, Host2RuntimeService.resolveHost2Status(HostStatus.STOPPED, true))
    }

    private fun candidate(name: String, available: Boolean) = Host2ListingCandidate(
        record = record(name),
        role = Role.GUEST,
        status = if (available) HostStatus.PLAYABLE else HostStatus.STOPPED,
        available = available,
    )

    private fun record(name: String) = Host2Record(
        id = UUID.nameUUIDFromBytes(name.toByteArray()),
        name = name,
        intro = "",
        iconUrl = null,
        ownerId = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef"),
        packSource = PackSource.Modpack2(UUID.fromString("019c9c18-778d-7000-8000-000000000003")),
        packStatus = Host2PackStatus.Ok,
        activeContentRevision = 1,
        pendingContentRevision = null,
        port = 25565,
        whitelist = false,
    )
}
