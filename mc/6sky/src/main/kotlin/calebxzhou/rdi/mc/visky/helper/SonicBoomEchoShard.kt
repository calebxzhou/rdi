package calebxzhou.rdi.mc.visky.helper

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ambient.Bat
import net.minecraft.world.entity.animal.Dolphin
import net.minecraft.world.item.Items

object SonicBoomEchoShard {
    @JvmStatic
    fun tryDrop(target: LivingEntity) {
        if ((target is Bat || target is Dolphin) && target.isDeadOrDying) {
            target.spawnAtLocation(Items.ECHO_SHARD)
        }
    }
}
