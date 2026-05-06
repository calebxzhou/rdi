package calebxzhou.rdi.mc.client.mcp;

import calebxzhou.rdi.mc.common2.mcp.RMcpBlockEntityData;
import calebxzhou.rdi.mc.common2.mcp.RMcpBlockPosData;
import calebxzhou.rdi.mc.common2.mcp.RMcpEntityDetailData;
import calebxzhou.rdi.mc.common2.mcp.RMcpPosData;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RMcpMcDataCodec211 {
    private RMcpMcDataCodec211() {
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

    public static RMcpEntityDetailData entityDetail(String dim, Entity entity, HolderLookup.Provider registryAccess) {
        var type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        var tag = entity.saveWithoutId(new CompoundTag());
        return new RMcpEntityDetailData(
                dim,
                entity.getStringUUID(),
                type,
                entity.getName().getString(),
                new RMcpPosData(dim, entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot()),
                entityRuntime(entity, dim, registryAccess),
                nbtCompoundToMap(tag),
                tag.toString()
        );
    }

    public static Map<String, Object> itemStackToMap(ItemStack stack, HolderLookup.Provider registryAccess) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        var map = new LinkedHashMap<String, Object>();
        map.put("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        map.put("count", stack.getCount());
        var tag = stack.saveOptional(registryAccess);
        map.put("nbt", tagToJson(tag));
        map.put("snbt", tag.toString());
        return map;
    }

    public static Map<String, Object> itemStackToSnbtMap(ItemStack stack, HolderLookup.Provider registryAccess) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        var map = new LinkedHashMap<String, Object>();
        map.put("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        map.put("count", stack.getCount());
        map.put("snbt", stack.saveOptional(registryAccess).toString());
        return map;
    }

    public static String stateString(String id, String raw) {
        int propertyStart = raw.indexOf('[');
        return propertyStart < 0 ? id : id + raw.substring(propertyStart);
    }

    private static Map<String, Object> entityRuntime(Entity entity, String dim, HolderLookup.Provider registryAccess) {
        var runtime = new LinkedHashMap<String, Object>();
        runtime.put("id", entity.getId());
        runtime.put("uuid", entity.getStringUUID());
        runtime.put("type", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        runtime.put("name", entity.getName().getString());
        runtime.put("displayName", entity.getDisplayName().getString());
        runtime.put("scoreboardName", entity.getScoreboardName());
        runtime.put("customName", entity.hasCustomName() && entity.getCustomName() != null ? entity.getCustomName().getString() : null);
        runtime.put("pos", new RMcpPosData(dim, entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot()));
        runtime.put("delta", vecToMap(entity.getDeltaMovement()));
        runtime.put("bbox", bboxToMap(entity.getBoundingBox()));
        runtime.put("bbWidth", entity.getBbWidth());
        runtime.put("bbHeight", entity.getBbHeight());
        runtime.put("alive", entity.isAlive());
        runtime.put("removed", entity.isRemoved());
        runtime.put("removalReason", entity.getRemovalReason() == null ? null : entity.getRemovalReason().name());
        runtime.put("onFire", entity.isOnFire());
        runtime.put("fireTicks", entity.getRemainingFireTicks());
        runtime.put("invisible", entity.isInvisible());
        runtime.put("invulnerable", entity.isInvulnerable());
        runtime.put("crouching", entity.isCrouching());
        runtime.put("sprinting", entity.isSprinting());
        runtime.put("swimming", entity.isSwimming());
        runtime.put("passenger", entity.isPassenger());
        runtime.put("air", entity.getAirSupply());
        runtime.put("maxAir", entity.getMaxAirSupply());
        runtime.put("frozenTicks", entity.getTicksFrozen());
        runtime.put("tags", new ArrayList<>(entity.getTags()));
        runtime.put("vehicleUuid", entity.getVehicle() == null ? null : entity.getVehicle().getStringUUID());
        runtime.put("passengers", entity.getPassengers().stream().map(Entity::getStringUUID).toList());
        if (entity instanceof LivingEntity living) {
            runtime.put("health", living.getHealth());
            runtime.put("maxHealth", living.getMaxHealth());
            runtime.put("absorption", living.getAbsorptionAmount());
            runtime.put("armor", living.getArmorValue());
            runtime.put("mainHand", itemStackToMap(living.getMainHandItem(), registryAccess));
            runtime.put("offHand", itemStackToMap(living.getOffhandItem(), registryAccess));
            var slots = new ArrayList<Map<String, Object>>();
            for (var stack : living.getAllSlots()) {
                slots.add(itemStackToMap(stack, registryAccess));
            }
            runtime.put("allSlots", slots);
            runtime.put("effects", living.getActiveEffects().stream().map(Object::toString).toList());
        }
        return runtime;
    }

    private static Map<String, Object> vecToMap(Vec3 vec) {
        var map = new LinkedHashMap<String, Object>();
        map.put("x", vec.x);
        map.put("y", vec.y);
        map.put("z", vec.z);
        return map;
    }

    private static Map<String, Object> bboxToMap(net.minecraft.world.phys.AABB box) {
        var map = new LinkedHashMap<String, Object>();
        map.put("min", vecToMap(box.getMinPosition()));
        map.put("max", vecToMap(box.getMaxPosition()));
        return map;
    }

    private static Map<String, Object> nbtCompoundToMap(CompoundTag tag) {
        var map = new LinkedHashMap<String, Object>();
        for (var key : tag.getAllKeys()) {
            map.put(key, tagToJson(tag.get(key)));
        }
        return map;
    }

    private static Object tagToJson(Tag tag) {
        if (tag == null) {
            return null;
        }
        if (tag instanceof CompoundTag compoundTag) {
            return nbtCompoundToMap(compoundTag);
        }
        if (tag instanceof ListTag listTag) {
            var list = new ArrayList<>();
            for (int i = 0; i < listTag.size(); i++) {
                list.add(tagToJson(listTag.get(i)));
            }
            return list;
        }
        if (tag instanceof StringTag stringTag) {
            return stringTag.getAsString();
        }
        if (tag instanceof ByteArrayTag arrayTag) {
            var list = new ArrayList<Byte>();
            for (int i = 0; i < arrayTag.size(); i++) {
                list.add(arrayTag.get(i).getAsByte());
            }
            return list;
        }
        if (tag instanceof IntArrayTag arrayTag) {
            var list = new ArrayList<Integer>();
            for (int i = 0; i < arrayTag.size(); i++) {
                list.add(arrayTag.get(i).getAsInt());
            }
            return list;
        }
        if (tag instanceof LongArrayTag arrayTag) {
            var list = new ArrayList<Long>();
            for (int i = 0; i < arrayTag.size(); i++) {
                list.add(arrayTag.get(i).getAsLong());
            }
            return list;
        }
        if (tag instanceof NumericTag numericTag) {
            return switch (tag.getId()) {
                case 1 -> numericTag.getAsByte();
                case 2, 3 -> numericTag.getAsInt();
                case 4 -> numericTag.getAsLong();
                case 5 -> numericTag.getAsFloat();
                case 6 -> numericTag.getAsDouble();
                default -> numericTag.getAsDouble();
            };
        }
        return tag.toString();
    }
}
