package com.example.addon.hud;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import static meteordevelopment.meteorclient.MeteorClient.mc;
import com.example.addon.utils.Assets;
import com.example.addon.utils.RoundRectRenderer;
import com.mojang.blaze3d.textures.GpuTextureView;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.util.Identifier;

import java.io.InputStream;

public class ImageHud extends HudElement {
    public static final HudElementInfo<ImageHud> INFO = new HudElementInfo<>(
        AddonTemplate.HUD_GROUP, "image-hud", "Sticker HUD, several built-in styles or your own image.", ImageHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum Style {
        YuioLogo("yuio logo", "hud/yuio_client.png"),
        IslandIcon("island icon", "dynamic_island/icon.png"),
        AppIcon("app icon", "icon.png"),
        Singleplayer("singleplayer", "gui/singleplayer.png"),
        Multiplayer("multiplayer", "gui/multiplayer.png"),
        Settings("settings", "gui/settings.png"),
        Quit("quit", "gui/quit.png"),
        TitleBackground("title background", "title_background.png"),
        Custom("custom path", "");

        private final String label;
        public final String path;

        Style(String label, String path) {
            this.label = label;
            this.path = path;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final Setting<Style> style = sgGeneral.add(new EnumSetting.Builder<Style>()
        .name("style").defaultValue(Style.YuioLogo).build());

    private final Setting<String> custom = sgGeneral.add(new StringSetting.Builder()
        .name("custom-path")
        .description("Path under assets/yuio/textures/, used when style is custom path.")
        .defaultValue("hud/yuio_client.png")
        .visible(() -> style.get() == Style.Custom)
        .build());

    private final Setting<Double> width = sgGeneral.add(new DoubleSetting.Builder()
        .name("width").defaultValue(256).min(1).sliderRange(1, 2048).build());

    private final Setting<Double> height = sgGeneral.add(new DoubleSetting.Builder()
        .name("height").defaultValue(128).min(1).sliderRange(1, 2048).build());

    private String sizedPath = "";
    private int textureWidth = 256;
    private int textureHeight = 128;

    public ImageHud() {
        super(INFO);
    }

    private String path() {
        return style.get() == Style.Custom ? custom.get() : style.get().path;
    }

    private void ensureSize(String path) {
        if (path.equals(sizedPath)) return;

        sizedPath = path;
        textureWidth = 256;
        textureHeight = 128;

        try (InputStream in = mc.getResourceManager().getResource(Identifier.of("yuio", "textures/" + path))
            .orElseThrow().getInputStream()) {

            byte[] header = in.readNBytes(24);
            int w = ((header[16] & 0xFF) << 24) | ((header[17] & 0xFF) << 16) | ((header[18] & 0xFF) << 8) | (header[19] & 0xFF);
            int h = ((header[20] & 0xFF) << 24) | ((header[21] & 0xFF) << 16) | ((header[22] & 0xFF) << 8) | (header[23] & 0xFF);

            if (w > 0 && h > 0) {
                textureWidth = w;
                textureHeight = h;
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void render(HudRenderer renderer) {
        double w = width.get();
        double h = height.get();
        setSize(w, h);

        Assets.poll();

        GpuTextureView view = Assets.texture(path());
        if (view == null) return;

        RoundRectRenderer.textured(x, y, w, h, 0, 0, 0, 0, view, 0f, 0f, 1f, 1f,
            new Color(255, 255, 255, 0), 255);
    }
}
