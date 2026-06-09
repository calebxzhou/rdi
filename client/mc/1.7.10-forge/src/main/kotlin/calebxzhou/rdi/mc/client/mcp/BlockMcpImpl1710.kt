package calebxzhou.rdi.mc.client.mcp

import calebxzhou.rdi.mc.common2.mcp.McpBlockError
import calebxzhou.rdi.mc.common2.mcp.McpError
import calebxzhou.rdi.mc.common2.mcp.McpNoPlayerError
import calebxzhou.rdi.mc.common2.mcp.model.BlockFetchBoxP
import calebxzhou.rdi.mc.common2.mcp.model.BlockFetchBoxQ
import calebxzhou.rdi.mc.common2.mcp.model.BlockFindP
import calebxzhou.rdi.mc.common2.mcp.model.BlockFindQ
import calebxzhou.rdi.mc.common2.mcp.model.BlockRange
import calebxzhou.rdi.mc.common2.mcp.model.RBlockAABB
import calebxzhou.rdi.mc.common2.mcp.model.RBlockPos
import net.minecraft.block.Block
import net.minecraft.client.Minecraft
import net.minecraft.world.chunk.Chunk
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException

object BlockMcpImpl1710 {
    private const val BLOCK_FIND_HORIZONTAL_SIZE = 256
    private const val BLOCK_FIND_LIMIT_PER_ID = 128
    private const val BLOCK_FETCH_BOX_LIMIT = 1024
    private const val MIN_Y = 0
    private const val MAX_Y = 255

    private val minecraft get() = Minecraft.getMinecraft()

    fun find(req: BlockFindQ): Result<BlockFindP> = onMainThread { scanBlocks(req) }

    fun fetchBox(req: BlockFetchBoxQ): Result<BlockFetchBoxP> = onMainThread { scanBox(req) }

    private fun <T> onMainThread(task: () -> T): Result<T> = runCatching {
        minecraft.func_152343_a(Callable { task() }).get() as T
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
        val player = minecraft.thePlayer ?: throw McpNoPlayerError()
        val world = minecraft.theWorld ?: throw McpNoPlayerError()
        val centerX = floorBlock(player.posX)
        val centerZ = floorBlock(player.posZ)
        val minX = centerX - BLOCK_FIND_HORIZONTAL_SIZE / 2
        val minZ = centerZ - BLOCK_FIND_HORIZONTAL_SIZE / 2
        val maxX = minX + BLOCK_FIND_HORIZONTAL_SIZE - 1
        val maxZ = minZ + BLOCK_FIND_HORIZONTAL_SIZE - 1
        val openCuboids = mutableMapOf<CuboidKey, OpenCuboid>()
        for (y in MIN_Y..MAX_Y) {
            val layerRects = mutableListOf<LayerRect>()
            val openRects = mutableMapOf<RectKey, LayerRect>()
            for (z in minZ..maxZ) {
                val rowKeys = mutableSetOf<RectKey>()
                var runBlock: Block? = null
                var runStartX = minX
                val chunkZ = z shr 4
                var chunkX = Int.MIN_VALUE
                var chunk: Chunk? = null
                for (x in minX..maxX) {
                    val nextChunkX = x shr 4
                    if (nextChunkX != chunkX) {
                        chunkX = nextChunkX
                        val chunkBlockX = chunkX shl 4
                        val chunkBlockZ = chunkZ shl 4
                        chunk = if (world.blockExists(chunkBlockX, y, chunkBlockZ)) {
                            world.getChunkFromBlockCoords(chunkBlockX, chunkBlockZ)
                        } else {
                            null
                        }
                    }
                    val foundBlock = chunk?.getBlock(x and 15, y, z and 15)
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
        return blockFindResult(targets, ranges)
    }

    private fun scanBox(req: BlockFetchBoxQ): BlockFetchBoxP {
        val world = minecraft.theWorld ?: throw McpNoPlayerError()
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
            throw McpBlockError("block fetch box max $BLOCK_FETCH_BOX_LIMIT blocks current ${total}")
        }
        if (minY < MIN_Y || maxY > MAX_Y) {
            throw McpBlockError("block fetch box y must be $MIN_Y..$MAX_Y")
        }

        val paletteIds = mutableListOf<String>()
        val paletteIndex = linkedMapOf<String, Int>()
        val layers = mutableListOf<BlockFetchBoxP.Layer>()
        for (y in minY..maxY) {
            val rows = mutableListOf<String>()
            for (z in minZ..maxZ) {
                val row = mutableListOf<String>()
                for (x in minX..maxX) {
                    val id = if (world.blockExists(x, y, z)) {
                        blockId(world.getBlock(x, y, z))
                    } else {
                        "unloaded"
                    }
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
        return BlockFetchBoxP(
            from = RBlockPos(minX, minY, minZ),
            to = RBlockPos(maxX, maxY, maxZ),
            sizeX = sizeX,
            sizeY = sizeY,
            sizeZ = sizeZ,
            total = total,
            palette = paletteIds,
            layers = layers,
        )
    }

    private fun resolveBlock(blockId: String): Block {
        val id = blockId.trim()
        val candidates = buildList {
            add(id)
            if (id.startsWith("minecraft:")) add(id.removePrefix("minecraft:"))
            if (!id.contains(":")) add("minecraft:$id")
        }
        val block = candidates.asSequence()
            .filter { Block.blockRegistry.containsKey(it) }
            .map { Block.blockRegistry.getObject(it) as Block }
            .firstOrNull()
        return block ?: throw McpBlockError("block id $id not found")
    }

    private fun blockId(block: Block): String {
        val id = Block.blockRegistry.getNameForObject(block)?.toString()
            ?: return "unknown:${Block.getIdFromBlock(block)}"
        return if (id.contains(":")) id else "minecraft:$id"
    }

    private fun blockFindResult(targets: Map<String, Block>, ranges: Map<Block, BlockRangeBuilder>): BlockFindP {
        return BlockFindP(targets.map { (id, block) ->
            val range = ranges.getValue(block)
            BlockFindP.Group(id, range.count, range.toBlockRange())
        })
    }

    private fun floorBlock(value: Double): Int {
        val intValue = value.toInt()
        return if (value < intValue.toDouble()) intValue - 1 else intValue
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
                return
            }
            box += RBlockAABB(RBlockPos(x1, y1, z1), RBlockPos(x2, y2, z2))
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

    private data class RectKey(
        val block: Block,
        val x1: Int,
        val x2: Int,
    )

    private data class CuboidKey(
        val block: Block,
        val x1: Int,
        val x2: Int,
        val z1: Int,
        val z2: Int,
    )

    private class LayerRect(
        val block: Block,
        val x1: Int,
        val x2: Int,
        val z1: Int,
        var z2: Int,
    )

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
