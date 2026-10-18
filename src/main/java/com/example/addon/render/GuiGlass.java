package com.example.addon.render;

import com.example.addon.modules.BlurSettings;
import com.example.addon.utils.BlurRenderer;
import com.example.addon.utils.RoundRectRenderer;
import com.mojang.blaze3d.textures.GpuTextureView;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.MinecraftClient;

public final class GuiGlass {
    public static final Color TINT = new Color(16, 18, 24, 255);

    private GuiGlass() {
    }

    public static void panel(double x, double y, double width, double height, double radius, int alpha) {
        panel(x, y, width, height, radius, radius, radius, radius, alpha, null);
    }

    public static void panel(double x, double y, double width, double height,
                             double topLeft, double topRight, double bottomRight, double bottomLeft,
                             int alpha, Color tintColor) {
        if (width < 1.0 || height < 1.0 || alpha <= 0) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        int strength = BlurSettings.strength();
        Color base = tintColor == null ? TINT : tintColor;
        Color tint = new Color(base.r, base.g, base.b, BlurSettings.tint());

        GpuTextureView texture = strength > 0 && BlurRenderer.isReady() ? BlurRenderer.texture() : null;

        if (texture != null) {

            float spaceWidth = Math.max(1.0f, RoundRectRenderer.uiWidth());
            float spaceHeight = Math.max(1.0f, RoundRectRenderer.uiHeight());

            RoundRectRenderer.glass(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, texture,
                (float) (x / spaceWidth), (float) (1.0 - y / spaceHeight),
                (float) ((x + width) / spaceWidth), (float) (1.0 - (y + height) / spaceHeight),
                tint, alpha, 0.0, false);
            return;
        }

        RoundRectRenderer.fill(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft,
            new Color(base.r, base.g, base.b, alpha));
    }
}
