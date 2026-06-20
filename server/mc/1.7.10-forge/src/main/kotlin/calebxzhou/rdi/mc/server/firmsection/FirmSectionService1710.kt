package calebxzhou.rdi.mc.server.firmsection

import calebxzhou.rdi.mc.common.RDI
import calebxzhou.rdi.mc.firmsection.FirmSectionKey
import calebxzhou.rdi.mc.firmsection.FirmSectionListResult
import calebxzhou.rdi.mc.firmsection.FirmSectionSetResult
import calebxzhou.rdi.mc.firmsection.FirmSectionSetStatus
import calebxzhou.rdi.mc.firmsection.FirmSectionUnsetResult
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.server.MinecraftServer
import net.minecraft.util.MathHelper
import net.minecraft.world.World
import net.minecraft.world.chunk.Chunk

object FirmSectionService1710 {
    fun isEnabled(): Boolean = RDI.ONLY_SAVE_FIRM_SECTIONS

    fun isAutoSetEnabled(player: EntityPlayerMP): Boolean = data(player.mcServer).isAutoSetEnabled(player.uniqueID)

    fun setAutoSetEnabled(player: EntityPlayerMP, enabled: Boolean) {
        data(player.mcServer).setAutoSetEnabled(player.uniqueID, enabled)
    }

    fun set(player: EntityPlayerMP): FirmSectionSetResult {
        return set(player, player.worldObj, player.posX, player.posY, player.posZ)
    }

    fun set(player: EntityPlayerMP, world: World, x: Double, y: Double, z: Double): FirmSectionSetResult {
        val key = target(world, x, y, z)
        val result = data(player.mcServer).set(player.uniqueID, key)
        if (result.status == FirmSectionSetStatus.ADDED) {
            world.getChunkFromChunkCoords(key.chunkX, key.chunkZ).isModified = true
        }
        return result
    }

    fun unset(player: EntityPlayerMP): FirmSectionUnsetResult {
        return data(player.mcServer).unset(player.uniqueID, target(player.worldObj, player.posX, player.posY, player.posZ))
    }

    fun list(player: EntityPlayerMP): FirmSectionListResult {
        return data(player.mcServer).list(player.uniqueID)
    }

    fun shouldSaveChunk(world: World, chunk: Chunk): Boolean {
        if (!RDI.ONLY_SAVE_FIRM_SECTIONS) {
            return true
        }
        return data(MinecraftServer.getServer()).hasFirmChunk(
            dimensionId = dimensionId(world),
            chunkX = chunk.xPosition,
            chunkZ = chunk.zPosition,
        )
    }

    fun all(server: MinecraftServer): List<FirmSectionKey> = data(server).allSections()

    private fun data(server: MinecraftServer): FirmSectionSavedData1710 {
        val storage = server.worldServerForDimension(0).mapStorage
        val existing = storage.loadData(FirmSectionSavedData1710::class.java, FirmSectionSavedData1710.FILE_ID)
        if (existing is FirmSectionSavedData1710) {
            return existing
        }
        val created = FirmSectionSavedData1710(FirmSectionSavedData1710.FILE_ID)
        storage.setData(FirmSectionSavedData1710.FILE_ID, created)
        return created
    }

    private fun target(world: World, x: Double, y: Double, z: Double): FirmSectionKey =
        FirmSectionKey(
            dimensionId = dimensionId(world),
            chunkX = blockToSectionCoord(x),
            sectionY = blockToSectionCoord(y),
            chunkZ = blockToSectionCoord(z),
        )

    private fun dimensionId(world: World): String = "legacy:${world.provider.dimensionId}"

    private fun blockToSectionCoord(value: Double): Int = MathHelper.floor_double(value) shr 4
}
