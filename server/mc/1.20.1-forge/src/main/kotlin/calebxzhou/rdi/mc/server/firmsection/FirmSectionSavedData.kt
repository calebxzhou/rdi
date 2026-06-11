package calebxzhou.rdi.mc.server.firmsection

import calebxzhou.rdi.mc.firmsection.FirmSectionKey
import calebxzhou.rdi.mc.firmsection.FirmSectionSetStatus
import calebxzhou.rdi.mc.firmsection.FirmSectionState
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

class FirmSectionSavedData : SavedData() {
    private val state = FirmSectionState()

    fun allSections(): List<FirmSectionKey> = state.allSections()

    fun isAutoSetEnabled(playerId: UUID): Boolean = state.isAutoSetEnabled(playerId)

    fun setAutoSetEnabled(playerId: UUID, enabled: Boolean) {
        if (state.setAutoSetEnabled(playerId, enabled)) {
            setDirty()
        }
    }

    fun hasFirmChunk(dimensionId: String, chunkX: Int, chunkZ: Int): Boolean =
        state.hasFirmChunk(dimensionId, chunkX, chunkZ)

    fun hasFirmSection(dimensionId: String, chunkX: Int, sectionY: Int, chunkZ: Int): Boolean =
        state.hasFirmSection(dimensionId, chunkX, sectionY, chunkZ)

    fun set(playerId: UUID, key: FirmSectionKey) = state.set(playerId, key).also {
        if (it.status == FirmSectionSetStatus.ADDED) {
            setDirty()
        }
    }

    fun unset(playerId: UUID, key: FirmSectionKey) = state.unset(playerId, key).also {
        if (it.removed) {
            setDirty()
        }
    }

    fun list(playerId: UUID) = state.list(playerId)

    override fun save(tag: CompoundTag): CompoundTag {
        val players = ListTag()
        state.players().forEach { player ->
            players.add(CompoundTag().apply {
                putString(UUID_TAG, player.playerId.toString())
                put(SECTIONS_TAG, player.sections.toTag())
                putBoolean(AUTO_SET_TAG, player.autoSet)
            })
        }
        tag.put(PLAYERS_TAG, players)
        return tag
    }

    private fun Collection<FirmSectionKey>.toTag() = ListTag().also { list ->
        forEach { section ->
            list.add(CompoundTag().apply {
                putString(DIMENSION_TAG, section.dimensionId)
                putInt(CHUNK_X_TAG, section.chunkX)
                putInt(SECTION_Y_TAG, section.sectionY)
                putInt(CHUNK_Z_TAG, section.chunkZ)
            })
        }
    }

    companion object {
        const val FILE_ID = "rdi_firm_sections"

        private const val PLAYERS_TAG = "players"
        private const val UUID_TAG = "uuid"
        private const val SECTIONS_TAG = "sections"
        private const val AUTO_SET_TAG = "autoSet"
        private const val DIMENSION_TAG = "dimensionId"
        private const val CHUNK_X_TAG = "chunkX"
        private const val SECTION_Y_TAG = "sectionY"
        private const val CHUNK_Z_TAG = "chunkZ"

        @JvmStatic
        fun load(tag: CompoundTag): FirmSectionSavedData {
            val data = FirmSectionSavedData()
            val players = tag.getList(PLAYERS_TAG, Tag.TAG_COMPOUND.toInt())
            for (playerTagBase in players) {
                val playerTag = playerTagBase as CompoundTag
                val playerId = try {
                    UUID.fromString(playerTag.getString(UUID_TAG))
                } catch (_: IllegalArgumentException) {
                    continue
                }
                val sections = linkedSetOf<FirmSectionKey>()
                val sectionTags = playerTag.getList(SECTIONS_TAG, Tag.TAG_COMPOUND.toInt())
                for (sectionTagBase in sectionTags) {
                    val sectionTag = sectionTagBase as CompoundTag
                    sections += FirmSectionKey(
                        sectionTag.getString(DIMENSION_TAG),
                        sectionTag.getInt(CHUNK_X_TAG),
                        sectionTag.getInt(SECTION_Y_TAG),
                        sectionTag.getInt(CHUNK_Z_TAG)
                    )
                }
                data.state.loadPlayer(
                    playerId,
                    sections,
                    playerTag.contains(AUTO_SET_TAG, Tag.TAG_BYTE.toInt()) && playerTag.getBoolean(AUTO_SET_TAG)
                )
            }
            return data
        }
    }
}
