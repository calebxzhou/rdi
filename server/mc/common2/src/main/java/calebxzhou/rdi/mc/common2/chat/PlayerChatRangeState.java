package calebxzhou.rdi.mc.common2.chat;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerChatRangeState {
    public static final ChatRange DEFAULT_RANGE = ChatRange.GLOBAL;
    private static final ConcurrentHashMap<UUID, ChatRange> CHAT_RANGES = new ConcurrentHashMap<>();

    private PlayerChatRangeState() {
    }

    public static void set(UUID playerId, ChatRange range) {
        CHAT_RANGES.put(playerId, range);
    }

    public static ChatRange get(UUID playerId) {
        return CHAT_RANGES.getOrDefault(playerId, DEFAULT_RANGE);
    }

    public static boolean isGlobal(UUID playerId) {
        return get(playerId) == ChatRange.GLOBAL;
    }

    public static void remove(UUID playerId) {
        CHAT_RANGES.remove(playerId);
    }

    public static void clear() {
        CHAT_RANGES.clear();
    }
}
