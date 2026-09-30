package com.example.addon.music;

import com.example.addon.AddonTemplate;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MusicReader {
    private static final Charset OEM = Charset.forName("GBK");
    private static final String PROCESS = "cloudmusic.exe";
    private static final String IDLE = "网易云音乐";
    private static final Path LYRIC_DIR = Path.of("yuio-lyrics");

    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");

    public record Track(String title, String artist, long since) {
        public boolean playing() {
            return !title.isEmpty();
        }
    }

    public record Line(long time, String text) {
    }

    private static volatile Track track = new Track("", "", 0L);
    private static volatile List<Line> lines = List.of();
    private static Thread thread;

    private MusicReader() {
    }

    public static void start() {
        if (thread != null) return;

        prepareFolder();
        thread = new Thread(MusicReader::loop, "yuio-music");
        thread.setDaemon(true);
        thread.start();
    }

    private static void prepareFolder() {
        try {
            Files.createDirectories(LYRIC_DIR);
            Path readme = LYRIC_DIR.resolve("README.txt");

            if (!Files.exists(readme)) {
                Files.writeString(readme,
                    "把 .lrc 歌词放这里, 文件名用 \"歌名 - 歌手.lrc\" 或 \"歌名.lrc\" (UTF-8 / GBK 都认)。\n"
                        + "Drop .lrc files here, named \"song - artist.lrc\" or \"song.lrc\" (UTF-8 or GBK).\n",
                    StandardCharsets.UTF_8);
            }
        } catch (Throwable ignored) {
        }
    }

    public static Track track() {
        return track;
    }

    public static boolean hasLyrics() {
        return !lines.isEmpty();
    }

    public static String lineAt(long elapsed) {
        String current = "";

        for (Line line : lines) {
            if (line.time() > elapsed) break;
            current = line.text();
        }

        return current;
    }

    public static String nextLine(long elapsed) {
        for (Line line : lines) {
            if (line.time() > elapsed) return line.text();
        }

        return "";
    }

    private static void loop() {
        while (true) {
            try {
                String raw = windowTitle();

                if (raw == null || raw.isBlank() || raw.startsWith(IDLE)) {
                    if (track.playing()) track = new Track("", "", 0L);
                } else {
                    update(raw);
                }
            } catch (Throwable ignored) {
            }

            sleep(1200);
        }
    }

    private static void update(String raw) {
        String title = raw.trim();
        String artist = "";

        int dash = title.lastIndexOf(" - ");
        if (dash > 0) {
            artist = title.substring(dash + 3).trim();
            title = title.substring(0, dash).trim();
        }

        Track current = track;
        if (current.title().equals(title) && current.artist().equals(artist)) return;

        List<Line> loaded = load(title, artist);
        track = new Track(title, artist, System.currentTimeMillis());
        lines = loaded;

        AddonTemplate.LOG.info("[yuio-music] now playing: {} - {} ({} lyric line(s))",
            title, artist.isEmpty() ? "?" : artist, loaded.size());
    }

    private static String windowTitle() throws Exception {
        Process process = new ProcessBuilder("tasklist", "/V", "/FO", "CSV", "/FI", "IMAGENAME eq " + PROCESS)
            .redirectErrorStream(true)
            .start();

        String output = new String(process.getInputStream().readAllBytes(), OEM);

        for (String row : output.split("\\R")) {
            if (!row.startsWith("\"")) continue;

            String[] parts = row.split("\",\"");
            String image = parts[0].replace("\"", "").trim();
            if (!image.equalsIgnoreCase(PROCESS)) continue;

            String title = parts[parts.length - 1].replace("\"", "").trim();
            if (title.isEmpty() || "N/A".equals(title)) continue;
            return title;
        }

        return null;
    }

    private static List<Line> load(String title, String artist) {
        if (!Files.isDirectory(LYRIC_DIR)) return List.of();

        Path best = null;
        int bestScore = -1;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(LYRIC_DIR, "*.lrc")) {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                int score = score(name.substring(0, name.length() - 4), title, artist);

                if (score > bestScore) {
                    bestScore = score;
                    best = file;
                }
            }
        } catch (Throwable ignored) {
            return List.of();
        }

        if (best == null || bestScore < 0) return List.of();

        try {
            List<Line> parsed = parse(readText(best));
            if (!parsed.isEmpty()) AddonTemplate.LOG.info("[yuio-music] lyrics from {}", best.getFileName());
            return parsed;
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    private static int score(String base, String title, String artist) {
        String name = base.toLowerCase(Locale.ROOT);
        String song = title.toLowerCase(Locale.ROOT);
        String singer = artist.toLowerCase(Locale.ROOT);

        if (song.isEmpty() || !name.contains(song)) return -1;
        if (!singer.isEmpty() && name.contains(singer)) return 2;
        if (name.equals(song)) return 1;
        return 0;
    }

    private static String readText(Path file) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        return utf8.indexOf('\uFFFD') < 0 ? utf8 : new String(bytes, OEM);
    }

    private static List<Line> parse(String text) {
        List<Line> out = new ArrayList<>();

        for (String raw : text.split("\\R")) {
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

            for (long time : times) out.add(new Line(time, body));
        }

        out.sort(Comparator.comparingLong(Line::time));
        return out;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}

