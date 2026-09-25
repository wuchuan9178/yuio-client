package com.example.addon.qq;

import com.example.addon.AddonTemplate;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 接管 NapCat: 补配置、按需启动/重启 QQ。
 *
 * <p>NapCat 是**注入进 QQ.exe 的插件**, 所以"QQ 是不是注入启动的"决定了一切:
 * 直接双击 QQ 图标启动的话, 进程里没有 NapCat, 端口不会开, addon 永远连不上。
 * 而 launcher.bat 再点一次会起第二个 QQ, 账号已被占用, 于是报
 * "当前账号已登录, 无法重复登录"。
 *
 * <p>这里把这些情况都收进一个后台任务: 先把配置补好, 再看 QQ 到底跑没跑、
 * 是不是注入了, 需要的话把它杀掉用 {@code launcher.bat -q <QQ号>} 重新拉起来。
 */
public final class NapCatConfig {
    private static final String MARKER = "yuio";

    /** 找 NapCat 时不去翻这些又大又不可能的目录。 */
    private static final Set<String> SKIP = Set.of(
        "run", "build", "out", "target", ".git", ".gradle", ".idea", ".dependencies",
        "node_modules", "saves", "logs", "cache", "debug", "data", "downloads",
        "crash-reports", "resourcepacks", "shaderpacks", "screenshots");

