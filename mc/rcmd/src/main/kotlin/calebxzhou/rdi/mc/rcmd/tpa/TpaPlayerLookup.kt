package calebxzhou.rdi.mc.rcmd.tpa

import java.util.*

interface TpaPlayerLookup {
    fun findByName(name: String): TpaPlayer?

    fun findById(id: UUID): TpaPlayer?

    fun teleportTo(requester: TpaPlayer, target: TpaPlayer)
}
