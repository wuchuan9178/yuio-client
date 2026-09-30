package com.example.addon.hud;

import com.example.addon.utils.BlurRenderer;
import com.example.addon.utils.RoundRectRenderer;
import meteordevelopment.meteorclient.renderer.text.VanillaTextRenderer;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.item.ItemStack;

public final class IslandRenderer {
    public static final Color GLASS_TINT = new Color(18, 20, 26, 255);

    private IslandRenderer() {
    }

    public static void beginFrame() {
        RoundRectRenderer.beginFrame();
    }

    public static void glass(double x, double y, double width, double height, double radius,
                             int blurStrength, int opacity, int tintStrength,
                             boolean border, boolean highlight) {
        if (width <= 0 || height <= 0) return;

        Color tint = new Color(GLASS_TINT.r, GLASS_TINT.g, GLASS_TINT.b, tintStrength);
        Color fallback = new Color(GLASS_TINT.r, GLASS_TINT.g, GLASS_TINT.b, opacity);

        if (blurStrength > 0) {
            BlurRenderer.drawOrFill(x, y, width, height, radius, tint, opacity, fallback);
        } else {
            RoundRectRenderer.fill(x, y, width, height, radius, fallback);
        }

        if (highlight && height > 10 && radius > 3) {
            double inset = radius * 1.2;
            double lineWidth = width - inset * 2;
            if (lineWidth > 4) {
                RoundRectRenderer.fill(x + inset, y + 1, lineWidth, 1, 0.5, new Color(255, 255, 255, 40));
            }
        }

        if (border && radius > 0) {
            RoundRectRenderer.border(x, y, width, height, radius, 1, new Color(255, 255, 255, 42));
        }
    }

    public static void round(double x, double y, double width, double height, double radius, Color color) {
        RoundRectRenderer.fill(x, y, width, height, radius, color);
    }

    public static void round(double x, double y, double width, double height,
                             double topLeft, double topRight, double bottomRight, double bottomLeft,
                             Color color) {
        RoundRectRenderer.fill(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, color);
    }

    public static void gradient(double x, double y, double width, double height,
                                double radius, Color top, Color bottom) {
        RoundRectRenderer.gradient(x, y, width, height, radius, top, bottom);
    }

    public static void border(double x, double y, double width, double height,
                              double radius, double thickness, Color color) {
        RoundRectRenderer.border(x, y, width, height, radius, thickness, color);
    }

    public static void rim(double x, double y, double width, double height,
                           double radius, double thickness, Color color) {
        RoundRectRenderer.rim(x, y, width, height, radius, thickness, color);
    }

    public static void shadow(double x, double y, double width, double height, double radius, double strength) {
        int alpha = (int) (70 * strength);
        if (alpha <= 0) return;
        RoundRectRenderer.glow(x, y, width, height, radius, 7.0, new Color(0, 0, 0, alpha));
    }

    public static void innerGlow(double x, double y, double width, double height,
                                 double radius, Color glow, double thickness) {
        RoundRectRenderer.rim(x, y, width, height, radius, thickness, glow);
    }

    private static boolean vanilla = true;

    public static void setVanillaFont(boolean value) {
        vanilla = value;
    }

    private static boolean needsVanilla(String text) {
        if (vanilla) return true;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) >= 0x2E80) return true;
        }
        return false;
    }

    public static double text(HudRenderer renderer, String text, double x, double y, Color color, boolean shadow) {
        return text(renderer, text, x, y, color, shadow, 1.0);
    }

    public static double text(HudRenderer renderer, String text, double x, double y,
                              Color color, boolean shadow, double scale) {
        if (text.isEmpty()) return x;

        if (needsVanilla(text)) {
            VanillaTextRenderer.INSTANCE.scaleIndividually = true;
            VanillaTextRenderer.INSTANCE.scale = scale;
            return VanillaTextRenderer.INSTANCE.render(text, x, y, color, shadow);
        }

        return renderer.text(text, x, y, color, shadow, scale);
    }

    public static double textWidth(HudRenderer renderer, String text, double scale) {
        if (text.isEmpty()) return 0;

        if (needsVanilla(text)) {
            VanillaTextRenderer.INSTANCE.scaleIndividually = true;
            VanillaTextRenderer.INSTANCE.scale = scale;
            return VanillaTextRenderer.INSTANCE.getWidth(text, true);
        }

        return renderer.textWidth(text, true, scale);
    }

    public static double textHeight(HudRenderer renderer, double scale) {
        if (vanilla) {
            VanillaTextRenderer.INSTANCE.scaleIndividually = true;
            VanillaTextRenderer.INSTANCE.scale = scale;
            return VanillaTextRenderer.INSTANCE.getHeight(true);
        }

        return renderer.textHeight(true, scale);
    }

    public static void item(HudRenderer renderer, ItemStack stack, double x, double y, double scale) {
        renderer.item(stack, (int) x, (int) y, (float) scale, true);
    }

    public static Color lerp(Color from, Color to, double t) {
        double clamped = Math.max(0, Math.min(1, t));
        return new Color(
            (int) (from.r + (to.r - from.r) * clamped),
            (int) (from.g + (to.g - from.g) * clamped),
            (int) (from.b + (to.b - from.b) * clamped),
            (int) (from.a + (to.a - from.a) * clamped)
        );
    }

    public static Color alpha(Color color, int alpha) {
        return new Color(color.r, color.g, color.b, alpha);
    }
}

