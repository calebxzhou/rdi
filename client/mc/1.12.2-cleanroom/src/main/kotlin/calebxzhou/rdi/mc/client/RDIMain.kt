package calebxzhou.rdi.mc.client

import calebxzhou.rdi.mc.client.network.RClientNetwork.register
import calebxzhou.rdi.mc.client.render.FirmSectionRenderer112.NOW_B
import calebxzhou.rdi.mc.client.render.FirmSectionRenderer112.NOW_G
import calebxzhou.rdi.mc.client.render.FirmSectionRenderer112.NOW_R
import calebxzhou.rdi.mc.client.render.FirmSectionRenderer112.SET_B
import calebxzhou.rdi.mc.client.render.FirmSectionRenderer112.SET_G
import calebxzhou.rdi.mc.client.render.FirmSectionRenderer112.SET_R
import calebxzhou.rdi.mc.client.render.FirmSectionRenderer112.currentSection
import calebxzhou.rdi.mc.client.render.FirmSectionRenderer112.drawSectionBox
import calebxzhou.rdi.mc.client.render.FirmSectionRenderer112.drawSections
import calebxzhou.rdi.mc.client.render.FirmSectionRenderer112.lerp
import calebxzhou.rdi.mc.common.RDI
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiMainMenu
import net.minecraft.client.multiplayer.GuiConnecting
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.util.ResourceLocation
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.network.FMLNetworkEvent
import org.apache.logging.log4j.LogManager
import org.lwjgl.opengl.GL11

/**
 * calebxzhou @ 2026-02-03 21:25
 */
@Mod(
    modid = "rdi",
    name = "rdi",
    version = "1",
    modLanguageAdapter = "io.github.chaosunity.forgelin.KotlinAdapter"
)
class RDIMain {
    init {
        register()
        MinecraftForge.EVENT_BUS.register(this)
        LogManager.getLogger("rdi").info("❄❄❄❄❄❄❄❄RDI客户端核心模块已加载❄❄❄❄❄❄❄❄")
    }

    companion object {
        @JvmField
        val JOIN_BUTTON_ID = 666

        @JvmStatic
        fun createJoinButton(x: Int, y: Int, width: Int) =
            GuiButton(JOIN_BUTTON_ID, x, y, width, 20, "进入地图：${RDI.HOST_NAME}")

        @JvmStatic
        fun onJoinRDI() {
            val minecraft = Minecraft.getMinecraft()
            minecraft.displayGuiScreen(
                GuiConnecting(
                    GuiMainMenu(),
                    minecraft,
                    ServerData("rdi", RDI.GAME_IP, false)
                )
            )
        }

        @JvmField
        val BG_RES = ResourceLocation("rdi", "textures/bg/1.jpg")
    }
    @SubscribeEvent
    fun onClientDisconnect(event: FMLNetworkEvent.ClientDisconnectionFromServerEvent) {
        RDI.FIRM_CHUNKS.clear()
    }

    @SubscribeEvent
    fun onRenderWorldLast(event: RenderWorldLastEvent) {
        if (!RDI.SHOW_SET_FIRM_SECTIONS && !RDI.SHOW_NOW_FIRM_SECTION) {
            return
        }
        val minecraft = Minecraft.getMinecraft()
        val world = minecraft.world ?: return
        val camera = minecraft.renderViewEntity ?: return
        val cameraX = lerp(camera.lastTickPosX, camera.posX, event.partialTicks)
        val cameraY = lerp(camera.lastTickPosY, camera.posY, event.partialTicks)
        val cameraZ = lerp(camera.lastTickPosZ, camera.posZ, event.partialTicks)
        val dimensionId = "legacy:${world.provider.dimension}"

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT or GL11.GL_LINE_BIT or GL11.GL_CURRENT_BIT or GL11.GL_DEPTH_BUFFER_BIT)
        GlStateManager.disableTexture2D()
        GlStateManager.enableBlend()
        GlStateManager.disableDepth()
        GL11.glLineWidth(2.0f)
        try {
            if (RDI.SHOW_SET_FIRM_SECTIONS) {
                drawSections(RDI.FIRM_CHUNKS[dimensionId].orEmpty(), cameraX, cameraY, cameraZ, SET_R, SET_G, SET_B)
            }
            if (RDI.SHOW_NOW_FIRM_SECTION) {
                drawSectionBox(currentSection(camera), cameraX, cameraY, cameraZ, NOW_R, NOW_G, NOW_B)
            }
        } finally {
            GL11.glPopAttrib()
        }
    }
}
