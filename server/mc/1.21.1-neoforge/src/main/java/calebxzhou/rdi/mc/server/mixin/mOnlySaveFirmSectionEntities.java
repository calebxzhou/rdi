package calebxzhou.rdi.mc.server.mixin;

import calebxzhou.rdi.mc.server.firmsection.FirmSectionService;
import calebxzhou.rdi.mc.common.RDI;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.ChunkEntities;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(EntityStorage.class)
public class mOnlySaveFirmSectionEntities {
    @Shadow
    @Final
    private ServerLevel level;

    @ModifyVariable(method = "storeEntities", at = @At("HEAD"), argsOnly = true)
    private ChunkEntities<Entity> rdi$onlySaveFirmSectionEntities(ChunkEntities<Entity> entities) {
        if (!RDI.ONLY_SAVE_FIRM_SECTIONS) {
            return entities;
        }
        List<Entity> filtered = entities.getEntities()
                .filter(entity -> FirmSectionService.INSTANCE.shouldSaveEntity(level, entity))
                .toList();
        return new ChunkEntities<>(entities.getPos(), filtered);
    }
}
