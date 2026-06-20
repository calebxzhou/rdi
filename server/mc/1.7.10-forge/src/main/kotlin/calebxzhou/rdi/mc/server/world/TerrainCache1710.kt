package calebxzhou.rdi.mc.server.world

import calebxzhou.rdi.mc.common.RDI
import calebxzhou.rdi.mc.server.mixin.AAnvilChunkLoader1710
import net.minecraft.nbt.CompressedStreamTools
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.world.World
import net.minecraft.world.chunk.Chunk
import net.minecraft.world.chunk.storage.AnvilChunkLoader
import net.minecraft.world.chunk.storage.RegionFileCache
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File

object TerrainCache1710 {
    private val lgr: Logger = LogManager.getLogger("rdi")
    private val warnedUnsafeCacheDirs = mutableSetOf<String>()
    private val safeCacheDirs = HashMap<Int, Boolean>()

    val isEnabled: Boolean
        get() = RDI.ONLY_SAVE_FIRM_SECTIONS && RDI.TERRAIN_CACHE_PATH != null

    @JvmStatic
    fun loadChunk(world: World, chunkX: Int, chunkZ: Int): Chunk? {
        if (!isEnabled) {
            return null
        }

        try {
            val cacheDir = worldCacheDir(world)
            if (!isCacheDirSafe(world, cacheDir)) {
                return null
            }
            if (!regionFile(cacheDir, chunkX, chunkZ).exists()) {
                return null
            }
            return AnvilChunkLoader(cacheDir).loadChunk(world, chunkX, chunkZ)
        } catch (e: Exception) {
            lgr.error(
                "Failed to load terrain cache chunk " + chunkX + "," + chunkZ + " in dimension " + world.provider.dimensionId,
                e
            )
            return null
        }
    }

    @JvmStatic
    fun saveChunk(world: World, chunk: Chunk) {
        if (!isEnabled) {
            return
        }

        try {
            val chunkTag = NBTTagCompound()
            val levelTag = NBTTagCompound()
            chunkTag.setTag("Level", levelTag)

            val cacheDir = worldCacheDir(world)
            if (!isCacheDirSafe(world, cacheDir)) {
                return
            }
            val loader = AnvilChunkLoader(cacheDir)
            (loader as AAnvilChunkLoader1710).`rdi$writeChunkToNbt`(chunk, world, levelTag)

            val out = RegionFileCache.getChunkOutputStream(cacheDir, chunk.xPosition, chunk.zPosition)
            out.use { out ->
                CompressedStreamTools.write(chunkTag, out)
            }
        } catch (e: Exception) {
            lgr.error(
                "Failed to save terrain cache chunk " + chunk.xPosition + "," + chunk.zPosition + " in dimension " + world.provider.dimensionId,
                e
            )
        }
    }

    private fun worldCacheDir(world: World): File {
        return File(File(RDI.TERRAIN_CACHE_PATH, "dim"), world.provider.dimensionId.toString())
    }

    @JvmStatic
    fun closeAll() {
        RegionFileCache.clearRegionFileReferences()
        synchronized(safeCacheDirs) {
            safeCacheDirs.clear()
        }
    }

    private fun isCacheDirSafe(world: World, cacheDir: File): Boolean {
        val dimensionId = world.provider.dimensionId
        synchronized(safeCacheDirs) {
            safeCacheDirs[dimensionId]?.let { return it }
        }
        val cachePath = cacheDir.canonicalFile.path
        val worldPath = world.saveHandler.worldDirectory.canonicalFile.path
        val overlaps = cachePath == worldPath ||
            cachePath.startsWith(worldPath + File.separator) ||
            worldPath.startsWith(cachePath + File.separator)
        val safe = !overlaps
        synchronized(safeCacheDirs) {
            safeCacheDirs[dimensionId] = safe
        }
        if (!safe) {
            synchronized(warnedUnsafeCacheDirs) {
                if (warnedUnsafeCacheDirs.add(cachePath)) {
                    lgr.error("Disabled terrain cache because cache path overlaps world save path: cache=$cachePath world=$worldPath")
                }
            }
        }
        return safe
    }

    private fun regionFile(cacheDir: File, chunkX: Int, chunkZ: Int): File {
        return File(File(cacheDir, "region"), "r." + (chunkX shr 5) + "." + (chunkZ shr 5) + ".mca")
    }
}
