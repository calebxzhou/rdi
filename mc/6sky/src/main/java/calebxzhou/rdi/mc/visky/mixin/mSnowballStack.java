package calebxzhou.rdi.mc.visky.mixin;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * calebxzhou @ 2026-05-27 15:28
 */
@Mixin(Items.class)
public class mSnowballStack {
    @ModifyArg(
            method = "<clinit>",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/SnowballItem;<init>(Lnet/minecraft/world/item/Item$Properties;)V"),
            index = 0
    )
    private static Item.Properties RDI$SnowballsStackTo64(Item.Properties properties) {
        return properties.stacksTo(64);
    }
}
