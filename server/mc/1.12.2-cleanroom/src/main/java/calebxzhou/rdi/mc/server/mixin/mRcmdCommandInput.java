package calebxzhou.rdi.mc.server.mixin;

import calebxzhou.rdi.mc.rcmd.Rcmd;
import calebxzhou.rdi.mc.rcmd.RcmdResult;
import calebxzhou.rdi.mc.rcmd.RcmdSource;
import calebxzhou.rdi.mc.server.rcmd.RcmdCommandSenderSource112;
import calebxzhou.rdi.mc.server.rcmd.RcmdServerCommands;
import net.minecraft.command.CommandHandler;
import net.minecraft.command.ICommandSender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CommandHandler.class)
public class mRcmdCommandInput {
    @Inject(method = "executeCommand", at = @At("HEAD"), cancellable = true)
    private void RDI$rcmd(ICommandSender sender, String command, CallbackInfoReturnable<Integer> cir) {
        String normalized = command.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (!Rcmd.isRcmd(normalized)) {
            return;
        }
        RcmdSource source = new RcmdCommandSenderSource112(sender);
        RcmdResult result = RcmdServerCommands.dispatcher().execute(source, normalized);
        RcmdServerCommands.reply(source, result);
        cir.setReturnValue(result.success() ? 1 : 0);
    }
}
