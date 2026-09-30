package com.example.addon.notify;

import com.example.addon.AddonTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class NotifyReader {
    private static final Path DB_DIR = Path.of(
        System.getenv("LOCALAPPDATA") == null ? "C:\\" : System.getenv("LOCALAPPDATA"),
        "Microsoft", "Windows", "Notifications");
    private static final Path DB = DB_DIR.resolve("wpndatabase.db");
    private static final Path WAL = DB_DIR.resolve("wpndatabase.db-wal");

    private static final String QQ_PROTOCOL = "ntqq-notification://";
    private static final int MAX_SEEN = 512;
    private static final int MAX_QUEUE = 16;

    private static final Queue<NotifyMessage> QUEUE = new ConcurrentLinkedQueue<>();
    private static final LinkedHashSet<String> SEEN = new LinkedHashSet<>();

    private static volatile boolean available = true;
    private static volatile String problem = "";
    private static volatile long stamp = -1;
    private static volatile long lastSize = -1;
    private static Thread thread;

    private NotifyReader() {
    }

    public static boolean available() {
        return available;
    }

    public static String problem() {
        return problem;
    }

    public static void start() {
        if (thread != null) return;

        thread = new Thread(NotifyReader::loop, "yuio-notify");
        thread.setDaemon(true);
        thread.start();
    }

    public static void drain(List<NotifyMessage> out) {
        NotifyMessage message;
        while ((message = QUEUE.poll()) != null) out.add(message);
    }

    private static void loop() {
        boolean seeded = false;

        while (true) {
            try {
                if (changed()) {
                    List<NotifyMessage> found = scan();

                    if (!seeded) {
                        seeded = true;
                        AddonTemplate.LOG.info("[yuio-qq] notification db ready, skipped {} old toast(es)", found.size());
                    } else {
                        for (NotifyMessage message : found) {
                            QUEUE.add(message);
                            while (QUEUE.size() > MAX_QUEUE) QUEUE.poll();
                        }
                    }
                }
            } catch (Throwable error) {
                fail(error.toString());
            }

            sleep(600);
        }
    }

    private static boolean changed() throws Exception {
        long time = Math.max(lastModified(DB), lastModified(WAL));
        long size = Files.size(DB) + (Files.exists(WAL) ? Files.size(WAL) : 0);

        if (time == stamp && size == lastSize) return false;

        stamp = time;
        lastSize = size;
        return true;
    }

    private static long lastModified(Path path) {
        try {
            return Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static List<NotifyMessage> scan() throws Exception {
        if (!Files.exists(DB)) throw new IllegalStateException(DB + " not found");

        StringBuilder builder = new StringBuilder(Files.readString(DB, StandardCharsets.ISO_8859_1));
        if (Files.exists(WAL) && Files.size(WAL) > 32) {
            builder.append(Files.readString(WAL, StandardCharsets.ISO_8859_1));
        }

        String raw = builder.toString();
        List<NotifyMessage> out = new ArrayList<>();

        int cursor = 0;
        while ((cursor = raw.indexOf("<toast", cursor)) >= 0) {
            int end = raw.indexOf("</toast>", cursor);
            if (end < 0) break;

            String block = raw.substring(cursor, end + 8);
            cursor = end + 8;

            int protocol = block.indexOf(QQ_PROTOCOL);
            if (protocol < 0 || protocol > 64) continue;
            if (!SEEN.add(block)) continue;

            while (SEEN.size() > MAX_SEEN) SEEN.remove(SEEN.iterator().next());

            NotifyMessage message = parse(decode(block));
            if (message != null) out.add(message);
        }

        if (!available) {
            available = true;
            problem = "";
        }

        return out;
    }

    private static String decode(String block) {
        return new String(block.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
    }

    private static NotifyMessage parse(String block) {
        try {
            String launch = attribute(block, "launch");
            if (launch == null) return null;

            String peerName = sanitize(launchValue(launch, "peerName"));
            long peerUin = number(launchValue(launch, "peerUin"));
            long account = number(launchValue(launch, "fuin"));
            boolean group = "2".equals(launchValue(launch, "chatType"));

            List<String> texts = texts(block);
            if (texts.isEmpty()) return null;

            String title = peerName.isEmpty() ? sanitize(texts.get(0)) : peerName;
            String body = "";

            if (texts.size() > 1) body = texts.get(1);
            else if (!sanitize(texts.get(0)).equals(title)) body = texts.get(0);

            body = body == null ? "" : body.trim();
            if (body.isEmpty() && title.isEmpty()) return null;

            List<NotifySegment> segments = segments(body);
            if (segments.isEmpty() && title.isEmpty()) return null;

            String sender = firstSegment(segments);
            boolean mention = body.contains("[有人@") || body.contains("@全体成员") || body.contains("@所有人");

            return new NotifyMessage(title, sender, image(block), account,
                group ? peerUin : -1L, mention, segments, System.currentTimeMillis());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String firstSegment(List<NotifySegment> segments) {
        for (NotifySegment segment : segments) {
            if (segment.rgb() == NotifySegment.SENDER) return segment.text().trim();
        }
        return "";
    }

    private static List<NotifySegment> segments(String body) {
        List<NotifySegment> out = new ArrayList<>();
        if (body == null || body.isEmpty()) return out;

        String rest = body.trim();

        while (rest.startsWith("[")) {
            int close = rest.indexOf(']');
            if (close < 0 || close > 12) break;

            String tag = rest.substring(0, close + 1);
            if (!tag.contains("@")) break;

            out.add(new NotifySegment(sanitize(tag) + " ", NotifySegment.MENTION_ME));
            rest = rest.substring(close + 1).trim();
        }

        int colon = rest.indexOf('：');
        if (colon < 0) colon = rest.indexOf(':');
        if (colon > 0 && colon < 24) {
            out.add(new NotifySegment(sanitize(rest.substring(0, colon)) + ": ", NotifySegment.SENDER));
            rest = rest.substring(colon + 1).trim();
        }

        int cursor = 0;
        while (cursor < rest.length()) {
            int at = rest.indexOf('@', cursor);

            if (at < 0) {
                append(out, rest.substring(cursor), NotifySegment.DEFAULT);
                break;
            }

            if (at > cursor) append(out, rest.substring(cursor, at), NotifySegment.DEFAULT);

            int end = at + 1;
            while (end < rest.length() && !Character.isWhitespace(rest.charAt(end))) end++;

            String mention = rest.substring(at, end);
            boolean everyone = mention.contains("全体") || mention.contains("所有");
            append(out, mention + " ", everyone ? NotifySegment.MENTION_ALL : NotifySegment.MENTION_OTHER);
            cursor = end;
        }

        return out;
    }

    private static void append(List<NotifySegment> out, String text, int rgb) {
        String clean = sanitize(text);
        if (clean.isEmpty()) return;

        if (!out.isEmpty() && out.get(out.size() - 1).rgb() == rgb) {
            NotifySegment last = out.get(out.size() - 1);
            out.set(out.size() - 1, new NotifySegment(last.text() + clean, rgb));
            return;
        }

        out.add(new NotifySegment(clean, rgb));
    }

    static String sanitize(String text) {
        if (text == null || text.isEmpty()) return "";

        StringBuilder builder = new StringBuilder(text.length());

        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);

            if (codePoint == '\n' || codePoint == '\r' || codePoint == '\t') builder.append(' ');
            else if (codePoint >= 0x20 && !unsupported(codePoint)) builder.appendCodePoint(codePoint);
        }

        return unescape(builder.toString().trim());
    }

    private static String unescape(String text) {
        if (text.indexOf('&') < 0) return text;

        return text.replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");
    }

    private static boolean unsupported(int codePoint) {
        return codePoint == 0x200D || codePoint == 0xFFFD
            || (codePoint >= 0xFE00 && codePoint <= 0xFE0F)
            || (codePoint >= 0x2600 && codePoint <= 0x27BF)
            || (codePoint >= 0x2B00 && codePoint <= 0x2BFF)
            || (codePoint >= 0x1F000 && codePoint <= 0x1FFFF)
            || (codePoint >= 0xE000 && codePoint <= 0xF8FF);
    }

    private static String attribute(String block, String name) {
        int at = block.indexOf(name + "=\"");
        if (at < 0) return null;

        int start = at + name.length() + 2;
        int end = block.indexOf('"', start);
        return end < 0 ? null : block.substring(start, end);
    }

    private static String launchValue(String launch, String key) {
        int at = launch.indexOf(key + "=");
        if (at < 0) return "";

        int start = at + key.length() + 1;
        int end = launch.indexOf(';', start);
        return end < 0 ? launch.substring(start) : launch.substring(start, end);
    }

    private static String image(String block) {
        String source = attribute(block, "src");
        if (source == null) return null;

        if (source.startsWith("file:///")) source = source.substring(8);
        source = source.replace('/', '\\');

        int end = 0;
        while (end < source.length() && end < 400 && source.charAt(end) >= 0x20
            && source.charAt(end) != '"') end++;
        source = source.substring(0, end).trim();

        boolean windowsPath = source.length() > 3
            && Character.isLetter(source.charAt(0)) && source.charAt(1) == ':';

        return windowsPath ? source : null;
    }

    private static long number(String value) {
        try {
            StringBuilder digits = new StringBuilder();
            for (int i = 0; i < value.length(); i++) {
                if (Character.isDigit(value.charAt(i))) digits.append(value.charAt(i));
            }
            return digits.isEmpty() ? -1L : Long.parseLong(digits.toString());
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private static List<String> texts(String block) {
        List<String> out = new ArrayList<>();
        int cursor = 0;

        while (true) {
            int at = block.indexOf("<text", cursor);
            if (at < 0) break;

            int start = block.indexOf('>', at);
            if (start < 0) break;

            int end = block.indexOf("</text>", start);
            if (end < 0) break;

            out.add(block.substring(start + 1, end));
            cursor = end + 7;
        }

        return out;
    }

    private static void fail(String reason) {
        if (available) {
            available = false;
            AddonTemplate.LOG.warn("[yuio-qq] cannot read Windows notifications: {}", reason);
        }

        problem = reason;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}

