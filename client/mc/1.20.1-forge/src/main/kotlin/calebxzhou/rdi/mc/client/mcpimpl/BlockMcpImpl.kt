package calebxzhou.rdi.mc.client.mcpimpl

import calebxzhou.rdi.mc.common2.mcp.McpBlockError
import calebxzhou.rdi.mc.common2.mcp.McpError
import calebxzhou.rdi.mc.common2.mcp.McpNoPlayerError
import calebxzhou.rdi.mc.common2.mcp.model.*
import calebxzau.mc.common2021.mc
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.chunk.LevelChunk
import java.util.concurrent.ExecutionException
import java.util.function.Supplier

object BlockMcpImpl {
    private const val BLOCK_FIND_HORIZONTAL_SIZE = 256
    private const val BLOCK_FIND_LIMIT_PER_ID = 128
    private const val BLOCK_FETCH_BOX_LIMIT = 256 * 256 * 256

    fun find(req: BlockFindQ): Result<BlockFindP> = runCatching {
        mc.submit(Supplier { scanBlocks(req) }).get()
    }.recoverCatching { e ->
        val cause = (e as? ExecutionException)?.cause
        if (cause is McpError) {
            throw cause
        }
        throw e
    }

    fun fetchBox(req: BlockFetchBoxQ): Result<BlockFetchBoxP> = runCatching {
        mc.submit(Supplier { scanBox(req) }).get()
    }.recoverCatching { e ->
        val cause = (e as? ExecutionException)?.cause
        if (cause is McpError) {
            throw cause
        }
        throw e
    }

    private fun scanBlocks(req: BlockFindQ): BlockFindP {
        val ids = req.ids.map(String::trim).filter(String::isNotBlank).distinct()
        if (ids.isEmpty()) {
            throw McpBlockError("invalid block id")
        }
        val targets = ids.associateWith(::resolveBlock)
        val ranges = targets.values.associateWith { BlockRangeBuilder() }
        val player = mc.player ?: throw McpNoPlayerError()
        val level = mc.level ?: throw McpNoPlayerError()
        val center = player.blockPosition()
        val minX = center.x - BLOCK_FIND_HORIZONTAL_SIZE / 2
        val minZ = center.z - BLOCK_FIND_HORIZONTAL_SIZE / 2
        val maxX = minX + BLOCK_FIND_HORIZONTAL_SIZE - 1
        val maxZ = minZ + BLOCK_FIND_HORIZONTAL_SIZE - 1
        val pos = BlockPos.MutableBlockPos()
        val openCuboids = mutableMapOf<CuboidKey, OpenCuboid>()
        for (y in level.minBuildHeight until level.maxBuildHeight) {
            val layerRects = mutableListOf<LayerRect>()
            val openRects = mutableMapOf<RectKey, LayerRect>()
            for (z in minZ..maxZ) {
                val chunkZ = SectionPos.blockToSectionCoord(z)
                val rowKeys = mutableSetOf<RectKey>()
                var runBlock: Block? = null
                var runStartX = minX
                var chunkX = Int.MIN_VALUE
                var chunk: LevelChunk? = null
                for (x in minX..maxX) {
                    val nextChunkX = SectionPos.blockToSectionCoord(x)
                    if (nextChunkX != chunkX) {
                        chunkX = nextChunkX
                        chunk = level.chunkSource.getChunkNow(chunkX, chunkZ)
                    }
                    val foundBlock = chunk?.getBlockState(pos.set(x, y, z))?.block
                    val range = foundBlock?.let(ranges::get)
                    val block = if (range != null && !range.isFull) foundBlock else null
                    if (block == runBlock) {
                        continue
                    }
                    runBlock?.let {
                        extendLayerRect(openRects, layerRects, rowKeys, RectKey(it, runStartX, x - 1), z)
                    }
                    runBlock = block
                    runStartX = x
                }
                runBlock?.let {
                    extendLayerRect(openRects, layerRects, rowKeys, RectKey(it, runStartX, maxX), z)
                }
                openRects.keys.filter { it !in rowKeys }.forEach {
                    layerRects += openRects.remove(it)!!
                }
            }
            layerRects += openRects.values
            mergeLayerCuboids(openCuboids, ranges, layerRects, y)
            if (ranges.values.all { it.isFull }) {
                break
            }
        }
        openCuboids.values.forEach { ranges.getValue(it.block).add(it) }
        return BlockFindP(targets.map { (id, block) ->
            val range = ranges.getValue(block)
            BlockFindP.Group(id, range.count, range.toBlockRange())
        })
    }

