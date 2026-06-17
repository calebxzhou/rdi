package calebxzhou.rdi.mc.server.tpa

import calebxzhou.rdi.mc.rcmd.tpa.TpaPlayer
import calebxzhou.rdi.mc.rcmd.tpa.TpaPlayerLookup
import net.minecraft.entity.Entity
import net.minecraft.server.MinecraftServer
import net.minecraft.world.World
import net.minecraftforge.common.util.ITeleporter
import java.util.UUID

class TpaPlayerLookup112(private val server: MinecraftServer) : TpaPlayerLookup {
    override fun findByName(name: String): TpaPlayer? =
        server.playerList.getPlayerByUsername(name)?.let(::TpaPlayer112)

    override fun findById(id: UUID): TpaPlayer? =
        server.playerList.getPlayerByUUID(id)?.let(::TpaPlayer112)

    override fun teleportTo(requester: TpaPlayer, target: TpaPlayer) {
        if (requester !is TpaPlayer112 || target !is TpaPlayer112) {
            return
        }
        val requesterPlayer = requester.unwrap()
        val targetPlayer = target.unwrap()
        if (requesterPlayer.dimension != targetPlayer.dimension) {
            requesterPlayer.server.playerList.transferPlayerToDimension(
                requesterPlayer,
                targetPlayer.dimension,
                ITeleporter { _: World, entity: Entity, _: Float ->
                    entity.setLocationAndAngles(
                        targetPlayer.posX,
                        targetPlayer.posY,
                        targetPlayer.posZ,
                        targetPlayer.rotationYaw,
                        targetPlayer.rotationPitch
                    )
                }
            )
        }
        requesterPlayer.connection.setPlayerLocation(
            targetPlayer.posX,
            targetPlayer.posY,
            targetPlayer.posZ,
            targetPlayer.rotationYaw,
            targetPlayer.rotationPitch
        )
    }
}
