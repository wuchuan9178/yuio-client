package com.example.addon.music;

import com.example.addon.AddonTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NeteaseCache {
    private static final long RETRY_MS = 60_000L;

    private static final Pattern BRACKETS = Pattern.compile("[(（\\[【].*?[)）\\]】]");
    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "yuio-netease");
        thread.setDaemon(true);
        return thread;
    });

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();

    private static final class Entry {
        volatile String coverUrl;
        volatile List<MusicReader.Line> lines = List.of();
        volatile boolean done;
        volatile long failedAt;
    }

    private NeteaseCache() {
    }

    public static void request(String title, String artist) {
        if (title == null || title.isBlank()) return;

        String key = key(title, artist);
        Entry entry = CACHE.computeIfAbsent(key, name -> new Entry());
        if (entry.done) return;
        if (entry.failedAt != 0 && System.currentTimeMillis() - entry.failedAt < RETRY_MS) return;

        entry.failedAt = System.currentTimeMillis();

        POOL.submit(() -> {
            try {
                NeteaseApi.Song song = NeteaseApi.find(title, artist);

                if (song == null || !matches(song, title, artist)) {
                    entry.done = true;
                    entry.failedAt = 0;
                    AddonTemplate.LOG.info("[yuio-music] no netease match for {} - {}", title, artist);
                    return;
                }

                List<MusicReader.Line> parsed = parse(NeteaseApi.lyrics(song.id()));
                entry.coverUrl = song.coverUrl();
                entry.lines = parsed;
                entry.done = true;
                entry.failedAt = 0;

                AddonTemplate.LOG.info("[yuio-music] netease #{} cover={} lyric line(s)={}",
                    song.id(), song.coverUrl() != null, parsed.size());
            } catch (Throwable error) {
                entry.failedAt = System.currentTimeMillis();
                AddonTemplate.LOG.warn("[yuio-music] netease lookup failed: {}", error.toString());
            }
        });
    }

    public static String coverUrl(String title, String artist) {
        Entry entry = CACHE.get(key(title, artist));
        return entry == null ? null : entry.coverUrl;
    }

    public static List<MusicReader.Line> lines(String title, String artist) {
        Entry entry = CACHE.get(key(title, artist));
        return entry == null ? List.of() : entry.lines;
    }

    private static String key(String title, String artist) {
        return (title + " - " + (artist == null ? "" : artist)).toLowerCase(Locale.ROOT);
    }

    private static boolean matches(NeteaseApi.Song song, String title, String artist) {
        String wanted = normalize(title);
        String found = normalize(song.name());
        if (wanted.isEmpty() || found.isEmpty()) return true;

        if (!found.contains(wanted) && !wanted.contains(found)) return false;
        if (artist == null || artist.isBlank()) return true;

        String singer = song.artist() == null ? "" : song.artist().toLowerCase(Locale.ROOT);
        return singer.contains(normalize(artist)) || normalize(artist).contains(singer);
    }

    private static String normalize(String text) {
        if (text == null) return "";

        Matcher matcher = BRACKETS.matcher(text);
        String stripped = matcher.replaceAll(" ");
        return stripped.toLowerCase(Locale.ROOT).replaceAll("[\\s\\-_/|·、,，.。'\"`]", "");
    }

    private static List<MusicReader.Line> parse(String lrc) {
        if (lrc == null || lrc.isBlank()) return List.of();

        List<MusicReader.Line> out = new ArrayList<>();

        for (String raw : lrc.split("\\R")) {
            Matcher matcher = TIME.matcher(raw);
            List<Long> times = new ArrayList<>();
            int end = 0;

            while (matcher.find()) {
                if (matcher.start() != end) break;

                long minutes = Long.parseLong(matcher.group(1));
                long seconds = Long.parseLong(matcher.group(2));
                long fraction = 0;

                if (matcher.group(3) != null) {
                    String value = matcher.group(3);
                    fraction = Long.parseLong(value) * (value.length() == 1 ? 100 : value.length() == 2 ? 10 : 1);
                }

                times.add(minutes * 60_000 + seconds * 1000 + fraction);
                end = matcher.end();
            }

            if (times.isEmpty() || end >= raw.length()) continue;

            String body = raw.substring(end).trim();
            if (body.isEmpty()) continue;

            for (long time : times) out.add(new MusicReader.Line(time, body));
        }

        out.sort((a, b) -> Long.compare(a.time(), b.time()));
        return out;
    }
}
