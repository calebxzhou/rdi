package calebxzhou.rdi.mc.server.mcp;

import calebxzhou.rdi.mc.common2.mcp.RMcpBlockEntityData;
import calebxzhou.rdi.mc.common2.mcp.RMcpBlockPosData;
import calebxzhou.rdi.mc.common2.mcp.RMcpHarvestToolData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RMcpServerDataCodec211 {
    private static final String HARVEST_TOOL_FORMAT = "harvest-tool-v1";
    private static final int DROP_SAMPLES = 16;

    private RMcpServerDataCodec211() {
    }

    public static RMcpBlockEntityData blockEntityData(String dim, net.minecraft.world.level.block.entity.BlockEntity blockEntity, HolderLookup.Provider registryAccess) {
        var pos = blockEntity.getBlockPos();
        var blockState = blockEntity.getBlockState();
        var blockId = BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString();
        var type = BlockEntityType.getKey(blockEntity.getType()).toString();
        var tag = blockEntity.saveWithFullMetadata(registryAccess);
        return new RMcpBlockEntityData(
                dim,
                new RMcpBlockPosData(pos.getX(), pos.getY(), pos.getZ()),
                blockId,
                stateString(blockId, blockState.toString()),
                type,
                blockEntity.getClass().getName(),
                tag.toString()
        );
    }

    public static RMcpHarvestToolData harvestToolData(ServerLevel level, ServerPlayer player, String blockId, Integer x, Integer y, Integer z) {
        BlockPos pos = null;
        BlockState state;
        String stateSource;
        if (x != null && y != null && z != null) {
            pos = new BlockPos(x, y, z);
            state = level.getBlockState(pos);
            stateSource = "world";
        } else {
            if (blockId == null || blockId.isBlank()) {
                return null;
            }
            var id = ResourceLocation.tryParse(blockId.trim());
            if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
                return null;
            }
            state = BuiltInRegistries.BLOCK.get(id).defaultBlockState();
            stateSource = "default";
        }

        var resolvedBlockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        var blockEntity = pos == null ? null : level.getBlockEntity(pos);
        var scenarios = new ArrayList<RMcpHarvestToolData.ToolScenario>();
        for (var tool : toolScenarios(level.registryAccess())) {
            scenarios.add(harvestScenario(level, player, pos == null ? player.blockPosition() : pos, blockEntity, state, tool));
        }

        return new RMcpHarvestToolData(
                HARVEST_TOOL_FORMAT,
                stateSource,
                new RMcpHarvestToolData.Block(
                        resolvedBlockId,
                        stateString(resolvedBlockId, state.toString()),
                        pos == null ? null : new RMcpBlockPosData(pos.getX(), pos.getY(), pos.getZ()),
                        state.requiresCorrectToolForDrops(),
                        mineableWith(state),
                        minimumTier(state)
                ),
                List.copyOf(scenarios),
                List.of(
                        "Drops are simulated on the server from the active loot table.",
                        "Wrong tools return no drops when the block requires a correct tool.",
                        "Random loot is sampled " + DROP_SAMPLES + " times and summarized as countMin/countMax."
                )
        );
    }

    private static List<ToolSample> toolScenarios(HolderLookup.Provider registryAccess) {
        var tools = new ArrayList<ToolSample>();
        tools.add(new ToolSample("minecraft:air", "hand", null, ItemStack.EMPTY, Map.of()));
        tools.add(tool("minecraft:wooden_pickaxe", "pickaxe", "wood", new ItemStack(Items.WOODEN_PICKAXE), Map.of()));
        tools.add(tool("minecraft:stone_pickaxe", "pickaxe", "stone", new ItemStack(Items.STONE_PICKAXE), Map.of()));
        tools.add(tool("minecraft:iron_pickaxe", "pickaxe", "iron", new ItemStack(Items.IRON_PICKAXE), Map.of()));
        tools.add(tool("minecraft:diamond_pickaxe", "pickaxe", "diamond", new ItemStack(Items.DIAMOND_PICKAXE), Map.of()));
        tools.add(tool("minecraft:netherite_pickaxe", "pickaxe", "netherite", new ItemStack(Items.NETHERITE_PICKAXE), Map.of()));
        tools.add(tool("minecraft:diamond_axe", "axe", "diamond", new ItemStack(Items.DIAMOND_AXE), Map.of()));
        tools.add(tool("minecraft:diamond_shovel", "shovel", "diamond", new ItemStack(Items.DIAMOND_SHOVEL), Map.of()));
        tools.add(tool("minecraft:diamond_hoe", "hoe", "diamond", new ItemStack(Items.DIAMOND_HOE), Map.of()));
        tools.add(tool("minecraft:diamond_sword", "sword", "diamond", new ItemStack(Items.DIAMOND_SWORD), Map.of()));
        tools.add(tool("minecraft:shears", "shears", null, new ItemStack(Items.SHEARS), Map.of()));

        var enchantments = registryAccess.lookupOrThrow(Registries.ENCHANTMENT);
        var silkTouch = enchantments.getOrThrow(Enchantments.SILK_TOUCH);
        var fortune = enchantments.getOrThrow(Enchantments.FORTUNE);
        var silkPickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        silkPickaxe.enchant(silkTouch, 1);
        tools.add(tool("minecraft:diamond_pickaxe", "pickaxe", "diamond", silkPickaxe, Map.of("minecraft:silk_touch", 1)));
        var fortunePickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        fortunePickaxe.enchant(fortune, 3);
        tools.add(tool("minecraft:diamond_pickaxe", "pickaxe", "diamond", fortunePickaxe, Map.of("minecraft:fortune", 3)));
        return tools;
    }

    private static ToolSample tool(String id, String category, String tier, ItemStack stack, Map<String, Integer> enchantments) {
        return new ToolSample(id, category, tier, stack, enchantments);
    }

    private static RMcpHarvestToolData.ToolScenario harvestScenario(
            ServerLevel level,
            ServerPlayer player,
            BlockPos pos,
            net.minecraft.world.level.block.entity.BlockEntity blockEntity,
            BlockState state,
            ToolSample tool
    ) {
        var stack = tool.stack();
        boolean correctTool = stack.isCorrectToolForDrops(state);
        boolean harvestable = !state.requiresCorrectToolForDrops() || correctTool;
        var drops = harvestable ? sampledDrops(level, player, pos, blockEntity, state, stack) : List.<RMcpHarvestToolData.Drop>of();
        return new RMcpHarvestToolData.ToolScenario(
                new RMcpHarvestToolData.Tool(tool.id(), tool.category(), tool.tier(), tool.enchantments()),
                correctTool,
                harvestable,
                drops
        );
    }

    private static List<RMcpHarvestToolData.Drop> sampledDrops(
            ServerLevel level,
            ServerPlayer player,
            BlockPos pos,
            net.minecraft.world.level.block.entity.BlockEntity blockEntity,
            BlockState state,
            ItemStack tool
    ) {
        var samples = new LinkedHashMap<String, DropAccumulator>();
        for (int i = 0; i < DROP_SAMPLES; i++) {
            var sampleCounts = new LinkedHashMap<String, SampleDrop>();
            for (var stack : Block.getDrops(state, level, pos, blockEntity, player, tool)) {
                if (stack.isEmpty()) {
                    continue;
                }
                var id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                var snbt = stack.saveOptional(level.registryAccess()).toString();
                sampleCounts.computeIfAbsent(id, ignored -> new SampleDrop(id, snbt)).add(stack.getCount());
            }
            for (var sample : sampleCounts.values()) {
                samples.computeIfAbsent(sample.id(), ignored -> new DropAccumulator(sample.id(), sample.snbt())).sample(sample.count());
            }
            for (var accumulator : samples.values()) {
                if (!sampleCounts.containsKey(accumulator.id())) {
                    accumulator.sample(0);
                }
            }
        }
        return samples.values().stream()
                .filter(drop -> drop.max() > 0)
                .map(DropAccumulator::data)
                .toList();
    }

    private static List<String> mineableWith(BlockState state) {
        var tools = new ArrayList<String>();
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            tools.add("pickaxe");
        }
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
            tools.add("axe");
        }
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            tools.add("shovel");
        }
        if (state.is(BlockTags.MINEABLE_WITH_HOE)) {
            tools.add("hoe");
        }
        if (state.is(BlockTags.SWORD_EFFICIENT)) {
            tools.add("sword");
        }
        return List.copyOf(tools);
    }

    private static String minimumTier(BlockState state) {
        if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) {
            return "diamond";
        }
        if (state.is(BlockTags.NEEDS_IRON_TOOL)) {
            return "iron";
        }
        if (state.is(BlockTags.NEEDS_STONE_TOOL)) {
            return "stone";
        }
        return null;
    }

    private static String stateString(String id, String raw) {
        int propertyStart = raw.indexOf('[');
        return propertyStart < 0 ? id : id + raw.substring(propertyStart);
    }

    private record ToolSample(String id, String category, String tier, ItemStack stack, Map<String, Integer> enchantments) {
    }

    private static final class SampleDrop {
        private final String id;
        private final String snbt;
        private int count;

        private SampleDrop(String id, String snbt) {
            this.id = id;
            this.snbt = snbt;
        }

        private String id() {
            return id;
        }

        private String snbt() {
            return snbt;
        }

        private int count() {
            return count;
        }

        private void add(int count) {
            this.count += count;
        }
    }

    private static final class DropAccumulator {
        private final String id;
        private final String snbt;
        private int min = Integer.MAX_VALUE;
        private int max;
        private int sampleCount;

        private DropAccumulator(String id, String snbt) {
            this.id = id;
            this.snbt = snbt;
        }

        private String id() {
            return id;
        }

        private int max() {
            return max;
        }

        private void sample(int count) {
            sampleCount++;
            min = Math.min(min, count);
            max = Math.max(max, count);
        }

        private RMcpHarvestToolData.Drop data() {
            var countMin = sampleCount < DROP_SAMPLES ? 0 : min;
            return new RMcpHarvestToolData.Drop(id, countMin == Integer.MAX_VALUE ? 0 : countMin, max, snbt);
        }
    }
}
