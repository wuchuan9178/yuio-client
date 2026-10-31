package com.example.addon.music;

import com.example.addon.AddonTemplate;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

// 网易云扫码登录: 拿 unikey -> 轮询 -> 存 cookie
public final class NeteaseLogin {
    public enum Status {
        IDLE, REQUESTING, WAITING, SCANNED, EXPIRED, SUCCESS, ERROR
    }

    private static final String API = "https://music.163.com";
    private static final String AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36";
    private static final Path COOKIE_FILE =
        FabricLoader.getInstance().getConfigDir().resolve("yuio").resolve("netease-cookie.txt");

    private static final CookieManager COOKIES = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(6))
        .version(HttpClient.Version.HTTP_1_1)
        .cookieHandler(COOKIES)
        .build();

    private static volatile Status status = Status.IDLE;
    private static volatile String payload = "";
    private static volatile int code;
    private static volatile String detail = "";
    private static volatile String nickname = "";
    private static volatile String cookie = "";
    private static volatile boolean running;

    private NeteaseLogin() {
    }

    public static Status status() {
        return status;
    }

    public static String payload() {
        return payload;
    }

    public static int code() {
        return code;
    }

    public static String detail() {
        return detail;
    }

    public static String nickname() {
        return nickname;
    }

    public static boolean loggedIn() {
        return !cookie.isBlank();
    }

    public static String cookieInfo() {
        String value = cookie;
        if (value.isBlank()) return "none";

        StringBuilder keys = new StringBuilder();
        for (String part : value.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            if (!keys.isEmpty()) keys.append(',');
            keys.append(part.substring(0, eq).trim());
        }
        return value.length() + " chars [" + keys + "]";
    }

    public static void restore() {
        try {
            if (Files.exists(COOKIE_FILE)) {
                cookie = Files.readString(COOKIE_FILE, StandardCharsets.UTF_8).trim();
                if (!cookie.isBlank()) fetchProfile();
            }
        } catch (Throwable ignored) {
        }
    }

    public static void start() {
        if (running) return;

        running = true;
        status = Status.REQUESTING;
        detail = "requesting unikey...";

        Thread thread = new Thread(NeteaseLogin::loop, "yuio-netease-login");
        thread.setDaemon(true);
        thread.start();
    }

    public static void logout() {
        running = false;
        cookie = "";
        nickname = "";
        payload = "";
        code = 0;
        status = Status.IDLE;
        detail = "";

        try {
            Files.deleteIfExists(COOKIE_FILE);
        } catch (Throwable ignored) {
        }
    }

    // 扫码被风控时用这个: 直接贴浏览器里的 cookie
    public static void loginWithCookie(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || !value.contains("MUSIC_U=")) {
            status = Status.ERROR;
            detail = "cookie must contain MUSIC_U=";
            return;
        }

        running = false;
        cookie = value;
        status = Status.REQUESTING;
        detail = "checking cookie...";

        Thread thread = new Thread(() -> {
            if (checkCookie()) {
                status = Status.SUCCESS;
                code = 200;
                detail = "cookie login ok";
                saveCookie();
            } else {
                cookie = "";
                status = Status.ERROR;
                detail = "cookie invalid";
            }
        }, "yuio-netease-cookie");
        thread.setDaemon(true);
        thread.start();
    }

    private static void loop() {
        try {
            String key = unikey();
            if (key == null) {
                fail("unikey failed");
                return;
            }

            payload = API + "/login?codekey=" + key;
            status = Status.WAITING;
            code = 801;
            detail = "waiting for scan";

            long deadline = System.currentTimeMillis() + 180_000L;

            while (running && System.currentTimeMillis() < deadline) {
                JsonObject result = get(API + "/api/login/qrcode/client/login?key=" + key + "&type=1", null);
                if (result == null) {
                    sleep(1500);
                    continue;
                }

                int responseCode = result.has("code") ? result.get("code").getAsInt() : -1;
                code = responseCode;

                switch (responseCode) {
                    case 800 -> {
                        status = Status.EXPIRED;
                        detail = "qr code expired, click login again";
                        running = false;
                        return;
                    }
                    case 801 -> {
                        status = Status.WAITING;
                        detail = "waiting for scan";
                    }
                    case 802 -> {
                        status = Status.SCANNED;
                        detail = "scanned, confirm on phone";
                    }
                    case 803 -> {
                        String value = text(result, "cookie");
                        if (value != null && !value.isBlank()) cookie = value;
                        status = Status.SUCCESS;
                        detail = "login success";
                        running = false;
                        saveCookie();
                        fetchProfile();
                        return;
                    }
                    default -> {
                        String message = text(result, "message");
                        status = Status.ERROR;
                        detail = message == null || message.isBlank() ? "code " + responseCode : "code " + responseCode + " " + message;
                        running = false;
                        AddonTemplate.LOG.warn("[yuio-music] netease qr code {}: {}", responseCode, detail);
                        return;
                    }
                }

                sleep(1500);
            }

            if (running) {
                status = Status.EXPIRED;
                detail = "timeout, click login again";
                running = false;
            }
        } catch (Throwable error) {
            fail(error.toString());
        }
    }

    private static String unikey() {
        try {
            JsonObject result = get(API + "/api/login/qrcode/unikey?type=1", null);
            return result == null ? null : text(result, "unikey");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void fetchProfile() {
        try {
            JsonObject result = get(API + "/api/nuser/account/get", cookie);
            if (result == null) return;

            JsonObject profile = result.has("profile") && result.get("profile").isJsonObject()
                ? result.getAsJsonObject("profile")
                : null;

            String name = profile == null ? null : text(profile, "nickname");
            if (name != null && !name.isBlank()) {
                nickname = name;
                AddonTemplate.LOG.info("[yuio-music] netease logged in as {}", name);
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean checkCookie() {
        try {
            JsonObject result = get(API + "/api/nuser/account/get", cookie);
            if (result == null) return false;

            JsonObject profile = result.has("profile") && result.get("profile").isJsonObject()
                ? result.getAsJsonObject("profile")
                : null;
            if (profile == null) return false;

            String name = text(profile, "nickname");
            if (name != null && !name.isBlank()) {
                nickname = name;
                AddonTemplate.LOG.info("[yuio-music] netease cookie login as {}", name);
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void saveCookie() {
        try {
            Files.createDirectories(COOKIE_FILE.getParent());
            Files.writeString(COOKIE_FILE, cookie, StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
        }
    }

    private static void fail(String message) {
        status = Status.ERROR;
        detail = message;
        running = false;
        AddonTemplate.LOG.warn("[yuio-music] netease login failed: {}", message);
    }

    private static JsonObject get(String url, String cookies) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
            .header("Referer", API + "/")
            .header("Origin", API)
            .header("User-Agent", AGENT)
            .header("Accept", "*/*")
            .timeout(Duration.ofSeconds(10))
            .GET();

        if (cookies != null && !cookies.isBlank()) builder.header("Cookie", cookies);

        HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) return null;

        JsonElement root = JsonParser.parseString(response.body());
        return root.isJsonObject() ? root.getAsJsonObject() : null;
    }

    private static String text(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