    /** 给灵动岛看的一句话, 比 "Connection refused" 有用得多。 */
    public static volatile String hint = "";

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "yuio-napcat");
        thread.setDaemon(true);
        return thread;
    });

    private NapCatConfig() {
    }

    public static void manage(String configuredDir, String wsUrl, String httpUrl, String token,
                              String account, boolean launch, boolean forceRestart, boolean hidden) {
        POOL.submit(() -> {
            Path dir = resolve(configuredDir);
            if (dir == null) {
                hint = "NapCat folder not found";
                AddonTemplate.LOG.warn("[yuio-qq] no NapCat.Shell found - set napcat-dir");
                return;
            }

            AddonTemplate.LOG.info("[yuio-qq] napcat folder: {}", dir);

            boolean restart = false;
            try {
                restart = apply(dir, wsUrl, httpUrl, token);
            } catch (Throwable error) {
                AddonTemplate.LOG.warn("[yuio-qq] napcat config failed: {}", error.toString());
            }

            String qq = account == null || account.isBlank() ? detectAccount(dir) : account.trim();
            if (!qq.isBlank()) AddonTemplate.LOG.info("[yuio-qq] quick login account: {}", qq);

            // 不知道账号就没法免扫码, 那控制台得留着给人扫二维码
            boolean hide = hidden && !qq.isBlank();

            String host = host(wsUrl);
            int port = port(wsUrl, 3001);
            boolean running = qqRunning();

            if (running && waitForPort(host, port, 15_000)) {
                hint = restart ? "restart QQ to apply the config" : "";
                if (restart) AddonTemplate.LOG.warn("[yuio-qq] napcat config changed - restart QQ to apply it");
                return;
            }

            if (!running) {
                hint = "QQ is not running";
                if (launch) startAndWait(dir, qq, hide, host, port);
                return;
            }

            // QQ 在跑, OneBot 端口却不通 —— 这个 QQ 不是注入启动的。
            // 同一个账号 QQ 只允许一个电脑端在线, 所以只能把它关掉换成注入版。
            hint = "QQ is running without NapCat";

            if (!forceRestart) {
                AddonTemplate.LOG.warn("[yuio-qq] QQ is running without NapCat - close it, or enable qq-force-restart");
                return;
            }

            AddonTemplate.LOG.warn("[yuio-qq] restarting QQ with NapCat injected");
            if (killQq()) startAndWait(dir, qq, hide, host, port);
        });
    }

    private static void startAndWait(Path dir, String account, boolean hidden, String host, int port) {
        start(dir, account, hidden);

        if (waitForPort(host, port, 40_000)) {
            hint = "";
            AddonTemplate.LOG.info("[yuio-qq] napcat is listening on {}:{}", host, port);
        } else {
            hint = "QQ started but NapCat is not listening";
            AddonTemplate.LOG.warn("[yuio-qq] napcat never came up - turn qq-hidden-launch off to see its console");
        }
    }

    /** 设置里给了路径就用, 写 auto 或者留空就从工作目录往上找。 */
    public static Path resolve(String configured) {
        if (configured != null) {
            String value = configured.trim();

            if ("off".equalsIgnoreCase(value)) return null;

            if (!value.isEmpty() && !"auto".equalsIgnoreCase(value)) {
                Path path = Path.of(value);
                return isNapCat(path) ? path : null;
            }
        }

        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path found = search(dir, 0);
            if (found != null) return found;
        }

        return null;
    }

    private static Path search(Path dir, int depth) {
        if (depth > 10 || !Files.isDirectory(dir)) return null;
        if (isNapCat(dir)) return dir;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                if (!Files.isDirectory(child)) continue;
                if (SKIP.contains(child.getFileName().toString().toLowerCase(Locale.ROOT))) continue;

                Path found = search(child, depth + 1);
                if (found != null) return found;
            }
        } catch (Throwable ignored) {
            // 没权限的目录直接跳过
        }

        return null;
    }

    private static boolean isNapCat(Path dir) {
        return Files.isRegularFile(dir.resolve("napcat.mjs"))
            && Files.isRegularFile(dir.resolve("NapCatWinBootMain.exe"));
    }

    /** config/onebot11_<QQ号>.json 里最近改过的那个就是当前在用的账号。 */
    public static String detectAccount(Path dir) {
        Path configDir = dir.resolve("config");
        String account = "";
        long newest = -1;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(configDir, "onebot11_*.json")) {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                String candidate = name.substring("onebot11_".length(), name.length() - ".json".length());
                if (candidate.isEmpty() || !candidate.chars().allMatch(Character::isDigit)) continue;

                long time = Files.getLastModifiedTime(file).toMillis();
                if (time > newest) {
                    newest = time;
                    account = candidate;
                }
            }
        } catch (Throwable ignored) {
        }

        return account;
    }

    /** @return 有没有改动过配置文件 */
    private static boolean apply(Path dir, String wsUrl, String httpUrl, String token) throws IOException {
        Path configDir = dir.resolve("config");
        if (!Files.isDirectory(configDir)) {
            AddonTemplate.LOG.warn("[yuio-qq] no napcat config folder at {}", configDir);
            return false;
        }

        int files = 0;
        int changed = 0;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(configDir, "onebot11_*.json")) {
            for (Path file : stream) {
                files++;
                if (patch(file, wsUrl, httpUrl, token)) changed++;
            }
        }

        if (files == 0) {
            AddonTemplate.LOG.warn("[yuio-qq] no onebot11_*.json in {} - log into QQ once first", configDir);
            return false;
        }

        AddonTemplate.LOG.info("[yuio-qq] napcat config: {} file(s), {} updated", files, changed);
        return changed > 0;
    }

    private static boolean patch(Path file, String wsUrl, String httpUrl, String token) throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();

        JsonObject network = root.has("network") && root.get("network").isJsonObject()
            ? root.getAsJsonObject("network")
            : new JsonObject();
        root.add("network", network);

        boolean changed = server(network, "websocketServers", wsUrl, 3001, token, true);
        changed |= server(network, "httpServers", httpUrl, 3000, token, false);
        if (!changed) return false;

        Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n",
            StandardCharsets.UTF_8);
        return true;
    }

    private static boolean server(JsonObject network, String key, String url, int fallbackPort,
                                  String token, boolean websocket) {
        JsonArray servers = network.has(key) && network.get(key).isJsonArray()
            ? network.getAsJsonArray(key)
            : new JsonArray();
        network.add(key, servers);

        int port = port(url, fallbackPort);
        String host = host(url);

        JsonObject entry = null;
        for (JsonElement element : servers) {
            if (!element.isJsonObject()) continue;
            JsonObject candidate = element.getAsJsonObject();

            if (MARKER.equals(text(candidate, "name")) || number(candidate, "port") == port) {
                entry = candidate;
                break;
            }
        }

        if (entry == null) {
            entry = new JsonObject();
            servers.add(entry);
        }

        boolean changed = put(entry, "name", new JsonPrimitive(MARKER));
        changed |= put(entry, "enable", new JsonPrimitive(true));
        changed |= put(entry, "host", new JsonPrimitive(host));
        changed |= put(entry, "port", new JsonPrimitive(port));
        changed |= put(entry, "messagePostFormat", new JsonPrimitive("array"));
        changed |= put(entry, "token", new JsonPrimitive(token == null ? "" : token));
        changed |= put(entry, "debug", new JsonPrimitive(false));
        changed |= put(entry, "reportSelfMessage", new JsonPrimitive(false));

        if (websocket) {
            changed |= put(entry, "enableForcePushEvent", new JsonPrimitive(true));
            changed |= put(entry, "heartInterval", new JsonPrimitive(30000));
        } else {
            changed |= put(entry, "enableCors", new JsonPrimitive(true));
            changed |= put(entry, "enableWebsocket", new JsonPrimitive(false));
        }

        return changed;
    }

    /** 只写不一样的值, 这样第二次跑就不会白改文件时间。 */
    private static boolean put(JsonObject entry, String key, JsonElement value) {
        if (value.equals(entry.get(key))) return false;
        entry.add(key, value);
        return true;
    }

    /** 所有跑着的 QQ(主进程 + 它的 Electron 子进程)。 */
    public static List<ProcessHandle> qqProcesses() {
        return ProcessHandle.allProcesses()
            .filter(process -> process.info().command()
                .map(command -> command.toLowerCase(Locale.ROOT).endsWith("qq.exe"))
                .orElse(false))
            .toList();
    }

    /**
     * 提权进程读不到 command(), 所以 ProcessHandle 看不见时再用 tasklist 兜底。
     * 这个判断很关键: 看不出来就以为 QQ 没开, 于是又起一个, 就变成
     * "当前账号已登录, 无法重复登录"。
     */
    public static boolean qqRunning() {
        if (!qqProcesses().isEmpty()) return true;

        try {
            Process process = new ProcessBuilder("tasklist", "/FI", "IMAGENAME eq QQ.exe", "/NH")
                .redirectErrorStream(true)
                .start();

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return output.contains("QQ.exe");
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * @return 有没有杀干净 —— 没杀干净就绝对不能再去起一个,
     *         否则就是"当前账号已登录, 无法重复登录"
     */
    private static boolean killQq() {
        for (ProcessHandle process : qqProcesses()) process.destroy();

        for (int i = 0; i < 8 && qqRunning(); i++) sleep(500);

        if (qqRunning()) {
            // 提权启动的 QQ, ProcessHandle 动不了, 让 taskkill 再试一次
            try {
                new ProcessBuilder("taskkill", "/IM", "QQ.exe", "/F")
                    .redirectErrorStream(true)
                    .start()
                    .waitFor();
            } catch (Throwable ignored) {
            }

            for (int i = 0; i < 6 && qqRunning(); i++) sleep(500);
        }

        if (qqRunning()) {
            AddonTemplate.LOG.warn("[yuio-qq] could not close QQ - close it by hand, then it can be started injected");
            return false;
        }

        sleep(1000);
        return true;
    }

    /**
     * 用 launcher.bat 把 QQ 拉起来, 顺手把控制台窗口藏掉。
     *
     * <p>加 -q 才是免扫码快速登录。本来就是管理员的话 -Verb RunAs 不会再弹 UAC,
     * 否则弹一次; 用 PowerShell 转一手是为了能指定 WindowStyle, 直接 ProcessBuilder
     * 起 bat 一定会闪一个黑框。
     */
    public static void start(Path dir, String account, boolean hidden) {
        Path launcher = dir.resolve("launcher.bat");

        if (!Files.isRegularFile(launcher)) {
            AddonTemplate.LOG.warn("[yuio-qq] no launcher.bat at {}", launcher);
            return;
        }

        // 走 cmd.exe 而不是直接起 bat: 这样 -WorkingDirectory 一定是 bat 的 %cd%,
        // launcher.bat 全靠 %cd% 找自己的 dll 和 mjs
        StringBuilder script = new StringBuilder()
            .append("Start-Process -FilePath 'cmd.exe' -ArgumentList '/c','\"")
            .append(launcher).append('"');

        if (account != null && !account.isBlank()) {
            script.append("','-q','").append(account.trim());
        }

        script.append("' -WorkingDirectory '").append(dir).append('\'');
        if (hidden) script.append(" -WindowStyle Hidden");
        script.append(" -Verb RunAs");

        try {
            new ProcessBuilder("powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command", script.toString())
                .start();
            AddonTemplate.LOG.info("[yuio-qq] starting injected QQ{}", hidden ? " (hidden)" : "");
        } catch (Throwable error) {
            AddonTemplate.LOG.warn("[yuio-qq] launching QQ failed: {}", error.toString());
        }
    }

    private static boolean waitForPort(String host, int port, long timeout) {
        long deadline = System.currentTimeMillis() + timeout;

        while (System.currentTimeMillis() < deadline) {
            if (portOpen(host, port)) return true;
            sleep(500);
        }

        return portOpen(host, port);
    }

    public static boolean portOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 700);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static int port(String url, int fallback) {
        try {
            int port = URI.create(url.trim()).getPort();
            return port > 0 ? port : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String host(String url) {
        try {
            String host = URI.create(url.trim()).getHost();
            return host == null || host.isBlank() ? "127.0.0.1" : host;
        } catch (Throwable ignored) {
            return "127.0.0.1";
        }
    }

    private static String text(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || !element.isJsonPrimitive() ? null : element.getAsString();
    }

    private static int number(JsonObject object, String key) {
        JsonElement element = object.get(key);
        try {
            return element == null || !element.isJsonPrimitive() ? -1 : element.getAsInt();
        } catch (Throwable ignored) {
            return -1;
        }
    }
}
