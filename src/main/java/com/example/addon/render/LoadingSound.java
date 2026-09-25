package com.example.addon.render;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.InputStream;

public class LoadingSound {
    private static Clip clip;

    public static void play() {
        try {
            InputStream is = LoadingSound.class
                .getResourceAsStream("/assets/yuio/loading/loading.wav");
            if (is == null) {
                System.err.println("[yuio] loading.wav not found");
                return;
            }
            AudioInputStream ais = AudioSystem.getAudioInputStream(is);
            clip = AudioSystem.getClip();
            clip.open(ais);
            clip.start();
            System.out.println("[yuio] sound playing");
        } catch (Exception e) {
            System.err.println("[yuio] failed to play sound");
            e.printStackTrace();
        }
    }

    public static void stop() {
        if (clip != null && clip.isRunning()) {
            clip.stop();
            clip.close();
        }
    }
}
