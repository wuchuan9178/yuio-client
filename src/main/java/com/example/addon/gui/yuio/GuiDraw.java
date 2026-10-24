package com.example.addon.gui.yuio;

import com.example.addon.render.GuiGlass;
import com.example.addon.render.GuiIcons;
import com.example.addon.utils.RoundRectRenderer;
import com.mojang.blaze3d.textures.GpuTextureView;
import meteordevelopment.meteorclient.renderer.Fonts;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

// 带裁剪的绘制工具: 形状走立即绘制, 文字用 meteor 字体
public final class GuiDraw {
    private static final int NO_CLIP = -100000;
    private static boolean meteorFontOk = true;
    private static double fontK = -1;

    public static double textScale = 1.0;

    public final DrawContext ctx;
    private final double x0;
    private final double y0;
    private final double x1;
    private final double y1;

    public GuiDraw(DrawContext ctx) {
        this(ctx, NO_CLIP, NO_CLIP, NO_CLIP + 1, NO_CLIP + 1);
    }

    private GuiDraw(DrawContext ctx, double x0, double y0, double x1, double y1) {
        this.ctx = ctx;
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
    }

    public GuiDraw clip(double x, double y, double w, double h) {
        if (x0 == NO_CLIP) return new GuiDraw(ctx, x, y, x + w, y + h);
        return new GuiDraw(ctx, Math.max(x0, x), Math.max(y0, y), Math.min(x1, x + w), Math.min(y1, y + h));
    }

    public boolean empty() {
        return x1 <= x0 || y1 <= y0;
    }

    public boolean intersects(double x, double y, double w, double h) {
        if (x0 == NO_CLIP) return true;
        return x + w > x0 && x < x1 && y + h > y0 && y < y1;
    }

    public double width() {
        return x1 - x0;
    }

    public double height() {
        return y1 - y0;
    }

    // ---------- 形状 ----------

    public void fill(double x, double y, double w, double h, double radius, Color color) {
        if (color.a <= 0 || !intersects(x, y, w, h)) return;

        if (x0 == NO_CLIP) {
            RoundRectRenderer.fill(x, y, w, h, radius, color);
            return;
        }

        Box b = clipBox(x, y, w, h);
        if (b == null) return;

        double tl = radius, tr = radius, br = radius, bl = radius;
        if (y < y0) { tl = 0; tr = 0; }
        if (y + h > y1) { br = 0; bl = 0; }
        if (x < x0) { tl = 0; bl = 0; }
        if (x + w > x1) { tr = 0; br = 0; }

        RoundRectRenderer.fill(b.x, b.y, b.w, b.h, tl, tr, br, bl, color);
    }

    public void fillGrad(double x, double y, double w, double h, double radius, Color top, Color bottom) {
        if (top.a <= 0 || bottom.a <= 0 || !intersects(x, y, w, h)) return;
        if (x0 != NO_CLIP) {
            fill(x, y, w, h, radius, bottom);
            return;
        }
        RoundRectRenderer.gradient(x, y, w, h, radius, top, bottom);
    }

    public void border(double x, double y, double w, double h, double radius, double thickness, Color color) {
        if (color.a <= 0 || !intersects(x, y, w, h)) return;
        if (x0 == NO_CLIP) {
            RoundRectRenderer.border(x, y, w, h, radius, thickness, color);
            return;
        }

        double t = Math.max(0.5, thickness);
        fill(x, y, w, t, 0, color);
        fill(x, y + h - t, w, t, 0, color);
        fill(x, y + t, t, h - t * 2, 0, color);
        fill(x + w - t, y + t, t, h - t * 2, 0, color);
    }

    public void glow(double x, double y, double w, double h, double radius, double spread, Color color) {
        if (color.a <= 0 || !intersects(x - spread, y - spread, w + spread * 2, h + spread * 2)) return;
        RoundRectRenderer.glow(x, y, w, h, radius, spread, color);
    }

    public void glass(double x, double y, double w, double h, double radius, int alpha) {
        if (alpha <= 0 || !intersects(x, y, w, h)) return;
        if (x0 != NO_CLIP) {
            fill(x, y, w, h, radius, new Color(16, 18, 24, alpha));
            return;
        }
        GuiGlass.panel(x, y, w, h, radius, alpha);
    }

    public void icon(String name, double x, double y, double size, Color tint) {
        if (tint.a <= 0 || !intersects(x, y, size, size)) return;
        GpuTextureView view = GuiIcons.get(name);
        if (view == null) return;
        // 纹理原点是左下, 这里翻转 v
        RoundRectRenderer.textured(x, y, size, size, 0, 0, 0, 0, view, 0, 1, 1, 0, tint, tint.a);
    }

