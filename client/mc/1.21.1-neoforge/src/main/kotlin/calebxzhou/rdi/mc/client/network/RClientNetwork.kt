package calebxzhou.rdi.mc.client.network

import calebxzhou.rdi.mc.common.RDI
import calebxzhou.rdi.mc.common.SectionPos
import calebxzhou.rdi.mc.common2.player.RGlobalPlayerList
import com.google.gson.Gson
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext
import net.neoforged.neoforge.network.handling.IPayloadHandler

@EventBusSubscriber(modid = "rdi", value = [Dist.CLIENT])
object RClientNetwork {
    private val GSON = Gson()

    @SubscribeEvent
    @JvmStatic
    fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        event.registrar("1")
            .optional()
            .playToClient(
                RGlobalPlayerListPayload.TYPE,
                RGlobalPlayerListPayload.STREAM_CODEC,
                IPayloadHandler { payload: RGlobalPlayerListPayload, _: IPayloadContext ->
                    val playerList = GSON.fromJson(payload.json, RGlobalPlayerList::class.java) ?: return@IPayloadHandler
                    GlobalPlayerListState.update(playerList)
                })
            .playToClient(
                RFirmSectionsPayload.TYPE,
                RFirmSectionsPayload.STREAM_CODEC
            ) { payload: RFirmSectionsPayload, _: IPayloadContext ->
                updateFirmSections(payload)
            }
    }

    private fun updateFirmSections(payload: RFirmSectionsPayload) {
        RDI.FIRM_CHUNKS = payload.entries.groupBy(
            keySelector = { it.dimensionId },
            valueTransform = { SectionPos(it.chunkX, it.sectionY, it.chunkZ) }
        )
    }
}
