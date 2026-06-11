package calebxzhou.rdi.mc.client.firmsection

import calebxzhou.rdi.mc.common.RDI
import calebxzhou.rdi.mc.common.SectionPos as RdiSectionPos
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.core.SectionPos
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.ClientPlayerNetworkEvent
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

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
        poseStack.pushPose()
        poseStack.setIdentity()
        try {
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
        } finally {
            poseStack.popPose()
        }
        bufferSource.endBatch(renderType)
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
        val sectionKeys = sections.mapTo(mutableSetOf()) { FirmSectionRenderKey(it.chunkX, it.index, it.chunkZ) }
        val lines = linkedSetOf<FirmSectionLine>()
        for (section in sectionKeys) {
            addCandidateLines(section, lines)
        }
        lines.asSequence()
            .filter { it.isOuterLine(sectionKeys) }
            .forEach { line ->
                addSectionGridLine(poseStack, vertexConsumer, cameraX, cameraY, cameraZ, line, red, green, blue)
            }
    }

    private fun addCandidateLines(section: FirmSectionRenderKey, lines: MutableSet<FirmSectionLine>) {
        val x = section.x
        val y = section.y
        val z = section.z
        lines += FirmSectionLine.of(x, y, z, x + 1, y, z)
        lines += FirmSectionLine.of(x, y + 1, z, x + 1, y + 1, z)
        lines += FirmSectionLine.of(x, y, z + 1, x + 1, y, z + 1)
        lines += FirmSectionLine.of(x, y + 1, z + 1, x + 1, y + 1, z + 1)
        lines += FirmSectionLine.of(x, y, z, x, y + 1, z)
        lines += FirmSectionLine.of(x + 1, y, z, x + 1, y + 1, z)
        lines += FirmSectionLine.of(x, y, z + 1, x, y + 1, z + 1)
        lines += FirmSectionLine.of(x + 1, y, z + 1, x + 1, y + 1, z + 1)
        lines += FirmSectionLine.of(x, y, z, x, y, z + 1)
        lines += FirmSectionLine.of(x + 1, y, z, x + 1, y, z + 1)
        lines += FirmSectionLine.of(x, y + 1, z, x, y + 1, z + 1)
        lines += FirmSectionLine.of(x + 1, y + 1, z, x + 1, y + 1, z + 1)
    }

    private fun FirmSectionLine.isOuterLine(sections: Set<FirmSectionRenderKey>): Boolean {
        val around = when {
            x1 != x2 -> booleanArrayOf(
                FirmSectionRenderKey(x1, y1 - 1, z1 - 1) in sections,
                FirmSectionRenderKey(x1, y1, z1 - 1) in sections,
                FirmSectionRenderKey(x1, y1 - 1, z1) in sections,
                FirmSectionRenderKey(x1, y1, z1) in sections
            )
            y1 != y2 -> booleanArrayOf(
                FirmSectionRenderKey(x1 - 1, y1, z1 - 1) in sections,
                FirmSectionRenderKey(x1, y1, z1 - 1) in sections,
                FirmSectionRenderKey(x1 - 1, y1, z1) in sections,
                FirmSectionRenderKey(x1, y1, z1) in sections
            )
            else -> booleanArrayOf(
                FirmSectionRenderKey(x1 - 1, y1 - 1, z1) in sections,
                FirmSectionRenderKey(x1, y1 - 1, z1) in sections,
                FirmSectionRenderKey(x1 - 1, y1, z1) in sections,
                FirmSectionRenderKey(x1, y1, z1) in sections
            )
        }
        val occupiedCount = around.count { it }
        return occupiedCount == 1 ||
            occupiedCount == 3 ||
            occupiedCount == 2 && ((around[0] && around[3]) || (around[1] && around[2]))
    }

    private fun addSectionGridLine(
        poseStack: PoseStack,
        vertexConsumer: VertexConsumer,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
        line: FirmSectionLine,
        red: Float,
        green: Float,
        blue: Float
    ) {
        val pose = poseStack.last()
        val x1 = (line.x1 * 16 - cameraX).toFloat()
        val y1 = (line.y1 * 16 - cameraY).toFloat()
        val z1 = (line.z1 * 16 - cameraZ).toFloat()
        val x2 = (line.x2 * 16 - cameraX).toFloat()
        val y2 = (line.y2 * 16 - cameraY).toFloat()
        val z2 = (line.z2 * 16 - cameraZ).toFloat()
        val normalX = if (line.x1 != line.x2) 1.0f else 0.0f
        val normalY = if (line.y1 != line.y2) 1.0f else 0.0f
        val normalZ = if (line.z1 != line.z2) 1.0f else 0.0f
        vertexConsumer.vertex(pose.pose(), x1, y1, z1).color(red, green, blue, 1.0f)
            .normal(pose.normal(), normalX, normalY, normalZ).endVertex()
        vertexConsumer.vertex(pose.pose(), x2, y2, z2).color(red, green, blue, 1.0f)
            .normal(pose.normal(), normalX, normalY, normalZ).endVertex()
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

    private data class FirmSectionRenderKey(val x: Int, val y: Int, val z: Int)

    private data class FirmSectionLine(
        val x1: Int,
        val y1: Int,
        val z1: Int,
        val x2: Int,
        val y2: Int,
        val z2: Int
    ) {
        companion object {
            fun of(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int): FirmSectionLine {
                return if (
                    x1 < x2 ||
                    x1 == x2 && y1 < y2 ||
                    x1 == x2 && y1 == y2 && z1 <= z2
                ) {
                    FirmSectionLine(x1, y1, z1, x2, y2, z2)
                } else {
                    FirmSectionLine(x2, y2, z2, x1, y1, z1)
                }
            }
        }
    }
}
