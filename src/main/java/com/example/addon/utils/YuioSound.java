package com.example.addon.utils;

import javax.sound.sampled.*;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class YuioSound {
    private static final Map<String, Long> lastPlayed = new ConcurrentHashMap<>();
    private static final long MIN_INTERVAL_MS = 500L;

    //播放神秘音效
    public static void play(String file, float volume, float speed) {
        long now = System.currentTimeMillis();
        Long last = lastPlayed.get(file);
        if (last != null && now - last < MIN_INTERVAL_MS) return;
        lastPlayed.put(file, now);

        new Thread(() -> {
            try {
                InputStream is = YuioSound.class.getResourceAsStream("/assets/yuio/sounds/" + file);
                if (is == null) {
                    System.err.println("[yuio] sound not found: " + file);
                    return;
                }

                AudioInputStream ais = AudioSystem.getAudioInputStream(is);
                AudioFormat base = ais.getFormat();
                AudioFormat pcm = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    base.getSampleRate(), 16,
                    base.getChannels(), base.getChannels() * 2,
                    base.getSampleRate(), false
                );
                AudioInputStream pcmStream = AudioSystem.getAudioInputStream(pcm, ais);
                byte[] data = pcmStream.readAllBytes();
                pcmStream.close();

                // 变速：重采样
                if (Math.abs(speed - 1.0f) > 0.01f) {
                    int newLen = (int)(data.length / speed);
                    newLen -= newLen % 2;
                    byte[] out = new byte[newLen];
                    for (int i = 0; i < newLen / 2; i++) {
                        int srcSample = (int)(i * speed);
                        int srcByte = srcSample * 2;
                        if (srcByte + 1 < data.length) {
                            out[i * 2] = data[srcByte];
                            out[i * 2 + 1] = data[srcByte + 1];
                        }
                    }
                    data = out;
                }

                // 音量
                if (Math.abs(volume - 1.0f) > 0.01f) {
                    for (int i = 0; i + 1 < data.length; i += 2) {
                        short s = (short)((data[i] & 0xFF) | (data[i + 1] << 8));
                        s = (short)Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, s * volume));
                        data[i] = (byte)(s & 0xFF);
                        data[i + 1] = (byte)((s >> 8) & 0xFF);
                    }
                }

                Clip clip = AudioSystem.getClip();
                clip.open(pcm, data, 0, data.length);
                clip.addLineListener(e -> {
                    if (e.getType() == LineEvent.Type.STOP) clip.close();
                });
                clip.start();
            } catch (Exception e) {
                System.err.println("[yuio] failed to play " + file);
                e.printStackTrace();
            }
        }, "yuio-sound").start();
    }
}
