package calebxzhou.rdi.mc.server.tpa

import calebxzhou.rdi.mc.common2.tpa.TpaPlayer
import java.util.UUID

class TpaPlayerLookup201(server: MinecraftServer) : TpaPlayerLookup {
    private val server: MinecraftServer

    init {
        this.server = server
    }

    public override fun findByName(name: String): TpaPlayer? {
        val player: ServerPlayer? = server.getPlayerList().getPlayerByName(name)
        return if (player == null) null else TpaPlayer201(player)
    }

    public override fun findById(id: UUID): TpaPlayer? {
        val player: ServerPlayer? = server.getPlayerList().getPlayer(id)
        return if (player == null) null else TpaPlayer201(player)
    }

    public override fun teleportTo(requester: TpaPlayer, target: TpaPlayer) {
        val requesterPlayer: ServerPlayer = (requester as TpaPlayer201).unwrap()
        val targetPlayer: ServerPlayer = (target as TpaPlayer201).unwrap()
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
