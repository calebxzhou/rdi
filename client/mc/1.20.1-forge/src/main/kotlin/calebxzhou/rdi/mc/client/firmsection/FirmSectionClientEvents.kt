package calebxzhou.rdi.mc.client.firmsection

import calebxzhou.rdi.mc.common.RDI
import calebxzhou.rdi.mc.common.SectionPos as RdiSectionPos
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.core.SectionPos
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.ClientPlayerNetworkEvent
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import org.joml.Matrix4f

@Mod.EventBusSubscriber(modid = "rdi", value = [Dist.CLIENT])
object FirmSectionClientEvents {
    @SubscribeEvent
    @JvmStatic
    fun onClientLeaveServer(event: ClientPlayerNetworkEvent.LoggingOut) {
        RDI.FIRM_CHUNKS.clear()
    }

    @SubscribeEvent
    @JvmStatic
    fun onRenderLevelStage(event: RenderLevelStageEvent) {
        if (
            event.stage !== RenderLevelStageEvent.Stage.AFTER_WEATHER ||
            (!RDI.SHOW_SET_FIRM_SECTIONS && !RDI.SHOW_NOW_FIRM_SECTION)
        ) {
            return
        }
        val minecraft = Minecraft.getInstance()
        val dimensionId = minecraft.level?.dimension()?.location()?.toString() ?: return
        val cameraEntity = event.camera.entity ?: return
        val cameraPos = event.camera.position
        val renderType = FirmSectionRenderType
        val bufferSource = minecraft.renderBuffers().bufferSource()
        val vertexConsumer = bufferSource.getBuffer(renderType)
        val poseStack = event.poseStack
        if (RDI.SHOW_SET_FIRM_SECTIONS) {
            addFirmSectionOutlines(
                poseStack,
                vertexConsumer,
                cameraPos.x,
                cameraPos.y,
                cameraPos.z,
                RDI.FIRM_CHUNKS[dimensionId].orEmpty(),
                0.0f,
                1.0f,
                0.0f
            )
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
        val modelViewStack = RenderSystem.getModelViewStack()
        modelViewStack.pushPose()
        modelViewStack.setIdentity()
        RenderSystem.applyModelViewMatrix()
        try {
            bufferSource.endBatch(renderType)
        } finally {
            modelViewStack.popPose()
            RenderSystem.applyModelViewMatrix()
        }
    }

    private fun addFirmSectionOutlines(
        poseStack: PoseStack,
        vertexConsumer: VertexConsumer,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
        sections: List<RdiSectionPos>,
        red: Float,
        green: Float,
        blue: Float
    ) {
        sections.asSequence()
            .map { FirmSectionRenderKey(it.chunkX, it.index, it.chunkZ) }
            .distinct()
            .forEach { section ->
                addSectionBox(
                    poseStack,
                    vertexConsumer,
                    cameraX,
                    cameraY,
                    cameraZ,
                    section.x * 16,
                    section.y * 16,
                    section.z * 16,
                    red,
                    green,
                    blue
                )
            }
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
        val maxX = minX + 16.0
        val maxY = minY + 16.0
        val maxZ = minZ + 16.0
        val pose = poseStack.last().pose()
        addVerticalStrip(vertexConsumer, pose, minX, minY, minZ, maxY, red, green, blue)
        addVerticalStrip(vertexConsumer, pose, minX, minY, maxZ, maxY, red, green, blue)
        addVerticalStrip(vertexConsumer, pose, maxX, minY, minZ, maxY, red, green, blue)
        addVerticalStrip(vertexConsumer, pose, maxX, minY, maxZ, maxY, red, green, blue)
        addHorizontalLoop(vertexConsumer, pose, minX, minY, minZ, maxX, maxZ, red, green, blue)
        addHorizontalLoop(vertexConsumer, pose, minX, maxY, minZ, maxX, maxZ, red, green, blue)
    }

    private fun addVerticalStrip(
        vertexConsumer: VertexConsumer,
        pose: Matrix4f,
        x1: Double,
        y1: Double,
        z1: Double,
        y2: Double,
        red: Float,
        green: Float,
        blue: Float
    ) {
        addVertex(vertexConsumer, pose, x1, y1, z1, red, green, blue, 0.0f)
        addVertex(vertexConsumer, pose, x1, y1, z1, red, green, blue, 1.0f)
        addVertex(vertexConsumer, pose, x1, y2, z1, red, green, blue, 1.0f)
        addVertex(vertexConsumer, pose, x1, y2, z1, red, green, blue, 0.0f)
    }

    private fun addHorizontalLoop(
        vertexConsumer: VertexConsumer,
        pose: Matrix4f,
        minX: Double,
        y: Double,
        minZ: Double,
        maxX: Double,
        maxZ: Double,
        red: Float,
        green: Float,
        blue: Float
    ) {
        addVertex(vertexConsumer, pose, minX, y, minZ, red, green, blue, 0.0f)
        addVertex(vertexConsumer, pose, minX, y, minZ, red, green, blue, 1.0f)
        addVertex(vertexConsumer, pose, minX, y, maxZ, red, green, blue, 1.0f)
        addVertex(vertexConsumer, pose, maxX, y, maxZ, red, green, blue, 1.0f)
        addVertex(vertexConsumer, pose, maxX, y, minZ, red, green, blue, 1.0f)
        addVertex(vertexConsumer, pose, minX, y, minZ, red, green, blue, 1.0f)
        addVertex(vertexConsumer, pose, minX, y, minZ, red, green, blue, 0.0f)
    }

    private fun addVertex(
        vertexConsumer: VertexConsumer,
        pose: Matrix4f,
        x: Double,
        y: Double,
        z: Double,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float
    ) {
        vertexConsumer.vertex(pose, x.toFloat(), y.toFloat(), z.toFloat()).color(red, green, blue, alpha).endVertex()
    }

    private data class FirmSectionRenderKey(val x: Int, val y: Int, val z: Int)
}
