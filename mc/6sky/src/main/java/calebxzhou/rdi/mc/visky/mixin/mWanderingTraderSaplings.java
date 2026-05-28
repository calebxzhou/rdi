package calebxzhou.rdi.mc.visky.mixin;

import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * calebxzhou @ 2026-05-27 15:10
 */
@Mixin(WanderingTrader.class)
public class mWanderingTraderSaplings {
    @Inject(method = "updateTrades", at = @At("TAIL"))
    private void RDI$AddOakSaplingTrade(CallbackInfo ci) {
        MerchantOffers offers = ((WanderingTrader) (Object) this).getOffers();
        offers.add(0, new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.OAK_SAPLING), 64, 1, 0.0F));
    }
}
