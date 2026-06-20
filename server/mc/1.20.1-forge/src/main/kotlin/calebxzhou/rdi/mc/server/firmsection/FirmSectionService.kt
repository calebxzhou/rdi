package calebxzhou.rdi.mc.server.firmsection

import calebxzhou.rdi.mc.common.RDI
import calebxzhou.rdi.mc.firmsection.FirmSectionKey
import calebxzhou.rdi.mc.firmsection.FirmSectionListResult
import calebxzhou.rdi.mc.firmsection.FirmSectionSetResult
import calebxzhou.rdi.mc.firmsection.FirmSectionSetStatus
import calebxzhou.rdi.mc.server.network.RServerNetwork
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos

object FirmSectionService {
    fun isAutoSetEnabled(player: ServerPlayer): Boolean =
        data(player.server).isAutoSetEnabled(player.uuid)

    fun setAutoSetEnabled(player: ServerPlayer, enabled: Boolean) {
        data(player.server).setAutoSetEnabled(player.uuid, enabled)
        RServerNetwork.sendFirmSectionsToAll(player.server)
    }

    fun set(player: ServerPlayer): FirmSectionSetResult =
        set(player, player.serverLevel(), player.blockPosition())

    fun set(player: ServerPlayer, level: ServerLevel, pos: BlockPos): FirmSectionSetResult {
        val result = data(player.server).set(player.uuid, target(level, pos))
        if (result.status == FirmSectionSetStatus.ADDED) {
            level.getChunkAt(pos).setUnsaved(true)
            RServerNetwork.sendFirmSectionsToAll(player.server)
        }
        return result
    }

    fun unset(player: ServerPlayer) =
        data(player.server).unset(player.uuid, target(player.serverLevel(), player.blockPosition())).also {
            if (it.removed) {
                RServerNetwork.sendFirmSectionsToAll(player.server)
            }
        }

    fun list(player: ServerPlayer): FirmSectionListResult =
        data(player.server).list(player.uuid)

    fun shouldSaveChunk(level: ServerLevel, chunkPos: ChunkPos): Boolean {
        if (!RDI.ONLY_SAVE_FIRM_SECTIONS) {
            return true
        }
        return data(level.server).hasFirmChunk(
            level.dimension().location().toString(),
            chunkPos.x,
            chunkPos.z
        )
    }

    fun all(server: MinecraftServer): List<FirmSectionKey> = data(server).allSections()

    private fun data(server: MinecraftServer): FirmSectionSavedData =
        server.overworld().dataStorage.computeIfAbsent(
            FirmSectionSavedData::load,
            ::FirmSectionSavedData,
            FirmSectionSavedData.FILE_ID
        )

    private fun target(level: ServerLevel, pos: BlockPos) = FirmSectionKey(
        level.dimension().location().toString(),
        SectionPos.blockToSectionCoord(pos.x),
        SectionPos.blockToSectionCoord(pos.y),
        SectionPos.blockToSectionCoord(pos.z)
    )

}
