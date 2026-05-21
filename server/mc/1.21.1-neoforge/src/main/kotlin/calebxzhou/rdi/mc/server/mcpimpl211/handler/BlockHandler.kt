package calebxzhou.rdi.mc.server.mcpimpl211.handler

import calebxzhou.rdi.mc.common2.mcp.McpBadBlockStateError
import calebxzhou.rdi.mc.common2.mcp.McpBlockError
import calebxzhou.rdi.mc.common2.mcp.model.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

private const val BLOCK_BATCH_LIMIT = 2048

object BlockHandler211 {
    private typealias Ok = ActionResult.Ok<Unit>
    private typealias Err = ActionResult.Err
    val RBlockPos.mcBpos get() = BlockPos(x,y,z)
    fun handleBox(req: BlockPlaceBoxQ, player: ServerPlayer): Result<BlockActionP> = runCatching {
        val box = RBlockAABB.fromDelta(req.startPos, req.deltaPos)
        val positions = when (req.form) {
            BlockActionForm.BOX -> box.posListBox
            BlockActionForm.RING -> box.posListRing
        }
        if (positions.size > BLOCK_BATCH_LIMIT) {
            throw McpBlockError("${positions.size} exceeds limit $BLOCK_BATCH_LIMIT")
        }
        val block = resolvePlaceBlock(req.blockId)
        if (countPlaceItems(player, block) == 0) {
            throw McpBlockError("insufficient item")
        }
        place(player, positions, block, req.state, req.test)
    }

    fun handleDiscrete(req: BlockPlaceDiscreteQ, player: ServerPlayer): Result<BlockActionP> = runCatching {
        if (req.targets.size > BLOCK_BATCH_LIMIT) {
            throw McpBlockError("${req.targets.size} exceeds limit $BLOCK_BATCH_LIMIT")
        }
        val block = resolvePlaceBlock(req.blockId)
        if (countPlaceItems(player, block) == 0) {
            throw McpBlockError("insufficient item")
        }
        val failures = mutableMapOf<Err, MutableList<RBlockPos>>()
        var count = 0
        for (target in req.targets) {
            when (val result = runCatching { placeOne(player, target.pos, block, target.state, req.test) }.getOrElse(::failureResult)) {
                is ActionResult.Ok<*> -> count++
                is Err -> failures.getOrPut(result) { mutableListOf() } += target.pos
            }
        }
        BlockActionP(count, failures, req.test)
    }

    private fun place(
        player: ServerPlayer,
        positions: List<RBlockPos>,
        block: Block,
        stateOverrides: Map<String, String>,
        test: Boolean,
    ): BlockActionP {
        val failures = mutableMapOf<Err, MutableList<RBlockPos>>()
        var count = 0
        for (pos in positions) {
            when (val result = runCatching { placeOne(player, pos, block, stateOverrides, test) }.getOrElse(::failureResult)) {
                is ActionResult.Ok<*> -> count++
                is Err -> failures.getOrPut(result) { mutableListOf() } += pos
            }
        }
        return BlockActionP(count, failures, test)
    }

    private fun placeOne(
        player: ServerPlayer,
        pos: RBlockPos,
        block: Block,
        stateOverrides: Map<String, String>,
        test: Boolean,
    ): ActionResult<Unit> {
        val level = player.serverLevel()
        val blockPos = pos.mcBpos
        val targetCheck = checkPlaceTarget(player, blockPos)
        if (targetCheck is Err) {
            return targetCheck
        }
        val beforeState = level.getBlockState(blockPos)
        if (!beforeState.isAir) {
            return Err("not air")
        }
        val stack = findPlaceStack(player, block) ?: return Err("insufficient item")
        val blockItem = stack.item as BlockItem
        val hitResult = placeHitResult(player, blockPos)
        if (!level.mayInteract(player, hitResult.blockPos)) {
            return Err("target protected")
        }
        val placeContext = BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hitResult)
        val plannedState = blockItem.block.getStateForPlacement(placeContext) ?: return Err("can't find state for placement ")
        applyState(plannedState, stateOverrides)
        if (test) {
            return Ok(Unit)
        }
        val result = blockItem.place(placeContext)
        val placedState = level.getBlockState(blockPos)
        if (!result.consumesAction() || placedState.isAir) {
            return Err("rejected")
        }
        val overrideState = applyState(placedState, stateOverrides)
        if (overrideState != placedState) {
            level.setBlock(blockPos, overrideState, 3)
        }
        player.inventory.setChanged()
        player.inventoryMenu.broadcastChanges()
        player.containerMenu.broadcastChanges()
        return Ok(Unit)
    }

    private fun failureResult(e: Throwable): Err {
        return Err(e.message ?: e.javaClass.simpleName)
    }

    private fun resolvePlaceBlock(blockId: String): Block {
        val id = ResourceLocation.tryParse(blockId.trim())
        if (id==null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            throw McpBlockError("block id $id not found")
        }
        return BuiltInRegistries.BLOCK.get(id)
    }

    private fun checkPlaceTarget(player: ServerPlayer, pos: BlockPos): ActionResult<Unit> {
        val level = player.serverLevel()
        if (level.isOutsideBuildHeight(pos)) {
            return Err("out of chunk build height")
        }
        if (!level.isLoaded(pos)) {
            return Err("chunk not loaded")
        }

        if (!level.mayInteract(player, pos)) {
            return Err("target can't be interacted")
        }
        return Ok(Unit)
    }

    private fun countPlaceItems(player: ServerPlayer, block: Block): Int {
        return player.inventory.items.sumOf { stack ->
            if (stack != null && !stack.isEmpty && (stack.item as? BlockItem)?.block == block) stack.count else 0
        }
    }

    private fun findPlaceStack(player: ServerPlayer, block: Block): ItemStack? {
        return player.inventory.items.firstOrNull { stack ->
            stack != null && !stack.isEmpty && (stack.item as? BlockItem)?.block == block
        }
    }

    private fun placeHitResult(player: ServerPlayer, target: BlockPos): BlockHitResult {
        val level = player.serverLevel()
        val faces = arrayOf(Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.DOWN)
        for (face in faces) {
            val clickedPos = target.relative(face.opposite)
            if (!level.isLoaded(clickedPos) || level.getBlockState(clickedPos).isAir || !level.mayInteract(player, clickedPos)) {
                continue
            }
            return BlockHitResult(Vec3.atCenterOf(target), face, clickedPos, false)
        }
        return BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false)
    }

    private fun applyState(state: BlockState, overrides: Map<String, String>): BlockState {
        var next = state
        for ((name, value) in overrides) {
            val property = next.block.stateDefinition.getProperty(name) ?: throw McpBadBlockStateError()
            next = setPropertyValue(next, property, value) ?: throw McpBadBlockStateError()
        }
        return next
    }

    private fun <T : Comparable<T>> setPropertyValue(state: BlockState, property: Property<T>, rawValue: String): BlockState? {
        return property.getValue(rawValue).map { state.setValue(property, it) }.orElse(null)
    }

}
