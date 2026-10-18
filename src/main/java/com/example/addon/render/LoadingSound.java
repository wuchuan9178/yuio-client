package com.example.addon.render;

import com.example.addon.AddonTemplate;
import com.example.addon.utils.YuioSound;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;

public class LoadingSound {
    private static Clip clip;
    private static boolean started;

    public static void play() {
        if (started) return;
        started = true;

        new Thread(() -> {
            try {

                byte[] bytes = YuioSound.readResource(YuioSound.LOADING_ROOT + "loading.wav");
                if (bytes == null) {
                    AddonTemplate.LOG.warn("[yuio] loading.wav not found");
                    YuioSound.report("loading.wav: FAILED, resource not found");
                    return;
                }

                try (AudioInputStream ais = AudioSystem.getAudioInputStream(
                    new BufferedInputStream(new ByteArrayInputStream(bytes)))) {
                    Clip opened = AudioSystem.getClip();
                    opened.open(ais);
                    opened.start();
                    clip = opened;

                    AddonTemplate.LOG.info("[yuio] loading sound playing ({}ms)", opened.getMicrosecondLength() / 1000L);
                    YuioSound.report("loading.wav: started");
                }
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
