package calebxzhou.rdi.mc.common2.tpa;

import java.util.UUID;

public interface TpaPlayerLookup {
    TpaPlayer findByName(String name);

    TpaPlayer findById(UUID id);

    void teleportTo(TpaPlayer requester, TpaPlayer target);
}
