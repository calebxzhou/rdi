package calebxzhou.rdi.mc.client.render

import calebxzhou.rdi.mc.common.RDI
import calebxzhou.rdi.mc.common.SectionPos
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderGlobal
import net.minecraft.client.renderer.BufferBuilder
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.entity.Entity
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.MathHelper
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.network.FMLNetworkEvent
import net.minecraftforge.fml.relauncher.Side
import org.lwjgl.opengl.GL11

@Mod.EventBusSubscriber(modid = "rdi", value = [Side.CLIENT])
object FirmSectionRenderer112 {
     const val SET_R = 0
     const val SET_G = 255
     const val SET_B = 0
     const val NOW_R = 255
     const val NOW_G = 255
     const val NOW_B = 0



     fun currentSection(entity: Entity): SectionPos =
        SectionPos(
            blockToSectionCoord(entity.posX),
            blockToSectionCoord(entity.posY),
            blockToSectionCoord(entity.posZ)
        )

     fun drawSections(
        sections: List<SectionPos>,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
        red: Int,
        green: Int,
        blue: Int
    ) {
        if (sections.isEmpty()) {
            return
        }
        val sectionKeys = sections.mapTo(mutableSetOf()) { FirmSectionRenderKey(it.chunkX, it.index, it.chunkZ) }
        val lines = linkedSetOf<FirmSectionLine>()
        sectionKeys.forEach { addCandidateLines(it, lines) }

        val tessellator = Tessellator.getInstance()
        val buffer = tessellator.buffer
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR)
        lines.asSequence()
            .filter { it.isOuterLine(sectionKeys) }
            .forEach { addSectionGridLine(buffer, cameraX, cameraY, cameraZ, it, red, green, blue) }
        tessellator.draw()
    }

     fun drawSectionBox(
        section: SectionPos,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
        red: Int,
        green: Int,
        blue: Int
    ) {
        val minX = section.chunkX * 16.0 - cameraX
        val minY = section.index * 16.0 - cameraY
        val minZ = section.chunkZ * 16.0 - cameraZ
        RenderGlobal.drawSelectionBoundingBox(
            AxisAlignedBB(minX, minY, minZ, minX + 16.0, minY + 16.0, minZ + 16.0),
            red / 255.0f,
            green / 255.0f,
            blue / 255.0f,
            1.0f
        )
    }

     fun blockToSectionCoord(value: Double): Int = MathHelper.floor(value) shr 4

     fun lerp(start: Double, end: Double, partialTicks: Float): Double =
        start + (end - start) * partialTicks.toDouble()

     fun addCandidateLines(section: FirmSectionRenderKey, lines: MutableSet<FirmSectionLine>) {
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

     fun FirmSectionLine.isOuterLine(sections: Set<FirmSectionRenderKey>): Boolean {
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

     fun addSectionGridLine(
        buffer: BufferBuilder,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
        line: FirmSectionLine,
        red: Int,
        green: Int,
        blue: Int
    ) {
        buffer.pos(line.x1 * 16.0 - cameraX, line.y1 * 16.0 - cameraY, line.z1 * 16.0 - cameraZ)
            .color(red, green, blue, 255)
            .endVertex()
        buffer.pos(line.x2 * 16.0 - cameraX, line.y2 * 16.0 - cameraY, line.z2 * 16.0 - cameraZ)
            .color(red, green, blue, 255)
            .endVertex()
    }

     data class FirmSectionRenderKey(val x: Int, val y: Int, val z: Int)

     data class FirmSectionLine(
        val x1: Int,
        val y1: Int,
        val z1: Int,
        val x2: Int,
        val y2: Int,
        val z2: Int
    ) {
        companion object {
            fun of(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int): FirmSectionLine =
                if (x1 < x2 || x1 == x2 && (y1 < y2 || y1 == y2 && z1 <= z2)) {
                    FirmSectionLine(x1, y1, z1, x2, y2, z2)
                } else {
                    FirmSectionLine(x2, y2, z2, x1, y1, z1)
                }
        }
    }
}
