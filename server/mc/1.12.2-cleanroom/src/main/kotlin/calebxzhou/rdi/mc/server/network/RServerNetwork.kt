package calebxzhou.rdi.mc.server.network

import calebxzhou.rdi.mc.common.RGlobalPlayerList
import calebxzhou.rdi.mc.server.firmsection.FirmSectionService112
import com.google.gson.Gson
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.server.MinecraftServer
import net.minecraft.server.dedicated.DedicatedServer
import net.minecraftforge.fml.common.network.NetworkRegistry
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper
import net.minecraftforge.fml.relauncher.Side
import kotlin.concurrent.Volatile

object RServerNetwork {
    val CHANNEL: SimpleNetworkWrapper = NetworkRegistry.INSTANCE.newSimpleChannel("rdi_gplayers")
    private val GSON = Gson()

    @Volatile
    private var registered = false

    @Volatile
    private var lastPacket: RGlobalPlayerListPacket? = null

    @JvmStatic
    fun register() {
        if (registered) {
            return
        }
        registered = true
        CHANNEL.registerMessage(
            RGlobalPlayerListPacket.Handler::class.java,
            RGlobalPlayerListPacket::class.java,
            0,
            Side.CLIENT
        )
        CHANNEL.registerMessage(
            RFirmSectionsPacket.Handler::class.java,
            RFirmSectionsPacket::class.java,
            1,
            Side.CLIENT
        )
    }

    fun sendToAll(server: DedicatedServer, playerList: RGlobalPlayerList?) {
        val packet = RGlobalPlayerListPacket(GSON.toJson(playerList))
        lastPacket = packet
        for (player in server.getPlayerList().getPlayers()) {
            sendTo(player, packet)
        }
    }

    @JvmStatic
    fun sendLastTo(player: EntityPlayerMP?) {
        val packet = lastPacket
        if (packet != null) {
            sendTo(player, packet)
        }
    }

    fun sendFirmSectionsToAll(server: MinecraftServer) {
        val packet = firmSectionsPacket(server)
        server.playerList.players.forEach { CHANNEL.sendTo(packet, it) }
    }

    fun sendFirmSectionsTo(player: EntityPlayerMP) {
        CHANNEL.sendTo(firmSectionsPacket(player.server), player)
    }

    private fun firmSectionsPacket(server: MinecraftServer) =
        RFirmSectionsPacket(FirmSectionService112.all(server).map {
            RFirmSectionsPacket.Entry(it.dimensionId, it.chunkX, it.sectionY, it.chunkZ)
        })

    private fun sendTo(player: EntityPlayerMP?, packet: RGlobalPlayerListPacket?) {
        CHANNEL.sendTo(packet, player)
    }
}
