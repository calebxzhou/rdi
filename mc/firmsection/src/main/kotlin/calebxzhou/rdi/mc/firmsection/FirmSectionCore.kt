package calebxzhou.rdi.mc.firmsection

import java.util.UUID

data class FirmSectionKey(
    val dimensionId: String,
    val chunkX: Int,
    val sectionY: Int,
    val chunkZ: Int,
)

data class FirmSectionPlayerData(
    val playerId: UUID,
    val sections: List<FirmSectionKey>,
    val autoSet: Boolean,
)

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

object FirmSectionLimits {
    val maxTotal: Int = Integer.getInteger("rdi.firmSectionTotalMax", 256)
    val maxPerson: Int = Integer.getInteger("rdi.firmSectionPersonMax", 0)
}

class FirmSectionState(
    private val maxTotal: Int = FirmSectionLimits.maxTotal,
    private val maxPerson: Int = FirmSectionLimits.maxPerson,
) {
    private val sectionsByPlayer = linkedMapOf<UUID, LinkedHashSet<FirmSectionKey>>()
    private val autoSetPlayers = linkedSetOf<UUID>()

    fun totalCount(): Int = sectionsByPlayer.values.sumOf { it.size }

    fun playerCount(playerId: UUID): Int = sectionsByPlayer[playerId]?.size ?: 0

    fun sectionsOf(playerId: UUID): List<FirmSectionKey> =
        sectionsByPlayer[playerId]?.sortedWith(KEY_ORDER) ?: emptyList()

    fun allSections(): List<FirmSectionKey> =
        sectionsByPlayer.values.flatten().distinct().sortedWith(KEY_ORDER)

    fun players(): List<FirmSectionPlayerData> =
        sectionsByPlayer.map { (playerId, sections) ->
            FirmSectionPlayerData(playerId, sections.sortedWith(KEY_ORDER), isAutoSetEnabled(playerId))
        }

    fun loadPlayer(playerId: UUID, sections: Collection<FirmSectionKey>, autoSet: Boolean) {
        if (sections.isNotEmpty() || autoSet) {
            sectionsByPlayer[playerId] = LinkedHashSet(sections)
        }
        if (autoSet) {
            autoSetPlayers += playerId
        } else {
            autoSetPlayers -= playerId
        }
    }

    fun clear() {
        sectionsByPlayer.clear()
        autoSetPlayers.clear()
    }

    fun isAutoSetEnabled(playerId: UUID): Boolean = playerId in autoSetPlayers

    fun setAutoSetEnabled(playerId: UUID, enabled: Boolean): Boolean {
        if (isAutoSetEnabled(playerId) == enabled) {
            return false
        }
        if (enabled) {
            autoSetPlayers += playerId
            sectionsByPlayer.getOrPut(playerId) { linkedSetOf() }
        } else {
            autoSetPlayers -= playerId
            if (sectionsByPlayer[playerId].isNullOrEmpty()) {
                sectionsByPlayer -= playerId
            }
        }
        return true
    }

    fun set(playerId: UUID, key: FirmSectionKey): FirmSectionSetResult {
        val existingSections = sectionsByPlayer[playerId]
        val status = when {
            existingSections != null && key in existingSections -> FirmSectionSetStatus.ALREADY_PRESENT
            maxPerson > 0 && (existingSections?.size ?: 0) >= maxPerson ->
                FirmSectionSetStatus.PLAYER_LIMIT_REACHED
            totalCount() >= maxTotal -> FirmSectionSetStatus.TOTAL_LIMIT_REACHED
            else -> {
                sectionsByPlayer.getOrPut(playerId) { linkedSetOf() } += key
                FirmSectionSetStatus.ADDED
            }
        }
        return FirmSectionSetResult(status, key, playerCount(playerId), totalCount())
    }

    fun unset(playerId: UUID, key: FirmSectionKey): FirmSectionUnsetResult {
        val sections = sectionsByPlayer[playerId]
        val removed = sections?.remove(key) == true
        if (sections != null && removed && sections.isEmpty() && !isAutoSetEnabled(playerId)) {
            sectionsByPlayer -= playerId
        }
        return FirmSectionUnsetResult(removed, key, playerCount(playerId), totalCount())
    }

    fun list(playerId: UUID): FirmSectionListResult =
        FirmSectionListResult(sectionsOf(playerId), playerCount(playerId), totalCount())

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

    companion object {
        private val KEY_ORDER = compareBy<FirmSectionKey>(
            { it.dimensionId },
            { it.chunkX },
            { it.sectionY },
            { it.chunkZ },
        )
    }
}
