package calebxzhou.rdi.master.service.host

import calebxzhou.rdi.common.model.HostStatus
import calebxzhou.rdi.master.service.DockerService
import kotlin.test.Test
import kotlin.test.assertEquals

class HostStatusSnapshotTest {
    @Test
    fun `docker container states map to legacy host states`() {
        assertEquals(HostStatus.STARTED, DockerService.parseContainerStatus("running"))
        assertEquals(HostStatus.PAUSED, DockerService.parseContainerStatus("paused"))
        assertEquals(HostStatus.STOPPED, DockerService.parseContainerStatus("exited"))
        assertEquals(HostStatus.UNKNOWN, DockerService.parseContainerStatus(null))
        assertEquals(HostStatus.UNKNOWN, DockerService.parseContainerStatus("unexpected"))
    }

    @Test
    fun `resolved host state preserves debug and runtime session semantics`() {
        assertEquals(
            HostStatus.PLAYABLE,
            resolveHostStatus(HostStatus.STARTED, debug = false, hasSession = true),
        )
        assertEquals(
            HostStatus.PLAYABLE,
            resolveHostStatus(HostStatus.STOPPED, debug = true, hasSession = true),
        )
        assertEquals(
            HostStatus.STOPPED,
            resolveHostStatus(HostStatus.STOPPED, debug = true, hasSession = false),
        )
    }
}
