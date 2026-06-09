package calebxzhou.rdi.mc.server.rcmd

import calebxzhou.rdi.mc.rcmd.tpa.TpaPlayer
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

class TpaPlayer211(private val player: ServerPlayer) : TpaPlayer {

    fun unwrap(): ServerPlayer {
        return player
    }

    public override fun id(): UUID {
        return player.getUUID()
    }

    public override fun name(): String {
        return player.getGameProfile().getName()
    }

    public override fun sendMessage(message: String) {
        player.sendSystemMessage(Component.literal(message))
    }
}
