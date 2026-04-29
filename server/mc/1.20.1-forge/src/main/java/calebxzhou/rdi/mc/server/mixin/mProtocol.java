package calebxzhou.rdi.mc.server.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * calebxzhou @ 2024-05-31 20:09
 */
@Mixin(ServerboundHelloPacket.class)
abstract class mAllowChineseNameLogin {

    @ModifyConstant(method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V", constant = @Constant(intValue = 16))
    private static int RDI$AllowChineseName(int constant) {
        return 64;
    }

}
/**
 * calebxzhou @ 2025-02-21 0:32
 */
@Mixin(ServerLoginPacketListenerImpl.class)
abstract
class mServerLoginPacketListener {

    @Shadow
    public abstract void disconnect(Component reason);

    //注入rdid到mc本体的uuid
    @Unique
    ServerboundHelloPacket $packet;
    @Inject(method = "handleHello",at=@At("HEAD"))
    private void storePacket(ServerboundHelloPacket pPacket, CallbackInfo ci){
        this.$packet = pPacket;
    }
    @Redirect(method = "handleHello",at= @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getSingleplayerProfile()Lcom/mojang/authlib/GameProfile;"))
    private GameProfile injectUUID(MinecraftServer instance) {
        if ($packet.profileId().isEmpty()) {
            disconnect(Component.literal("未登录RDI账号！"));
            return null;
        }
        return new GameProfile($packet.profileId().get(),$packet.name());
    }

    @Redirect(method = "handleCustomQueryPacket",at= @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerLoginPacketListenerImpl;disconnect(Lnet/minecraft/network/chat/Component;)V"))
    private void RDI$IgnoreUnexpCusQuery(ServerLoginPacketListenerImpl disconnect, Component reason) {

    }
}