    private fun scanBox(req: BlockFetchBoxQ): BlockFetchBoxP {
        val level = mc.level ?: throw McpNoPlayerError()
        val minX = minOf(req.from.x, req.to.x)
        val minY = minOf(req.from.y, req.to.y)
        val minZ = minOf(req.from.z, req.to.z)
        val maxX = maxOf(req.from.x, req.to.x)
        val maxY = maxOf(req.from.y, req.to.y)
        val maxZ = maxOf(req.from.z, req.to.z)
        val sizeX = maxX - minX + 1
        val sizeY = maxY - minY + 1
        val sizeZ = maxZ - minZ + 1
        val total = sizeX * sizeY * sizeZ
        if (total > BLOCK_FETCH_BOX_LIMIT) {
            throw McpBlockError("block fetch box max $BLOCK_FETCH_BOX_LIMIT blocks current $total")
        }
        if (minY < level.minBuildHeight || maxY >= level.maxBuildHeight) {
            throw McpBlockError("block fetch box y must be ${level.minBuildHeight}..${level.maxBuildHeight - 1}")
        }

        val paletteIds = mutableListOf<String>()
        val paletteIndex = linkedMapOf<String, Int>()
        val pos = BlockPos.MutableBlockPos()
        val layers = mutableListOf<BlockFetchBoxP.Layer>()
        for (y in minY..maxY) {
            val rows = mutableListOf<String>()
            for (z in minZ..maxZ) {
                val row = mutableListOf<String>()
                for (x in minX..maxX) {
                    val id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos.set(x, y, z)).block).toString()
                    val index = paletteIndex.getOrPut(id) {
                        paletteIds += id
                        paletteIds.lastIndex
                    }
                    row += index.toString()
                }
                rows += row.joinToString(" ")
            }
            layers += BlockFetchBoxP.Layer(y, rows)
        }
        return BlockFetchBoxP(RBlockPos(minX, minY, minZ), RBlockPos(maxX, maxY, maxZ), sizeX, sizeY, sizeZ, total, paletteIds, layers)
    }

    private fun resolveBlock(blockId: String): Block {
        val id = ResourceLocation.tryParse(blockId.trim())
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            throw McpBlockError("block id $blockId not found")
        }
        return BuiltInRegistries.BLOCK.get(id)
    }

    private class BlockRangeBuilder {
        val discrete = mutableListOf<RBlockPos>()
        val box = mutableListOf<RBlockAABB>()
        var count = 0
            private set
        val isFull get() = count >= BLOCK_FIND_LIMIT_PER_ID

        fun add(cuboid: OpenCuboid) {
            if (isFull) {
                return
            }
            val remaining = BLOCK_FIND_LIMIT_PER_ID - count
            if (cuboid.count <= remaining) {
                addCuboid(cuboid)
            } else {
                addPartialCuboid(cuboid, remaining)
            }
        }

        private fun addCuboid(cuboid: OpenCuboid) {
            count += cuboid.count
            addRange(cuboid.x1, cuboid.x2, cuboid.y1, cuboid.y2, cuboid.z1, cuboid.z2)
        }

        private fun addPartialCuboid(cuboid: OpenCuboid, limit: Int) {
            var remaining = limit
            for (y in cuboid.y1..cuboid.y2) {
                for (z in cuboid.z1..cuboid.z2) {
                    val take = minOf(cuboid.width, remaining)
                    addRange(cuboid.x1, cuboid.x1 + take - 1, y, y, z, z)
                    count += take
                    remaining -= take
                    if (remaining == 0) {
                        return
                    }
                }
            }
        }

        private fun addRange(x1: Int, x2: Int, y1: Int, y2: Int, z1: Int, z2: Int) {
            if (x1 == x2 && y1 == y2 && z1 == z2) {
                discrete += RBlockPos(x1, y1, z1)
            } else {
                box += RBlockAABB(RBlockPos(x1, y1, z1), RBlockPos(x2, y2, z2))
            }
        }

        fun toBlockRange() = BlockRange(discrete, box)
    }

    private fun extendLayerRect(
        openRects: MutableMap<RectKey, LayerRect>,
        layerRects: MutableList<LayerRect>,
        rowKeys: MutableSet<RectKey>,
        key: RectKey,
        z: Int,
    ) {
        val rect = openRects[key]
        if (rect == null) {
            openRects[key] = LayerRect(key.block, key.x1, key.x2, z, z)
        } else {
            rect.z2 = z
        }
        rowKeys += key
    }

    private fun mergeLayerCuboids(
        openCuboids: MutableMap<CuboidKey, OpenCuboid>,
        ranges: Map<Block, BlockRangeBuilder>,
        layerRects: List<LayerRect>,
        y: Int,
    ) {
        val layerKeys = mutableSetOf<CuboidKey>()
        for (rect in layerRects) {
            val key = CuboidKey(rect.block, rect.x1, rect.x2, rect.z1, rect.z2)
            val cuboid = openCuboids[key]
            if (cuboid == null) {
                openCuboids[key] = OpenCuboid(rect.block, rect.x1, rect.x2, y, y, rect.z1, rect.z2)
            } else {
                cuboid.y2 = y
            }
            layerKeys += key
        }
        openCuboids.keys.filter { it !in layerKeys }.forEach {
            val cuboid = openCuboids.remove(it)!!
            ranges.getValue(cuboid.block).add(cuboid)
        }
    }

    private data class RectKey(val block: Block, val x1: Int, val x2: Int)
    private data class CuboidKey(val block: Block, val x1: Int, val x2: Int, val z1: Int, val z2: Int)
    private class LayerRect(val block: Block, val x1: Int, val x2: Int, val z1: Int, var z2: Int)
    private class OpenCuboid(
        val block: Block,
        val x1: Int,
        val x2: Int,
        val y1: Int,
        var y2: Int,
        val z1: Int,
        val z2: Int,
    ) {
        val width get() = x2 - x1 + 1
        val count get() = (x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1)
    }
}
