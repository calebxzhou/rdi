package calebxzhou.rdi.mc.server.network

import calebxzhou.rdi.mc.common.RGlobalPlayerList
import calebxzhou.rdi.mc.server.firmsection.FirmSectionService1710
import com.google.gson.Gson
import cpw.mods.fml.common.network.NetworkRegistry
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper
import cpw.mods.fml.relauncher.Side
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.server.dedicated.DedicatedServer
import kotlin.concurrent.Volatile

object RServerNetwork {
    val CHANNEL: SimpleNetworkWrapper = NetworkRegistry.INSTANCE.newSimpleChannel("rdi_gplist")
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

    @JvmStatic
    fun sendToAll(server: DedicatedServer, playerList: RGlobalPlayerList) {
        val packet = RGlobalPlayerListPacket(GSON.toJson(playerList))
        lastPacket = packet
        for (rawPlayer in server.getConfigurationManager().playerEntityList) {
            sendTo(rawPlayer, packet)
        }
    }

    @JvmStatic
    fun sendLastTo(player: EntityPlayerMP) {
        val packet = lastPacket
        if (packet != null) {
            sendTo(player, packet)
        }
    }

    @JvmStatic
    fun sendFirmSectionsToAll(server: DedicatedServer) {
        val packet = firmSectionsPacket(server)
        for (rawPlayer in server.getConfigurationManager().playerEntityList) {
            CHANNEL.sendTo(packet, rawPlayer as EntityPlayerMP)
        }
    }

    @JvmStatic
    fun sendFirmSectionsTo(player: EntityPlayerMP) {
        CHANNEL.sendTo(firmSectionsPacket(player.mcServer), player)
    }

    private fun sendTo(player: EntityPlayerMP, packet: RGlobalPlayerListPacket) {
        CHANNEL.sendTo(packet, player)
    }

    private fun firmSectionsPacket(server: net.minecraft.server.MinecraftServer): RFirmSectionsPacket =
        RFirmSectionsPacket(
            FirmSectionService1710.all(server).map {
                RFirmSectionsPacket.Entry(
                    dimensionId = it.dimensionId,
                    chunkX = it.chunkX,
                    sectionY = it.sectionY,
                    chunkZ = it.chunkZ,
                )
            }
        )
}
