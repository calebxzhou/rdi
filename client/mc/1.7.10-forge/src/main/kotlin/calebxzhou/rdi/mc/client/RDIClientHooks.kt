package calebxzhou.rdi.mc.client

import calebxzhou.rdi.mc.common.RDI
import cpw.mods.fml.client.FMLClientHandler
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.util.ResourceLocation

object RDIClientHooks {
    const val JOIN_BUTTON_ID: Int = 666
    const val JOIN_BUTTON_WIDTH: Int = 200
    const val JOIN_BUTTON_HEIGHT: Int = 20
    @JvmField
    val BG_RES: ResourceLocation = ResourceLocation("rdi", "textures/bg/1.jpg")

    @JvmStatic
    fun createJoinButton(x: Int, y: Int): GuiButton {
        return GuiButton(
            JOIN_BUTTON_ID, x, y, JOIN_BUTTON_WIDTH, JOIN_BUTTON_HEIGHT, "进入地图：" + RDI.HOST_NAME
        )
    }

    @JvmStatic
    fun isJoinButton(button: GuiButton): Boolean {
        return button != null && button.id == JOIN_BUTTON_ID
    }

    @JvmStatic
    fun joinHost(parentScreen: GuiScreen) {
        val handler = FMLClientHandler.instance()
        handler.setupServerList()
        handler.connectToServer(parentScreen, ServerData("rdi", RDI.GAME_IP, false))
    }
}
