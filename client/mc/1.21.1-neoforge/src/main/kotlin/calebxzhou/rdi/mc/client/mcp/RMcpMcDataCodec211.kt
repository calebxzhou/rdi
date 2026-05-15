package calebxzhou.rdi.mc.client.mcp

import calebxzhou.rdi.mc.common2.mcp.RMcpBlockEntityData
import calebxzhou.rdi.mc.common2.mcp.RBlockPos
import calebxzhou.rdi.mc.common2.mcp.RMcpEntityDetailData
import calebxzhou.rdi.mc.common2.mcp.REntityPosData
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

object RMcpMcDataCodec211 {
    fun blockEntityData(
        dim: String,
        blockEntity: BlockEntity,
        registryAccess: HolderLookup.Provider
    ): RMcpBlockEntityData {
        val pos = blockEntity.blockPos
        val blockState = blockEntity.blockState
        val blockId = BuiltInRegistries.BLOCK.getKey(blockState.block).toString()
        val type = BlockEntityType.getKey(blockEntity.type).toString()
        val tag = blockEntity.saveWithFullMetadata(registryAccess)
        return RMcpBlockEntityData(
            dim,
            RBlockPos(pos.x, pos.y, pos.z),
            blockId,
            stateString(blockId, blockState.toString()),
            type,
            blockEntity.javaClass.getName(),
            tag.toString()
        )
    }

    @JvmStatic
    fun entityDetail(dim: String, entity: Entity, registryAccess: HolderLookup.Provider): RMcpEntityDetailData {
        val type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()
        val tag = entity.saveWithoutId(CompoundTag())
        return RMcpEntityDetailData(
            dim,
            entity.getStringUUID(),
            type,
            entity.getName().getString(),
            REntityPosData(dim, entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot()),
            entityRuntime(entity, dim, registryAccess),
            tag.toString()
        )
    }

    @JvmStatic
    fun itemStackToMap(stack: ItemStack, registryAccess: HolderLookup.Provider): MutableMap<String, Any?>? {
        if (stack == null || stack.isEmpty()) {
            return null
        }
        val map = LinkedHashMap<String, Any?>()
        map.put("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
        map.put("count", stack.getCount())
        val tag = stack.saveOptional(registryAccess)

        map.put("snbt", tag.toString())
        return map
    }

    @JvmStatic
    fun itemStackToSnbtMap(stack: ItemStack, registryAccess: HolderLookup.Provider): MutableMap<String, Any?>? {
        if (stack == null || stack.isEmpty()) {
            return null
        }
        val map = LinkedHashMap<String, Any?>()
        map.put("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
        map.put("count", stack.getCount())
        map.put("snbt", stack.saveOptional(registryAccess).toString())
        return map
    }

    @JvmStatic
    fun stateString(id: String, raw: String): String? {
        val propertyStart = raw.indexOf('[')
        return if (propertyStart < 0) id else id + raw.substring(propertyStart)
    }

    private fun entityRuntime(
        entity: Entity,
        dim: String,
        registryAccess: HolderLookup.Provider
    ): MutableMap<String, Any?> {
        val runtime = LinkedHashMap<String, Any?>()
        runtime.put("id", entity.getId())
        runtime["uuid"] = entity.getStringUUID()
        runtime.put("type", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString())
        runtime.put("name", entity.getName().getString())
        runtime.put("displayName", entity.getDisplayName()!!.getString())
        runtime.put("scoreboardName", entity.getScoreboardName())
        runtime.put(
            "customName",
            if (entity.hasCustomName() && entity.getCustomName() != null) entity.getCustomName()!!.getString() else null
        )
        runtime.put(
            "pos",
            REntityPosData(dim, entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot())
        )
        runtime.put("delta", vecToMap(entity.getDeltaMovement()))
        runtime.put("bbox", bboxToMap(entity.getBoundingBox()))
        runtime.put("bbWidth", entity.getBbWidth())
        runtime.put("bbHeight", entity.getBbHeight())
        runtime.put("alive", entity.isAlive())
        runtime.put("removed", entity.isRemoved())
        runtime.put("removalReason", if (entity.getRemovalReason() == null) null else entity.getRemovalReason()!!.name)
        runtime.put("onFire", entity.isOnFire())
        runtime.put("fireTicks", entity.getRemainingFireTicks())
        runtime.put("invisible", entity.isInvisible())
        runtime.put("invulnerable", entity.isInvulnerable())
        runtime.put("crouching", entity.isCrouching())
        runtime.put("sprinting", entity.isSprinting())
        runtime.put("swimming", entity.isSwimming())
        runtime.put("passenger", entity.isPassenger())
        runtime.put("air", entity.getAirSupply())
        runtime.put("maxAir", entity.getMaxAirSupply())
        runtime.put("frozenTicks", entity.getTicksFrozen())
        runtime.put("tags", ArrayList<String?>(entity.getTags()))
        runtime.put("vehicleUuid", if (entity.getVehicle() == null) null else entity.getVehicle()!!.getStringUUID())
        runtime.put(
            "passengers",
            entity.getPassengers().stream().map<String?> { obj: Entity? -> obj!!.getStringUUID() }.toList()
        )
        if (entity is LivingEntity) {
            runtime.put("health", entity.getHealth())
            runtime.put("maxHealth", entity.getMaxHealth())
            runtime.put("absorption", entity.getAbsorptionAmount())
            runtime.put("armor", entity.getArmorValue())
            runtime.put("mainHand", itemStackToMap(entity.getMainHandItem(), registryAccess))
            runtime.put("offHand", itemStackToMap(entity.getOffhandItem(), registryAccess))
            val slots = ArrayList<MutableMap<String, Any?>?>()
            for (stack in entity.getAllSlots()) {
                slots.add(itemStackToMap(stack, registryAccess))
            }
            runtime.put("allSlots", slots)
            runtime.put(
                "effects",
                entity.getActiveEffects().stream().map<String?> { obj: MobEffectInstance? -> obj.toString() }.toList()
            )
        }
        return runtime
    }

    private fun vecToMap(vec: Vec3): MutableMap<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        map.put("x", vec.x)
        map.put("y", vec.y)
        map.put("z", vec.z)
        return map
    }

    private fun bboxToMap(box: AABB): MutableMap<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        map.put("min", vecToMap(box.getMinPosition()))
        map.put("max", vecToMap(box.getMaxPosition()))
        return map
    }

}