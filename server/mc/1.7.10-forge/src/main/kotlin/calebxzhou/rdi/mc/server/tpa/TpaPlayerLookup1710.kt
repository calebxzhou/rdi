package calebxzhou.rdi.mc.server.tpa

import calebxzhou.rdi.mc.rcmd.tpa.TpaPlayer
import calebxzhou.rdi.mc.rcmd.tpa.TpaPlayerLookup
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.server.dedicated.DedicatedServer
import java.util.*

class TpaPlayerLookup1710(private val server: DedicatedServer) : TpaPlayerLookup {
    override fun findByName(name: String): TpaPlayer? {
        val player = server.configurationManager.func_152612_a(name)
        return if (player == null) null else TpaPlayer1710(player)
    }

    override fun findById(id: UUID): TpaPlayer? {
        for (onlinePlayer in server.configurationManager.playerEntityList) {
            if (onlinePlayer is EntityPlayerMP && onlinePlayer.getUniqueID() == id) {
                return TpaPlayer1710(onlinePlayer)
            }
        }
        return null
    }

    override fun teleportTo(requester: TpaPlayer, target: TpaPlayer) {
        val requesterPlayer = (requester as TpaPlayer1710).unwrap()
        val targetPlayer = (target as TpaPlayer1710).unwrap()
        if (requesterPlayer.dimension != targetPlayer.dimension) {
            server.getConfigurationManager().transferPlayerToDimension(requesterPlayer, targetPlayer.dimension)
        }
        requesterPlayer.fallDistance = 0.0f
        requesterPlayer.playerNetServerHandler.setPlayerLocation(
            targetPlayer.posX,
            targetPlayer.posY,
            targetPlayer.posZ,
            targetPlayer.rotationYaw,
            targetPlayer.rotationPitch
        )
    }
}
