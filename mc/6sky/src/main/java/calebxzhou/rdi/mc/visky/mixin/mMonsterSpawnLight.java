package calebxzhou.rdi.mc.visky.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * calebxzhou @ 2026-05-25 23:30
 */
@Mixin(Monster.class)
public class mMonsterSpawnLight {

    @Overwrite
    public static boolean isDarkEnoughToSpawn(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
        return level.getBrightness(LightLayer.BLOCK, pos) <= 7;
    }
}
