package calebxzhou.rdi.master.service

import calebxzhou.mykotutils.std.displayLength
import calebxzhou.rdi.master.DB
import calebxzhou.rdi.common.model.ChunkSurfaceMap
import calebxzhou.rdi.common.model.PalettedContainer
import calebxzhou.rdi.common.model.RegionSurfaceMap
import calebxzhou.rdi.common.model.World
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.master.net.*
import calebxzhou.rdi.master.service.WorldService.createWorld
import calebxzhou.mykotutils.log.Loggers
import calebxzhou.mykotutils.std.deleteRecursivelyNoSymlink
import calebxzhou.rdi.common.anvilrw.core.Chunk
import calebxzhou.rdi.common.anvilrw.format.AnvilReader
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.isDav
import calebxzhou.rdi.common.model.world.RChunkPos
import calebxzhou.rdi.common.util.ioScope
import calebxzhou.rdi.common.util.validateName
import calebxzhou.rdi.master.WORLDS_DIR
import calebxzhou.rdi.master.service.WorldService.getDimensions
import calebxzhou.rdi.master.service.WorldService.readSurfaceCacheOnly
import calebxzhou.rdi.master.service.WorldService.startSurfaceCacheBuild
import calebxzhou.rdi.master.service.WorldService.toVo
import calebxzhou.rdi.master.service.WorldService.world
import com.mongodb.client.model.CreateCollectionOptions
import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.or
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.Updates.set
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import net.benwoodworth.knbt.nbtByte
import net.benwoodworth.knbt.nbtCompound
import net.benwoodworth.knbt.nbtInt
import net.benwoodworth.knbt.nbtList
import net.benwoodworth.knbt.nbtLongArray
import net.benwoodworth.knbt.nbtString
import org.bson.Document
import org.bson.types.ObjectId
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.max

fun Route.worldRoutes() = route("/world") {
    get {
        response(data = WorldService.listByOwnerVo(uid))
    }
    post {
        val modpackId = ObjectId(param("modpackId"))
        createWorld(uid, paramNull("name"), modpackId)
    }
    route("/{worldId}") {
        get("/dimensions"){
            response(data = call.world().getDimensions())
        }
        post("/surface/build"){
            val msg = call.world().startSurfaceCacheBuild(uid)
            ok(msg)
        }
        get("/surface/{dimension}") {
            val dimension = call.pathParam("dimension")
            val scaleLevel = call.paramNull("scale")?.toIntOrNull() ?: throw RequestError("scale nan")
            val scale = runCatching { World.Scale.fromLevel(scaleLevel) }.getOrElse {
                throw RequestError("scale should be 0~7")
            }
            val regions = call.paramNull("regions")
            val chunks = call.paramNull("chunks")
            response(data = call.world().readSurfaceCacheOnly(dimension, regions, chunks, scale))
        }
        post("/copy") {
            val sourceId = ObjectId(param("worldId"))
            val name = paramNull("name")
            val world = WorldService.duplicate(uid, sourceId, name)
            response(data = world.toVo())
        }
        delete {
            val worldId = ObjectId(param("worldId"))
            WorldService.delete(uid, worldId)
            ok()
        }
    }


}

object WorldService {
    private const val PLAYER_MAX_WORLD = 5
    private const val CHUNK_SIDE = 16
    private const val REGION_CHUNK_SIDE = 32
    private const val REGION_CHUNK_COUNT = REGION_CHUNK_SIDE * REGION_CHUNK_SIDE
    private const val DEFAULT_BLOCK_ID = "minecraft:air"
    private const val WORLD_SURFACE_COLLECTION = "world_surface"
    private const val MAX_SURFACE_QUERY_CHUNKS = 65_536
    private const val MAX_SURFACE_QUERY_REGIONS = 4_096
    private const val MAX_SURFACE_QUERY_TOKENS = 512
    private val DIMENSION_CODE_REGEX = Regex("^[a-z0-9_.-]+:[a-z0-9_./-]+$")
    private const val SURFACE_MAIL_PROGRESS_REPORT_INTERVAL = 512
    private const val SURFACE_REGION_PROGRESS_REPORT_INTERVAL = 8
    private const val SURFACE_MAX_CONCURRENT_REQUESTS = 2
    private val lgr by Loggers
    private val regionFileRegex = Regex("""^r\.(-?\d+)\.(-?\d+)\.mca$""")
    private val surfaceRequestSemaphore = Semaphore(
        System.getProperty("rdi.surface.concurrent")
            ?.toIntOrNull()
            ?.coerceIn(1, 8)
            ?: SURFACE_MAX_CONCURRENT_REQUESTS
    )
    private val surfaceDimensionConcurrency: Int =
        System.getProperty("rdi.surface.dimension.concurrent")
            ?.toIntOrNull()
            ?.coerceIn(1, 4)
            ?: 2
    private val surfaceRegionConcurrency: Int =
        System.getProperty("rdi.surface.region.concurrent")
            ?.toIntOrNull()
            ?.coerceIn(1, 8)
            ?: 4

    private val worldSurfaceBuildInProgress = ConcurrentHashMap.newKeySet<String>()
    val dbcl = DB.getCollection<World>("world")
    private val worldSurfaceCol = DB.getCollection<Document>(WORLD_SURFACE_COLLECTION)

    init {
        runBlocking {
            ensureWorldCacheCollectionAndIndexes()
        }
    }

    fun getDir(worldId: ObjectId) = WORLDS_DIR.resolve(worldId.toHexString())
    fun getDataDir(worldId: ObjectId) = getDir(worldId).resolve("data").also { it.mkdir() }
    val World.dir get() = getDir(_id)
    val World.dataDir get() = getDataDir(_id)
    suspend fun getById(id: ObjectId): World? = dbcl.find(eq("_id", id)).firstOrNull()

    suspend fun listByOwner(ownerId: ObjectId): List<World> =
        dbcl.find(eq("ownerId", ownerId)).toList()

    suspend fun listByOwnerVo(ownerId: ObjectId): List<World.Vo> =
        listByOwner(ownerId).map { it.toVo() }

    suspend fun World.toVo(): World.Vo {
        val modpack = ModpackService.getById(modpackId)
        return World.Vo(
            id = _id,
            name = name,
            ownerId = ownerId,
            size = size,
            modpackId = modpackId,
            modpackName = modpack?.name ?: "未知整合包",
            modpackIconUrl = modpack?.iconUrl
        )
    }

    suspend fun ApplicationCall.world(): World {
        return getById(idPathParam("worldId")) ?: throw RequestError("无此地图")
    }

