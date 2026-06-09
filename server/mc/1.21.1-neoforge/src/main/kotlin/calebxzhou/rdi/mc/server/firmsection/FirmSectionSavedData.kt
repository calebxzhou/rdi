package calebxzhou.rdi.mc.server.firmsection

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

data class FirmSectionKey(
    val dimensionId: String,
    val chunkX: Int,
    val sectionY: Int,
    val chunkZ: Int,
)

enum class FirmSectionAddResult {
    ADDED,
    ALREADY_PRESENT,
    PLAYER_LIMIT_REACHED,
    TOTAL_LIMIT_REACHED,
}

class FirmSectionSavedData : SavedData() {
    private val sectionsByPlayer = linkedMapOf<UUID, LinkedHashSet<FirmSectionKey>>()
    private val autoSetByPlayer = linkedMapOf<UUID, Boolean>()
    fun totalCount(): Int = sectionsByPlayer.values.sumOf { it.size }

    fun playerCount(playerId: UUID): Int = sectionsByPlayer[playerId]?.size ?: 0

    fun sectionsOf(playerId: UUID): List<FirmSectionKey> =
        sectionsByPlayer[playerId]?.sortedWith(KEY_ORDER) ?: emptyList()

    fun allSections(): List<FirmSectionKey> =
        sectionsByPlayer.values.flatten().distinct().sortedWith(KEY_ORDER)

    fun isAutoSetEnabled(playerId: UUID): Boolean = autoSetByPlayer[playerId] ?: false

    fun setAutoSetEnabled(playerId: UUID, enabled: Boolean) {
        if (isAutoSetEnabled(playerId) == enabled) {
            return
        }
        if (enabled) {
            autoSetByPlayer[playerId] = true
            sectionsByPlayer.getOrPut(playerId) { linkedSetOf() }
        } else {
            autoSetByPlayer -= playerId
            if (sectionsByPlayer[playerId].isNullOrEmpty()) {
                sectionsByPlayer -= playerId
            }
        }
        setDirty()
    }

    fun hasFirmChunk(dimensionId: String, chunkX: Int, chunkZ: Int): Boolean =
        sectionsByPlayer.values.any { sections ->
            sections.any { it.dimensionId == dimensionId && it.chunkX == chunkX && it.chunkZ == chunkZ }
        }

    fun hasFirmSection(dimensionId: String, chunkX: Int, sectionY: Int, chunkZ: Int): Boolean =
        sectionsByPlayer.values.any { sections ->
            sections.any {
                it.dimensionId == dimensionId &&
                    it.chunkX == chunkX &&
                    it.sectionY == sectionY &&
                    it.chunkZ == chunkZ
            }
        }

    fun add(playerId: UUID, key: FirmSectionKey): FirmSectionAddResult {
        val existingSections = sectionsByPlayer[playerId]
        if (existingSections != null && key in existingSections) {
            return FirmSectionAddResult.ALREADY_PRESENT
        }
        if (MAX_SECTIONS_PERSON > 0 && (existingSections?.size ?: 0) >= MAX_SECTIONS_PERSON) {
            return FirmSectionAddResult.PLAYER_LIMIT_REACHED
        }
        if (totalCount() >= MAX_SECTIONS_TOTAL) {
            return FirmSectionAddResult.TOTAL_LIMIT_REACHED
        }
        val sections = sectionsByPlayer.getOrPut(playerId) { linkedSetOf() }
        sections += key
        setDirty()
        return FirmSectionAddResult.ADDED
    }

    fun remove(playerId: UUID, key: FirmSectionKey): Boolean {
        val sections = sectionsByPlayer[playerId] ?: return false
        if (!sections.remove(key)) {
            return false
        }
        if (sections.isEmpty() && !isAutoSetEnabled(playerId)) {
            sectionsByPlayer -= playerId
        }
        setDirty()
        return true
    }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val players = ListTag()
        sectionsByPlayer.forEach { (playerId, sections) ->
            val playerTag = CompoundTag()
            playerTag.putString(UUID_TAG, playerId.toString())
            playerTag.put(SECTIONS_TAG, sections.toTag())
            playerTag.putBoolean(AUTO_SET_TAG, isAutoSetEnabled(playerId))
            players.add(playerTag)
        }
        tag.put(PLAYERS_TAG, players)
        return tag
    }

    private fun Collection<FirmSectionKey>.toTag(): ListTag {
        val tag = ListTag()
        forEach { section ->
            tag.add(CompoundTag().apply {
                putString(DIMENSION_TAG, section.dimensionId)
                putInt(CHUNK_X_TAG, section.chunkX)
                putInt(SECTION_Y_TAG, section.sectionY)
                putInt(CHUNK_Z_TAG, section.chunkZ)
            })
        }
        return tag
    }

    companion object {
        const val FILE_ID = "rdi_firm_sections"
        val MAX_SECTIONS_TOTAL: Int = Integer.getInteger("rdi.firmSectionTotalMax",160)
        val MAX_SECTIONS_PERSON: Int = Integer.getInteger("rdi.firmSectionPersonMax",0)

        private const val PLAYERS_TAG = "players"
        private const val UUID_TAG = "uuid"
        private const val SECTIONS_TAG = "sections"
        private const val AUTO_SET_TAG = "autoSet"
        private const val DIMENSION_TAG = "dimensionId"
        private const val CHUNK_X_TAG = "chunkX"
        private const val SECTION_Y_TAG = "sectionY"
        private const val CHUNK_Z_TAG = "chunkZ"

        private val KEY_ORDER = compareBy<FirmSectionKey>(
            { it.dimensionId },
            { it.chunkX },
            { it.sectionY },
            { it.chunkZ },
        )

        fun factory(): Factory<FirmSectionSavedData> = Factory(::FirmSectionSavedData, ::load)

        private fun load(tag: CompoundTag, registries: HolderLookup.Provider): FirmSectionSavedData {
            val data = FirmSectionSavedData()
            val players = tag.getList(PLAYERS_TAG, Tag.TAG_COMPOUND.toInt())
            for (playerTagBase in players) {
                val playerTag = playerTagBase as CompoundTag
                val playerId = runCatching { UUID.fromString(playerTag.getString(UUID_TAG)) }.getOrNull() ?: continue
                val sections = linkedSetOf<FirmSectionKey>()
                val sectionTags = playerTag.getList(SECTIONS_TAG, Tag.TAG_COMPOUND.toInt())
                for (sectionTagBase in sectionTags) {
                    val sectionTag = sectionTagBase as CompoundTag
                    sections += FirmSectionKey(
                        dimensionId = sectionTag.getString(DIMENSION_TAG),
                        chunkX = sectionTag.getInt(CHUNK_X_TAG),
                        sectionY = sectionTag.getInt(SECTION_Y_TAG),
                        chunkZ = sectionTag.getInt(CHUNK_Z_TAG),
                    )
                }
                if (sections.isNotEmpty()) {
                    data.sectionsByPlayer[playerId] = sections
                }
                if (playerTag.contains(AUTO_SET_TAG, Tag.TAG_BYTE.toInt()) && playerTag.getBoolean(AUTO_SET_TAG)) {
                    data.autoSetByPlayer[playerId] = true
                    if (playerId !in data.sectionsByPlayer) {
                        data.sectionsByPlayer[playerId] = linkedSetOf()
                    }
                }
            }
            return data
        }
    }
}
