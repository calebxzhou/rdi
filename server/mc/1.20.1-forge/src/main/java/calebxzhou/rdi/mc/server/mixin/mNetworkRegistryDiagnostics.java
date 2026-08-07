package calebxzhou.rdi.mc.server.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Mixin(NetworkRegistry.class)
public class mNetworkRegistryDiagnostics {
    @Unique
    private static final Logger rdi$logger = LogManager.getLogger("rdi");

    @Invoker("buildChannelVersions")
    private static Map<ResourceLocation, String> rdi$buildChannelVersions() {
        throw new AssertionError();
    }

    @Inject(method = "validateServerChannels", at = @At("RETURN"))
    private static void rdi$logChannelMismatch(
            Map<ResourceLocation, String> incoming,
            CallbackInfoReturnable<Map<ResourceLocation, String>> cir
    ) {
        Map<ResourceLocation, String> mismatchedChannels = cir.getReturnValue();
        if (mismatchedChannels.isEmpty()) {
            return;
        }

        Map<ResourceLocation, String> serverChannels = rdi$buildChannelVersions();
        Set<ResourceLocation> serverOnly = new HashSet<>(serverChannels.keySet());
        serverOnly.removeAll(incoming.keySet());
        Set<ResourceLocation> clientOnly = new HashSet<>(incoming.keySet());
        clientOnly.removeAll(serverChannels.keySet());

        rdi$logger.error(
                "[RDI-HANDSHAKE] network channel mismatch: serverOnly={}, clientOnly={}",
                rdi$formatChannelIds(serverOnly),
                rdi$formatChannelIds(clientOnly)
        );

        List<ResourceLocation> rejectedChannels = new ArrayList<>(mismatchedChannels.keySet());
        rejectedChannels.sort((left, right) -> left.toString().compareTo(right.toString()));
        for (ResourceLocation channel : rejectedChannels) {
            rdi$logger.error(
                    "[RDI-HANDSHAKE] rejected channel: channel={}, serverExpected={}, clientReported={}",
                    channel,
                    serverChannels.get(channel),
                    mismatchedChannels.get(channel)
            );
        }
    }

    @Unique
    private static String rdi$formatChannelIds(Collection<ResourceLocation> channels) {
        return channels.stream()
                .map(ResourceLocation::toString)
                .sorted()
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
