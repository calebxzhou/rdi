package calebxzhou.rdi.mc.server.world

import calebxzhou.rdi.mc.common.RDI
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.storage.RegionFileStorage
import net.minecraft.world.level.chunk.storage.RegionStorageInfo
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.nio.file.Path
import java.util.Optional
import kotlin.io.path.exists

object TerrainCache211 {
    private val lgr: Logger = LogManager.getLogger("rdi")
    private val warnedUnsafeCacheDirs = mutableSetOf<String>()
    private val safeCacheDirs = HashMap<String, Boolean>()
    private val storages = HashMap<String, TerrainStorage>()

    val isEnabled: Boolean
        get() = RDI.ONLY_SAVE_FIRM_SECTIONS && RDI.TERRAIN_CACHE_PATH != null

    @JvmStatic
    fun read(level: ServerLevel, dimensionPath: Path?, pos: ChunkPos): Optional<CompoundTag> {
        if (!isEnabled || dimensionPath == null) {
            return Optional.empty()
        }

        try {
            val cacheRegionDir = cacheRegionDir(level)
            if (!isCacheDirSafe(level, cacheRegionDir, dimensionPath)) {
                return Optional.empty()
            }
            if (!regionFile(cacheRegionDir, pos).exists()) {
                return Optional.empty()
            }

            val storage = storage(level, cacheRegionDir)
            synchronized(storage.storage) {
                return Optional.ofNullable(storage.storage.read(pos))
            }
        } catch (e: Exception) {
            lgr.error("Failed to load terrain cache chunk ${pos.x},${pos.z} in dimension ${level.dimension().location()}", e)
            return Optional.empty()
        }
    }

    private fun storageInfo(level: ServerLevel): RegionStorageInfo =
        RegionStorageInfo(level.server.worldData.levelName, level.dimension(), "rdi-terrain-cache")

    private fun cacheDimensionDir(level: ServerLevel): Path {
        val id = level.dimension().location()
        return Path.of(RDI.TERRAIN_CACHE_PATH, "dim", id.namespace, id.path)
    }

    private fun cacheRegionDir(level: ServerLevel): Path = cacheDimensionDir(level).resolve("region")

    private fun regionFile(regionDir: Path, pos: ChunkPos): Path =
        regionDir.resolve("r.${pos.regionX}.${pos.regionZ}.mca")

    @JvmStatic
    fun closeAll() {
        val openStorages = synchronized(storages) {
            storages.values.toList().also { storages.clear() }
        }
        for (storage in openStorages) {
            try {
                synchronized(storage.storage) {
                    storage.storage.close()
                }
            } catch (e: Exception) {
                lgr.error("Failed to close terrain cache storage ${storage.regionDir}", e)
            }
        }
        synchronized(safeCacheDirs) {
            safeCacheDirs.clear()
        }
    }

    private fun storage(level: ServerLevel, cacheRegionDir: Path): TerrainStorage {
        val key = level.dimension().location().toString()
        return synchronized(storages) {
            storages.getOrPut(key) {
                TerrainStorage(cacheRegionDir, RegionFileStorage(storageInfo(level), cacheRegionDir, false))
            }
        }
    }

    private fun isCacheDirSafe(level: ServerLevel, cacheRegionDir: Path, dimensionPath: Path): Boolean {
        val key = level.dimension().location().toString()
        synchronized(safeCacheDirs) {
            safeCacheDirs[key]?.let { return it }
        }
        val cacheDir = cacheRegionDir.toFile().canonicalFile
        val worldDir = dimensionPath.toFile().canonicalFile
        val cachePath = cacheDir.path
        val worldPath = worldDir.path
        val overlaps = cachePath == worldPath ||
            cachePath.startsWith(worldPath + java.io.File.separator) ||
            worldPath.startsWith(cachePath + java.io.File.separator)
        val safe = !overlaps
        synchronized(safeCacheDirs) {
            safeCacheDirs[key] = safe
        }
        if (!safe) {
            synchronized(warnedUnsafeCacheDirs) {
                if (warnedUnsafeCacheDirs.add(cachePath)) {
                    lgr.error("Disabled terrain cache because cache path overlaps world path: cache=$cachePath world=$worldPath")
                }
            }
        }
        return safe
    }

    private data class TerrainStorage(val regionDir: Path, val storage: RegionFileStorage)
}
