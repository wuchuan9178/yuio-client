package com.example.addon.hud;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import static meteordevelopment.meteorclient.MeteorClient.mc;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.entity.player.PlayerEntity;

public class RadarHud extends GlassHud {
    public static final HudElementInfo<RadarHud> INFO = new HudElementInfo<>(
        AddonTemplate.HUD_GROUP, "radar-hud", "Top-down radar with a crosshair and a rotating grid.", RadarHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> size = sgGeneral.add(new IntSetting.Builder()
        .name("size").defaultValue(110).min(40).sliderRange(40, 260).build());

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range").description("How many blocks the radar covers.").defaultValue(64).min(8).sliderRange(8, 256).build());

    private final Setting<Double> gridSize = sgGeneral.add(new DoubleSetting.Builder()
        .name("grid-size").description("Grid spacing in blocks.").defaultValue(16).min(2).sliderRange(2, 64).build());

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate-with-view").description("Rotate the grid with the way you look.").defaultValue(true).build());

    private final Setting<Boolean> cross = sgGeneral.add(new BoolSetting.Builder()
        .name("crosshair").defaultValue(true).build());

    private final Setting<Double> dotSize = sgGeneral.add(new DoubleSetting.Builder()
        .name("dot-size").defaultValue(3).min(1).sliderRange(1, 8).build());

    private final Setting<SettingColor> gridColor = sgGeneral.add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
        .name("grid-color").defaultValue(new SettingColor(255, 255, 255, 40)).build());

    private final Setting<SettingColor> enemyColor = sgGeneral.add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
        .name("enemy-color").defaultValue(new SettingColor(255, 80, 80)).build());

    private final Setting<SettingColor> friendColor = sgGeneral.add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
        .name("friend-color").defaultValue(new SettingColor(85, 255, 120)).build());

    private final Setting<SettingColor> selfColor = sgGeneral.add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
        .name("self-color").defaultValue(new SettingColor(120, 200, 255)).build());

    public RadarHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        double base = unit();
        double side = size.get() * base;

        setSize(side, side);
        panel(x, y, side, side);

        double centerX = x + side / 2;
        double centerY = y + side / 2;
        double half = side / 2 - 3 * base;

        if (cross.get()) {
            IslandRenderer.round(centerX, y + 3 * base, 1, side - 6 * base, 0.5, gridColor.get());
            IslandRenderer.round(x + 3 * base, centerY, side - 6 * base, 1, 0.5, gridColor.get());
        }

        drawGrid(renderer, centerX, centerY, half, base);

        if (mc.player == null || mc.world == null) return;

        double yaw = rotate.get() ? Math.toRadians(mc.player.getYaw()) : 0;
        double scale = half / range.get();
        double dot = dotSize.get() * base;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;

            double dx = player.getX() - mc.player.getX();
            double dz = player.getZ() - mc.player.getZ();

            double rotatedX = dx * Math.cos(yaw) - dz * Math.sin(yaw);
            double rotatedY = dx * Math.sin(yaw) + dz * Math.cos(yaw);

            double px = centerX + rotatedX * scale;
            double py = centerY + rotatedY * scale;

            if (Math.abs(px - centerX) > half || Math.abs(py - centerY) > half) continue;

            Color color = Friends.get().isFriend(player) ? friendColor.get() : enemyColor.get();
            IslandRenderer.round(px - dot / 2, py - dot / 2, dot, dot, dot / 2, color);
        }

        double self = dot * 1.3;
        IslandRenderer.round(centerX - self / 2, centerY - self / 2, self, self, self / 2, selfColor.get());
    }

    private void drawGrid(HudRenderer renderer, double centerX, double centerY, double half, double base) {
        Color color = gridColor.get();
        if (color.a <= 0) return;

        double scale = half / range.get();
        double step = gridSize.get() * scale;
        if (step < 3) return;

        double yaw = rotate.get() && mc.player != null ? Math.toRadians(mc.player.getYaw()) : 0;
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);

        int cells = (int) Math.ceil(range.get() / gridSize.get()) + 1;
        double cell = 2.5 * base;
        double limit = half - cell;

        for (int gx = -cells; gx <= cells; gx++) {
            for (int gz = -cells; gz <= cells; gz++) {
                double wx = gx * step;
                double wz = gz * step;

                double px = centerX + (wx * cos - wz * sin);
                double py = centerY + (wx * sin + wz * cos);

                if (Math.abs(px - centerX) > limit || Math.abs(py - centerY) > limit) continue;

                IslandRenderer.round(px - cell / 2, py - cell / 2, cell, cell, 0.5, color);
            }
        }
    }
}