    suspend fun updateWorldSize(worldId: ObjectId): Long {
        val worldDir = getDir(worldId)
        val totalSize = if (worldDir.exists()) {
            worldDir.walkTopDown()
                .filter { it.isFile && !Files.isSymbolicLink(it.toPath()) }
                .sumOf { it.length() }
        } else {
            0L
        }
        dbcl.updateOne(eq("_id", worldId), set(World::size.name, totalSize))
        return totalSize
    }

    suspend fun RAccount.ownWorlds() = WorldService.listByOwner(_id)

    private suspend fun ensureCapacity(ownerId: ObjectId) {
        val player = PlayerService.getById(ownerId)
        if (listByOwner(ownerId).size >= PLAYER_MAX_WORLD && player?.isDav == false) {
            throw RequestError("存档最多${PLAYER_MAX_WORLD}个")
        }
    }

    suspend fun createWorld(uid: ObjectId, name: String?, packId: ObjectId): World {
        val modpack = ModpackService.getById(packId) ?: throw RequestError("整合包不存在")
        val name = name ?: "存档${listByOwner(uid).size + 1}"
        name.validateName()
        ensureCapacity(uid)
        val world = World(name = name, ownerId = uid, modpackId = packId)
        world.dir.mkdir()
        dbcl.insertOne(world)
        return world
    }

    suspend fun duplicate(uid: ObjectId, worldId: ObjectId, newName: String?): World {
        val world = getById(worldId) ?: throw RequestError("存档不存在")
        val newName = newName ?: (world.name + "副本")
        ensureCapacity(uid)
        if (newName.displayLength > 64) throw RequestError("名称过长")
        if (world.ownerId != uid) throw RequestError("无权限")
        val newWorld = World(name = newName, ownerId = uid, modpackId = world.modpackId)
        val sourceDir = world.dir
        if (!sourceDir.exists() || !sourceDir.isDirectory) {
            throw RequestError("存档不存在或创建失败")
        }
        newWorld.dir.mkdirs()
        sourceDir.copyRecursively(newWorld.dir, overwrite = true)
        dbcl.insertOne(newWorld)
        return newWorld
    }

    suspend fun delete(uid: ObjectId, worldId: ObjectId) {
        val world = getById(worldId) ?: throw RequestError("存档不存在")
        if (world.ownerId != uid) throw RequestError("无权限")
        HostService.findByWorld(worldId)?.let { throw RequestError("须先删除地图“${it.name}”，再删除此区块数据") }
        dbcl.deleteOne(eq("_id", worldId))
        worldSurfaceCol.deleteMany(eq("worldId", worldId))
        val dir = world.dir
        if (dir.exists()) {
            dir.deleteRecursivelyNoSymlink()
        }
    }

    private data class SurfaceSection(
        val sectionY: Int,
        val palette: List<String>,
        val data: LongArray?
    )

    private data class RegionFileMeta(
        val file: File,
        val regionX: Int,
        val regionZ: Int
    )

    private data class RegionHeader(
        val chunkExists: BooleanArray,
        val chunkTimes: IntArray,
        val existingChunkIndices: IntArray
    )

    private data class RegionBuildPlan(
        val region: RegionFileMeta,
        val header: RegionHeader
    )

    private data class ChunkCoord(val x: Int, val z: Int)

    fun World.getDimensions(): List<String> {
        val worldRootDir = this.dataDir.resolve("world")
        val dimensions = linkedSetOf<String>()

        if (worldRootDir.resolve("region").isDirectory) {
            dimensions += "minecraft:overworld"
        }
        if (worldRootDir.resolve("DIM-1").resolve("region").isDirectory) {
            dimensions += "minecraft:the_nether"
        }
        if (worldRootDir.resolve("DIM1").resolve("region").isDirectory) {
            dimensions += "minecraft:the_end"
        }

        val customDimensionsRoot = worldRootDir.resolve("dimensions")
        if (customDimensionsRoot.isDirectory) {
            val customDimensions = mutableSetOf<String>()
            customDimensionsRoot.listFiles()
                ?.asSequence()
                ?.filter { it.isDirectory }
                ?.sortedBy { it.name }
                ?.forEach { namespaceDir ->
                    val namespace = namespaceDir.name
                    namespaceDir.walkTopDown()
                        .onEnter { it.name != "region" }
                        .filter { it.isDirectory && it.resolve("region").isDirectory }
                        .forEach { dimensionDir ->
                            val path = namespaceDir.toPath()
                                .relativize(dimensionDir.toPath())
                                .toString()
                                .replace(File.separatorChar, '/')
                            if (path.isBlank()) return@forEach
                            val code = "$namespace:$path"
                            runCatching { normalizeDimensionCode(code) }
                                .getOrNull()
                                ?.let { customDimensions += it }
                        }
                }
            dimensions += customDimensions.sorted()
        }
        return dimensions.toList()
    }

    suspend fun World.startSurfaceCacheBuild(
        receiverId: ObjectId
    ): String {
        val worldKey = _id.toHexString()
        if (!worldSurfaceBuildInProgress.add(worldKey)) {
            return "地图缓存正在全量构建中，请稍后到邮件查看进度"
        }
        ioScope.launch {
            buildAllChunkSurfaceCachesInBackground(receiverId, worldKey)
        }
        return "已开始全量构建地图缓存（全部维度/区块），进度将通过邮件通知"
    }

    suspend fun World.readSurfaceCacheOnly(
        dimension: String,
        regionsRaw: String?,
        chunksRaw: String?,
        scale: World.Scale
    ): World.SurfaceQueryDto = surfaceRequestSemaphore.withPermit {
        val dimensionCode = normalizeDimensionCode(dimension)
        composeSurfaceQueryFromRegionCaches(dimensionCode, regionsRaw, chunksRaw, scale)
    }

