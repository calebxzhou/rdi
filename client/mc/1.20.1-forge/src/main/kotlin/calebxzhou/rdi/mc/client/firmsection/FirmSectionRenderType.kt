package calebxzhou.rdi.mc.client.firmsection

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.RenderType

object FirmSectionRenderType : RenderType(
    "rdi_firm_section_lines",
    DefaultVertexFormat.POSITION_COLOR_NORMAL,
    VertexFormat.Mode.LINES,
    256,
    false,
    false,
    RenderType.lines()::setupRenderState,
    RenderType.lines()::clearRenderState
)
