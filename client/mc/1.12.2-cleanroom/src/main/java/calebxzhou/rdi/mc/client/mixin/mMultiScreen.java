package calebxzhou.rdi.mc.client.mixin;

import calebxzhou.rdi.mc.client.RDIMain;
import calebxzhou.rdi.mc.common.RDI;
import net.minecraft.client.gui.ServerSelectionList;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * calebxzhou @ 2026-02-03 23:04
 */
@Mixin(GuiMultiplayer.class)
public class mMultiScreen extends GuiScreen {
    @Shadow
    private ServerSelectionList serverListSelector;

    @Shadow
    private ServerList savedServerList;

    @Inject(method = "createButtons", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", ordinal = 0))
    public void RDI$AddButton(CallbackInfo ci) {
        buttonList.add(RDIMain.createJoinButton(this.width / 2 - 150, 8, 300));
    }

    @Inject(method = "initGui", at = @At("RETURN"))
    public void RDI$OnlyShowLocalProxyServer(CallbackInfo ci) {
        if (this.savedServerList == null || this.serverListSelector == null) {
            return;
        }
        while (this.savedServerList.countServers() > 0) {
            this.savedServerList.removeServerData(this.savedServerList.countServers() - 1);
        }
        this.savedServerList.addServerData(new ServerData(RDI.HOST_NAME, "127.0.0.1:55667", false));
        this.savedServerList.saveServerList();
        this.serverListSelector.updateOnlineServers(this.savedServerList);
        ((GuiMultiplayer) (Object) this).selectServer(0);
    }

    @Inject(method = "actionPerformed", at = @At("TAIL"))
    public void RDI$OnClickButton(GuiButton button, CallbackInfo ci) {
        if (button.id == RDIMain.JOIN_BUTTON_ID) {
            RDIMain.onJoinRDI();
        }
    }
}
