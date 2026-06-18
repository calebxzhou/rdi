package calebxzhou.rdi.mc.client

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.TickEvent
import cpw.mods.fml.common.network.FMLNetworkEvent
import net.minecraft.client.Minecraft
import net.minecraftforge.client.event.RenderGameOverlayEvent

object JoinServerSubtitle1710 {
    private const val DISPLAY_TICKS = 200
    private const val MESSAGE = "设定“持久子区块” 否则丢数据 见说明书"
    private var ticksLeft = 0

    @SubscribeEvent
    fun onClientJoinServer(event: FMLNetworkEvent.ClientConnectedToServerEvent) {
        ticksLeft = DISPLAY_TICKS
    }

    @SubscribeEvent
    fun onClientDisconnect(event: FMLNetworkEvent.ClientDisconnectionFromServerEvent) {
        ticksLeft = 0
    }

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase == TickEvent.Phase.END && ticksLeft > 0) {
            ticksLeft--
        }
    }

    @SubscribeEvent
    fun onRenderOverlay(event: RenderGameOverlayEvent.Post) {
        if (event.type != RenderGameOverlayEvent.ElementType.TEXT || ticksLeft <= 0) {
            return
        }
        val minecraft = Minecraft.getMinecraft()
        val font = minecraft.fontRenderer ?: return
        val x = (event.resolution.scaledWidth - font.getStringWidth(MESSAGE)) / 2
        val y = event.resolution.scaledHeight / 2 + 30
        font.drawStringWithShadow(MESSAGE, x, y, 0xFFFF55)
    }
}
