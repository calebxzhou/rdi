package calebxzhou.rdi.mc.common2.chat;

public record RChatMessage(
        String msgId,
        String sourceHostId,
        String playerId,
        String playerName,
        String content,
        long timestamp
) {
}
