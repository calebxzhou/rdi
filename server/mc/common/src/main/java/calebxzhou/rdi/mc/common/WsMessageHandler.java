package calebxzhou.rdi.mc.common;

import com.google.gson.JsonElement;

/**
 * calebxzhou @ 2026-01-12 17:57
 */

public interface WsMessageHandler {
    void onMessage(WsMessage<JsonElement> message);
}
