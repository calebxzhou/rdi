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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.net.SocketFactory;

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
    private static final SocketFactory DIRECT_SOCKET_FACTORY = new DirectSocketFactory();
    private static final ProxySelector DIRECT_PROXY_SELECTOR = new DirectProxySelector();
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
        disableJvmProxy();
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
        lgr.info("ws try conn");
        try {
            disableJvmProxy();
            WebSocket newWs = new WebSocketFactory()
                    .setSocketFactory(DIRECT_SOCKET_FACTORY)
                    .setConnectionTimeout(CONNECT_TIMEOUT_MS)
                    .createSocket(wsUrl)
                    .addListener(new Listener());
            currentWebSocket = newWs;
            newWs.connectAsynchronously();
        } catch (IOException ex) {
            isConnecting = false;
            lgr.error("Failed to connect WebSocket ", ex);
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
        lgr.info("ws reconn 5s");
    }

    private static class Listener extends WebSocketAdapter {
        @Override
        public void onConnected(WebSocket webSocket, Map<String, List<String>> headers) {
            isConnecting = false;
            lgr.info("ws-conn");
            lgr.debug("WebSocket connection opened: {}", wsUrl);
        }

        @Override
        public void onTextMessage(WebSocket webSocket, String text) {
            lgr.debug("Received text message: {}", text);
            WsMessage<JsonElement> msg = gson.fromJson(text, WS_MESSAGE_JSON_TYPE);
            handler.onMessage(msg);
        }

        @Override
        public void onBinaryMessage(WebSocket webSocket, byte[] binary) {
            lgr.debug("Received binary message (length={})", binary.length);
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
            lgr.info("ws closed: {} - {}", code, reason);
            currentWebSocket = null;
            scheduleReconnect();
        }

        @Override
        public void onConnectError(WebSocket webSocket, WebSocketException exception) {
            isConnecting = false;
            lgr.error("ws conn error", exception);
            currentWebSocket = null;
            scheduleReconnect();
        }

        @Override
        public void onError(WebSocket webSocket, WebSocketException cause) {
            isConnecting = false;
            lgr.error("ws error", cause);
            currentWebSocket = null;
            scheduleReconnect();
        }
    }

    private static void disableJvmProxy() {
        System.setProperty("java.net.useSystemProxies", "false");
        System.clearProperty("socksProxyHost");
        System.clearProperty("socksProxyPort");
        System.clearProperty("socksProxyVersion");
        System.clearProperty("java.net.socks.username");
        System.clearProperty("java.net.socks.password");
        System.clearProperty("http.proxyHost");
        System.clearProperty("http.proxyPort");
        System.clearProperty("https.proxyHost");
        System.clearProperty("https.proxyPort");
        System.clearProperty("ftp.proxyHost");
        System.clearProperty("ftp.proxyPort");
        System.clearProperty("http.nonProxyHosts");
        ProxySelector.setDefault(DIRECT_PROXY_SELECTOR);
    }

    private static class DirectSocketFactory extends SocketFactory {
        @Override
        public Socket createSocket() {
            return new Socket(Proxy.NO_PROXY);
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            Socket socket = createSocket();
            socket.connect(new InetSocketAddress(host, port));
            return socket;
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            Socket socket = createSocket();
            socket.bind(new InetSocketAddress(localHost, localPort));
            socket.connect(new InetSocketAddress(host, port));
            return socket;
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            Socket socket = createSocket();
            socket.connect(new InetSocketAddress(host, port));
            return socket;
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            Socket socket = createSocket();
            socket.bind(new InetSocketAddress(localAddress, localPort));
            socket.connect(new InetSocketAddress(address, port));
            return socket;
        }
    }

    private static class DirectProxySelector extends ProxySelector {
        @Override
        public List<Proxy> select(URI uri) {
            return Collections.singletonList(Proxy.NO_PROXY);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
        }
    }


}
