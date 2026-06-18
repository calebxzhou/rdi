package calebxzhou.rdi.mc.server.network

import calebxzhou.rdi.mc.common.RGlobalPlayerList
import calebxzhou.rdi.mc.server.firmsection.FirmSectionService.all
import com.google.gson.Gson
import net.minecraft.server.MinecraftServer
import net.minecraft.server.dedicated.DedicatedServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext
import net.neoforged.neoforge.network.handling.IPayloadHandler
import kotlin.concurrent.Volatile

@EventBusSubscriber(modid = "rdi")
object RServerNetwork {
    private val GSON = Gson()

    @Volatile
    private var lastPayload: RGlobalPlayerListPayload? = null
@JvmStatic
    @SubscribeEvent
    fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        event.registrar("1")
            .optional()
            .playToClient(
                RGlobalPlayerListPayload.TYPE,
                RGlobalPlayerListPayload.STREAM_CODEC,
                IPayloadHandler { _: RGlobalPlayerListPayload, _: IPayloadContext -> })
            .playToClient(
                RFirmSectionsPayload.TYPE,
                RFirmSectionsPayload.STREAM_CODEC,
                IPayloadHandler { _: RFirmSectionsPayload, _: IPayloadContext -> })
    }

    fun sendToAll(server: DedicatedServer, playerList: RGlobalPlayerList?) {
        val payload = RGlobalPlayerListPayload(GSON.toJson(playerList))
        lastPayload = payload
        for (player in server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload)
        }
    }

    fun sendLastTo(player: ServerPlayer) {
        lastPayload?.let { PacketDistributor.sendToPlayer(player, it) }
    }

    fun sendFirmSectionsToAll(server: MinecraftServer) {
        val payload = firmSectionsPayload(server)
        for (player in server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload)
        }
    }

    fun sendFirmSectionsTo(player: ServerPlayer) {
        PacketDistributor.sendToPlayer(player, firmSectionsPayload(player.server))
    }

    private fun firmSectionsPayload(server: MinecraftServer): RFirmSectionsPayload {
        val entries = all(server).map { section ->
            RFirmSectionsPayload.Entry(
                section.dimensionId,
                section.chunkX,
                section.sectionY,
                section.chunkZ
            )
        }
        return RFirmSectionsPayload(entries)
    }
}
