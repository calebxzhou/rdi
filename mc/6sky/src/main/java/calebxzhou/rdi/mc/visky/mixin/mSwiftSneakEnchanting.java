package calebxzhou.rdi.mc.visky.mixin;

import calebxzhou.rdi.mc.visky.helper.SwiftSneakEnchanting;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.stream.Stream;

@Mixin(EnchantmentMenu.class)
public abstract class mSwiftSneakEnchanting {
    @Shadow
    @Final
    private ContainerLevelAccess access;

    @WrapOperation(
        method = "getEnchantmentList",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;selectEnchantment(Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/item/ItemStack;ILjava/util/stream/Stream;)Ljava/util/List;"
        )
    )
    private List<EnchantmentInstance> RDI$AddSwiftSneak(
        RandomSource random, ItemStack stack, int cost, Stream<Holder<Enchantment>> stream,
        Operation<List<EnchantmentInstance>> original
    ) {
        List<EnchantmentInstance> list = original.call(random, stack, cost, stream);
        this.access.execute((level, pos) ->
            SwiftSneakEnchanting.tryAddSwiftSneak(level, pos, stack, cost, list)
        );
        return list;
    }
}