    // ---------- 文字 ----------

    public void text(String s, double x, double y, Color color, boolean shadow) {
        if (color.a <= 0 || s.isEmpty()) return;
        if (x0 != NO_CLIP && (y + 12 < y0 || y > y1)) return;
        if (meteorText(s, x, y, color, shadow)) return;

        ctx.drawText(meteordevelopment.meteorclient.MeteorClient.mc.textRenderer, s,
            (int) Math.round(x), (int) Math.round(y), color.getPacked(), shadow);
    }

    public void text(Text t, double x, double y, Color color, boolean shadow) {
        if (color.a <= 0 || t.getString().isEmpty()) return;
        if (x0 != NO_CLIP && (y + 12 < y0 || y > y1)) return;
        if (meteorText(t.getString(), x, y, color, shadow)) return;

        ctx.drawText(meteordevelopment.meteorclient.MeteorClient.mc.textRenderer, t,
            (int) Math.round(x), (int) Math.round(y), color.getPacked(), shadow);
    }

    public void textBold(String s, double x, double y, Color color) {
        text(Text.literal(s).formatted(Formatting.BOLD), x, y, color, false);
    }

    public void textTrim(String s, double x, double y, double maxWidth, Color color, boolean shadow) {
        if (maxWidth <= 2) return;
        text(trim(s, (int) maxWidth), x, y, color, shadow);
    }

    public void textRight(String s, double right, double y, Color color, boolean shadow) {
        text(s, right - textWidth(s), y, color, shadow);
    }

    public void textCentered(String s, double centerX, double y, Color color, boolean shadow) {
        text(s, centerX - textWidth(s) * 0.5, y, color, shadow);
    }

    private static boolean meteorText(String s, double x, double y, Color color, boolean shadow) {
        if (!meteorFontOk || Fonts.RENDERER == null) return false;

        double fb = fbScale();
        double k = fontK();
        if (k <= 0) return false;

        try {
            Utils.unscaledProjection();
            Fonts.RENDERER.begin(k * fb * textScale, shadow, false);
            Fonts.RENDERER.render(s, x * fb, y * fb, color, shadow);
            Fonts.RENDERER.end();
            Utils.scaledProjection();
            return true;
        } catch (Throwable t) {
            meteorFontOk = false;
            try {
                if (Fonts.RENDERER.isBuilding()) Fonts.RENDERER.end();
            } catch (Throwable ignored) {
            }
            try {
                Utils.scaledProjection();
            } catch (Throwable ignored) {
            }
            return false;
        }
    }

    private static double fbScale() {
        return meteordevelopment.meteorclient.MeteorClient.mc.getWindow().getScaleFactor();
    }

    // meteor 字体按像素设计, 这里换算成和原版字体同高, 并保持宽度同尺度
    private static double fontK() {
        if (fontK >= 0) return fontK;
        try {
            double base = Fonts.RENDERER.getHeight(false);
            double target = meteordevelopment.meteorclient.MeteorClient.mc.textRenderer.fontHeight;
            fontK = base > 0 ? target / base : 1.0;
        } catch (Throwable t) {
            meteorFontOk = false;
            fontK = -1;
        }
        return fontK;
    }

    public double textWidth(String s) {
        if (meteorFontOk && Fonts.RENDERER != null) {
            try {
                double k = fontK();
                if (k > 0) return Fonts.RENDERER.getWidth(s) * k * textScale;
            } catch (Throwable t) {
                meteorFontOk = false;
            }
        }
        return meteordevelopment.meteorclient.MeteorClient.mc.textRenderer.getWidth(s);
    }

    public double textHeight() {
        return meteordevelopment.meteorclient.MeteorClient.mc.textRenderer.fontHeight * textScale;
    }

    public String trim(String s, int maxWidth) {
        if (s.isEmpty() || textWidth(s) <= maxWidth) return s;

        double width = 0;
        int i = 0;
        for (; i < s.length(); i++) {
            double charWidth = textWidth(String.valueOf(s.charAt(i)));
            if (width + charWidth > maxWidth - 4) break;
            width += charWidth;
        }
        return s.substring(0, Math.max(0, i)) + "..";
    }

    // ---------- 内部 ----------

    private record Box(double x, double y, double w, double h) {
    }

    private Box clipBox(double x, double y, double w, double h) {
        double nx = Math.max(x, x0);
        double ny = Math.max(y, y0);
        double nx1 = Math.min(x + w, x1);
        double ny1 = Math.min(y + h, y1);
        if (nx1 - nx <= 0.5 || ny1 - ny <= 0.5) return null;
        return new Box(nx, ny, nx1 - nx, ny1 - ny);
    }
}
