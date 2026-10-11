package com.example.addon.utils;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.SourceDataLine;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class YuioSound {
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
                InputStream is = YuioSound.class.getResourceAsStream("/assets/yuio/sounds/" + file);
                if (is == null) {
                    report(file + ": FAILED, /assets/yuio/sounds/" + file + " is not on the classpath");
                    return;
                }

                AudioInputStream ais = AudioSystem.getAudioInputStream(is);
                AudioFormat base = ais.getFormat();
                report(file + ": source " + base.getEncoding() + " " + base.getSampleRate() + "Hz "
                    + base.getChannels() + "ch " + base.getSampleSizeInBits() + "bit");

                AudioFormat pcm = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    base.getSampleRate(), 16,
                    base.getChannels(), base.getChannels() * 2,
                    base.getSampleRate(), false
                );
                AudioInputStream pcmStream = AudioSystem.getAudioInputStream(pcm, ais);
                byte[] data = pcmStream.readAllBytes();
                pcmStream.close();

                if (data.length == 0) {
                    report(file + ": FAILED, decoded to 0 bytes");
                    return;
                }

                int frameSize = pcm.getFrameSize();

                // 变速：按帧重采样，避免破坏声道交错
                if (Math.abs(speed - 1.0f) > 0.01f) {
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

                // 音量
                if (Math.abs(volume - 1.0f) > 0.01f) {
                    for (int i = 0; i + 1 < data.length; i += 2) {
                        short s = (short)((data[i] & 0xFF) | (data[i + 1] << 8));
                        int scaled = Math.round(s * volume);
                        if (scaled > Short.MAX_VALUE) scaled = Short.MAX_VALUE;
                        if (scaled < Short.MIN_VALUE) scaled = Short.MIN_VALUE;
                        data[i] = (byte)(scaled & 0xFF);
                        data[i + 1] = (byte)((scaled >> 8) & 0xFF);
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
            } catch (Exception e) {
                report(file + ": FAILED, " + e);
                System.err.println("[yuio] failed to play " + file);
                e.printStackTrace();
            }
        }, "yuio-sound").start();
    }
}
