package calebxzhou.rdi.mc.common2.schem;

/**
 * Maps classic numeric schematic block IDs to modern Minecraft resource locations.
 *
 * calebxzhou @ 2026-05-08 16:10
 */
public final class SchemLegacyBlockMap {
    private SchemLegacyBlockMap() {
    }

    public static LegacyBlock resolve(int id, int data) {
        return switch (id) {
            case 0 -> block("minecraft:air");
            case 1 -> block(stone(data));
            case 2 -> block("minecraft:grass_block");
            case 3 -> block(switch (data) {
                case 1 -> "minecraft:coarse_dirt";
                case 2 -> "minecraft:podzol";
                default -> "minecraft:dirt";
            });
            case 4 -> block("minecraft:cobblestone");
            case 5 -> block(wood(data, "planks"));
            case 6 -> block(wood(data, "sapling"));
            case 7 -> block("minecraft:bedrock");
            case 8 -> block("minecraft:water", "legacy flowing_water");
            case 9 -> block("minecraft:water");
            case 10 -> block("minecraft:lava", "legacy flowing_lava");
            case 11 -> block("minecraft:lava");
            case 12 -> block(data == 1 ? "minecraft:red_sand" : "minecraft:sand");
            case 13 -> block("minecraft:gravel");
            case 14 -> block("minecraft:gold_ore");
            case 15 -> block("minecraft:iron_ore");
            case 16 -> block("minecraft:coal_ore");
            case 17 -> block(wood(data, "log"), logNote(data));
            case 18 -> block(wood(data, "leaves"), "legacy leaves data keeps decay/check bits");
            case 19 -> block(data == 1 ? "minecraft:wet_sponge" : "minecraft:sponge");
            case 20 -> block("minecraft:glass");
            case 21 -> block("minecraft:lapis_ore");
            case 22 -> block("minecraft:lapis_block");
            case 23 -> block("minecraft:dispenser", facingNote(data));
            case 24 -> block(switch (data) {
                case 1 -> "minecraft:chiseled_sandstone";
                case 2 -> "minecraft:cut_sandstone";
                default -> "minecraft:sandstone";
            });
            case 25 -> block("minecraft:note_block");
            case 26 -> block("minecraft:red_bed", "legacy bed data keeps part/facing/occupied bits");
            case 27 -> block("minecraft:powered_rail");
            case 28 -> block("minecraft:detector_rail");
            case 29 -> block("minecraft:sticky_piston", facingNote(data));
            case 30 -> block("minecraft:cobweb");
            case 31 -> block(switch (data) {
                case 1 -> "minecraft:short_grass";
                case 2 -> "minecraft:fern";
                default -> "minecraft:dead_bush";
            });
            case 32 -> block("minecraft:dead_bush");
            case 33 -> block("minecraft:piston", facingNote(data));
            case 34 -> block("minecraft:piston_head", facingNote(data));
            case 35 -> block(color(data) + "_wool");
            case 36 -> block("minecraft:moving_piston");
            case 37 -> block("minecraft:dandelion");
            case 38 -> block(flower(data));
            case 39 -> block("minecraft:brown_mushroom");
            case 40 -> block("minecraft:red_mushroom");
            case 41 -> block("minecraft:gold_block");
            case 42 -> block("minecraft:iron_block");
            case 43 -> block(slab(data), "legacy double stone slab");
            case 44 -> block(slab(data));
            case 45 -> block("minecraft:bricks");
            case 46 -> block("minecraft:tnt");
            case 47 -> block("minecraft:bookshelf");
            case 48 -> block("minecraft:mossy_cobblestone");
            case 49 -> block("minecraft:obsidian");
            case 50 -> block("minecraft:torch");
            case 51 -> block("minecraft:fire");
            case 52 -> block("minecraft:spawner");
            case 53 -> block("minecraft:oak_stairs", stairsNote(data));
            case 54 -> block("minecraft:chest", facingNote(data));
            case 55 -> block("minecraft:redstone_wire");
            case 56 -> block("minecraft:diamond_ore");
            case 57 -> block("minecraft:diamond_block");
            case 58 -> block("minecraft:crafting_table");
            case 59 -> block("minecraft:wheat");
            case 60 -> block("minecraft:farmland");
            case 61 -> block("minecraft:furnace", facingNote(data));
            case 62 -> block("minecraft:furnace", "legacy lit_furnace; " + facingNote(data));
            case 63 -> block("minecraft:oak_sign", "legacy standing_sign rotation data: " + data);
            case 64 -> block("minecraft:oak_door", "legacy door data keeps half/facing/open bits");
            case 65 -> block("minecraft:ladder", facingNote(data));
            case 66 -> block("minecraft:rail");
            case 67 -> block("minecraft:cobblestone_stairs", stairsNote(data));
            case 68 -> block("minecraft:oak_wall_sign", facingNote(data));
            case 69 -> block("minecraft:lever", "legacy lever data keeps face/facing/powered bits");
            case 70 -> block("minecraft:stone_pressure_plate");
            case 71 -> block("minecraft:iron_door", "legacy door data keeps half/facing/open bits");
            case 72 -> block("minecraft:oak_pressure_plate");
            case 73 -> block("minecraft:redstone_ore");
            case 74 -> block("minecraft:redstone_ore", "legacy lit_redstone_ore");
            case 75 -> block("minecraft:redstone_torch", "legacy unlit_redstone_torch; " + facingNote(data));
            case 76 -> block("minecraft:redstone_torch", facingNote(data));
            case 77 -> block("minecraft:stone_button", facingNote(data));
            case 78 -> block("minecraft:snow");
            case 79 -> block("minecraft:ice");
            case 80 -> block("minecraft:snow_block");
            case 81 -> block("minecraft:cactus");
            case 82 -> block("minecraft:clay");
            case 83 -> block("minecraft:sugar_cane");
            case 84 -> block("minecraft:jukebox");
            case 85 -> block("minecraft:oak_fence");
            case 86 -> block("minecraft:pumpkin");
            case 87 -> block("minecraft:netherrack");
            case 88 -> block("minecraft:soul_sand");
            case 89 -> block("minecraft:glowstone");
            case 90 -> block("minecraft:nether_portal");
            case 91 -> block("minecraft:jack_o_lantern");
            case 92 -> block("minecraft:cake");
            case 93 -> block("minecraft:repeater", "legacy unpowered_repeater");
            case 94 -> block("minecraft:repeater", "legacy powered_repeater");
            case 95 -> block(color(data) + "_stained_glass");
            case 96 -> block("minecraft:oak_trapdoor", "legacy trapdoor data keeps half/facing/open bits");
            case 97 -> block(infested(data));
            case 98 -> block(switch (data) {
                case 1 -> "minecraft:mossy_stone_bricks";
                case 2 -> "minecraft:cracked_stone_bricks";
                case 3 -> "minecraft:chiseled_stone_bricks";
                default -> "minecraft:stone_bricks";
            });
            case 99 -> block("minecraft:brown_mushroom_block", "legacy mushroom data keeps cap/stem shape");
            case 100 -> block("minecraft:red_mushroom_block", "legacy mushroom data keeps cap/stem shape");
            case 101 -> block("minecraft:iron_bars");
            case 102 -> block("minecraft:glass_pane");
            case 103 -> block("minecraft:melon");
            case 104 -> block("minecraft:pumpkin_stem");
            case 105 -> block("minecraft:melon_stem");
            case 106 -> block("minecraft:vine", "legacy vine data keeps attached faces");
            case 107 -> block("minecraft:oak_fence_gate", facingNote(data));
            case 108 -> block("minecraft:brick_stairs", stairsNote(data));
            case 109 -> block("minecraft:stone_brick_stairs", stairsNote(data));
            case 110 -> block("minecraft:mycelium");
            case 111 -> block("minecraft:lily_pad");
            case 112 -> block("minecraft:nether_bricks");
            case 113 -> block("minecraft:nether_brick_fence");
            case 114 -> block("minecraft:nether_brick_stairs", stairsNote(data));
            case 115 -> block("minecraft:nether_wart");
            case 116 -> block("minecraft:enchanting_table");
            case 117 -> block("minecraft:brewing_stand");
            case 118 -> block("minecraft:cauldron");
            case 119 -> block("minecraft:end_portal");
            case 120 -> block("minecraft:end_portal_frame", "legacy data keeps eye/facing bits");
            case 121 -> block("minecraft:end_stone");
            case 122 -> block("minecraft:dragon_egg");
            case 123 -> block("minecraft:redstone_lamp");
            case 124 -> block("minecraft:redstone_lamp", "legacy lit_redstone_lamp");
            case 125 -> block(wood(data, "slab"), "legacy double wooden slab");
            case 126 -> block(wood(data, "slab"));
            case 127 -> block("minecraft:cocoa", facingNote(data));
            case 128 -> block("minecraft:sandstone_stairs", stairsNote(data));
            case 129 -> block("minecraft:emerald_ore");
            case 130 -> block("minecraft:ender_chest", facingNote(data));
            case 131 -> block("minecraft:tripwire_hook");
            case 132 -> block("minecraft:tripwire");
            case 133 -> block("minecraft:emerald_block");
            case 134 -> block("minecraft:spruce_stairs", stairsNote(data));
            case 135 -> block("minecraft:birch_stairs", stairsNote(data));
            case 136 -> block("minecraft:jungle_stairs", stairsNote(data));
            case 137 -> block("minecraft:command_block", facingNote(data));
            case 138 -> block("minecraft:beacon");
            case 139 -> block(data == 1 ? "minecraft:mossy_cobblestone_wall" : "minecraft:cobblestone_wall");
            case 140 -> block("minecraft:flower_pot");
            case 141 -> block("minecraft:carrots");
            case 142 -> block("minecraft:potatoes");
            case 143 -> block("minecraft:oak_button", facingNote(data));
            case 144 -> block("minecraft:skeleton_skull", "legacy skull data may be skeleton/wither/zombie/player/creeper/dragon");
            case 145 -> block(switch (data >> 2) {
                case 1 -> "minecraft:chipped_anvil";
                case 2 -> "minecraft:damaged_anvil";
                default -> "minecraft:anvil";
            }, facingNote(data));
            case 146 -> block("minecraft:trapped_chest", facingNote(data));
            case 147 -> block("minecraft:light_weighted_pressure_plate");
            case 148 -> block("minecraft:heavy_weighted_pressure_plate");
            case 149 -> block("minecraft:comparator", "legacy unpowered_comparator");
            case 150 -> block("minecraft:comparator", "legacy powered_comparator");
            case 151 -> block("minecraft:daylight_detector");
            case 152 -> block("minecraft:redstone_block");
            case 153 -> block("minecraft:nether_quartz_ore");
            case 154 -> block("minecraft:hopper", facingNote(data));
            case 155 -> block(data == 1 ? "minecraft:chiseled_quartz_block" : data == 2 ? "minecraft:quartz_pillar" : "minecraft:quartz_block");
            case 156 -> block("minecraft:quartz_stairs", stairsNote(data));
            case 157 -> block("minecraft:activator_rail");
            case 158 -> block("minecraft:dropper", facingNote(data));
            case 159 -> block(color(data) + "_terracotta");
            case 160 -> block(color(data) + "_stained_glass_pane");
            case 161 -> block(data % 4 == 1 ? "minecraft:dark_oak_leaves" : "minecraft:acacia_leaves", "legacy leaves data keeps decay/check bits");
            case 162 -> block(data % 4 == 1 ? "minecraft:dark_oak_log" : "minecraft:acacia_log", logNote(data));
            case 163 -> block("minecraft:acacia_stairs", stairsNote(data));
            case 164 -> block("minecraft:dark_oak_stairs", stairsNote(data));
            case 165 -> block("minecraft:slime_block");
            case 166 -> block("minecraft:barrier");
            case 167 -> block("minecraft:iron_trapdoor", "legacy trapdoor data keeps half/facing/open bits");
            case 168 -> block(switch (data) {
                case 1 -> "minecraft:prismarine_bricks";
                case 2 -> "minecraft:dark_prismarine";
                default -> "minecraft:prismarine";
            });
            case 169 -> block("minecraft:sea_lantern");
            case 170 -> block("minecraft:hay_block");
            case 171 -> block(color(data) + "_carpet");
            case 172 -> block("minecraft:terracotta");
            case 173 -> block("minecraft:coal_block");
            case 174 -> block("minecraft:packed_ice");
            case 175 -> block(doublePlant(data));
            case 176 -> block("minecraft:white_banner", "legacy standing_banner rotation data: " + data);
            case 177 -> block("minecraft:white_wall_banner", facingNote(data));
            case 178 -> block("minecraft:daylight_detector", "legacy inverted_daylight_detector");
            case 179 -> block(data == 1 ? "minecraft:chiseled_red_sandstone" : data == 2 ? "minecraft:cut_red_sandstone" : "minecraft:red_sandstone");
            case 180 -> block("minecraft:red_sandstone_stairs", stairsNote(data));
            case 181 -> block("minecraft:red_sandstone_slab", "legacy double red sandstone slab");
            case 182 -> block("minecraft:red_sandstone_slab");
            case 183 -> block("minecraft:spruce_fence_gate", facingNote(data));
            case 184 -> block("minecraft:birch_fence_gate", facingNote(data));
            case 185 -> block("minecraft:jungle_fence_gate", facingNote(data));
            case 186 -> block("minecraft:dark_oak_fence_gate", facingNote(data));
            case 187 -> block("minecraft:acacia_fence_gate", facingNote(data));
            case 188 -> block("minecraft:spruce_fence");
            case 189 -> block("minecraft:birch_fence");
            case 190 -> block("minecraft:jungle_fence");
            case 191 -> block("minecraft:dark_oak_fence");
            case 192 -> block("minecraft:acacia_fence");
            case 193 -> block("minecraft:spruce_door", "legacy door data keeps half/facing/open bits");
            case 194 -> block("minecraft:birch_door", "legacy door data keeps half/facing/open bits");
            case 195 -> block("minecraft:jungle_door", "legacy door data keeps half/facing/open bits");
            case 196 -> block("minecraft:acacia_door", "legacy door data keeps half/facing/open bits");
            case 197 -> block("minecraft:dark_oak_door", "legacy door data keeps half/facing/open bits");
            case 198 -> block("minecraft:end_rod", facingNote(data));
            case 199 -> block("minecraft:chorus_plant");
            case 200 -> block("minecraft:chorus_flower");
            case 201 -> block("minecraft:purpur_block");
            case 202 -> block("minecraft:purpur_pillar");
            case 203 -> block("minecraft:purpur_stairs", stairsNote(data));
            case 204 -> block("minecraft:purpur_slab", "legacy double purpur slab");
            case 205 -> block("minecraft:purpur_slab");
            case 206 -> block("minecraft:end_stone_bricks");
            case 207 -> block("minecraft:beetroots");
            case 208 -> block("minecraft:dirt_path");
            case 209 -> block("minecraft:end_gateway");
            case 210 -> block("minecraft:repeating_command_block", facingNote(data));
            case 211 -> block("minecraft:chain_command_block", facingNote(data));
            case 212 -> block("minecraft:frosted_ice");
            case 213 -> block("minecraft:magma_block");
            case 214 -> block("minecraft:nether_wart_block");
            case 215 -> block("minecraft:red_nether_bricks");
            case 216 -> block("minecraft:bone_block");
            case 217 -> block("minecraft:structure_void");
            case 218 -> block("minecraft:observer", facingNote(data));
            case 219 -> block("minecraft:white_shulker_box");
            case 220 -> block("minecraft:orange_shulker_box");
            case 221 -> block("minecraft:magenta_shulker_box");
            case 222 -> block("minecraft:light_blue_shulker_box");
            case 223 -> block("minecraft:yellow_shulker_box");
            case 224 -> block("minecraft:lime_shulker_box");
            case 225 -> block("minecraft:pink_shulker_box");
            case 226 -> block("minecraft:gray_shulker_box");
            case 227 -> block("minecraft:light_gray_shulker_box");
            case 228 -> block("minecraft:cyan_shulker_box");
            case 229 -> block("minecraft:purple_shulker_box");
            case 230 -> block("minecraft:blue_shulker_box");
            case 231 -> block("minecraft:brown_shulker_box");
            case 232 -> block("minecraft:green_shulker_box");
            case 233 -> block("minecraft:red_shulker_box");
            case 234 -> block("minecraft:black_shulker_box");
            case 235 -> block("minecraft:white_glazed_terracotta", facingNote(data));
            case 236 -> block("minecraft:orange_glazed_terracotta", facingNote(data));
            case 237 -> block("minecraft:magenta_glazed_terracotta", facingNote(data));
            case 238 -> block("minecraft:light_blue_glazed_terracotta", facingNote(data));
            case 239 -> block("minecraft:yellow_glazed_terracotta", facingNote(data));
            case 240 -> block("minecraft:lime_glazed_terracotta", facingNote(data));
            case 241 -> block("minecraft:pink_glazed_terracotta", facingNote(data));
            case 242 -> block("minecraft:gray_glazed_terracotta", facingNote(data));
            case 243 -> block("minecraft:light_gray_glazed_terracotta", facingNote(data));
            case 244 -> block("minecraft:cyan_glazed_terracotta", facingNote(data));
            case 245 -> block("minecraft:purple_glazed_terracotta", facingNote(data));
            case 246 -> block("minecraft:blue_glazed_terracotta", facingNote(data));
            case 247 -> block("minecraft:brown_glazed_terracotta", facingNote(data));
            case 248 -> block("minecraft:green_glazed_terracotta", facingNote(data));
            case 249 -> block("minecraft:red_glazed_terracotta", facingNote(data));
            case 250 -> block("minecraft:black_glazed_terracotta", facingNote(data));
            case 251 -> block(color(data) + "_concrete");
            case 252 -> block(color(data) + "_concrete_powder");
            case 255 -> block("minecraft:structure_block");
            default -> new LegacyBlock(null, "no built-in legacy mapping");
        };
    }

