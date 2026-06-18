package calebxzhou.rdi.mc.server.firmsection

import calebxzhou.rdi.mc.common.RDI
import calebxzhou.rdi.mc.firmsection.FirmSectionKey
import calebxzhou.rdi.mc.firmsection.FirmSectionListResult
import calebxzhou.rdi.mc.firmsection.FirmSectionSetResult
import calebxzhou.rdi.mc.firmsection.FirmSectionSetStatus
import calebxzhou.rdi.mc.firmsection.FirmSectionUnsetResult
import calebxzhou.rdi.mc.server.network.RServerNetwork
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import net.minecraft.world.World
import net.minecraft.world.chunk.Chunk
import com.feed_the_beast.ftbutilities.data.ClaimedChunks
import com.feed_the_beast.ftblib.lib.math.ChunkDimPos
import java.util.OptionalInt

object FirmSectionService112 {
    fun isEnabled(): Boolean = RDI.ONLY_SAVE_FIRM_SECTIONS

    fun isAutoSetEnabled(player: EntityPlayerMP): Boolean =
        data(player.server).isAutoSetEnabled(player.uniqueID)

    fun setAutoSetEnabled(player: EntityPlayerMP, enabled: Boolean) {
        data(player.server).setAutoSetEnabled(player.uniqueID, enabled)
    }

    fun set(player: EntityPlayerMP): FirmSectionSetResult =
        set(player, player.world, player.position)

    fun set(player: EntityPlayerMP, world: World, pos: BlockPos): FirmSectionSetResult {
        val key = target(world, pos)
        val result = data(player.server).set(player.uniqueID, key)
        if (result.status == FirmSectionSetStatus.ADDED) {
            world.getChunk(key.chunkX, key.chunkZ).markDirty()
            RServerNetwork.sendFirmSectionsToAll(player.server)
        }
        return result
    }

    fun unset(player: EntityPlayerMP): FirmSectionUnsetResult =
        data(player.server).unset(player.uniqueID, target(player.world, player.position)).also {
            if (it.removed) {
                RServerNetwork.sendFirmSectionsToAll(player.server)
            }
        }

    fun list(player: EntityPlayerMP): FirmSectionListResult =
        data(player.server).list(player.uniqueID)

    fun shouldSaveChunk(world: World, chunk: Chunk): Boolean {
        if (!RDI.ONLY_SAVE_FIRM_SECTIONS) {
            return true
        }
        if (data(world.minecraftServer!!).hasFirmChunk(
            dimensionId(world),
            chunk.x,
            chunk.z
        )) {
            return true
        }
        return isFtbUtilitiesClaimedChunk(world, chunk)
    }

    fun shouldSaveEntityPosition(world: World, x: Double, y: Double, z: Double): Boolean =
        !RDI.ONLY_SAVE_FIRM_SECTIONS || data(world.minecraftServer!!).hasFirmSection(
            dimensionId(world),
            blockToSectionCoord(x),
            blockToSectionCoord(y),
            blockToSectionCoord(z)
        )

    fun all(server: MinecraftServer): List<FirmSectionKey> = data(server).allSections()

    private fun data(server: MinecraftServer): FirmSectionSavedData112 {
        val storage = server.getWorld(0).mapStorage!!
        val existing = storage.getOrLoadData(FirmSectionSavedData112::class.java, FirmSectionSavedData112.FILE_ID)
        if (existing is FirmSectionSavedData112) {
            return existing
        }
        return FirmSectionSavedData112(FirmSectionSavedData112.FILE_ID).also {
            storage.setData(FirmSectionSavedData112.FILE_ID, it)
        }
    }

    private fun target(world: World, pos: BlockPos) = FirmSectionKey(
        dimensionId(world),
        pos.x shr 4,
        pos.y shr 4,
        pos.z shr 4
    )

    private fun dimensionId(world: World): String = "legacy:${world.provider.dimension}"

    private fun blockToSectionCoord(value: Double): Int = MathHelper.floor(value) shr 4

    private fun isFtbUtilitiesClaimedChunk(world: World, chunk: Chunk): Boolean {
        if (!ClaimedChunks.isActive()) {
            return false
        }
        val pos = ChunkDimPos(chunk.x, chunk.z, world.provider.dimension)
        return ClaimedChunks.instance.universe.teams.any { team ->
            ClaimedChunks.instance.getTeamChunks(team, OptionalInt.of(pos.dim), true).any {
                it.pos.equalsChunkDimPos(pos)
            }
        }
    }
}
