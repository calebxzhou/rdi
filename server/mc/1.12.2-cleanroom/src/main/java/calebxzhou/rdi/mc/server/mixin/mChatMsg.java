package calebxzhou.rdi.mc.server.mixin;

import calebxzhou.rdi.mc.common.WebSocketClient;
import calebxzhou.rdi.mc.common.WsMessage;
import calebxzhou.rdi.mc.rcmd.Rcmd;
import calebxzhou.rdi.mc.rcmd.RcmdResult;
import calebxzhou.rdi.mc.rcmd.RcmdSource;
import calebxzhou.rdi.mc.rcmd.chat.PlayerChatRangeState;
import calebxzhou.rdi.mc.rcmd.chat.RChatMessage;
import calebxzhou.rdi.mc.server.rcmd.RcmdServerCommands;
import calebxzhou.rdi.mc.server.rcmd.RcmdServerSource112;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.play.client.CPacketChatMessage;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.text.TextComponentString;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

import static calebxzhou.rdi.mc.common.RDI.HOST_ID;

@Mixin(NetHandlerPlayServer.class)
public abstract class mChatMsg {
    @Shadow
    public EntityPlayerMP player;

    @Inject(
            method = "processChatMessage",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/EntityPlayerMP;markPlayerActive()V",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void RDI$chat(CPacketChatMessage packet, CallbackInfo ci) {
        String message = StringUtils.normalizeSpace(packet.getMessage());
        if (!isVanillaValidChat(message)) {
            return;
        }
        if (Rcmd.isRcmd(message)) {
            ci.cancel();
            RcmdSource source = new RcmdServerSource112(player);
            RcmdResult result = RcmdServerCommands.dispatcher().execute(source, message);
            RcmdServerCommands.reply(source, result);
            return;
        }
        if (message.startsWith("/")) {
            return;
        }
        if (PlayerChatRangeState.isGlobal(player.getUniqueID())) {
            ci.cancel();
            sendGlobalChat(message);
        } else if (!sendHostChat(message)) {
            ci.cancel();
            player.sendMessage(new TextComponentString("聊天服务未连接"));
        }
    }

    private boolean isVanillaValidChat(String message) {
        for (int i = 0; i < message.length(); i++) {
            if (!ChatAllowedCharacters.isAllowedCharacter(message.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private void sendGlobalChat(String message) {
        RChatMessage chatMessage = new RChatMessage(
                UUID.randomUUID().toString(),
                HOST_ID,
                player.getUniqueID().toString(),
                player.getGameProfile().getName(),
                message,
                System.currentTimeMillis(),
                true
        );
        if (WebSocketClient.sendMessage(WsMessage.Channel.Chat, chatMessage)) {
            TextComponentString component = new TextComponentString("[公共] " + player.getGameProfile().getName() + ": " + message);
            for (EntityPlayerMP recipient : player.server.getPlayerList().getPlayers()) {
                if (PlayerChatRangeState.isGlobal(recipient.getUniqueID())) {
                    recipient.sendMessage(component);
                }
            }
        } else {
            player.sendMessage(new TextComponentString("聊天服务未连接"));
        }
    }

    private boolean sendHostChat(String message) {
        RChatMessage chatMessage = new RChatMessage(
                UUID.randomUUID().toString(),
                HOST_ID,
                player.getUniqueID().toString(),
                player.getGameProfile().getName(),
                message,
                System.currentTimeMillis(),
                false
        );
        return WebSocketClient.sendMessage(WsMessage.Channel.Chat, chatMessage);
    }
}