    private static LegacyBlock block(String resourceLocation) {
        return new LegacyBlock(resourceLocation, "");
    }

    private static LegacyBlock block(String resourceLocation, String note) {
        return new LegacyBlock(resourceLocation, note);
    }

    private static String stone(int data) {
        return switch (data) {
            case 1 -> "minecraft:granite";
            case 2 -> "minecraft:polished_granite";
            case 3 -> "minecraft:diorite";
            case 4 -> "minecraft:polished_diorite";
            case 5 -> "minecraft:andesite";
            case 6 -> "minecraft:polished_andesite";
            default -> "minecraft:stone";
        };
    }

    private static String slab(int data) {
        return switch (data & 7) {
            case 1 -> "minecraft:sandstone_slab";
            case 2 -> "minecraft:petrified_oak_slab";
            case 3 -> "minecraft:cobblestone_slab";
            case 4 -> "minecraft:brick_slab";
            case 5 -> "minecraft:stone_brick_slab";
            case 6 -> "minecraft:nether_brick_slab";
            case 7 -> "minecraft:quartz_slab";
            default -> "minecraft:stone_slab";
        };
    }

    private static String wood(int data, String suffix) {
        return "minecraft:" + switch (data & 3) {
            case 1 -> "spruce";
            case 2 -> "birch";
            case 3 -> "jungle";
            default -> "oak";
        } + "_" + suffix;
    }

