package calebxzhou.rdi.mc.server.mixin;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.network.HandshakeMessages;
import net.minecraftforge.network.NetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Mixin(net.minecraftforge.network.HandshakeHandler.class)
public class mHandshakeDiagnostics {
    @Unique
    private static final Logger rdi$logger = LogManager.getLogger("rdi");

    @Inject(method = "handleClientModListOnServer", at = @At("HEAD"))
    private void rdi$logClientModList(
            HandshakeMessages.C2SModListReply clientModList,
            Supplier<NetworkEvent.Context> contextSupplier,
            CallbackInfo ci
    ) {
        Set<String> serverModIds = ModList.get().getMods().stream()
                .map(IModInfo::getModId)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> clientModIds = new TreeSet<>(clientModList.getModList());

        Set<String> clientOnly = new TreeSet<>(clientModIds);
        clientOnly.removeAll(serverModIds);

        Set<String> serverOnly = new TreeSet<>(serverModIds);
        serverOnly.removeAll(clientModIds);

        if (!clientOnly.isEmpty() || !serverOnly.isEmpty()) {
            rdi$logger.warn(
                    "[RDI-HANDSHAKE] mod list difference from {}: clientOnly={}, serverOnly={}, clientCount={}, serverCount={}",
                    contextSupplier.get().getNetworkManager().getRemoteAddress(),
                    clientOnly,
                    serverOnly,
                    clientModIds.size(),
                    serverModIds.size()
            );
        }
    }
}
