package calebxzhou.rdi.mc.client

import calebxzhou.rdi.mc.client.mcpimpl211.McpGameImpl
import calebxzhou.rdi.mc.client.mcp.McpServer
import calebxzhou.rdi.mc.client.rcmd.RcmdClientCommands
import calebxzhou.rdi.mc.common.RDI
import com.google.common.net.HostAndPort
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.client.renderer.RenderType
import net.minecraft.commands.Commands
import net.minecraft.core.SectionPos
import net.minecraft.network.chat.Component
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.client.event.ClientChatEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import org.joml.Matrix4f
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/**
 * calebxzhou @ 2026-01-10 22:33
 */
val mc get() = Minecraft.getInstance()
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
        fun onRegisterClientCommands(event: RegisterClientCommandsEvent) {
            event.getDispatcher().register(
                Commands.literal("rdi")
                    .then(
                        Commands.literal("firmchunk")
                            .then(
                                Commands.literal("show")
                                    .executes { context ->
                                        RDI.SHOW_FIRM_CHUNKS = true
                                        context.source.sendSuccess({ Component.literal("永久区块边框：显示") }, false)
                                        1
                                    }
                            )
                            .then(
                                Commands.literal("hide")
                                    .executes { context ->
                                        RDI.SHOW_FIRM_CHUNKS = false
                                        context.source.sendSuccess({ Component.literal("永久区块边框：隐藏") }, false)
                                        1
                                    }
                            )
                    )
            )
        }

        @SubscribeEvent
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
        }

        @SubscribeEvent
        fun onRenderLevelStage(event: RenderLevelStageEvent) {
            if (event.stage !== RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || !RDI.SHOW_FIRM_CHUNKS) {
                return
            }
            val cameraEntity = event.camera.getEntity() ?: return
            val sectionPos = SectionPos.of(cameraEntity)
            val cameraPos = event.getCamera().getPosition()
            val bufferSource = Minecraft.getInstance().renderBuffers().bufferSource()
            val renderType = RenderType.debugLineStrip(4.0)
            val vertexConsumer = bufferSource.getBuffer(renderType)
            val matrix4f = event.getPoseStack().last().pose()
            val minX = (sectionPos.minBlockX() - cameraPos.x).toFloat()
            val minY = (sectionPos.minBlockY() - cameraPos.y).toFloat()
            val minZ = (sectionPos.minBlockZ() - cameraPos.z).toFloat()
            val maxX = minX + 16.0f
            val maxY = minY + 16.0f
            val maxZ = minZ + 16.0f

            addLine(vertexConsumer, matrix4f, minX, minY, minZ, maxX, minY, minZ)
            addLine(vertexConsumer, matrix4f, maxX, minY, minZ, maxX, minY, maxZ)
            addLine(vertexConsumer, matrix4f, maxX, minY, maxZ, minX, minY, maxZ)
            addLine(vertexConsumer, matrix4f, minX, minY, maxZ, minX, minY, minZ)

            addLine(vertexConsumer, matrix4f, minX, maxY, minZ, maxX, maxY, minZ)
            addLine(vertexConsumer, matrix4f, maxX, maxY, minZ, maxX, maxY, maxZ)
            addLine(vertexConsumer, matrix4f, maxX, maxY, maxZ, minX, maxY, maxZ)
            addLine(vertexConsumer, matrix4f, minX, maxY, maxZ, minX, maxY, minZ)

            addLine(vertexConsumer, matrix4f, minX, minY, minZ, minX, maxY, minZ)
            addLine(vertexConsumer, matrix4f, maxX, minY, minZ, maxX, maxY, minZ)
            addLine(vertexConsumer, matrix4f, maxX, minY, maxZ, maxX, maxY, maxZ)
            addLine(vertexConsumer, matrix4f, minX, minY, maxZ, minX, maxY, maxZ)

            bufferSource.endBatch(renderType)
        }

        private fun addLine(
            vertexConsumer: VertexConsumer,
            matrix4f: Matrix4f,
            x1: Float,
            y1: Float,
            z1: Float,
            x2: Float,
            y2: Float,
            z2: Float
        ) {
            vertexConsumer.addVertex(matrix4f, x1, y1, z1).setColor(1.0f, 1.0f, 0.0f, 0.0f)
            vertexConsumer.addVertex(matrix4f, x1, y1, z1).setColor(1.0f, 1.0f, 0.0f, 1.0f)
            vertexConsumer.addVertex(matrix4f, x2, y2, z2).setColor(1.0f, 1.0f, 0.0f, 1.0f)
            vertexConsumer.addVertex(matrix4f, x2, y2, z2).setColor(1.0f, 1.0f, 0.0f, 0.0f)
        }


    }
}
