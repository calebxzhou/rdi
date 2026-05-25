package calebxzhou.rdi.mc.client

import calebxzhou.rdi.mc.client.mcpimpl211.McpGameImpl
import calebxzhou.rdi.mc.client.mcp.McpServer
import calebxzhou.rdi.mc.client.mcpimpl211.Search
import calebxzhou.rdi.mc.client.rcmd.RcmdClientCommands
import calebxzhou.rdi.mc.common.RDI
import calebxzhou.rdi.mc.common.SectionPos as RdiSectionPos
import com.google.common.net.HostAndPort
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.SectionPos
import net.minecraft.network.chat.Component
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.client.event.ClientChatEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.OptionalDouble

/**
 * calebxzhou @ 2026-01-10 22:33
 */
@Mod("rdi")
@EventBusSubscriber(modid = "rdi", value = [Dist.CLIENT])
class RDIMain {

    companion object {
        val SCREENSHOT_EXECUTOR: ExecutorService =
            Executors.newSingleThreadExecutor(ThreadFactory { task: Runnable? ->
                val thread = Thread(task, "rdi-mcp-screenshot.md")
                thread.setDaemon(true)
                thread
            })
        private val FIRM_SECTION_LINES: RenderType = RenderType.create(
            "rdi_firm_section_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            1536,
            RenderType.CompositeState.builder()
                .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                .setLineState(RenderStateShard.LineStateShard(OptionalDouble.empty()))
                .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .createCompositeState(false)
        )

        @JvmField
        var JOIN_BUTTON: Button =
            Button.builder(Component.literal("进入地图 · " + RDI.HOST_NAME), Button.OnPress { _ ->
                val hp = HostAndPort.fromString(RDI.GAME_IP)
                ConnectScreen.startConnecting(
                    TitleScreen(),
                    Minecraft.getInstance(),
                    ServerAddress(hp.getHost(), hp.getPort()),
                    ServerData("rdi", RDI.GAME_IP, ServerData.Type.OTHER),
                    false,
                    null
                )
            }).bounds(100, 0, 200, 50).build()

        @JvmStatic
        fun layoutJoinButton(screenWidth: Int) {
            JOIN_BUTTON.setX(screenWidth / 2 - 100)
            JOIN_BUTTON.setY(0)
            JOIN_BUTTON.setWidth(200)
            JOIN_BUTTON.setHeight(20)
        }
        @SubscribeEvent
        @JvmStatic
        fun onResourceReload(event: RegisterClientReloadListenersEvent) {
            event.registerReloadListener(ResourceManagerReloadListener {
                Search.refreshResourceIndex()
            })
        }
        @SubscribeEvent @JvmStatic
        fun onClientChat(event: ClientChatEvent) {
            val message = event.message
            if (!RcmdClientCommands.isRcmd(message)) {
                return
            }
            val minecraft = Minecraft.getInstance()
            val result = RcmdClientCommands.dispatch(minecraft, message)
            if (!result.found) {
                return
            }
            event.setCanceled(true)
            RcmdClientCommands.reply(minecraft, result.result)
        }

        @SubscribeEvent
        @JvmStatic
        fun onClientJoinServer(event: ClientPlayerNetworkEvent.LoggingIn) {
            McpServer.start(McpGameImpl,if(RDI.DEBUG)25565 else null).onFailure { it.printStackTrace() }
        }

        @SubscribeEvent
        @JvmStatic
        fun onClientLeaveServer(event: ClientPlayerNetworkEvent.LoggingOut) {
            McpServer.stop()
            RDI.FIRM_CHUNKS.clear()
        }

        @SubscribeEvent @JvmStatic
        fun onRenderLevelStage(event: RenderLevelStageEvent) {
            if (
                event.stage !== RenderLevelStageEvent.Stage.AFTER_WEATHER ||
                (!RDI.SHOW_SET_FIRM_SECTIONS && !RDI.SHOW_NOW_FIRM_SECTION)
            ) {
                return
            }
            val minecraft = Minecraft.getInstance()
            val dimensionId = minecraft.level?.dimension()?.location()?.toString() ?: return
            val cameraEntity = event.camera.getEntity() ?: return
            val cameraPos = event.getCamera().getPosition()
            val bufferSource = minecraft.renderBuffers().bufferSource()
            val renderType = FIRM_SECTION_LINES
            val vertexConsumer = bufferSource.getBuffer(renderType)
            val poseStack = event.getPoseStack()
            if (RDI.SHOW_SET_FIRM_SECTIONS) {
                RDI.FIRM_CHUNKS[dimensionId].orEmpty().forEach { section ->
                    addSectionBox(poseStack, vertexConsumer, cameraPos.x, cameraPos.y, cameraPos.z, section, 0.0f, 1.0f, 0.0f)
                }
            }

            if (RDI.SHOW_NOW_FIRM_SECTION) {
                val currentSection = SectionPos.of(cameraEntity)
                addSectionBox(
                    poseStack,
                    vertexConsumer,
                    cameraPos.x,
                    cameraPos.y,
                    cameraPos.z,
                    currentSection.minBlockX(),
                    currentSection.minBlockY(),
                    currentSection.minBlockZ(),
                    1.0f,
                    1.0f,
                    0.0f
                )
            }

            bufferSource.endBatch(renderType)
        }

        private fun addSectionBox(
            poseStack: PoseStack,
            vertexConsumer: VertexConsumer,
            cameraX: Double,
            cameraY: Double,
            cameraZ: Double,
            section: RdiSectionPos,
            red: Float,
            green: Float,
            blue: Float
        ) {
            addSectionBox(
                poseStack,
                vertexConsumer,
                cameraX,
                cameraY,
                cameraZ,
                section.chunkX * 16,
                section.index * 16,
                section.chunkZ * 16,
                red,
                green,
                blue
            )
        }

        private fun addSectionBox(
            poseStack: PoseStack,
            vertexConsumer: VertexConsumer,
            cameraX: Double,
            cameraY: Double,
            cameraZ: Double,
            minBlockX: Int,
            minBlockY: Int,
            minBlockZ: Int,
            red: Float,
            green: Float,
            blue: Float
        ) {
            val minX = minBlockX - cameraX
            val minY = minBlockY - cameraY
            val minZ = minBlockZ - cameraZ
            LevelRenderer.renderLineBox(
                poseStack,
                vertexConsumer,
                minX,
                minY,
                minZ,
                minX + 16.0,
                minY + 16.0,
                minZ + 16.0,
                red,
                green,
                blue,
                1.0f
            )
        }


    }
}
