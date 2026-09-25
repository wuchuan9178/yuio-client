package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TotemParticle extends Module {
    public static TotemParticle INSTANCE;

    public enum Mode { Color, Text }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode").defaultValue(Mode.Color).build());

    public final Setting<SettingColor> particleColor = sgGeneral.add(new ColorSetting.Builder()
        .name("particle-color")
        .defaultValue(new SettingColor(255, 215, 0, 255))
        .visible(() -> mode.get() == Mode.Color)
        .build());

    public final Setting<String> text = sgGeneral.add(new StringSetting.Builder()
        .name("text").defaultValue("EZ")
        .visible(() -> mode.get() == Mode.Text)
        .build());

    public final Setting<Integer> textCount = sgGeneral.add(new IntSetting.Builder()
        .name("text-count").description("How many texts per pop.")
        .defaultValue(1).min(1).max(20)
        .visible(() -> mode.get() == Mode.Text)
        .build());

    public final Setting<SettingColor> textColor = sgGeneral.add(new ColorSetting.Builder()
        .name("text-color")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .visible(() -> mode.get() == Mode.Text)
        .build());

    public static final List<Spot> spots = new ArrayList<>();
    private static final Random rand = new Random();
    private static long lastSpawnMs = 0;

    public static class Spot {
        public double x, y, z;
        public double vx, vy, vz;
        public int life = 40;
        public final int maxLife = 40;

        public Spot(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = (rand.nextDouble() - 0.5) * 0.05;
            this.vy = 0.05 + rand.nextDouble() * 0.05;
            this.vz = (rand.nextDouble() - 0.5) * 0.05;
        }
    }

    public static void addSpot(double x, double y, double z) {
        long now = System.currentTimeMillis();
        if (now - lastSpawnMs < 250 && !spots.isEmpty()) return;
        lastSpawnMs = now;

        int n = INSTANCE != null ? INSTANCE.textCount.get() : 1;
        for (int i = 0; i < n; i++) {
            double ox = (rand.nextDouble() - 0.5) * 1.5;
            double oy = (rand.nextDouble() - 0.5) * 0.5;
            double oz = (rand.nextDouble() - 0.5) * 1.5;
            spots.add(new Spot(x + ox, y + 0.5 + oy, z + oz));
        }
    }

    public TotemParticle() {
        super(AddonTemplate.OTHER, "totem-particle", "Modify pop particles");
        INSTANCE = this;
    }

    @Override
    public void onDeactivate() {
        spots.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (spots.isEmpty()) return;
        for (Spot sp : spots) {
            sp.x += sp.vx;
            sp.y += sp.vy;
            sp.z += sp.vz;
            sp.vy -= 0.002;
            sp.life--;
        }
        spots.removeIf(s -> s.life <= 0);
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mode.get() != Mode.Text) return;
        if (spots.isEmpty()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        var cam = mc.gameRenderer.getCamera();
        var camPos = cam.getCameraPos();

        var c = textColor.get();
        String s = text.get();
        int textWidth = mc.textRenderer.getWidth(s);
        int textHeight = mc.textRenderer.fontHeight;

        Matrix4f proj = mc.gameRenderer.getBasicProjectionMatrix(mc.options.getFov().getValue());
        Quaternionf invRot = new Quaternionf(cam.getRotation()).conjugate();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        for (Spot sp : spots) {
            Vector3f v = new Vector3f(
                (float)(sp.x - camPos.x),
                (float)(sp.y - camPos.y),
                (float)(sp.z - camPos.z)
            );
            invRot.transform(v);

            Vector4f vp = new Vector4f(v.x, v.y, v.z, 1f);
            proj.transform(vp);
            if (vp.w <= 0) continue;

            float ndcX = vp.x / vp.w;
            float ndcY = vp.y / vp.w;
            int sx = (int)((ndcX * 0.5f + 0.5f) * sw);
            int sy = (int)((1 - (ndcY * 0.5f + 0.5f)) * sh);

            int alpha = (int)(255f * sp.life / sp.maxLife);
            int color = (alpha << 24) | (c.r << 16) | (c.g << 8) | c.b;

            event.drawContext.drawText(
                mc.textRenderer, s,
                sx - textWidth / 2,
                sy - textHeight / 2,
                color, false
            );
        }
    }
}
