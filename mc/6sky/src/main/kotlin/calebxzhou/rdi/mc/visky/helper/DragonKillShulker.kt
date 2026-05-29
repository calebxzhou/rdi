package calebxzhou.rdi.mc.visky.helper

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobSpawnType

object DragonKillShulker {
    @JvmStatic
    fun spawn(level: ServerLevel, portalLocation: BlockPos?) {
        if (portalLocation == null) {
            return
        }

        val shulkerPos = portalLocation.offset(0, 4, 0)
        if (!level.getBlockState(shulkerPos).isAir) {
            return
        }

        val shulker = EntityType.SHULKER.create(level, null, shulkerPos, MobSpawnType.EVENT, true, false) ?: return
        if (level.noCollision(shulker)) {
            level.addFreshEntity(shulker)
        }
    }
}
