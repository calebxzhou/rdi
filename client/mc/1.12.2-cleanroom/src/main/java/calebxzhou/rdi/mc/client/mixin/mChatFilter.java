package calebxzhou.rdi.mc.client.mixin;

import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.util.text.ITextComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiNewChat.class)
public class mChatFilter {
    @Inject(method = "setChatLine", at = @At("HEAD"), cancellable = true)
    private void RDI$hideInvalidJarMessage(ITextComponent chatComponent, int chatLineId, int updateCounter, boolean displayOnly, CallbackInfo ci) {
        if (chatComponent != null && chatComponent.getUnformattedText().contains("时装工坊已检测到无效的")) {
            ci.cancel();
        }
    }
}
