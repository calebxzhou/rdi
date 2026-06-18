package calebxzhou.rdi.mc.client

import calebxzhou.rdi.mc.client.network.RClientNetwork
import calebxzhou.rdi.mc.common.RDI
import com.google.common.net.HostAndPort
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.network.chat.Component
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.ClientPlayerNetworkEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import org.apache.logging.log4j.LogManager

/**
 * calebxzhou @ 2026-01-06 19:19
 */
@Mod("rdi")
@Mod.EventBusSubscriber(modid = "rdi", value = [Dist.CLIENT])
class RDIMain {
    init {
        RClientNetwork.register()
        LogManager.getLogger("rdi").info("❄❄❄❄❄❄❄❄RDI客户端核心模块已加载❄❄❄❄❄❄❄❄")
    }

    companion object {
        @JvmField
        var JOIN_BUTTON: Button =
            Button.builder(Component.literal("进入地图 · ${RDI.HOST_NAME}"), Button.OnPress {
                val hp = HostAndPort.fromString(RDI.GAME_IP)
                ConnectScreen.startConnecting(
                    TitleScreen(),
                    Minecraft.getInstance(),
                    ServerAddress(hp.host, hp.port),
                    ServerData("rdi", RDI.GAME_IP, false),
                    false
                )
            }).bounds(100, 0, 200, 50).build()

        @JvmStatic
        fun layoutJoinButton(screenWidth: Int) {
            JOIN_BUTTON.x = screenWidth / 2 - 100
            JOIN_BUTTON.y = 0
            JOIN_BUTTON.width = 200
            JOIN_BUTTON.height = 20
        }
        @SubscribeEvent
        @JvmStatic
        fun onClientJoinServer(event: ClientPlayerNetworkEvent.LoggingIn) {
            Minecraft.getInstance().gui.apply {
                setTimes(10, 200, 20)
                setSubtitle(Component.literal("设定“持久子区块” 否则丢数据 见说明书"))
                setTitle(Component.empty())
            }
        }

    }
}
