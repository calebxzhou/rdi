package calebxzhou.rdi.mc.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static calebxzhou.rdi.mc.common.RDI.HOST_ID;
import static calebxzhou.rdi.mc.common.RDI.IHQ_URL;

/**
 * calebxzhou @ 2026-01-06 23:36
 */
public class WebSocketClient {
    private static int reqId = 0;
    private static final Logger lgr = LogManager.getLogger("rdi-ws-client");
    private static final Gson gson = new GsonBuilder().create();
    private static final Type WS_MESSAGE_JSON_TYPE = new TypeToken<WsMessage<JsonElement>>() {
    }.getType();
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "rdi-ws-reconnect");
        t.setDaemon(true);
        return t;
    });

    private static volatile WebSocket currentWebSocket;
    private static volatile boolean shuttingDown = false;
    private static volatile boolean isConnecting = false;
    private static volatile String wsUrl;
    private static volatile WsMessageHandler handler;

    public static void start(WsMessageHandler handler) {
        // Convert http/https to ws/wss and append the path
        wsUrl = "ws://" + IHQ_URL + "/host/play/" + HOST_ID;
        shuttingDown = false;
        WebSocketClient.handler = handler;
        attemptConnect();
    }
    public static void stop() {
        shuttingDown = true;

        WebSocket ws = currentWebSocket;
        if (ws != null) {
            try {
                ws.disconnect(1000, "server stopping");
            } catch (Exception ex) {
                lgr.warn("Failed to send WebSocket close frame", ex);
            }
        }

        reconnectExecutor.shutdownNow();
    }
    public static <T> boolean sendMessage(WsMessage.Channel channel, T data) {
        if (sendMessage(reqId, channel, data)) {
            reqId++;
            return true;
        }
        return false;
    }
    public static <T> boolean sendMessage(int id, WsMessage.Channel channel, T data) {
        WebSocket ws = currentWebSocket;
        if (ws != null && ws.isOpen()) {
            String json = gson.toJson(new WsMessage<T>(id, channel, data));
            lgr.info("Sending message: {}", json);
            ws.sendText(json);
            return true;
        } else {
            lgr.warn("Cannot send message, WebSocket is not connected");
            return false;
        }
    }
    public static <T> T fromJson(JsonElement json, Class<T> type) {
        return gson.fromJson(json, type);
    }
    private WebSocketClient() {

    }

    private static void attemptConnect() {
        if (wsUrl == null || shuttingDown) {
            return;
        }

        // Check if there's already an active connection
        WebSocket ws = currentWebSocket;
        if (ws != null && ws.isOpen()) {
            lgr.debug("WebSocket already connected, skipping connection attempt");
            return;
        }

        // Check if a connection attempt is already in progress
        if (isConnecting) {
            lgr.debug("WebSocket connection already in progress, skipping");
            return;
        }

        isConnecting = true;
        lgr.info("Attempting WebSocket connection to {}", wsUrl);
        try {
            WebSocket newWs = new WebSocketFactory()
                    .setConnectionTimeout(CONNECT_TIMEOUT_MS)
                    .createSocket(wsUrl)
                    .addListener(new Listener());
            currentWebSocket = newWs;
            newWs.connectAsynchronously();
        } catch (IOException ex) {
            isConnecting = false;
            lgr.error("Failed to connect WebSocket to {}", wsUrl, ex);
            scheduleReconnect();
        }
    }

    private static void scheduleReconnect() {
        if (shuttingDown) {
            return;
        }

        // Don't schedule reconnect if already connected
        WebSocket ws = currentWebSocket;
        if (ws != null && ws.isOpen()) {
            lgr.debug("WebSocket already connected, skipping reconnect scheduling");
            return;
        }

        reconnectExecutor.schedule(WebSocketClient::attemptConnect, 5, TimeUnit.SECONDS);
        lgr.info("Scheduled WebSocket reconnect in 5s");
    }

    private static class Listener extends WebSocketAdapter {
        @Override
        public void onConnected(WebSocket webSocket, Map<String, List<String>> headers) {
            isConnecting = false;
            lgr.info("WebSocket connection opened: {}", wsUrl);
        }

        @Override
        public void onTextMessage(WebSocket webSocket, String text) {
            lgr.info("Received text message: {}", text);
            WsMessage<JsonElement> msg = gson.fromJson(text, WS_MESSAGE_JSON_TYPE);
            handler.onMessage(msg);
        }

        @Override
        public void onBinaryMessage(WebSocket webSocket, byte[] binary) {
            lgr.info("Received binary message (length={})", binary.length);
        }

        @Override
        public void onPingFrame(WebSocket webSocket, WebSocketFrame frame) {
            lgr.debug("Received ping");
        }

        @Override
        public void onPongFrame(WebSocket webSocket, WebSocketFrame frame) {
            lgr.debug("Received pong");
        }

        @Override
        public void onDisconnected(WebSocket webSocket, WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame, boolean closedByServer) {
            isConnecting = false;
            String reason = serverCloseFrame != null ? serverCloseFrame.getCloseReason() : "unknown";
            int code = serverCloseFrame != null ? serverCloseFrame.getCloseCode() : -1;
            lgr.info("WebSocket closed: {} - {}", code, reason);
            currentWebSocket = null;
            scheduleReconnect();
        }

        @Override
        public void onConnectError(WebSocket webSocket, WebSocketException exception) {
            isConnecting = false;
            lgr.error("WebSocket connect error", exception);
            currentWebSocket = null;
            scheduleReconnect();
        }

        @Override
        public void onError(WebSocket webSocket, WebSocketException cause) {
            isConnecting = false;
            lgr.error("WebSocket error", cause);
            currentWebSocket = null;
            scheduleReconnect();
        }
    }


}