    private suspend fun World.buildAllChunkSurfaceCachesInBackground(
        receiverId: ObjectId,
        worldKey: String
    ) {
        val mailId = runCatching {
            MailService.sendSystemMail(
                receiverId = receiverId,
                title = "地图缓存构建中：$name",
                content = "开始全量构建地图缓存（全部维度/区块）\n"
            )._id
        }.onFailure {
            lgr.warn(it) { "发送地图缓存构建邮件失败: world=${_id.toHexString()}" }
        }.getOrNull()

        fun updateMail(message: String, title: String? = null, append: Boolean = true) {
            mailId?.let { MailService.changeMail(it, newTitle = title, newContent = message, append = append) }
        }

        try {
            val dimensions = getDimensions()
                .map { normalizeDimensionCode(it) }
                .distinct()
            if (dimensions.isEmpty()) {
                updateMail("没有可构建的维度数据", title = "地图缓存构建结束：$name")
                return
            }

            val plansByDimension = linkedMapOf<String, List<RegionBuildPlan>>()
            var totalRegionCount = 0
            var totalChunkCount = 0
            for (dimension in dimensions) {
                val plans = collectRegionBuildPlansForDimension(dimension)
                plansByDimension[dimension] = plans
                totalRegionCount += plans.size
                totalChunkCount += plans.sumOf { it.header.existingChunkIndices.size }
            }
            if (totalRegionCount <= 0 || totalChunkCount <= 0) {
                updateMail("未发现可用区块，无需构建缓存", title = "地图缓存构建结束：$name")
                return
            }

            val processedRegions = AtomicInteger(0)
            val processedChunks = AtomicInteger(0)
            val successChunks = AtomicInteger(0)
            val skippedChunks = AtomicInteger(0)
            val failedChunks = AtomicInteger(0)
            val dimensionSemaphore = Semaphore(surfaceDimensionConcurrency)
            val regionSemaphore = Semaphore(surfaceRegionConcurrency)

            updateMail("维度 ${dimensions.size} 个，区域 $totalRegionCount 个，区块 $totalChunkCount 个")

            coroutineScope {
                dimensions.map { dimensionCode ->
                    async(Dispatchers.IO) {
                        dimensionSemaphore.withPermit {
                            val regionPlans = plansByDimension[dimensionCode].orEmpty()
                            if (regionPlans.isEmpty()) return@withPermit
                            coroutineScope {
                                regionPlans.map { plan ->
                                    async(Dispatchers.IO) {
                                        regionSemaphore.withPermit {
                                            processRegionBuildPlan(
                                                dimensionCode = dimensionCode,
                                                plan = plan,
                                                processedChunks = processedChunks,
                                                successChunks = successChunks,
                                                skippedChunks = skippedChunks,
                                                failedChunks = failedChunks,
                                                totalChunkCount = totalChunkCount,
                                                onProgress = { updateMail(it) }
                                            )
                                            val regionsDone = processedRegions.incrementAndGet()
                                            if (
                                                regionsDone == totalRegionCount ||
                                                regionsDone % SURFACE_REGION_PROGRESS_REPORT_INTERVAL == 0
                                            ) {
                                                updateMail(
                                                    "区域进度 $regionsDone/$totalRegionCount · 区块进度 ${processedChunks.get()}/$totalChunkCount"
                                                )
                                            }
                                        }
                                    }
                                }.awaitAll()
                            }
                        }
                    }
                }.awaitAll()
            }

            val finalTitle = if (failedChunks.get() == 0) {
                "地图缓存构建完成：$name"
            } else {
                "地图缓存构建完成（部分失败）：$name"
            }
            updateMail(
                "构建结束：区块总计 $totalChunkCount，成功 ${successChunks.get()}，跳过 ${skippedChunks.get()}，失败 ${failedChunks.get()}",
                title = finalTitle
            )
        } catch (cancelled: CancellationException) {
            updateMail("构建任务已取消", title = "地图缓存构建已取消：$name")
            throw cancelled
        } catch (e: Throwable) {
            lgr.error(e) { "surface cache build crashed: world=${_id.toHexString()}" }
            updateMail("构建任务异常终止：${e.message ?: "unknown"}", title = "地图缓存构建失败：$name")
        } finally {
            worldSurfaceBuildInProgress.remove(worldKey)
        }
    }

