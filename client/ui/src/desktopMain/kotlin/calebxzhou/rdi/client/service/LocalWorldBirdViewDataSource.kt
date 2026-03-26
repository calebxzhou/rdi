package calebxzhou.rdi.client.service

import calebxzhou.rdi.common.anvilrw.core.Chunk
import calebxzhou.rdi.common.anvilrw.format.AnvilReader
import calebxzhou.rdi.common.anvilrw.util.AnvilUtils
import calebxzhou.rdi.common.anvilrw.util.buildSurfaceSections
import calebxzhou.rdi.common.anvilrw.util.sampleSurfaceFromSections
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.World
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalWorldBirdViewDataSource(
    rootPath: String
) : WorldBirdViewDataSource {
    private val rootDir = File(rootPath)

    override val sourceKey: String = "local:${rootDir.absolutePath}"
    override val supportsBuildAllSurfaceCaches: Boolean = false

    override suspend fun listDimensions(): List<String> = withContext(Dispatchers.IO) {
        if (!rootDir.isDirectory) return@withContext emptyList()
        val dimensions = linkedSetOf<String>()

        if (rootDir.resolve("region").isDirectory) {
            dimensions += "minecraft:overworld"
        }
        if (rootDir.resolve("DIM-1").resolve("region").isDirectory) {
            dimensions += "minecraft:the_nether"
        }
        if (rootDir.resolve("DIM1").resolve("region").isDirectory) {
            dimensions += "minecraft:the_end"
        }

        val customDimensionsRoot = rootDir.resolve("dimensions")
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
        dimensions.toList()
    }

    override suspend fun querySurface(
        dimension: String,
        scale: World.Scale,
        regionsRaw: String?,
        chunksRaw: String?
    ): World.SurfaceQueryDto = withContext(Dispatchers.IO) {
        ensureRootDir()
        val dimensionCode = normalizeDimensionCode(dimension)
        if (scale.level >= World.Scale.L5.level) {
            queryWideScaleSurface(dimensionCode, regionsRaw, chunksRaw, scale)
        } else {
            queryChunkSurface(dimensionCode, regionsRaw, chunksRaw, scale)
        }
    }

    override suspend fun buildAllSurfaceCaches(): String = "本地存档无需预构建缓存"

    private fun queryChunkSurface(
        dimensionCode: String,
        regionsRaw: String?,
        chunksRaw: String?,
        scale: World.Scale
    ): World.SurfaceQueryDto {
        val requestedChunks = parseRequestedChunks(regionsRaw, chunksRaw)
        if (requestedChunks.isEmpty()) {
            return emptySurfaceQuery(scale)
        }

        val requestedByRegion = LinkedHashMap<Pair<Int, Int>, MutableList<LocalChunkCoord>>()
        for (coord in requestedChunks) {
            val regionKey = Math.floorDiv(coord.x, REGION_CHUNK_SIDE) to Math.floorDiv(coord.z, REGION_CHUNK_SIDE)
            requestedByRegion.getOrPut(regionKey) { arrayListOf() }.add(coord)
        }

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
        val dimensionRegionDir = resolveDimensionDataDir(rootDir, dimensionCode).resolve("region")

        for ((regionKey, coords) in requestedByRegion) {
            val regionFile = resolveRegionFile(dimensionRegionDir, regionKey.first, regionKey.second)
            if (regionFile == null) {
                coords.forEach { missing += (it.x to it.z) }
                continue
            }
            runCatching {
                AnvilReader(regionFile).use { reader ->
                    coords.forEach { coord ->
                        val chunk = runCatching { reader.readChunk(coord.x, coord.z) }.getOrNull()
                        if (chunk == null || chunk.isEmpty) {
                            missing += (coord.x to coord.z)
                            return@forEach
                        }
                        try {
                            chunks += World.SurfaceChunkDto(
                                chunkX = coord.x,
                                chunkZ = coord.z,
                                side = side,
                                data = sampleChunkForScale(chunk, scale, ::paletteId)
                            )
                        } catch (_: Throwable) {
                            missing += (coord.x to coord.z)
                        } finally {
                            chunk.releaseSurfaceReadData()
                        }
                    }
                }
            }.onFailure {
                coords.forEach { missing += (it.x to it.z) }
            }
        }

        chunks.sortWith(compareBy<World.SurfaceChunkDto> { it.chunkZ }.thenBy { it.chunkX })
        return World.SurfaceQueryDto(
            scale = scale.level,
            palette = palette,
            chunks = chunks,
            missing = missing
                .sortedWith(compareBy<Pair<Int, Int>> { it.second }.thenBy { it.first })
                .map { (x, z) -> World.SurfaceChunkCoordDto(chunkX = x, chunkZ = z) },
            regions = emptyList(),
            missingRegions = emptyList()
        )
    }

    private fun queryWideScaleSurface(
        dimensionCode: String,
        regionsRaw: String?,
        chunksRaw: String?,
        scale: World.Scale
    ): World.SurfaceQueryDto {
        val requestedRegionKeys = parseRequestedRegions(regionsRaw, chunksRaw)
        if (requestedRegionKeys.isEmpty()) {
            return emptySurfaceQuery(scale)
        }

        val palette = mutableListOf(DEFAULT_BLOCK_ID)
        val paletteIndex = hashMapOf(DEFAULT_BLOCK_ID to 0)
        fun paletteId(blockId: String): Int {
            return paletteIndex[blockId] ?: palette.size.also {
                paletteIndex[blockId] = it
                palette += blockId
            }
        }

        val dimensionRegionDir = resolveDimensionDataDir(rootDir, dimensionCode).resolve("region")
        val regions = ArrayList<World.SurfaceRegionDto>(requestedRegionKeys.size)
        val missingRegions = ArrayList<Pair<Int, Int>>()
        val regionSide = wideScaleRegionSide(scale)
        val orderedKeys = requestedRegionKeys.sortedWith(compareBy<Pair<Int, Int>> { it.second }.thenBy { it.first })

        for ((regionX, regionZ) in orderedKeys) {
            val regionFile = resolveRegionFile(dimensionRegionDir, regionX, regionZ)
            if (regionFile == null) {
                missingRegions += (regionX to regionZ)
                continue
            }
            val sampled = runCatching {
                sampleRegionForWideScale(regionFile, regionX, regionZ, scale, ::paletteId)
            }.getOrNull()
            if (sampled == null) {
                missingRegions += (regionX to regionZ)
                continue
            }
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

    private fun sampleRegionForWideScale(
        regionFile: File,
        regionX: Int,
        regionZ: Int,
        scale: World.Scale,
        paletteIdOf: (String) -> Int
    ): IntArray {
        val groupSize = wideScaleChunkGroupSize(scale)
        val side = wideScaleRegionSide(scale)
        val sampled = IntArray(side * side)
        val regionBaseX = regionX * REGION_CHUNK_SIDE
        val regionBaseZ = regionZ * REGION_CHUNK_SIDE
        val centerOffset = (groupSize / 2).coerceAtMost(groupSize - 1)
        var writeIndex = 0

        AnvilReader(regionFile).use { reader ->
            for (sampleZ in 0 until side) {
                val sampleChunkZ = regionBaseZ + sampleZ * groupSize + centerOffset
                for (sampleX in 0 until side) {
                    val sampleChunkX = regionBaseX + sampleX * groupSize + centerOffset
                    val blockName = readSurfaceBlock(reader, sampleChunkX, sampleChunkZ)
                    sampled[writeIndex++] = paletteIdOf(blockName)
                }
            }
        }
        return sampled
    }

    private fun readSurfaceBlock(
        reader: AnvilReader,
        chunkX: Int,
        chunkZ: Int
    ): String {
        val chunk = runCatching { reader.readChunk(chunkX, chunkZ) }.getOrNull()
            ?: return DEFAULT_BLOCK_ID
        if (chunk.isEmpty) {
            chunk.releaseSurfaceReadData()
            return DEFAULT_BLOCK_ID
        }
        return try {
            val sections = chunk.buildSurfaceSections()
            sampleSurfaceFromSections(CHUNK_SIDE / 2, CHUNK_SIDE / 2, sections)
        } catch (_: Throwable) {
            DEFAULT_BLOCK_ID
        } finally {
            chunk.releaseSurfaceReadData()
        }
    }

    private fun sampleChunkForScale(
        chunk: Chunk,
        scale: World.Scale,
        paletteIdOf: (String) -> Int
    ): IntArray {
        val step = 1 shl scale.level
        val side = (CHUNK_SIDE / step).coerceAtLeast(1)
        val sampled = IntArray(side * side)
        val sections = chunk.buildSurfaceSections()
        var writeIndex = 0

        for (z in 0 until side) {
            for (x in 0 until side) {
                val sampleX = (x * step + step / 2).coerceIn(0, CHUNK_SIDE - 1)
                val sampleZ = (z * step + step / 2).coerceIn(0, CHUNK_SIDE - 1)
                val blockName = sampleSurfaceFromSections(sampleX, sampleZ, sections)
                sampled[writeIndex++] = paletteIdOf(blockName)
            }
        }
        return sampled
    }

    private fun ensureRootDir() {
        if (!rootDir.isDirectory) {
            throw RequestError("本地存档目录不存在:${rootDir.absolutePath}")
        }
    }
}

private fun emptySurfaceQuery(scale: World.Scale): World.SurfaceQueryDto {
    return World.SurfaceQueryDto(
        scale = scale.level,
        palette = listOf(DEFAULT_BLOCK_ID),
        chunks = emptyList(),
        missing = emptyList(),
        regions = emptyList(),
        missingRegions = emptyList()
    )
}

private fun resolveRegionFile(dimensionRegionDir: File, regionX: Int, regionZ: Int): File? {
    val regionFile = dimensionRegionDir.resolve(AnvilUtils.generateRegionFilename(regionX, regionZ))
    return regionFile.takeIf { it.isFile && it.canRead() }
}

private fun parseRequestedChunks(
    regionsRaw: String?,
    chunksRaw: String?
): Set<LocalChunkCoord> {
    if (regionsRaw.isNullOrBlank() && chunksRaw.isNullOrBlank()) {
        throw RequestError("请提供 regions 或 chunks")
    }
    val result = LinkedHashSet<LocalChunkCoord>()

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
                result += LocalChunkCoord(x, z)
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
                        result += LocalChunkCoord(baseChunkX + localX, baseChunkZ + localZ)
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
        if (parts.size != 2) throw RequestError("$fieldName 格式错误:$token")
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

private data class LocalChunkCoord(
    val x: Int,
    val z: Int
)

private const val DEFAULT_BLOCK_ID = "minecraft:air"
private const val CHUNK_SIDE = 16
private const val REGION_CHUNK_SIDE = 32
private const val REGION_CHUNK_COUNT = REGION_CHUNK_SIDE * REGION_CHUNK_SIDE
private const val MAX_SURFACE_QUERY_CHUNKS = 65_536
private const val MAX_SURFACE_QUERY_REGIONS = 4_096
private const val MAX_SURFACE_QUERY_TOKENS = 512
private val DIMENSION_CODE_REGEX = Regex("^[a-z0-9_.-]+:[a-z0-9_./-]+$")
