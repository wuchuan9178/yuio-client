package com.example.addon.hud;

import com.example.addon.utils.BlurRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public abstract class GlassHud extends HudElement {
    protected final SettingGroup sgAppearance = settings.createGroup("Appearance");

    protected final Setting<Double> scale = sgAppearance.add(new DoubleSetting.Builder()
        .name("scale").defaultValue(1).min(0.5).sliderRange(0.5, 2).build());

    protected final Setting<Double> radius = sgAppearance.add(new DoubleSetting.Builder()
        .name("radius").defaultValue(12).min(0).sliderRange(0, 30).build());

    protected final Setting<Integer> blurStrength = sgAppearance.add(new IntSetting.Builder()
        .name("blur-strength").description("Frosted glass blur radius: 1 = 3 pixels (default 12 = 36px). 0 disables the frosted glass.").defaultValue(12).min(0).max(30).sliderRange(0, 30).build());

    protected final Setting<Integer> backgroundAlpha = sgAppearance.add(new IntSetting.Builder()
        .name("background-alpha").defaultValue(165).min(0).sliderRange(0, 255).build());

    protected final Setting<Integer> tintStrength = sgAppearance.add(new IntSetting.Builder()
        .name("tint-strength").defaultValue(170).min(0).sliderRange(0, 255).build());

    protected final Setting<Boolean> border = sgAppearance.add(new BoolSetting.Builder()
        .name("border").defaultValue(true).build());

    protected final Setting<Integer> dividerAlpha = sgAppearance.add(new IntSetting.Builder()
        .name("divider-alpha").defaultValue(46).min(0).sliderRange(0, 255).build());

    protected final Setting<SettingColor> textColor = sgAppearance.add(new ColorSetting.Builder()
        .name("text-color").defaultValue(new SettingColor(255, 255, 255)).build());

    protected final Setting<SettingColor> dimColor = sgAppearance.add(new ColorSetting.Builder()
        .name("dim-color").defaultValue(new SettingColor(150, 150, 150)).build());

    protected GlassHud(HudElementInfo<?> info) {
        super(info);
    }

    protected double unit() {
        return scale.get();
    }

    protected double padding() {
        return 10 * unit();
    }

    protected void panel(double x, double y, double w, double h) {
        IslandRenderer.beginFrame();
        BlurRenderer.init();
        BlurRenderer.request(blurStrength.get());

        IslandRenderer.shadow(x, y, w, h, radius.get() * unit(), 1.0);
        IslandRenderer.glass(x, y, w, h, radius.get() * unit(),
            blurStrength.get(), backgroundAlpha.get(), tintStrength.get(), border.get(), true);
    }

    protected void divider(double x, double y, double w) {
        if (dividerAlpha.get() <= 0) return;
        IslandRenderer.round(x, y, w, 1, 0.5, new Color(255, 255, 255, dividerAlpha.get()));
    }

    protected static Color faded(Color color, double alpha) {
        return IslandRenderer.alpha(color, (int) (color.a * Math.max(0, Math.min(1, alpha))));
    }
}

