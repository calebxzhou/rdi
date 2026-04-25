package calebxzhou.rdi.mc.common2.tpa;

import java.util.UUID;

public interface TpaPlayer {
    UUID id();

    String name();

    void sendMessage(String message);
}
