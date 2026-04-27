package calebxzhou.rdi.mc.server.mixin;

import calebxzhou.rdi.mc.rcmd.Rcmd;
import calebxzhou.rdi.mc.rcmd.RcmdResult;
import calebxzhou.rdi.mc.rcmd.RcmdSource;
import calebxzhou.rdi.mc.server.rcmd.RcmdCommandSourceStackSource;
import calebxzhou.rdi.mc.server.rcmd.RcmdServerCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Commands.class)
public class mRcmdCommandInput {
    @Inject(method = "performPrefixedCommand", at = @At("HEAD"), cancellable = true)
    private void RDI$rcmd(CommandSourceStack source, String command, CallbackInfoReturnable<Integer> cir) {
        String normalized = command.startsWith("/") ? command.substring(1) : command;
        if (!Rcmd.isRcmd(normalized)) {
            return;
        }
        RcmdSource rcmdSource = new RcmdCommandSourceStackSource(source);
        RcmdResult result = RcmdServerCommands.dispatcher().execute(rcmdSource, normalized);
        RcmdServerCommands.reply(rcmdSource, result);
        cir.setReturnValue(result.success() ? 1 : 0);
    }
}