    private suspend fun World.processRegionBuildPlan(
        dimensionCode: String,
        plan: RegionBuildPlan,
        processedChunks: AtomicInteger,
        successChunks: AtomicInteger,
        skippedChunks: AtomicInteger,
        failedChunks: AtomicInteger,
        totalChunkCount: Int,
        onProgress: (String) -> Unit
    ) {
        val regionFile = plan.region.file
        val regionX = plan.region.regionX
        val regionZ = plan.region.regionZ
        val existingDoc = readRegionSurfaceMap(dimensionCode, regionX, regionZ)
        val existingChunkMap = existingDoc?.chunks
            ?.associateBy { it.chunkX to it.chunkZ }
            ?.toMutableMap()
            ?: hashMapOf()
        val oldChunkTimes = existingDoc?.chunkTimes
        var docChanged = existingDoc == null

        for (localIndex in 0 until REGION_CHUNK_COUNT) {
            if (plan.header.chunkExists[localIndex]) continue
            val chunkCoord = regionChunkIndexToWorldChunk(regionX, regionZ, localIndex)
            if (existingChunkMap.remove(chunkCoord.x to chunkCoord.z) != null) {
                docChanged = true
            }
        }

        val changedIndices = ArrayList<Int>()
        for (localIndex in plan.header.existingChunkIndices) {
            val chunkCoord = regionChunkIndexToWorldChunk(regionX, regionZ, localIndex)
            val key = chunkCoord.x to chunkCoord.z
            val oldTimestamp = oldChunkTimes?.getOrNull(localIndex)
            val newTimestamp = plan.header.chunkTimes[localIndex]
            if (existingChunkMap.containsKey(key) && oldTimestamp != null && oldTimestamp == newTimestamp) {
                val processed = processedChunks.incrementAndGet()
                skippedChunks.incrementAndGet()
                reportBuildChunkProgress(processed, totalChunkCount, successChunks, skippedChunks, failedChunks, onProgress)
            } else {
                changedIndices += localIndex
            }
        }

        if (changedIndices.isNotEmpty()) {
            var changedProcessed = 0
            runCatching {
                AnvilReader(regionFile).use { reader ->
                    for (localIndex in changedIndices) {
                        val chunkCoord = regionChunkIndexToWorldChunk(regionX, regionZ, localIndex)
                        try {
                            val chunk = reader.readChunk(chunkCoord.x, chunkCoord.z)
                            if (chunk == null || chunk.isEmpty) {
                                if (existingChunkMap.remove(chunkCoord.x to chunkCoord.z) != null) {
                                    docChanged = true
                                }
                                skippedChunks.incrementAndGet()
                            } else {
                                val chunkSurface = buildChunkSurfaceMap(chunk)
                                if (chunkSurface != null) {
                                    existingChunkMap[chunkSurface.chunkX to chunkSurface.chunkZ] = chunkSurface
                                    docChanged = true
                                    successChunks.incrementAndGet()
                                } else {
                                    skippedChunks.incrementAndGet()
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (e: Throwable) {
                            failedChunks.incrementAndGet()
                            lgr.warn(e) {
                                "构建区块surface失败: world=${_id.toHexString()}, dim=$dimensionCode, " +
                                    "region=($regionX,$regionZ), localIndex=$localIndex"
                            }
                        } finally {
                            changedProcessed++
                            val processed = processedChunks.incrementAndGet()
                            reportBuildChunkProgress(
                                processed,
                                totalChunkCount,
                                successChunks,
                                skippedChunks,
                                failedChunks,
                                onProgress
                            )
                        }
                    }
                }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                lgr.warn(e) { "读取region失败: ${regionFile.absolutePath}" }
                val remaining = changedIndices.size - changedProcessed
                repeat(remaining.coerceAtLeast(0)) {
                    failedChunks.incrementAndGet()
                    val processed = processedChunks.incrementAndGet()
                    reportBuildChunkProgress(
                        processed,
                        totalChunkCount,
                        successChunks,
                        skippedChunks,
                        failedChunks,
                        onProgress
                    )
                }
            }
        }

        if (existingChunkMap.isEmpty()) {
            if (existingDoc != null) {
                deleteRegionSurfaceMap(dimensionCode, regionX, regionZ)
            }
            return
        }

        val chunkTimesChanged = existingDoc == null || !existingDoc.chunkTimes.contentEquals(plan.header.chunkTimes)
        if (docChanged || chunkTimesChanged) {
            writeRegionSurfaceMap(
                RegionSurfaceMap(
                    _id = existingDoc?._id ?: ObjectId(),
                    worldId = _id,
                    dimension = dimensionCode,
                    regionX = regionX,
                    regionZ = regionZ,
                    chunkTimes = plan.header.chunkTimes.copyOf(),
                    chunks = existingChunkMap.values.sortedWith(
                        compareBy<ChunkSurfaceMap> { it.chunkZ }.thenBy { it.chunkX }
                    )
                )
            )
        }
    }

    private fun reportBuildChunkProgress(
        processed: Int,
        total: Int,
        successChunks: AtomicInteger,
        skippedChunks: AtomicInteger,
        failedChunks: AtomicInteger,
        onProgress: (String) -> Unit
    ) {
        if (processed == total || processed % SURFACE_MAIL_PROGRESS_REPORT_INTERVAL == 0) {
            onProgress(
                "区块进度 $processed/$total · 成功 ${successChunks.get()} · " +
                    "跳过 ${skippedChunks.get()} · 失败 ${failedChunks.get()}"
            )
        }
    }

    private fun World.collectRegionBuildPlansForDimension(dimensionCode: String): List<RegionBuildPlan> {
        val worldRootDir = dataDir.resolve("world")
        val dimensionDataDir = resolveDimensionDataDir(worldRootDir, dimensionCode)
        val regionDir = dimensionDataDir.resolve("region")
        if (!regionDir.exists() || !regionDir.isDirectory) return emptyList()

        return regionDir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile }
            ?.mapNotNull(::parseRegionFileMeta)
            ?.sortedWith(compareBy<RegionFileMeta> { it.regionX }.thenBy { it.regionZ })
            ?.mapNotNull { meta ->
                val header = readRegionHeader(meta.file) ?: return@mapNotNull null
                RegionBuildPlan(meta, header)
            }
            ?.toList()
            ?: emptyList()
    }

    private fun parseRegionFileMeta(file: File): RegionFileMeta? {
        val match = regionFileRegex.matchEntire(file.name) ?: return null
        val regionX = match.groupValues[1].toIntOrNull() ?: return null
        val regionZ = match.groupValues[2].toIntOrNull() ?: return null
        return RegionFileMeta(file, regionX, regionZ)
    }

    private fun readRegionHeader(regionFile: File): RegionHeader? {
        return runCatching {
            RandomAccessFile(regionFile, "r").use { raf ->
                if (raf.length() < 8192L) return@use null
                val locations = ByteArray(4096)
                val timestamps = ByteArray(4096)
                raf.seek(0L)
                if (raf.read(locations) != 4096) return@use null
                if (raf.read(timestamps) != 4096) return@use null

                val exists = BooleanArray(REGION_CHUNK_COUNT)
                val chunkTimes = IntArray(REGION_CHUNK_COUNT)
                val existingIndices = IntArray(REGION_CHUNK_COUNT)
                var existingCount = 0

                for (chunkIndex in 0 until REGION_CHUNK_COUNT) {
                    val offset = chunkIndex * 4
                    val sectorOffset =
                        ((locations[offset].toInt() and 0xFF) shl 16) or
                            ((locations[offset + 1].toInt() and 0xFF) shl 8) or
                            (locations[offset + 2].toInt() and 0xFF)
                    val sectorCount = locations[offset + 3].toInt() and 0xFF
                    val hasChunk = sectorOffset != 0 && sectorCount != 0
                    exists[chunkIndex] = hasChunk
                    if (hasChunk) {
                        existingIndices[existingCount++] = chunkIndex
                    }
                    chunkTimes[chunkIndex] = readInt32(timestamps, chunkIndex * 4)
                }
                RegionHeader(
                    chunkExists = exists,
                    chunkTimes = chunkTimes,
                    existingChunkIndices = existingIndices.copyOf(existingCount)
                )
            }
        }.onFailure { e ->
            lgr.warn(e) { "读取region头失败: ${regionFile.absolutePath}" }
        }.getOrNull()
    }

    private fun readInt32(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun regionChunkIndexToWorldChunk(regionX: Int, regionZ: Int, chunkIndex: Int): ChunkCoord {
        val localX = chunkIndex and 31
        val localZ = chunkIndex ushr 5
        return ChunkCoord(
            x = regionX * REGION_CHUNK_SIDE + localX,
            z = regionZ * REGION_CHUNK_SIDE + localZ
        )
    }

    private fun buildChunkSurfaceMap(chunk: Chunk): ChunkSurfaceMap? {
        return try {
            val sections = buildSurfaceSections(chunk)
            val container = PalettedContainer(
                size = CHUNK_SIDE * CHUNK_SIDE,
                defaultValue = DEFAULT_BLOCK_ID
            )
            for (z in 0 until CHUNK_SIDE) {
                for (x in 0 until CHUNK_SIDE) {
                    val blockName = sampleSurfaceFromSections(x, z, sections)
                    container.set(z * CHUNK_SIDE + x, blockName)
                }
            }
            ChunkSurfaceMap(
                chunkX = chunk.x,
                chunkZ = chunk.z,
                data = container.toSnapshot()
            )
        } finally {
            chunk.releaseSurfaceReadData()
        }
    }

    private suspend fun World.composeSurfaceQueryFromRegionCaches(
        dimensionCode: String,
        regionsRaw: String?,
        chunksRaw: String?,
        scale: World.Scale
    ): World.SurfaceQueryDto {
        if (scale.level >= World.Scale.L5.level) {
            return composeWideScaleSurfaceQueryFromRegions(
                dimensionCode = dimensionCode,
                regionsRaw = regionsRaw,
                chunksRaw = chunksRaw,
                scale = scale
            )
        }

        val requestedChunks = parseRequestedChunks(regionsRaw, chunksRaw)
        if (requestedChunks.isEmpty()) {
            return World.SurfaceQueryDto(
                scale = scale.level,
                palette = listOf(DEFAULT_BLOCK_ID),
                chunks = emptyList(),
                missing = emptyList()
            )
        }

        val requestedByRegion = LinkedHashMap<Pair<Int, Int>, MutableList<ChunkCoord>>()
        for (coord in requestedChunks) {
            val regionKey = Math.floorDiv(coord.x, REGION_CHUNK_SIDE) to Math.floorDiv(coord.z, REGION_CHUNK_SIDE)
            requestedByRegion.getOrPut(regionKey) { arrayListOf() }.add(coord)
        }

        val cachedRegionMaps = readRegionSurfaceMaps(
            dimensionCode = dimensionCode,
            regionKeys = requestedByRegion.keys
        )

        val palette = mutableListOf(DEFAULT_BLOCK_ID)
        val paletteIndex = hashMapOf(DEFAULT_BLOCK_ID to 0)
        fun paletteId(blockId: String): Int {
            return paletteIndex[blockId] ?: palette.size.also {
                paletteIndex[blockId] = it
                palette += blockId
            }
        }

        val side = (CHUNK_SIDE / (1 shl scale.level)).coerceAtLeast(1)
        val chunks = ArrayList<World.SurfaceChunkDto>(requestedChunks.size)
        val missing = LinkedHashSet<Pair<Int, Int>>()

        for ((regionKey, coords) in requestedByRegion) {
            val regionMap = cachedRegionMaps[regionKey]
            val chunkMap = regionMap?.chunks?.associateBy { it.chunkX to it.chunkZ } ?: emptyMap()
            for (coord in coords) {
                val chunkSurface = chunkMap[coord.x to coord.z]
                if (chunkSurface == null) {
                    missing += (coord.x to coord.z)
                    continue
                }
                val sampled = sampleChunkForScale(chunkSurface, scale, ::paletteId)
                chunks += World.SurfaceChunkDto(
                    chunkX = coord.x,
                    chunkZ = coord.z,
                    side = side,
                    data = sampled
                )
            }
        }

        chunks.sortWith(compareBy<World.SurfaceChunkDto> { it.chunkZ }.thenBy { it.chunkX })
        return World.SurfaceQueryDto(
            scale = scale.level,
            palette = palette,
            chunks = chunks,
            missing = missing
                .sortedWith(compareBy<Pair<Int, Int>> { it.second }.thenBy { it.first })
                .map { (x, z) -> World.SurfaceChunkCoordDto(chunkX = x, chunkZ = z) }
        )
    }

    private suspend fun World.composeWideScaleSurfaceQueryFromRegions(
        dimensionCode: String,
        regionsRaw: String?,
        chunksRaw: String?,
        scale: World.Scale
    ): World.SurfaceQueryDto {
        val requestedRegionKeys = parseRequestedRegions(regionsRaw, chunksRaw)
        if (requestedRegionKeys.isEmpty()) {
            return World.SurfaceQueryDto(
                scale = scale.level,
                palette = listOf(DEFAULT_BLOCK_ID),
                chunks = emptyList(),
                missing = emptyList(),
                regions = emptyList(),
                missingRegions = emptyList()
            )
        }

        val cachedRegionMaps = readRegionSurfaceMaps(
            dimensionCode = dimensionCode,
            regionKeys = requestedRegionKeys
        )

        val palette = mutableListOf(DEFAULT_BLOCK_ID)
        val paletteIndex = hashMapOf(DEFAULT_BLOCK_ID to 0)
        fun paletteId(blockId: String): Int {
            return paletteIndex[blockId] ?: palette.size.also {
                paletteIndex[blockId] = it
                palette += blockId
            }
        }

        val regions = ArrayList<World.SurfaceRegionDto>(requestedRegionKeys.size)
        val missingRegions = ArrayList<Pair<Int, Int>>()
        val orderedKeys = requestedRegionKeys.sortedWith(compareBy<Pair<Int, Int>> { it.second }.thenBy { it.first })
        val regionSide = wideScaleRegionSide(scale)
        for ((regionX, regionZ) in orderedKeys) {
            val regionMap = cachedRegionMaps[regionX to regionZ]
            if (regionMap == null) {
                missingRegions += (regionX to regionZ)
                continue
            }
            val sampled = sampleRegionForWideScale(regionMap, scale, ::paletteId)
            regions += World.SurfaceRegionDto(
                regionX = regionX,
                regionZ = regionZ,
                side = regionSide,
                data = sampled
            )
        }

        return World.SurfaceQueryDto(
            scale = scale.level,
            palette = palette,
            chunks = emptyList(),
            missing = emptyList(),
            regions = regions,
            missingRegions = missingRegions.map { (x, z) ->
                World.SurfaceRegionCoordDto(regionX = x, regionZ = z)
            }
        )
    }

    private fun parseRequestedChunks(
        regionsRaw: String?,
        chunksRaw: String?
    ): Set<ChunkCoord> {
        if (regionsRaw.isNullOrBlank() && chunksRaw.isNullOrBlank()) {
            throw RequestError("请提供 regions 或 chunks")
        }
        val result = LinkedHashSet<ChunkCoord>()

        fun ensureBudget(additionalCount: Long) {
            if (additionalCount <= 0) return
            if (result.size.toLong() + additionalCount > MAX_SURFACE_QUERY_CHUNKS.toLong()) {
                throw RequestError("查询区块范围过大，最多 $MAX_SURFACE_QUERY_CHUNKS 个区块")
            }
        }

        parseCoordRanges(chunksRaw, "chunks").forEach { (xRange, zRange) ->
            ensureBudget(rangeSize(xRange) * rangeSize(zRange))
            for (z in zRange) {
                for (x in xRange) {
                    result += ChunkCoord(x, z)
                }
            }
        }

        parseCoordRanges(regionsRaw, "regions").forEach { (xRange, zRange) ->
            val regionCount = rangeSize(xRange) * rangeSize(zRange)
            ensureBudget(regionCount * REGION_CHUNK_COUNT.toLong())
            for (regionZ in zRange) {
                for (regionX in xRange) {
                    val baseChunkX = regionX * REGION_CHUNK_SIDE
                    val baseChunkZ = regionZ * REGION_CHUNK_SIDE
                    for (localZ in 0 until REGION_CHUNK_SIDE) {
                        for (localX in 0 until REGION_CHUNK_SIDE) {
                            result += ChunkCoord(baseChunkX + localX, baseChunkZ + localZ)
                        }
                    }
                }
            }
        }

        return result
    }

    private fun parseRequestedRegions(
        regionsRaw: String?,
        chunksRaw: String?
    ): Set<Pair<Int, Int>> {
        if (regionsRaw.isNullOrBlank() && chunksRaw.isNullOrBlank()) {
            throw RequestError("请提供 regions 或 chunks")
        }
        val result = LinkedHashSet<Pair<Int, Int>>()

        fun ensureBudget(additionalCount: Long) {
            if (additionalCount <= 0L) return
            if (result.size.toLong() + additionalCount > MAX_SURFACE_QUERY_REGIONS.toLong()) {
                throw RequestError("查询区域范围过大，最多 $MAX_SURFACE_QUERY_REGIONS 个区域")
            }
        }

        parseCoordRanges(regionsRaw, "regions").forEach { (xRange, zRange) ->
            ensureBudget(rangeSize(xRange) * rangeSize(zRange))
            for (regionZ in zRange) {
                for (regionX in xRange) {
                    result += (regionX to regionZ)
                }
            }
        }

        parseCoordRanges(chunksRaw, "chunks").forEach { (xRange, zRange) ->
            val regionXRange = floorDivRange(xRange, REGION_CHUNK_SIDE)
            val regionZRange = floorDivRange(zRange, REGION_CHUNK_SIDE)
            ensureBudget(rangeSize(regionXRange) * rangeSize(regionZRange))
            for (regionZ in regionZRange) {
                for (regionX in regionXRange) {
                    result += (regionX to regionZ)
                }
            }
        }
        return result
    }

    private fun parseCoordRanges(raw: String?, fieldName: String): List<Pair<IntRange, IntRange>> {
        if (raw.isNullOrBlank()) return emptyList()
        val tokens = raw.split(',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (tokens.size > MAX_SURFACE_QUERY_TOKENS) {
            throw RequestError("$fieldName 条目过多，最多 $MAX_SURFACE_QUERY_TOKENS")
        }
        return tokens.map { token ->
            val parts = token.split('/', limit = 2)
            if (parts.size != 2) throw RequestError("$fieldName 格式错误: $token")
            val xRange = parseIntRange(parts[0], "$fieldName.x")
            val zRange = parseIntRange(parts[1], "$fieldName.z")
            xRange to zRange
        }
    }

    private fun parseIntRange(raw: String, fieldName: String): IntRange {
        val token = raw.trim()
        if (".." !in token) {
            val value = token.toIntOrNull() ?: throw RequestError("$fieldName 不是整数")
            return value..value
        }
        val parts = token.split("..", limit = 2)
        if (parts.size != 2) throw RequestError("$fieldName 范围格式错误")
        val left = parts[0].trim().toIntOrNull() ?: throw RequestError("$fieldName 起始值错误")
        val right = parts[1].trim().toIntOrNull() ?: throw RequestError("$fieldName 结束值错误")
        return if (left <= right) left..right else right..left
    }

    private fun rangeSize(range: IntRange): Long =
        (range.last.toLong() - range.first.toLong() + 1L).coerceAtLeast(0L)

    private fun floorDivRange(range: IntRange, divisor: Int): IntRange {
        val min = Math.floorDiv(range.first, divisor)
        val max = Math.floorDiv(range.last, divisor)
        return if (min <= max) min..max else max..min
    }

    private fun wideScaleChunkGroupSize(scale: World.Scale): Int = when (scale) {
        World.Scale.L5 -> 1
        World.Scale.L6 -> 2
        World.Scale.L7 -> 4
        else -> 1
    }

    private fun wideScaleRegionSide(scale: World.Scale): Int =
        (REGION_CHUNK_SIDE / wideScaleChunkGroupSize(scale)).coerceAtLeast(1)

    private fun sampleRegionForWideScale(
        regionMap: RegionSurfaceMap,
        scale: World.Scale,
        paletteIdOf: (String) -> Int
    ): IntArray {
        val groupSize = wideScaleChunkGroupSize(scale)
        val side = wideScaleRegionSide(scale)
        val sampled = IntArray(side * side)
        val chunkMap = regionMap.chunks.associateBy { it.chunkX to it.chunkZ }
        val regionBaseX = regionMap.regionX * REGION_CHUNK_SIDE
        val regionBaseZ = regionMap.regionZ * REGION_CHUNK_SIDE
        val centerOffset = (groupSize / 2).coerceAtMost(groupSize - 1)
        var writeIndex = 0

        for (sampleZ in 0 until side) {
            val sampleChunkZ = regionBaseZ + sampleZ * groupSize + centerOffset
            for (sampleX in 0 until side) {
                val sampleChunkX = regionBaseX + sampleX * groupSize + centerOffset
                val chunkSurface = chunkMap[sampleChunkX to sampleChunkZ]
                val blockName = if (chunkSurface == null) {
                    DEFAULT_BLOCK_ID
                } else {
                    readSnapshotBlock(chunkSurface.data, CHUNK_SIDE / 2, CHUNK_SIDE / 2)
                }
                sampled[writeIndex++] = paletteIdOf(blockName)
            }
        }
        return sampled
    }

    private fun sampleChunkForScale(
        chunkSurface: ChunkSurfaceMap,
        scale: World.Scale,
        paletteIdOf: (String) -> Int
    ): IntArray {
        val step = 1 shl scale.level
        val side = (CHUNK_SIDE / step).coerceAtLeast(1)
        val sampled = IntArray(side * side)
        var writeIndex = 0

        for (z in 0 until side) {
            for (x in 0 until side) {
                val sampleX = (x * step + step / 2).coerceIn(0, CHUNK_SIDE - 1)
                val sampleZ = (z * step + step / 2).coerceIn(0, CHUNK_SIDE - 1)
                val blockName = readSnapshotBlock(chunkSurface.data, sampleX, sampleZ)
                sampled[writeIndex++] = paletteIdOf(blockName)
            }
        }
        return sampled
    }

    private fun readSnapshotBlock(
        snapshot: PalettedContainer.Snapshot<String>,
        localX: Int,
        localZ: Int
    ): String {
        val palette = snapshot.palette
        if (palette.isEmpty()) return DEFAULT_BLOCK_ID

        val bits = snapshot.bits
        if (bits <= 0 || snapshot.data.isEmpty()) {
            return palette.firstOrNull() ?: DEFAULT_BLOCK_ID
        }

        val entriesPerLong = 64 / bits
        if (entriesPerLong <= 0) return palette.firstOrNull() ?: DEFAULT_BLOCK_ID

        val blockIndex = ((localZ and 0xF) shl 4) or (localX and 0xF)
        val longIndex = blockIndex / entriesPerLong
        if (longIndex !in snapshot.data.indices) return palette.firstOrNull() ?: DEFAULT_BLOCK_ID

        val bitOffset = (blockIndex % entriesPerLong) * bits
        val mask = (1L shl bits) - 1L
        val paletteIndex = ((snapshot.data[longIndex] ushr bitOffset) and mask).toInt()
        return palette.getOrElse(paletteIndex) { DEFAULT_BLOCK_ID }
    }

    private suspend fun World.readRegionSurfaceMaps(
        dimensionCode: String,
        regionKeys: Set<Pair<Int, Int>>
    ): Map<Pair<Int, Int>, RegionSurfaceMap> {
        if (regionKeys.isEmpty()) return emptyMap()

        val regionFilters = regionKeys.map { (regionX, regionZ) ->
            and(eq("regionX", regionX), eq("regionZ", regionZ))
        }
        val scopedRegionFilter = if (regionFilters.size == 1) {
            regionFilters.first()
        } else {
            or(*regionFilters.toTypedArray())
        }
        val filter = and(
            eq("worldId", _id),
            eq("dimension", dimensionCode),
            scopedRegionFilter
        )
        val docs = worldSurfaceCol.find(filter).toList()
        val result = HashMap<Pair<Int, Int>, RegionSurfaceMap>(docs.size)
        docs.forEach { doc ->
            val parsed = documentToRegionSurfaceMap(doc) ?: return@forEach
            result[parsed.regionX to parsed.regionZ] = parsed
        }
        return result
    }

    private suspend fun World.readRegionSurfaceMap(
        dimensionCode: String,
        regionX: Int,
        regionZ: Int
    ): RegionSurfaceMap? {
        val doc = worldSurfaceCol.find(
            and(
                eq("worldId", _id),
                eq("dimension", dimensionCode),
                eq("regionX", regionX),
                eq("regionZ", regionZ)
            )
        ).firstOrNull() ?: return null
        return documentToRegionSurfaceMap(doc)
    }

    private suspend fun World.writeRegionSurfaceMap(data: RegionSurfaceMap) {
        runCatching {
            worldSurfaceCol.replaceOne(
                and(
                    eq("worldId", _id),
                    eq("dimension", data.dimension),
                    eq("regionX", data.regionX),
                    eq("regionZ", data.regionZ)
                ),
                regionSurfaceMapToDocument(data),
                ReplaceOptions().upsert(true)
            )
        }.onFailure {
            lgr.warn(it) {
                "保存region surface缓存失败: world=${_id.toHexString()}, dim=${data.dimension}, " +
                    "region=(${data.regionX},${data.regionZ})"
            }
        }
    }

    private suspend fun World.deleteRegionSurfaceMap(
        dimensionCode: String,
        regionX: Int,
        regionZ: Int
    ) {
        runCatching {
            worldSurfaceCol.deleteOne(
                and(
                    eq("worldId", _id),
                    eq("dimension", dimensionCode),
                    eq("regionX", regionX),
                    eq("regionZ", regionZ)
                )
            )
        }.onFailure {
            lgr.warn(it) {
                "删除region surface缓存失败: world=${_id.toHexString()}, dim=$dimensionCode, region=($regionX,$regionZ)"
            }
        }
    }

    private fun regionSurfaceMapToDocument(data: RegionSurfaceMap): Document {
        return Document("_id", data._id)
            .append("worldId", data.worldId)
            .append("dimension", data.dimension)
            .append("regionX", data.regionX)
            .append("regionZ", data.regionZ)
            .append("chunkTimes", data.chunkTimes.toList())
            .append("chunks", data.chunks.map(::chunkSurfaceMapToDocument))
    }

    private fun chunkSurfaceMapToDocument(chunk: ChunkSurfaceMap): Document {
        return Document("chunkX", chunk.chunkX)
            .append("chunkZ", chunk.chunkZ)
            .append("data", snapshotToDocument(chunk.data))
    }

    private fun snapshotToDocument(snapshot: PalettedContainer.Snapshot<String>): Document {
        return Document("size", snapshot.size)
            .append("palette", snapshot.palette)
            .append("bits", snapshot.bits)
            .append("data", snapshot.data.toList())
    }

    private fun documentToRegionSurfaceMap(doc: Document): RegionSurfaceMap? {
        val id = doc["_id"] as? ObjectId ?: return null
        val worldId = doc["worldId"] as? ObjectId ?: return null
        val dimension = doc["dimension"]?.toString() ?: return null
        val regionX = (doc["regionX"] as? Number)?.toInt() ?: return null
        val regionZ = (doc["regionZ"] as? Number)?.toInt() ?: return null
        val rawChunkTimes = (doc["chunkTimes"] as? List<*>)
            ?.mapNotNull { (it as? Number)?.toInt() }
            ?: emptyList()
        val chunkTimes = IntArray(REGION_CHUNK_COUNT) { index ->
            rawChunkTimes.getOrElse(index) { 0 }
        }
        val chunks = (doc["chunks"] as? List<*>)
            ?.mapNotNull { (it as? Document)?.let(::documentToChunkSurfaceMap) }
            ?: emptyList()
        return RegionSurfaceMap(
            _id = id,
            worldId = worldId,
            dimension = dimension,
            regionX = regionX,
            regionZ = regionZ,
            chunkTimes = chunkTimes,
            chunks = chunks
        )
    }

    private fun documentToChunkSurfaceMap(doc: Document): ChunkSurfaceMap? {
        val chunkX = (doc["chunkX"] as? Number)?.toInt()
        val chunkZ = (doc["chunkZ"] as? Number)?.toInt()
        val normalizedChunkX: Int
        val normalizedChunkZ: Int
        if (chunkX != null && chunkZ != null) {
            normalizedChunkX = chunkX
            normalizedChunkZ = chunkZ
        } else {
            val legacyChunkIndex = (doc["chunkIndex"] as? Number)?.toInt() ?: return null
            val pos = RChunkPos(legacyChunkIndex)
            normalizedChunkX = pos.x.toInt()
            normalizedChunkZ = pos.z.toInt()
        }
        val snapshot = (doc["data"] as? Document)?.let(::documentToSnapshot) ?: return null
        return ChunkSurfaceMap(chunkX = normalizedChunkX, chunkZ = normalizedChunkZ, data = snapshot)
    }

    private fun documentToSnapshot(doc: Document): PalettedContainer.Snapshot<String>? {
        val size = (doc["size"] as? Number)?.toInt() ?: return null
        val bits = (doc["bits"] as? Number)?.toInt() ?: return null
        val palette = (doc["palette"] as? List<*>)?.map { it?.toString() ?: DEFAULT_BLOCK_ID } ?: return null
        if (palette.isEmpty()) return null
        val data = ((doc["data"] as? List<*>) ?: emptyList<Any>())
            .mapNotNull { (it as? Number)?.toLong() }
            .toLongArray()
        return PalettedContainer.Snapshot(
            size = size,
            palette = palette,
            bits = bits,
            data = data
        )
    }

    private suspend fun ensureWorldCacheCollectionAndIndexes() {
        ensureWorldCacheCollection()
        runCatching {
            worldSurfaceCol.createIndex(
                Indexes.compoundIndex(
                    Indexes.ascending("worldId"),
                    Indexes.ascending("dimension"),
                    Indexes.ascending("regionX"),
                    Indexes.ascending("regionZ")
                ),
                IndexOptions()
                    .name("uniq_world_dimension_region")
                    .unique(true)
            )
            worldSurfaceCol.createIndex(
                Indexes.compoundIndex(
                    Indexes.ascending("worldId"),
                    Indexes.ascending("dimension")
                ),
                IndexOptions().name("idx_world_dimension")
            )
        }.onFailure {
            lgr.warn(it) { "初始化 world_surface 索引失败" }
        }
    }

    private suspend fun ensureWorldCacheCollection() {
        val exists = DB.listCollectionNames().toList().contains(WORLD_SURFACE_COLLECTION)
        if (exists) return
        val zstdOptions = CreateCollectionOptions().storageEngineOptions(
            Document(
                "wiredTiger",
                Document("configString", "block_compressor=zstd")
            )
        )
        runCatching {
            DB.createCollection(WORLD_SURFACE_COLLECTION, zstdOptions)
            lgr.info { "创建 world_surface 集合成功（zstd 压缩）" }
        }.onFailure { err ->
            if (isNamespaceExistsError(err)) return
            lgr.warn(err) { "创建 zstd world_surface 失败，回退默认配置创建集合" }
            runCatching {
                DB.createCollection(WORLD_SURFACE_COLLECTION)
                lgr.info { "创建 world_surface 集合成功（默认压缩）" }
            }.onFailure { fallbackErr ->
                if (!isNamespaceExistsError(fallbackErr)) throw fallbackErr
            }
        }
    }

    private fun isNamespaceExistsError(err: Throwable): Boolean {
        val msg = err.message ?: return false
        return "NamespaceExists" in msg || "already exists" in msg
    }

    private fun resolveDimensionDataDir(worldRootDir: File, dimensionCode: String): File {
        return when (dimensionCode) {
            "minecraft:overworld" -> worldRootDir
            "minecraft:the_nether" -> worldRootDir.resolve("DIM-1")
            "minecraft:the_end" -> worldRootDir.resolve("DIM1")
            else -> {
                val parts = dimensionCode.split(":", limit = 2)
                val dimensionsRoot = worldRootDir.resolve("dimensions")
                val candidate = dimensionsRoot.resolve(parts[0]).resolve(parts[1])
                val normalizedRoot = dimensionsRoot.toPath().toAbsolutePath().normalize()
                val normalizedCandidate = candidate.toPath().toAbsolutePath().normalize()
                if (!normalizedCandidate.startsWith(normalizedRoot)) {
                    throw RequestError("dimension code malformed")
                }
                normalizedCandidate.toFile()
            }
        }
    }

    private fun normalizeDimensionCode(raw: String): String {
        val dimensionCode = raw.trim()
        if (dimensionCode.isBlank()) throw RequestError("dimension code malformed")
        return when (dimensionCode.lowercase()) {
            "0", "overworld", "minecraft:overworld" -> "minecraft:overworld"
            "-1", "nether", "the_nether", "minecraft:the_nether" -> "minecraft:the_nether"
            "1", "end", "the_end", "minecraft:the_end" -> "minecraft:the_end"
            else -> {
                if (!DIMENSION_CODE_REGEX.matches(dimensionCode) || ".." in dimensionCode) {
                    throw RequestError("dimension code malformed")
                }
                val path = dimensionCode.split(":", limit = 2)[1]
                if (path.startsWith("/") || path.startsWith("\\") || path.contains("//") || path.contains("\\")) {
                    throw RequestError("dimension code malformed")
                }
                dimensionCode
            }
        }
    }

    private fun sampleSurfaceFromSections(
        localX: Int,
        localZ: Int,
        sections: List<SurfaceSection>
    ): String {
        for (section in sections) {
            for (localY in CHUNK_SIDE - 1 downTo 0) {
                val paletteIndex = decodePaletteIndex(localX, localY, localZ, section.palette.size, section.data)
                val blockName = section.palette.getOrElse(paletteIndex) { DEFAULT_BLOCK_ID }
                if (!isAirBlock(blockName)) {
                    return blockName
                }
            }
        }
        return DEFAULT_BLOCK_ID
    }

    private fun buildSurfaceSections(chunk: Chunk): List<SurfaceSection> {
        val root = chunk.getNbtData() ?: return emptyList()
        val sectionTags = root["sections"]?.nbtList ?: return emptyList()
        return sectionTags.mapNotNull { sectionTag ->
            val section = sectionTag.nbtCompound ?: return@mapNotNull null
            val sectionY = section["Y"]?.nbtByte?.value?.toInt()
                ?: section["Y"]?.nbtInt?.value
                ?: return@mapNotNull null
            val blockStates = section["block_states"]?.nbtCompound ?: return@mapNotNull null
            val palette = blockStates["palette"]?.nbtList
                ?.mapNotNull { it.nbtCompound["Name"]?.nbtString?.value }
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(DEFAULT_BLOCK_ID)
            SurfaceSection(
                sectionY = sectionY,
                palette = palette,
                data = blockStates["data"]?.nbtLongArray?.toLongArray()
            )
        }.sortedByDescending { it.sectionY }
    }

    private fun decodePaletteIndex(
        localX: Int,
        localY: Int,
        localZ: Int,
        paletteSize: Int,
        data: LongArray?
    ): Int {
        if (paletteSize <= 1 || data == null || data.isEmpty()) return 0
        val bitsPerBlock = max(4, ceil(log2(paletteSize.toDouble())).toInt())
        val entriesPerLong = 64 / bitsPerBlock
        if (entriesPerLong <= 0) return 0

        val blockIndex = ((localY and 0xF) shl 8) or ((localZ and 0xF) shl 4) or (localX and 0xF)
        val longIndex = blockIndex / entriesPerLong
        if (longIndex >= data.size) return 0
        val bitOffset = (blockIndex % entriesPerLong) * bitsPerBlock
        val mask = (1L shl bitsPerBlock) - 1L
        val value = (data[longIndex] ushr bitOffset) and mask
        return value.toInt().coerceIn(0, paletteSize - 1)
    }

    private fun isAirBlock(name: String): Boolean {
        val id = name.removePrefix("minecraft:")
        return id == "air" || id == "cave_air" || id == "void_air" || id == "barrier" || id == "structure_void"
    }
}
