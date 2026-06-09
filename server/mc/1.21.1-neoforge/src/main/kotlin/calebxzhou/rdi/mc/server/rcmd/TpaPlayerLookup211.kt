package calebxzhou.rdi.mc.server.rcmd

import calebxzhou.rdi.mc.rcmd.tpa.TpaPlayer
import calebxzhou.rdi.mc.rcmd.tpa.TpaPlayerLookup
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.*

class TpaPlayerLookup211(private val server: MinecraftServer) : TpaPlayerLookup {

    public override fun findByName(name: String): TpaPlayer? {
        val player: ServerPlayer? = server.getPlayerList().getPlayerByName(name)
        return if (player == null) null else TpaPlayer211(player)
    }

    public override fun findById(id: UUID): TpaPlayer? {
        val player: ServerPlayer? = server.getPlayerList().getPlayer(id)
        return if (player == null) null else TpaPlayer211(player)
    }

    public override fun teleportTo(requester: TpaPlayer, target: TpaPlayer) {
        val requesterPlayer: ServerPlayer = (requester as TpaPlayer211).unwrap()
        val targetPlayer: ServerPlayer = (target as TpaPlayer211).unwrap()
        requesterPlayer.teleportTo(
            targetPlayer.serverLevel(),
            targetPlayer.getX(),
            targetPlayer.getY(),
            targetPlayer.getZ(),
            targetPlayer.getYRot(),
            targetPlayer.getXRot()
        )
    }
}
