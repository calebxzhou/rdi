package calebxzhou.rdi.mc.server.compat

import dev.ftb.mods.ftbchunks.api.FTBChunksAPI
import dev.ftb.mods.ftblibrary.math.ChunkDimPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos

object FTBChunksCompat {
    fun isClaimedChunk(level: ServerLevel, chunkPos: ChunkPos): Boolean {
        val api = FTBChunksAPI.api()
        return api.isManagerLoaded && api.manager.getChunk(ChunkDimPos(level.dimension(), chunkPos)) != null
    }
}
