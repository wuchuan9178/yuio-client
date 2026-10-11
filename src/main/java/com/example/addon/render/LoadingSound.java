package com.example.addon.render;

import com.example.addon.AddonTemplate;
import com.example.addon.utils.YuioSound;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.InputStream;

public class LoadingSound {
    private static Clip clip;
    private static boolean started;

    public static void play() {
        if (started) return;
        started = true;

        new Thread(() -> {
            try {
                InputStream is = LoadingSound.class
                    .getResourceAsStream("/assets/yuio/loading/loading.wav");
                if (is == null) {
                    AddonTemplate.LOG.warn("[yuio] loading.wav not found");
                    YuioSound.report("loading.wav: FAILED, /assets/yuio/loading/loading.wav is not on the classpath");
                    return;
                }

                AudioInputStream ais = AudioSystem.getAudioInputStream(is);
                Clip opened = AudioSystem.getClip();
                opened.open(ais);
                opened.start();
                clip = opened;

                AddonTemplate.LOG.info("[yuio] loading sound playing ({}ms)", opened.getMicrosecondLength() / 1000L);
                YuioSound.report("loading.wav: started");
            } catch (Exception e) {
                AddonTemplate.LOG.warn("[yuio] failed to play the loading sound: {}", e.toString());
                YuioSound.report("loading.wav: FAILED, " + e);
                e.printStackTrace();
            }
        }, "yuio-loading-sound").start();
    }

    public static void stop() {
        Clip current = clip;
        if (current != null && current.isRunning()) {
            current.stop();
            current.close();
        }
        started = false;
    }
}
