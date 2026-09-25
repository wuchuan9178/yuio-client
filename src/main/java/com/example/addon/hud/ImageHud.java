package com.example.addon.hud;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.util.Identifier;

public class ImageHud extends HudElement {
    public static final HudElementInfo<ImageHud> INFO = new HudElementInfo<>(
        AddonTemplate.HUD_GROUP, "image-hud", "Show a custom image on screen.", ImageHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> texture = sgGeneral.add(new StringSetting.Builder()
        .name("texture")
        .description("Path under assets/yuio/textures/")
        .defaultValue("hud/yuio_client.png")
        .build());

    private final Setting<Double> width = sgGeneral.add(new DoubleSetting.Builder()
        .name("width").defaultValue(256).min(1).sliderRange(1, 2048).build());

    private final Setting<Double> height = sgGeneral.add(new DoubleSetting.Builder()
        .name("height").defaultValue(128).min(1).sliderRange(1, 2048).build());

    public ImageHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        double w = width.get();
        double h = height.get();
        setSize(w, h);

        Identifier id = Identifier.of("yuio", "textures/" + texture.get());

        renderer.drawContext.drawTexture(
            RenderPipelines.GUI_TEXTURED,
            id,
            (int) x, (int) y, 0f, 0f,
            (int) w, (int) h,
            (int) w, (int) h
        );
    }
}
