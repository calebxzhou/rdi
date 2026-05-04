package calebxzhou.rdi.mc.common2.mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.fusionauth.http.HTTPMethod;
import io.fusionauth.http.server.HTTPListenerConfiguration;
import io.fusionauth.http.server.HTTPRequest;
import io.fusionauth.http.server.HTTPResponse;
import io.fusionauth.http.server.HTTPServer;
import io.fusionauth.http.server.HTTPServerConfiguration;

import java.io.IOException;
import java.net.InetAddress;

public final class RMcpHttpServer {
    public static final int DEFAULT_PORT = 65232;
    private static final Gson GSON = new GsonBuilder().create();
    private static HTTPServer server;
    private static RMcpGameConnector connector;

    private RMcpHttpServer() {
    }

    public static synchronized void start(RMcpGameConnector connector) {
        start(DEFAULT_PORT, connector);
    }

    public static synchronized void start(int port, RMcpGameConnector newConnector) {
        if (server != null) {
            connector = newConnector;
            return;
        }
        connector = newConnector;
        server = new HTTPServer()
                .withConfiguration(new HTTPServerConfiguration()
                        .withHandler(RMcpHttpServer::handle)
                        .withListener(new HTTPListenerConfiguration(InetAddress.getLoopbackAddress(), port)))
                .start();
        Runtime.getRuntime().addShutdownHook(new Thread(RMcpHttpServer::stop, "rdi-mcp-http-stop"));
    }

    public static synchronized void stop() {
        if (server == null) {
            return;
        }
        server.close();
        server = null;
    }

    private static void handle(HTTPRequest request, HTTPResponse response) throws IOException {
        if (!request.getMethod().is(HTTPMethod.GET)) {
            writeJson(response, 405, RMcpResponse.error("method_not_allowed", "only GET is supported"));
            return;
        }
        if ("/test".equals(request.getPath())) {
            handleTest(response);
            return;
        }
        if ("/pos".equals(request.getPath())) {
            handlePos(response);
            return;
        }
        if ("/staring-block".equals(request.getPath())) {
            handleStaringBlock(request, response);
            return;
        }
        writeJson(response, 404, RMcpResponse.error("not_found", "endpoint not found"));
    }

    private static void handleTest(HTTPResponse response) throws IOException {
        try {
            writeJson(response, 200, RMcpResponse.ok(connector.testData()));
        } catch (Exception e) {
            writeJson(response, 500, RMcpResponse.error("internal_error", e.getMessage()));
        }
    }

    private static void handlePos(HTTPResponse response) throws IOException {
        try {
            var pos = connector.posData();
            if (pos == null) {
                writeJson(response, 409, RMcpResponse.error("no_player", "player is not in world"));
                return;
            }
            writeJson(response, 200, RMcpResponse.ok(pos));
        } catch (Exception e) {
            writeJson(response, 500, RMcpResponse.error("internal_error", e.getMessage()));
        }
    }

    private static void handleStaringBlock(HTTPRequest request, HTTPResponse response) throws IOException {
        try {
            if (!connector.playerInWorld()) {
                writeJson(response, 409, RMcpResponse.error("no_player", "player is not in world"));
                return;
            }
            var block = connector.staringBlockData("true".equals(request.getURLParameter("fluid")));
            if (block == null) {
                writeJson(response, 404, RMcpResponse.error("no_block", "player is not looking at a block"));
                return;
            }
            writeJson(response, 200, RMcpResponse.ok(block));
        } catch (Exception e) {
            writeJson(response, 500, RMcpResponse.error("internal_error", e.getMessage()));
        }
    }

    private static void writeJson(HTTPResponse response, int status, RMcpResponse<?> data) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json; charset=utf-8");
        response.getWriter().write(GSON.toJson(data));
    }
}
