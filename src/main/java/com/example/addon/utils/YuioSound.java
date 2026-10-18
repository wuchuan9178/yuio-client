package com.example.addon.utils;

import net.fabricmc.loader.api.FabricLoader;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.SourceDataLine;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class YuioSound {
    public static final String SOUND_ROOT = "assets/yuio/sounds/";
    public static final String LOADING_ROOT = "assets/yuio/loading/";
    private static final Map<String, Long> lastPlayed = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<String> reports = new ConcurrentLinkedQueue<>();
    private static final int MAX_REPORTS = 64;
    private static final long MIN_INTERVAL_MS = 500L;

    private YuioSound() {
    }

    public static void report(String message) {
        reports.add(message);
        while (reports.size() > MAX_REPORTS) reports.poll();
    }

    public static List<String> drainReports() {
        List<String> out = new ArrayList<>();
        String line;
        while ((line = reports.poll()) != null) out.add(line);
        return out;
    }

    public static byte[] readResource(String path) {
        String fileName = path.substring(path.lastIndexOf('/') + 1);

        byte[] data = readClasspath(path);
        if (data != null) {
            report(path + ": loaded from the classpath (" + data.length + " bytes)");
            return data;
        }

        data = readModContainer(path);
        if (data != null) {
            report(path + ": loaded from the mod container (" + data.length + " bytes)");
            return data;
        }

        for (Path candidate : userCandidates(fileName)) {
            try {
                if (Files.isRegularFile(candidate)) {
                    data = Files.readAllBytes(candidate);
                    report(path + ": loaded from " + candidate + " (" + data.length + " bytes)");
                    return data;
                }
            } catch (IOException e) {
                report(path + ": failed to read " + candidate + ", " + e);
            }
        }

        report(path + ": NOT FOUND. " + describeCodeSource());
        return null;
    }

    private static byte[] readClasspath(String path) {
        try (InputStream direct = YuioSound.class.getResourceAsStream("/" + path)) {
            if (direct != null) return direct.readAllBytes();
        } catch (IOException e) {
            report(path + ": classpath stream failed, " + e);
        }

        ClassLoader loader = YuioSound.class.getClassLoader();
        if (loader != null) {
            try (InputStream viaLoader = loader.getResourceAsStream(path)) {
                if (viaLoader != null) return viaLoader.readAllBytes();
            } catch (IOException e) {
                report(path + ": classloader stream failed, " + e);
            }
        }

        return null;
    }

    private static byte[] readModContainer(String path) {
        try {
            return FabricLoader.getInstance()
                .getModContainer("yuio")
                .flatMap(container -> container.findPath(path))
                .filter(Files::isRegularFile)
                .map(p -> {
                    try {
                        return Files.readAllBytes(p);
                    } catch (IOException e) {
                        report(path + ": mod container read failed, " + e);
                        return null;
                    }
                })
                .orElse(null);
        } catch (Throwable t) {
            report(path + ": mod container lookup failed, " + t);
            return null;
        }
    }

    private static List<Path> userCandidates(String fileName) {
        List<Path> out = new ArrayList<>();
        try {
            Path gameDir = FabricLoader.getInstance().getGameDir();
            out.add(gameDir.resolve("config/yuio/sounds").resolve(fileName));
            out.add(gameDir.resolve("yuio/sounds").resolve(fileName));
            out.add(gameDir.resolve("config/yuio").resolve(fileName));
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static String describeCodeSource() {
        try {
            CodeSource source = YuioSound.class.getProtectionDomain().getCodeSource();
            if (source == null) return "code source: <unknown>";

            URL url = source.getLocation();
            String info = "code source: " + url;

            Path path;
            try {
                path = Paths.get(url.toURI());
            } catch (Exception e) {
                return info;
            }

            if (!Files.isRegularFile(path)) {
                try (var stream = Files.walk(path.resolve(SOUND_ROOT))) {
                    long count = stream.filter(Files::isRegularFile).count();
                    return info + ", sounds on disk: " + count;
                } catch (Exception e) {
                    return info + ", sounds on disk: <unreadable>";
                }
            }

            try (JarFile jar = new JarFile(path.toFile())) {
                List<String> names = new ArrayList<>();
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement().getName();
                    if (name.startsWith(SOUND_ROOT) && !name.endsWith("/")) {
                        if (names.size() < 6) names.add(name.substring(SOUND_ROOT.length()));
                    }
                }
                return info + ", sounds in jar: " + (names.isEmpty() ? "<none>" : names);
            }
        } catch (Throwable t) {
            return "code source: <failed, " + t + ">";
        }
    }

    //播放神秘音效
    public static void play(String file, float volume, float speed) {
        long now = System.currentTimeMillis();
        Long last = lastPlayed.get(file);
        if (last != null && now - last < MIN_INTERVAL_MS) {
            report(file + ": skipped, throttled (" + (now - last) + "ms since the last play)");
            return;
        }
        lastPlayed.put(file, now);

        report(file + ": queued, volume=" + volume + ", speed=" + speed);

        new Thread(() -> {
            try {
                byte[] bytes = readResource(SOUND_ROOT + file);
                if (bytes == null) {
                    report(file + ": FAILED, resource not found");
                    return;
                }

                if (bytes.length < 4) {
                    report(file + ": FAILED, only " + bytes.length + " bytes");
                    return;
                }

                try (AudioInputStream ais = AudioSystem.getAudioInputStream(
                    new BufferedInputStream(new ByteArrayInputStream(bytes)))) {
                    AudioFormat base = ais.getFormat();
                    report(file + ": source " + base.getEncoding() + " " + base.getSampleRate() + "Hz "
                        + base.getChannels() + "ch " + base.getSampleSizeInBits() + "bit");

                    AudioFormat pcm = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        base.getSampleRate(), 16,
                        base.getChannels(), base.getChannels() * 2,
                        base.getSampleRate(), false
                    );
                    byte[] data;
                    try (AudioInputStream pcmStream = AudioSystem.getAudioInputStream(pcm, ais)) {
                        data = pcmStream.readAllBytes();
                    }

                    if (data.length == 0) {
                        report(file + ": FAILED, decoded to 0 bytes");
                        return;
                    }

                    int frameSize = pcm.getFrameSize();

                    if (Math.abs(speed - 1.0f) > 0.01f) {
                        // 变速：按帧重采样，避免破坏声道交错
                        int frames = data.length / frameSize;
                        int newFrames = (int) (frames / speed);
                        if (newFrames < 1) newFrames = 1;

                        byte[] out = new byte[newFrames * frameSize];
                        for (int i = 0; i < newFrames; i++) {
                            int srcByte = (int) (i * speed) * frameSize;
                            if (srcByte + frameSize > data.length) break;
                            System.arraycopy(data, srcByte, out, i * frameSize, frameSize);
                        }
                        data = out;
                    }

                    if (Math.abs(volume - 1.0f) > 0.01f) {
                        // 音量
                        for (int i = 0; i + 1 < data.length; i += 2) {
                            short s = (short) ((data[i] & 0xFF) | (data[i + 1] << 8));
                            int scaled = Math.round(s * volume);
                            if (scaled > Short.MAX_VALUE) scaled = Short.MAX_VALUE;
                            if (scaled < Short.MIN_VALUE) scaled = Short.MIN_VALUE;
                            data[i] = (byte) (scaled & 0xFF);
                            data[i + 1] = (byte) ((scaled >> 8) & 0xFF);
                        }
                    }

                    report(file + ": " + data.length + " bytes ready, opening a clip");

                    try {
                        Clip clip = AudioSystem.getClip();
                        clip.open(pcm, data, 0, data.length);
                        clip.addLineListener(e -> {
                            if (e.getType() == LineEvent.Type.STOP) {
                                clip.close();
                                report(file + ": finished");
                            }
                        });
                        clip.start();
                        report(file + ": clip started, " + (clip.getMicrosecondLength() / 1000L) + "ms");
                        return;
                    } catch (Exception clipError) {
                        report(file + ": clip unavailable (" + clipError + "), falling back to a raw line");
                    }

                    DataLine.Info info = new DataLine.Info(SourceDataLine.class, pcm);
                    try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
                        line.open(pcm);
                        line.start();
                        line.write(data, 0, data.length);
                        line.drain();
                        line.stop();
                        report(file + ": raw line finished");
                    }
                }
            } catch (Exception e) {
                report(file + ": FAILED, " + e);
                System.err.println("[yuio] failed to play " + file);
                e.printStackTrace();
            }
        }, "yuio-sound").start();
    }
}
