package calebxzhou.rdi.mc.rcmd.tpa

import java.util.*

interface TpaPlayer {
    fun id(): UUID

    fun name(): String

    fun sendMessage(message: String)
}
