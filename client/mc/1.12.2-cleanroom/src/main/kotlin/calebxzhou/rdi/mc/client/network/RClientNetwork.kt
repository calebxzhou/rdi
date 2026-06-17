package calebxzhou.rdi.mc.client.network

import net.minecraftforge.fml.common.network.NetworkRegistry
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper
import net.minecraftforge.fml.relauncher.Side
import kotlin.concurrent.Volatile

object RClientNetwork {
    val CHANNEL: SimpleNetworkWrapper = NetworkRegistry.INSTANCE.newSimpleChannel("rdi_gplayers")

    @Volatile
    private var registered = false

    @JvmStatic
    fun register() {
        if (registered) {
            return
        }
        registered = true
        CHANNEL.registerMessage<RGlobalPlayerListPacket, IMessage?>(
            RGlobalPlayerListPacket.Handler::class.java,
            RGlobalPlayerListPacket::class.java,
            0,
            Side.CLIENT
        )
        CHANNEL.registerMessage<RFirmSectionsPacket, IMessage?>(
            RFirmSectionsPacket.Handler::class.java,
            RFirmSectionsPacket::class.java,
            1,
            Side.CLIENT
        )
    }
}
