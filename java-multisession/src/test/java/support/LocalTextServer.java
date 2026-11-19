package support;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Tiny local chat backend used only for this demo.
 *
 * CUSTOMER: delete this class when you point the test at your real chat app. It exists so the
 * example is self-contained. It keeps all messages in memory, so every browser session that reaches
 * it (through BrowserStack Local) sees the same conversation.
 *
 * It also shows where an auth header is checked: /api/login and /api/messages return 401 unless the
 * request carries "Authorization: Bearer <expectedToken>". Your real app does this its own way.
 */
public class LocalTextServer {
    public static final int PORT = 45455;

    private final String expectedToken;
    private final List<String[]> messages = Collections.synchronizedList(new ArrayList<>());
    private HttpServer server;

    public LocalTextServer(String expectedToken) {
        this.expectedToken = expectedToken;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", this::serveIndex);
        server.createContext("/api/login", this::handleLogin);
        server.createContext("/api/messages", this::handleMessages);
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void serveIndex(HttpExchange ex) throws IOException {
        if (!"/".equals(ex.getRequestURI().getPath())) {
            respond(ex, 404, "text/plain", "Not found");
            return;
        }
        // The page itself is public; only the API calls need the auth header
        try (InputStream in = LocalTextServer.class.getResourceAsStream("/site/index.html")) {
            respond(ex, 200, "text/html; charset=utf-8", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private boolean authorised(HttpExchange ex) {
        String header = ex.getRequestHeaders().getFirst("Authorization");
        return ("Bearer " + expectedToken).equals(header);
    }

    private void handleLogin(HttpExchange ex) throws IOException {
        if (!authorised(ex)) {
            respond(ex, 401, "text/plain", "Unauthorized");
            return;
        }
        String name = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (name.isEmpty()) {
            respond(ex, 400, "text/plain", "Name required");
            return;
        }
        respond(ex, 200, "text/plain", "ok");
    }

    private void handleMessages(HttpExchange ex) throws IOException {
        if (!authorised(ex)) {
            respond(ex, 401, "text/plain", "Unauthorized");
            return;
        }
        if ("POST".equals(ex.getRequestMethod())) {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            messages.add(new String[] { jsonField(body, "user"), jsonField(body, "text") });
        }
        StringBuilder json = new StringBuilder("[");
        synchronized (messages) {
            for (int i = 0; i < messages.size(); i++) {
                if (i > 0) {
                    json.append(',');
                }
                json.append("{\"user\":\"").append(escape(messages.get(i)[0]))
                        .append("\",\"text\":\"").append(escape(messages.get(i)[1])).append("\"}");
            }
        }
        json.append(']');
        respond(ex, 200, "application/json; charset=utf-8", json.toString());
    }

    /** Minimal extraction for the two flat string fields this demo sends. */
    private static String jsonField(String json, String field) {
        String key = "\"" + field + "\":\"";
        int start = json.indexOf(key);
        if (start < 0) {
            return "";
        }
        start += key.length();
        StringBuilder out = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                out.append(json.charAt(++i));
            } else if (c == '"') {
                break;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void respond(HttpExchange ex, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