    private static String color(int data) {
        return "minecraft:" + switch (data & 15) {
            case 1 -> "orange";
            case 2 -> "magenta";
            case 3 -> "light_blue";
            case 4 -> "yellow";
            case 5 -> "lime";
            case 6 -> "pink";
            case 7 -> "gray";
            case 8 -> "light_gray";
            case 9 -> "cyan";
            case 10 -> "purple";
            case 11 -> "blue";
            case 12 -> "brown";
            case 13 -> "green";
            case 14 -> "red";
            case 15 -> "black";
            default -> "white";
        };
    }

    private static String flower(int data) {
        return switch (data) {
            case 1 -> "minecraft:poppy";
            case 2 -> "minecraft:blue_orchid";
            case 3 -> "minecraft:allium";
            case 4 -> "minecraft:azure_bluet";
            case 5 -> "minecraft:red_tulip";
            case 6 -> "minecraft:orange_tulip";
            case 7 -> "minecraft:white_tulip";
            case 8 -> "minecraft:pink_tulip";
            case 9 -> "minecraft:oxeye_daisy";
            default -> "minecraft:poppy";
        };
    }

    private static String infested(int data) {
        return switch (data) {
            case 1 -> "minecraft:infested_cobblestone";
            case 2 -> "minecraft:infested_stone_bricks";
            case 3 -> "minecraft:infested_mossy_stone_bricks";
            case 4 -> "minecraft:infested_cracked_stone_bricks";
            case 5 -> "minecraft:infested_chiseled_stone_bricks";
            default -> "minecraft:infested_stone";
        };
    }

    private static String doublePlant(int data) {
        return switch (data & 7) {
            case 1 -> "minecraft:lilac";
            case 2 -> "minecraft:tall_grass";
            case 3 -> "minecraft:large_fern";
            case 4 -> "minecraft:rose_bush";
            case 5 -> "minecraft:peony";
            default -> "minecraft:sunflower";
        };
    }

    private static String logNote(int data) {
        return switch (data & 12) {
            case 4 -> "legacy log axis is x";
            case 8 -> "legacy log axis is z";
            case 12 -> "legacy log has all-bark axis";
            default -> "legacy log axis is y";
        };
    }

    private static String stairsNote(int data) {
        return "legacy stair data keeps facing and half bits: " + data;
    }

    private static String facingNote(int data) {
        return "legacy data keeps facing bits: " + data;
    }

    public record LegacyBlock(String resourceLocation, String note) {
        public boolean known() {
            return resourceLocation != null && !resourceLocation.isBlank();
        }
    }
}
