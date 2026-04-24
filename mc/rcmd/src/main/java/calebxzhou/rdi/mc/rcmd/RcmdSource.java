package calebxzhou.rdi.mc.rcmd;

import java.util.UUID;

public interface RcmdSource {
    String getName();

    UUID getPlayerId();

    boolean hasPermission(String permission);

    void sendFeedback(String message);

    void sendError(String message);
}
