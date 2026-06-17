package calebxzhou.rdi.mc.server.firmsection

import calebxzhou.rdi.mc.firmsection.FirmSectionKey
import calebxzhou.rdi.mc.firmsection.FirmSectionSetStatus
import calebxzhou.rdi.mc.firmsection.FirmSectionState
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraft.world.storage.WorldSavedData
import net.minecraftforge.common.util.Constants
import java.util.UUID

class FirmSectionSavedData112(name: String) : WorldSavedData(name) {
    private val state = FirmSectionState()

    fun allSections(): List<FirmSectionKey> = state.allSections()

    fun isAutoSetEnabled(playerId: UUID): Boolean = state.isAutoSetEnabled(playerId)

    fun setAutoSetEnabled(playerId: UUID, enabled: Boolean) {
        if (state.setAutoSetEnabled(playerId, enabled)) {
            markDirty()
        }
    }

    fun hasFirmChunk(dimensionId: String, chunkX: Int, chunkZ: Int): Boolean =
        state.hasFirmChunk(dimensionId, chunkX, chunkZ)

    fun hasFirmSection(dimensionId: String, chunkX: Int, sectionY: Int, chunkZ: Int): Boolean =
        state.hasFirmSection(dimensionId, chunkX, sectionY, chunkZ)

    fun set(playerId: UUID, key: FirmSectionKey) = state.set(playerId, key).also {
        if (it.status == FirmSectionSetStatus.ADDED) {
            markDirty()
        }
    }

    fun unset(playerId: UUID, key: FirmSectionKey) = state.unset(playerId, key).also {
        if (it.removed) {
            markDirty()
        }
    }

    fun list(playerId: UUID) = state.list(playerId)

    override fun readFromNBT(tag: NBTTagCompound) {
        state.clear()
        val players = tag.getTagList(PLAYERS_TAG, Constants.NBT.TAG_COMPOUND)
        for (playerIndex in 0 until players.tagCount()) {
            val playerTag = players.getCompoundTagAt(playerIndex)
            val playerId = runCatching { UUID.fromString(playerTag.getString(UUID_TAG)) }.getOrNull() ?: continue
            val sections = linkedSetOf<FirmSectionKey>()
            val sectionTags = playerTag.getTagList(SECTIONS_TAG, Constants.NBT.TAG_COMPOUND)
            for (sectionIndex in 0 until sectionTags.tagCount()) {
                val sectionTag = sectionTags.getCompoundTagAt(sectionIndex)
                sections += FirmSectionKey(
                    sectionTag.getString(DIMENSION_TAG),
                    sectionTag.getInteger(CHUNK_X_TAG),
                    sectionTag.getInteger(SECTION_Y_TAG),
                    sectionTag.getInteger(CHUNK_Z_TAG)
                )
            }
            state.loadPlayer(playerId, sections, playerTag.getBoolean(AUTO_SET_TAG))
        }
    }

    override fun writeToNBT(tag: NBTTagCompound): NBTTagCompound {
        val players = NBTTagList()
        state.players().forEach { player ->
            val playerTag = NBTTagCompound()
            playerTag.setString(UUID_TAG, player.playerId.toString())
            playerTag.setTag(SECTIONS_TAG, player.sections.toTag())
            playerTag.setBoolean(AUTO_SET_TAG, player.autoSet)
            players.appendTag(playerTag)
        }
        tag.setTag(PLAYERS_TAG, players)
        return tag
    }

    private fun Collection<FirmSectionKey>.toTag(): NBTTagList {
        val tags = NBTTagList()
        forEach { section ->
            val tag = NBTTagCompound()
            tag.setString(DIMENSION_TAG, section.dimensionId)
            tag.setInteger(CHUNK_X_TAG, section.chunkX)
            tag.setInteger(SECTION_Y_TAG, section.sectionY)
            tag.setInteger(CHUNK_Z_TAG, section.chunkZ)
            tags.appendTag(tag)
        }
        return tags
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
    }
}
