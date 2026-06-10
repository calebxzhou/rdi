package calebxzhou.rdi.mc.server.network

import calebxzhou.rdi.mc.common2.player.RGlobalPlayerList
import com.google.gson.Gson
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.dedicated.DedicatedServer
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.network.NetworkDirection
import net.minecraftforge.network.NetworkRegistry
import net.minecraftforge.network.PacketDistributor
import net.minecraftforge.network.simple.SimpleChannel
import java.util.function.Supplier
import kotlin.concurrent.Volatile

object RServerNetwork {
    private const val PROTOCOL_VERSION = "1"
    private val GSON = Gson()

    @Volatile
    private var registered = false

    @Volatile
    private var lastPacket: RGlobalPlayerListPacket? = null

    private val CHANNEL: SimpleChannel = NetworkRegistry.newSimpleChannel(
        ResourceLocation.fromNamespaceAndPath("rdi", "global_player_list"),
        { PROTOCOL_VERSION },
        { anObject: String -> PROTOCOL_VERSION.equals(anObject) },
        { anObject: String -> PROTOCOL_VERSION.equals(anObject) }
    )

    fun register() {
        if (registered) {
            return
        }
        registered = true
        CHANNEL.messageBuilder(
            RGlobalPlayerListPacket::class.java,
            0,
            NetworkDirection.PLAY_TO_CLIENT
        )
            .encoder { obj: RGlobalPlayerListPacket, packet: FriendlyByteBuf ->
                RGlobalPlayerListPacket.encode(
                    obj,
                    packet
                )
            }
            .decoder { obj: FriendlyByteBuf -> RGlobalPlayerListPacket.decode(obj) }
            .consumerMainThread(RGlobalPlayerListPacket::handle)
            .add()
    }

    fun sendToAll(server: DedicatedServer, playerList: RGlobalPlayerList) {
        val packet = RGlobalPlayerListPacket(GSON.toJson(playerList))
        lastPacket = packet
        for (player in server.getPlayerList().getPlayers()) {
            sendTo(player, packet)
        }
    }

    fun sendLastTo(player: ServerPlayer) {
        val packet = lastPacket
        if (packet != null) {
            sendTo(player, packet)
        }
    }

    private fun sendTo(player: ServerPlayer, packet: RGlobalPlayerListPacket) {
        CHANNEL.send<RGlobalPlayerListPacket>(PacketDistributor.PLAYER.with(Supplier { player }), packet)
    }
}
