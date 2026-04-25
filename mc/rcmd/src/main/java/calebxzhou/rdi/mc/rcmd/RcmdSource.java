package calebxzhou.rdi.mc.rcmd;

import java.util.UUID;

public interface RcmdSource {
    UUID NO_PLAYER_ID = new UUID(0L, 0L);

    String name();

    UUID playerId();

    default boolean isPlayer() {
        return !NO_PLAYER_ID.equals(playerId());
    }

    boolean hasPermission(String permission);

    void sendFeedback(String message);

    void sendError(String message);
}
