package com.example.addon.music;

import com.example.addon.AddonTemplate;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;

public final class CdpReader {
    private static final String JS =
        "(()=>{const lc=document.querySelector('[class*=\"LyricDisplayInnerContainer\"]');"
            + "const tw=document.querySelector('[class*=\"TimeWarpper\"]');"
            + "return JSON.stringify({line:lc?lc.innerText.trim():'',time:tw?tw.innerText.trim():''});})()";

    private static final long EVALUATE_INTERVAL = 700L;
    private static final long RETRY_MS = 5000L;

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build();

    private static final String EVALUATE = buildEvaluate();

    private static volatile int port;
    private static volatile WebSocket socket;
    private static volatile boolean available;
    private static volatile long positionMs = -1;
    private static volatile long durationMs = -1;
    private static volatile String line = "";

    private static long lastEvaluate;
    private static long nextAttempt;
    private static boolean connecting;
    private static Thread thread;

    private CdpReader() {
    }

    public static void port(int value) {
        if (value != port) {
            port = value;
            close();
        }

        if (thread == null) {
            thread = new Thread(CdpReader::loop, "yuio-cdp");
            thread.setDaemon(true);
            thread.start();
        }
    }

    public static boolean available() {
        return available;
    }

    public static long positionMs() {
        return positionMs;
    }

    public static long durationMs() {
        return durationMs;
    }

    public static String line() {
        return line;
    }

    private static void loop() {
        while (true) {
            try {
                tick();
            } catch (Throwable ignored) {
            }

            sleep(150);
        }
    }

    private static void tick() {
        if (port <= 0) {
            available = false;
            return;
        }

        long now = System.currentTimeMillis();

        if (socket == null) {
            available = false;
            if (!connecting && now >= nextAttempt) {
                nextAttempt = now + RETRY_MS;
                connect();
            }
            return;
        }

        if (now - lastEvaluate < EVALUATE_INTERVAL) return;

        lastEvaluate = now;

        try {
            socket.sendText(EVALUATE, true);
        } catch (Throwable error) {
            close();
        }
    }

    private static void connect() {
        connecting = true;

        try {
            String wsUrl = findTarget();
            if (wsUrl == null) return;

            HTTP.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .buildAsync(URI.create(wsUrl), new Listener())
                .whenComplete((webSocket, error) -> {
                    connecting = false;

                    if (error == null) {
                        socket = webSocket;
                        available = true;
                        lastEvaluate = 0;
                    }
                });
        } catch (Throwable error) {
            connecting = false;
        }
    }

    private static String findTarget() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/json/list"))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;

            JsonElement root = JsonParser.parseString(response.body());
            if (!root.isJsonArray()) return null;

            JsonArray targets = root.getAsJsonArray();
            for (JsonElement element : targets) {
                if (!element.isJsonObject()) continue;

                JsonObject target = element.getAsJsonObject();
                String url = text(target, "webSocketDebuggerUrl");
                String type = text(target, "type");
                if (url != null && !url.isBlank() && "page".equals(type)) return url;
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static void parse(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return;

            JsonObject result = root.getAsJsonObject().getAsJsonObject("result");
            if (result == null) return;

            JsonObject inner = result.getAsJsonObject("result");
            if (inner == null) return;

            JsonElement value = inner.get("value");
            if (value == null || !value.isJsonPrimitive()) return;

            JsonElement payload = JsonParser.parseString(value.getAsString());
            if (!payload.isJsonObject()) return;

            JsonObject data = payload.getAsJsonObject();
            String text = text(data, "line");
            if (text != null) line = text;

            String time = text(data, "time");
            if (time == null) return;

            String[] parts = time.split("/");
            if (parts.length == 0) return;

            positionMs = stamp(parts[0]);
            if (parts.length > 1) durationMs = stamp(parts[1]);
        } catch (Throwable ignored) {
        }
    }

    private static long stamp(String text) {
        String[] parts = text.trim().split(":");
        if (parts.length < 2) return -1;

        try {
            long minutes = Long.parseLong(parts[0].trim());
            long seconds = Long.parseLong(parts[1].trim());
            return minutes * 60_000L + seconds * 1000L;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static String text(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private static void close() {
        WebSocket current = socket;
        socket = null;
        available = false;

        if (current != null) {
            try {
                current.abort();
            } catch (Throwable ignored) {
            }
        }
    }

    private static String buildEvaluate() {
        JsonObject params = new JsonObject();
        params.addProperty("expression", JS);
        params.addProperty("returnByValue", true);

        JsonObject message = new JsonObject();
        message.addProperty("id", 1);
        message.addProperty("method", "Runtime.evaluate");
        message.add("params", params);

        return message.toString();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class Listener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);

            if (last) {
                String json = buffer.toString();
                buffer.setLength(0);
                parse(json);
            }

            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            AddonTemplate.LOG.info("[yuio-music] cdp closed: {}", statusCode);
            socket = null;
            available = false;
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            socket = null;
            available = false;
        }
    }
}
