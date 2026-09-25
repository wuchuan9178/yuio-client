package com.example.addon.qq;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * OneBot 11 的 WebSocket 客户端。
 *
 * <p>QQ 仍然是官方客户端, 后台运行 + 登录, 由 NapCat / LLOneBot 这类**注入进 QQ 的插件**
 * 把消息通过本地 WebSocket 推出来。这里只用 JDK 自带的 HttpClient, 不额外引依赖。
 */
public final class QqClient {
    private static final char SECTION = '\u00A7';

    /** MC 的 16 个颜色码。 */
    private static final int[] COLOR_CODES = {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    private final Consumer<QqMessage> sink;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Map<Long, String> groupNames = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "yuio-qq-http");
        thread.setDaemon(true);
        return thread;
    });

    /** 连接状态, 给灵动岛显示用。 */
    public enum State { OFF, CONNECTING, CONNECTED, RETRYING }

    private volatile WebSocket socket;
    private volatile State state = State.OFF;
    private volatile String detail = "";
    private volatile long nextAttempt;
    private volatile long lastEvent;
    private volatile int failures;
    private volatile boolean wanted;

    private long selfId = -1L;
    private String url = "";
    private String httpBase = "http://127.0.0.1:3000";
    private String token = "";
    private String groupAvatarTemplate = "https://p.qlogo.cn/gh/{group}/{group}/100";
    private String userAvatarTemplate = "https://q1.qlogo.cn/g?b=qq&nk={qq}&s=100";

    public QqClient(Consumer<QqMessage> sink) {
        this.sink = sink;
    }

    public State state() {
        return state;
    }

    public String detail() {
        return detail;
    }

    public void connect(String url, String token, String base, String groupAvatar, String userAvatar) {
        disconnect();
        if (url == null || url.isBlank()) return;

        if (groupAvatar != null && !groupAvatar.isBlank()) groupAvatarTemplate = groupAvatar;
        if (userAvatar != null && !userAvatar.isBlank()) userAvatarTemplate = userAvatar;

        if (base != null && !base.isBlank()) {
            this.httpBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        }

        this.url = url;
        this.token = token == null ? "" : token;
        this.failures = 0;
        this.wanted = true;
        attempt();
    }

    public void disconnect() {
        wanted = false;
        state = State.OFF;
        detail = "";
        close();
    }

    /** 每帧调一次: 掉线了就自己重连, 指数退避。 */
    public void tick() {
        if (!wanted) return;

        if (socket != null) {
            // NapCat 每 30s 一个心跳; 超过 90s 没动静说明 QQ 那边掉了, socket 还活着但已经死了
            if (System.currentTimeMillis() - lastEvent > 90_000L) {
                AddonTemplate.LOG.warn("[yuio-qq] no heartbeat for 90s, reconnecting");
                close();
                failures++;
                state = State.RETRYING;
                detail = "no heartbeat for 90s";
                nextAttempt = 0;
            }
            return;
        }

        if (state == State.CONNECTING) return;
        if (System.currentTimeMillis() < nextAttempt) return;
        attempt();
    }

    private void attempt() {
        if (!wanted || url.isBlank()) return;

        // 2s 起, 最多退到 30s, 免得 NapCat 没开时疯狂重试
        nextAttempt = System.currentTimeMillis() + Math.min(30_000L, 2_000L << Math.min(failures, 4));
        state = State.CONNECTING;

        // NapCat 有的版本只认查询参数, 有的只认头, 两个都带上最稳
        String target = token.isBlank() || url.contains("access_token=")
            ? url
            : url + (url.contains("?") ? "&" : "?") + "access_token=" + token;

        AddonTemplate.LOG.info("[yuio-qq] connecting to {} (token {})", target, token.isBlank() ? "none" : "set");

        try {
            WebSocket.Builder builder = http.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(6));

            if (!token.isBlank()) {
                builder.header("Authorization", "Bearer " + token);
            }

            builder.buildAsync(URI.create(target), new Listener()).whenComplete((ws, error) -> {
                if (error == null) {
                    socket = ws;
                    failures = 0;
                    lastEvent = System.currentTimeMillis();
                    state = State.CONNECTED;
                    detail = "";
                    AddonTemplate.LOG.info("[yuio-qq] websocket connected");
                } else {
                    failed(error);
                }
            });
        } catch (Throwable error) {
            failed(error);
        }
    }

    private void failed(Throwable error) {
        if (!wanted) return;

        socket = null;
        failures++;
        state = State.RETRYING;
        detail = reason(error);
        AddonTemplate.LOG.warn("[yuio-qq] websocket failed ({}): {}", failures, detail);
    }

    private void close() {
        WebSocket current = socket;
        socket = null;
        if (current != null) {
            try {
                current.abort();
            } catch (Throwable ignored) {
            }
        }
    }

    /** 异常链最里面那条才是"connection refused"这种有用的信息。 */
    private static String reason(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();

        String message = cause.getMessage();
        String text = cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
        return text.length() > 90 ? text.substring(0, 90) + "..." : text;
    }

    private final class Listener implements WebSocket.Listener {
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
                handle(json);
            }

            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            socket = null;
            if (!wanted) return null;

            failures++;
            state = State.RETRYING;
            detail = "closed " + statusCode + (reason == null || reason.isBlank() ? "" : " " + reason);
            AddonTemplate.LOG.warn("[yuio-qq] websocket closed: {} {}", statusCode, reason);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            socket = null;
            if (!wanted) return;

            failures++;
            state = State.RETRYING;
            detail = reason(error);
            AddonTemplate.LOG.warn("[yuio-qq] websocket error: {}", detail);
        }
    }

    private void handle(String json) {
        lastEvent = System.currentTimeMillis();

        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            String postType = str(root, "post_type");

            // 元事件里带 self_id, 用来判断 @你
            if ("meta_event".equals(postType)) {
                selfId = num(root, "self_id");
                return;
            }

            if (!"message".equals(postType)) return;

            JsonObject sender = root.getAsJsonObject("sender");
            if (sender == null) return;

            long userId = num(sender, "user_id");
            boolean group = "group".equals(str(root, "message_type"));
            long groupId = group ? num(root, "group_id") : -1L;

            String name = sanitize(group
                ? firstNonEmpty(str(sender, "card"), str(sender, "nickname"), String.valueOf(userId))
                : firstNonEmpty(str(sender, "nickname"), String.valueOf(userId)));

            String title;
            String avatar;

            if (group) {
                title = sanitize(groupNames.getOrDefault(groupId, "群 " + groupId));
                if (!groupNames.containsKey(groupId)) resolveGroupName(groupId);
                avatar = groupAvatarTemplate.replace("{group}", String.valueOf(groupId));
            } else {
                // 私聊顶部直接显示联系人, 不用再写一行"私聊"
                title = name;
                avatar = userAvatarTemplate.replace("{qq}", String.valueOf(userId));
            }

            // messagePostFormat 可能是 array 也可能是 string, 两种都得认
            JsonElement rawMessage = root.get("message");
            Parsed parsed = rawMessage != null && rawMessage.isJsonArray()
                ? parseMessage(rawMessage.getAsJsonArray(), userId, group ? name : null)
                : parseCqt(str(root, "raw_message"), userId, group ? name : null);
            if (parsed.segments().isEmpty()) return;

            sink.accept(new QqMessage(title, name, avatar, groupId, parsed.mention(),
                parsed.segments(), System.currentTimeMillis()));
        } catch (Throwable ignored) {
            // 结构不认识的消息直接丢掉, 不能让一条脏数据把整个连接搞崩
        }
    }


    /** 解析结果: 分段 + 有没有 @ 到自己。 */
    private record Parsed(List<QqSegment> segments, boolean mention) {
    }

    private Parsed parseMessage(JsonArray message, long userId, String senderPrefix) {
        List<QqSegment> out = new ArrayList<>();
        boolean mention = false;
        if (message == null) return new Parsed(out, false);

        if (senderPrefix != null) {
            out.add(new QqSegment(senderPrefix + ": ", 0xAAAAAA));
        }

        for (JsonElement element : message) {
            try {
                JsonObject segment = element.getAsJsonObject();
                String type = str(segment, "type");
                JsonObject data = segment.getAsJsonObject("data");

                switch (type) {
                    case "text" -> appendCoded(out, str(data, "text"));
                    case "at" -> {
                        String qq = str(data, "qq");
                        if ("all".equals(qq)) {
                            mention = true;
                            out.add(new QqSegment("@全体成员 ", QqSegment.MENTION_ALL));
                        } else if (String.valueOf(userId).equals(qq) || String.valueOf(selfId).equals(qq)) {
                            mention = true;
                            out.add(new QqSegment("@你 ", QqSegment.MENTION_ME));
                        } else {
                            out.add(new QqSegment("@" + sanitize(firstNonEmpty(str(data, "name"), qq)) + " ",
                                QqSegment.MENTION_OTHER));
                        }
                    }
                    case "image" -> out.add(new QqSegment("[图片]", QqSegment.DEFAULT));
                    case "face" -> out.add(new QqSegment("[表情]", QqSegment.DEFAULT));
                    case "record" -> out.add(new QqSegment("[语音]", QqSegment.DEFAULT));
                    case "video" -> out.add(new QqSegment("[视频]", QqSegment.DEFAULT));
                    case "file" -> out.add(new QqSegment("[文件]", QqSegment.DEFAULT));
                    default -> {
                        // 别的类型不显示, 免得刷屏
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        return new Parsed(out, mention);
    }

    /** string 格式(CQ 码)的兜底解析。 */
    private Parsed parseCqt(String raw, long userId, String senderPrefix) {
        List<QqSegment> out = new ArrayList<>();
        boolean mention = false;
        if (raw == null || raw.isEmpty()) return new Parsed(out, false);

        if (senderPrefix != null) out.add(new QqSegment(senderPrefix + ": ", 0xAAAAAA));

        int cursor = 0;
        while (cursor < raw.length()) {
            int start = raw.indexOf("[CQ:", cursor);

            if (start < 0) {
                appendCoded(out, raw.substring(cursor));
                break;
            }

            if (start > cursor) appendCoded(out, raw.substring(cursor, start));

            int end = raw.indexOf(']', start);
            if (end < 0) {
                appendCoded(out, raw.substring(start));
                break;
            }

            String body = raw.substring(start + 4, end);
            String[] parts = body.split(",");
            String type = parts.length > 0 ? parts[0] : "";

            switch (type) {
                case "at" -> {
                    String qq = "";
                    for (String part : parts) {
                        if (part.startsWith("qq=")) qq = part.substring(3);
                    }
                    if ("all".equals(qq)) {
                        mention = true;
                        out.add(new QqSegment("@全体成员 ", QqSegment.MENTION_ALL));
                    } else if (String.valueOf(userId).equals(qq) || String.valueOf(selfId).equals(qq)) {
                        mention = true;
                        out.add(new QqSegment("@你 ", QqSegment.MENTION_ME));
                    } else {
                        out.add(new QqSegment("@" + qq + " ", QqSegment.MENTION_OTHER));
                    }
                }
                case "image" -> out.add(new QqSegment("[图片]", QqSegment.DEFAULT));
                case "face" -> out.add(new QqSegment("[表情]", QqSegment.DEFAULT));
                case "record" -> out.add(new QqSegment("[语音]", QqSegment.DEFAULT));
                case "video" -> out.add(new QqSegment("[视频]", QqSegment.DEFAULT));
                case "file" -> out.add(new QqSegment("[文件]", QqSegment.DEFAULT));
                default -> {
                }
            }

            cursor = end + 1;
        }

        return new Parsed(out, mention);
    }

    /**
     * 字体里没有的字符(emoji / 各种符号)画出来是个空方框, 不如直接去掉。
     * 游戏字体被换成一个普通中文 TTF, 没有彩色 emoji 的字形。
     */
    static String sanitize(String text) {
        if (text == null || text.isEmpty()) return "";

        StringBuilder builder = new StringBuilder(text.length());

        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);

            if (codePoint == '\n' || codePoint == '\r' || codePoint == '\t') {
                builder.append(' ');
            } else if (codePoint >= 0x20 && !unsupported(codePoint)) {
                builder.appendCodePoint(codePoint);
            }
        }

        return builder.toString();
    }

    private static boolean unsupported(int codePoint) {
        return codePoint == 0x200D || codePoint == 0xFFFD
            || (codePoint >= 0xFE00 && codePoint <= 0xFE0F)
            || (codePoint >= 0x2600 && codePoint <= 0x27BF)
            || (codePoint >= 0x2B00 && codePoint <= 0x2BFF)
            || (codePoint >= 0x1F000 && codePoint <= 0x1FFFF)
            || (codePoint >= 0xE000 && codePoint <= 0xF8FF);
    }

    /** 把 MC 的 §x 颜色码拆成带色的段。 */
    private static void appendCoded(List<QqSegment> out, String text) {
        if (text == null || text.isEmpty()) return;

        int color = QqSegment.DEFAULT;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == SECTION && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                int mapped = colorOf(code);

                if (mapped != -1) {
                    if (!current.isEmpty()) {
                        out.add(new QqSegment(sanitize(current.toString()), color));
                        current.setLength(0);
                    }
                    color = mapped;
                    i++;
                    continue;
                }
            }

            current.append(c);
        }

        if (!current.isEmpty()) out.add(new QqSegment(sanitize(current.toString()), color));
    }

    private static int colorOf(char code) {
        int index = "0123456789abcdef".indexOf(code);
        return index < 0 ? -1 : COLOR_CODES[index];
    }

    /** 群名不在消息事件里, 得单独问一次 HTTP 接口, 拿到之前先显示群号。 */
    private void resolveGroupName(long groupId) {
        groupNames.put(groupId, "群 " + groupId);
        // 忽略列表要填群号, 所以进日志里方便抄
        AddonTemplate.LOG.info("[yuio-qq] group id {}", groupId);

        pool.submit(() -> {
            try {
                HttpRequest.Builder request = HttpRequest.newBuilder(
                        URI.create(httpBase + "/get_group_info?group_id=" + groupId))
                    .timeout(Duration.ofSeconds(5))
                    .GET();

                if (!token.isBlank()) request.header("Authorization", "Bearer " + token);

                HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
                JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                JsonObject data = root.getAsJsonObject("data");
                if (data == null) return;

                String name = str(data, "group_name");
                if (name != null && !name.isBlank()) {
                    groupNames.put(groupId, name);
                    AddonTemplate.LOG.info("[yuio-qq] group {} = {}", groupId, name);
                }
            } catch (Throwable ignored) {
            }
        });
    }

    private static String str(JsonObject object, String key) {
        if (object == null) return null;
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private static long num(JsonObject object, String key) {
        if (object == null) return -1L;
        JsonElement element = object.get(key);
        try {
            return element == null || element.isJsonNull() ? -1L : element.getAsLong();
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }
}
