package calebxzhou.rdi.mc.client.render

import calebxzhou.rdi.mc.common.RDI
import calebxzhou.rdi.mc.common.SectionPos
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.network.FMLNetworkEvent
import net.minecraft.client.Minecraft
import net.minecraft.entity.Entity
import net.minecraft.util.MathHelper
import net.minecraft.client.renderer.Tessellator
import net.minecraftforge.client.event.RenderWorldLastEvent
import org.lwjgl.opengl.GL11

object FirmSectionRenderer1710 {
    private const val SET_SECTION_COLOR = 0x00FF00
    private const val NOW_SECTION_COLOR = 0xFFFF00

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
        val world = minecraft.theWorld ?: return
        val camera = minecraft.renderViewEntity ?: return
        val cameraX = lerp(camera.lastTickPosX, camera.posX, event.partialTicks)
        val cameraY = lerp(camera.lastTickPosY, camera.posY, event.partialTicks)
        val cameraZ = lerp(camera.lastTickPosZ, camera.posZ, event.partialTicks)
        val dimensionId = "legacy:${world.provider.dimensionId}"

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT or GL11.GL_LINE_BIT or GL11.GL_CURRENT_BIT or GL11.GL_DEPTH_BUFFER_BIT)
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glDisable(GL11.GL_DEPTH_TEST)
        GL11.glLineWidth(2.0f)
        try {
            if (RDI.SHOW_SET_FIRM_SECTIONS) {
                drawSections(RDI.FIRM_CHUNKS[dimensionId].orEmpty(), cameraX, cameraY, cameraZ, SET_SECTION_COLOR)
            }
            if (RDI.SHOW_NOW_FIRM_SECTION) {
                drawSections(listOf(currentSection(camera)), cameraX, cameraY, cameraZ, NOW_SECTION_COLOR)
            }
        } finally {
            GL11.glPopAttrib()
        }
    }

    private fun currentSection(entity: Entity): SectionPos =
        SectionPos(
            blockToSectionCoord(entity.posX),
            blockToSectionCoord(entity.posY),
            blockToSectionCoord(entity.posZ),
        )

    private fun drawSections(sections: List<SectionPos>, cameraX: Double, cameraY: Double, cameraZ: Double, color: Int) {
        val sectionKeys = sections.mapTo(mutableSetOf()) { FirmSectionRenderKey(it.chunkX, it.index, it.chunkZ) }
        val lines = linkedSetOf<FirmSectionLine>()
        for (section in sectionKeys) {
            addCandidateLines(section, lines)
        }
        val tessellator = Tessellator.instance
        tessellator.startDrawing(GL11.GL_LINES)
        tessellator.setColorOpaque_I(color)
        lines.asSequence()
            .filter { it.isOuterLine(sectionKeys) }
            .forEach { line -> addSectionGridLine(tessellator, cameraX, cameraY, cameraZ, line) }
        tessellator.draw()
    }

    private fun blockToSectionCoord(value: Double): Int = MathHelper.floor_double(value) shr 4

    private fun lerp(start: Double, end: Double, partialTicks: Float): Double =
        start + (end - start) * partialTicks.toDouble()

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
        tessellator: Tessellator,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
        line: FirmSectionLine
    ) {
        tessellator.addVertex(line.x1 * 16.0 - cameraX, line.y1 * 16.0 - cameraY, line.z1 * 16.0 - cameraZ)
        tessellator.addVertex(line.x2 * 16.0 - cameraX, line.y2 * 16.0 - cameraY, line.z2 * 16.0 - cameraZ)
    }

    private data class FirmSectionRenderKey(val x: Int, val y: Int, val z: Int)

    private data class FirmSectionLine(
        val x1: Int,
        val y1: Int,
        val z1: Int,
        val x2: Int,
        val y2: Int,
        val z2: Int,
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
