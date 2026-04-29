package calebxzhou.rdi.mc.client.mixin;

import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.gui.ServerListEntryNormal;
import net.minecraft.client.gui.ServerSelectionList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ServerSelectionList.class)
public class mServerSelectionList {
    @Shadow
    @Final
    private List<ServerListEntryNormal> serverListInternet;

    @Inject(method = "getListEntry", at = @At("HEAD"), cancellable = true)
    private void RDI$OnlySavedServerEntries(int index, CallbackInfoReturnable<GuiListExtended.IGuiListEntry> cir) {
        cir.setReturnValue(this.serverListInternet.get(index));
    }

    @Inject(method = "getSize", at = @At("HEAD"), cancellable = true)
    private void RDI$HideLanScanEntry(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(this.serverListInternet.size());
    }
}
