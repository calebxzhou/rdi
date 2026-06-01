package calebxzhou.rdi.mc.visky.helper

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.animal.sniffer.Sniffer
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.structure.BuiltinStructures
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.storage.loot.BuiltInLootTables
import net.minecraft.world.level.storage.loot.LootTable

object SuspiciousSniffer {
    private const val CONVERSION_CHANCE = 0.1f

    @JvmStatic
    fun isExtraDiggable(state: BlockState): Boolean {
        return state.`is`(BlockTags.SAND)
                || state.`is`(Blocks.GRAVEL)
                || state.`is`(Blocks.SUSPICIOUS_SAND)
                || state.`is`(Blocks.SUSPICIOUS_GRAVEL)
    }

    @JvmStatic
    fun onDig(sniffer: Sniffer, headPos: BlockPos): Boolean {
        val level = sniffer.level()
        if (level.isClientSide || level !is ServerLevel) {
            return false
        }

        val digPos = headPos.below()
        val state = level.getBlockState(digPos)
        if (!isExtraDiggable(state)) {
            return false
        }

        dropIronNugget(level, headPos)
        tryConvertToSuspicious(level, digPos, state)
        level.playSound(null, sniffer, SoundEvents.SNIFFER_DROP_SEED, SoundSource.NEUTRAL, 1.0f, 1.0f)
        return true
    }

    private fun dropIronNugget(level: ServerLevel, pos: BlockPos) {
        val itemEntity = ItemEntity(level, pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), ItemStack(Items.IRON_NUGGET))
        itemEntity.setDefaultPickUpDelay()
        level.addFreshEntity(itemEntity)
    }

    private fun tryConvertToSuspicious(level: ServerLevel, pos: BlockPos, state: BlockState): Boolean {
        if (!level.gameRules.getBoolean(GameRules.RULE_MOBGRIEFING) || level.random.nextFloat() >= CONVERSION_CHANCE) {
            return false
        }

        val lootTable = getArchaeologyLootTable(level, pos, state.block) ?: return false
        val suspiciousBlock = getSuspiciousBlock(state) ?: return false
        level.setBlockAndUpdate(pos, suspiciousBlock.defaultBlockState())
        level.getBlockEntity(pos, BlockEntityType.BRUSHABLE_BLOCK)
            .ifPresent { it.setLootTable(lootTable, level.random.nextLong()) }
        return true
    }

    private fun getSuspiciousBlock(state: BlockState): Block? {
        if (state.`is`(Blocks.SAND) || state.`is`(Blocks.RED_SAND)) {
            return Blocks.SUSPICIOUS_SAND
        }
        if (state.`is`(Blocks.GRAVEL)) {
            return Blocks.SUSPICIOUS_GRAVEL
        }
        return null
    }

    private fun getArchaeologyLootTable(level: ServerLevel, pos: BlockPos, block: Block): ResourceKey<LootTable>? {
        if (block == Blocks.SAND || block == Blocks.RED_SAND) {
            return getSandLootTable(level, pos)
        }
        if (block == Blocks.GRAVEL) {
            return getGravelLootTable(level, pos)
        }
        return null
    }

    private fun getSandLootTable(level: ServerLevel, pos: BlockPos): ResourceKey<LootTable>? {
        if (isInStructure(level, pos, BuiltinStructures.DESERT_PYRAMID)) {
            return if (level.random.nextFloat() < 0.2f) BuiltInLootTables.DESERT_WELL_ARCHAEOLOGY else BuiltInLootTables.DESERT_PYRAMID_ARCHAEOLOGY
        }
        if (isInStructure(level, pos, BuiltinStructures.OCEAN_RUIN_WARM)) {
            return BuiltInLootTables.OCEAN_RUIN_WARM_ARCHAEOLOGY
        }
        return null
    }

    private fun getGravelLootTable(level: ServerLevel, pos: BlockPos): ResourceKey<LootTable>? {
        if (isInStructure(level, pos, BuiltinStructures.OCEAN_RUIN_COLD)) {
            return BuiltInLootTables.OCEAN_RUIN_COLD_ARCHAEOLOGY
        }
        if (isInStructure(level, pos, BuiltinStructures.TRAIL_RUINS)) {
            return if (level.random.nextFloat() < 0.2f) BuiltInLootTables.TRAIL_RUINS_ARCHAEOLOGY_RARE else BuiltInLootTables.TRAIL_RUINS_ARCHAEOLOGY_COMMON
        }
        return null
    }

    private fun isInStructure(level: ServerLevel, pos: BlockPos, structureKey: ResourceKey<Structure>): Boolean {
        val structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getOrThrow(structureKey)
        return level.structureManager().getStructureWithPieceAt(pos, structure).isValid
    }
}
