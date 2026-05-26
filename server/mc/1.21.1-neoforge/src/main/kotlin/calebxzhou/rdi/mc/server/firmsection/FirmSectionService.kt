package calebxzhou.rdi.mc.server.firmsection

import calebxzhou.rdi.mc.common.RDI
import calebxzhou.rdi.mc.server.network.RServerNetwork
import net.minecraft.core.SectionPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ChunkPos

enum class FirmSectionSetStatus {
    ADDED,
    ALREADY_PRESENT,
    PLAYER_LIMIT_REACHED,
    TOTAL_LIMIT_REACHED,
}

data class FirmSectionSetResult(
    val status: FirmSectionSetStatus,
    val key: FirmSectionKey,
    val playerCount: Int,
    val total: Int,
)

data class FirmSectionUnsetResult(
    val removed: Boolean,
    val key: FirmSectionKey,
    val playerCount: Int,
    val total: Int,
)

data class FirmSectionListResult(
    val sections: List<FirmSectionKey>,
    val playerCount: Int,
    val total: Int,
)

object FirmSectionService {

    fun set(player: ServerPlayer): FirmSectionSetResult {
        val key = target(player)
        val data = data(player.server)
        val status = when (data.add(player.uuid, key)) {
            FirmSectionAddResult.ADDED -> FirmSectionSetStatus.ADDED
            FirmSectionAddResult.ALREADY_PRESENT -> FirmSectionSetStatus.ALREADY_PRESENT
            FirmSectionAddResult.PLAYER_LIMIT_REACHED -> FirmSectionSetStatus.PLAYER_LIMIT_REACHED
            FirmSectionAddResult.TOTAL_LIMIT_REACHED -> FirmSectionSetStatus.TOTAL_LIMIT_REACHED
        }
        if (status == FirmSectionSetStatus.ADDED) {
            player.level().getChunkAt(player.blockPosition()).setUnsaved(true)
            RServerNetwork.sendFirmSectionsToAll(player.server)
        }
        return FirmSectionSetResult(status, key, data.playerCount(player.uuid), data.totalCount())
    }

    fun unset(player: ServerPlayer): FirmSectionUnsetResult {
        val key = target(player)
        val data = data(player.server)
        val removed = data.remove(player.uuid, key)
        if (removed) {
            RServerNetwork.sendFirmSectionsToAll(player.server)
        }
        return FirmSectionUnsetResult(removed, key, data.playerCount(player.uuid), data.totalCount())
    }

    fun list(player: ServerPlayer): FirmSectionListResult {
        val data = data(player.server)
        return FirmSectionListResult(
            sections = data.sectionsOf(player.uuid),
            playerCount = data.playerCount(player.uuid),
            total = data.totalCount(),
        )
    }

    fun shouldSaveChunk(level: ServerLevel, chunkPos: ChunkPos): Boolean =
        !RDI.ONLY_SAVE_FIRM_SECTIONS || data(level.server).hasFirmChunk(
            dimensionId = level.dimension().location().toString(),
            chunkX = chunkPos.x,
            chunkZ = chunkPos.z,
        )

    fun shouldSaveEntity(level: ServerLevel, entity: Entity): Boolean {
        if (!RDI.ONLY_SAVE_FIRM_SECTIONS) {
            return true
        }
        val pos = entity.blockPosition()
        return data(level.server).hasFirmSection(
            dimensionId = level.dimension().location().toString(),
            chunkX = SectionPos.blockToSectionCoord(pos.x),
            sectionY = SectionPos.blockToSectionCoord(pos.y),
            chunkZ = SectionPos.blockToSectionCoord(pos.z),
        )
    }

    fun all(server: MinecraftServer): List<FirmSectionKey> = data(server).allSections()

    private fun data(server: MinecraftServer): FirmSectionSavedData =
        server.overworld().dataStorage.computeIfAbsent(FirmSectionSavedData.factory(), FirmSectionSavedData.FILE_ID)

    private fun target(player: ServerPlayer): FirmSectionKey {
        val pos = player.blockPosition()
        return FirmSectionKey(
            dimensionId = player.level().dimension().location().toString(),
            chunkX = SectionPos.blockToSectionCoord(pos.x),
            sectionY = SectionPos.blockToSectionCoord(pos.y),
            chunkZ = SectionPos.blockToSectionCoord(pos.z),
        )
    }
}
