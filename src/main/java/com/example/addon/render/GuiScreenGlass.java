package com.example.addon.render;

import com.example.addon.gui.YuioGuiTheme;
import com.example.addon.modules.BlurSettings;
import com.example.addon.utils.RoundRectRenderer;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.utils.render.color.Color;

public final class GuiScreenGlass {

    private static final Color RIM = new Color(188, 212, 245, 52);
    private static final Color ROW_RIM = new Color(150, 186, 238, 72);
    public static final Color NAVY = new Color(36, 50, 72, 255);
    public static final Color BLUE = new Color(90, 126, 178, 255);
    private static final int WINDOW_ALPHA = 172;
    private static final int HEADER_ALPHA = 210;
    private static final int FLOATING_ALPHA = 214;
    private static final int ROW_ALPHA = 196;

    private GuiScreenGlass() {
    }

    public static boolean enabled() {
        return YuioGuiTheme.isActive() && BlurSettings.guiGlass() && !RoundRectRenderer.isUnavailable();
    }

    public static double radius(WWidget widget) {
        GuiTheme theme = widget == null ? null : widget.theme;
        return theme == null ? 12.0 : Math.max(10.0, theme.scale(10));
    }

    // 必须先 flush 现有批次, 否则后面积攒的 quad 会盖在我们的玻璃上
    public static void flush(GuiRenderer renderer) {
        if (renderer instanceof YuioGuiRenderer flusher) flusher.yuio$flushBatch();
    }

    private static Color mix(Color a, Color b, double factor) {
        double f = Math.max(0.0, Math.min(1.0, factor));
        return new Color(
            (int) Math.round(a.r + (b.r - a.r) * f),
            (int) Math.round(a.g + (b.g - a.g) * f),
            (int) Math.round(a.b + (b.b - a.b) * f),
            255);
    }

    private static double boost(Color original) {
        if (original == null) return 0.0;
        double v = (original.r - 20.0) / 20.0;
        return Math.max(0.0, Math.min(1.0, v));
    }

    public static Color tint(double factor) {
        return mix(NAVY, BLUE, factor);
    }

    public static boolean window(GuiRenderer renderer, double x, double y, double width, double height, double radius) {
        if (!enabled() || width < 6.0 || height < 6.0) return false;

        flush(renderer);
        GuiGlass.panel(x, y, width, height, radius, radius, radius, radius, WINDOW_ALPHA, NAVY);
        RoundRectRenderer.border(x, y, width, height, radius, 1.5, RIM);
        return true;
    }

    public static boolean header(GuiRenderer renderer, double x, double y, double width, double height,
                                 double radius, Color accent, boolean allCorners) {
        if (!enabled() || width < 4.0 || height < 4.0) return false;

        flush(renderer);

        Color tint = mix(BLUE, accent == null ? BLUE : accent, 0.22);
        double bottom = allCorners ? radius : 0;
        GuiGlass.panel(x, y, width, height, radius, radius, bottom, bottom, HEADER_ALPHA, tint);
        RoundRectRenderer.border(x, y, width, height, radius, radius, bottom, bottom, 1.5, RIM);
        return true;
    }

    public static boolean chip(GuiRenderer renderer, double x, double y, double width, double height, double radius) {
        if (!enabled() || width < 4.0 || height < 4.0) return false;

        flush(renderer);
        GuiGlass.panel(x, y, width, height, radius, radius, radius, radius, FLOATING_ALPHA, mix(NAVY, BLUE, 0.3));
        RoundRectRenderer.border(x, y, width, height, radius, 1.0, RIM);
        return true;
    }

    public static boolean floating(GuiRenderer renderer, double x, double y, double width, double height, double radius) {
        if (!enabled() || width < 4.0 || height < 4.0) return false;

        flush(renderer);
        GuiGlass.panel(x, y, width, height, radius, radius, radius, radius, FLOATING_ALPHA, mix(NAVY, BLUE, 0.22));
        RoundRectRenderer.border(x, y, width, height, radius, 1.5, RIM);
        return true;
    }

    public static boolean background(GuiRenderer renderer, WWidget widget, Color original) {
        if (!enabled() || widget == null) return false;

        double width = widget.width;
        double height = widget.height;
        if (width < 8.0 || height < 8.0) return false;

        if (width * height > 0.6 * RoundRectRenderer.uiWidth() * RoundRectRenderer.uiHeight()) return false;

        double r = Math.max(3.0, Math.min(7.0, height * 0.26));
        double b = boost(original);

        flush(renderer);
        GuiGlass.panel(widget.x, widget.y, width, height, r, r, r, r,
            (int) Math.round(168 + 40 * b), mix(NAVY, BLUE, 0.18 + 0.32 * b));
        RoundRectRenderer.border(widget.x, widget.y, width, height, r, 1.0, RIM);
        return true;
    }

    public static boolean row(GuiRenderer renderer, double x, double y, double width, double height, Color original) {
        if (!enabled() || width < 6.0 || height < 6.0) return false;

        double r = Math.max(3.0, Math.min(6.0, height * 0.26));
        flush(renderer);
        GuiGlass.panel(x, y, width, height, r, r, r, r, ROW_ALPHA, mix(NAVY, BLUE, 0.72));
        RoundRectRenderer.border(x, y, width, height, r, 1.0, ROW_RIM);
        return true;
    }

    public static boolean check(GuiRenderer renderer, double x, double y, double width, double height) {
        if (!enabled() || width < 2.0 || height < 2.0) return false;

        double r = Math.max(1.5, Math.min(width, height) * 0.32);
        flush(renderer);
        RoundRectRenderer.fill(x, y, width, height, r, r, r, r, new Color(BLUE.r, BLUE.g, BLUE.b, 255));
        return true;
    }

    public static boolean sliderBar(GuiRenderer renderer, double x, double y, double width, double height, boolean filled) {
        if (!enabled() || width < 1.0 || height < 1.0) return false;

        double r = Math.min(width, height) * 0.5;
        Color color = filled ? new Color(BLUE.r, BLUE.g, BLUE.b, 255) : new Color(54, 70, 98, 240);

        flush(renderer);
        RoundRectRenderer.fill(x, y, width, height, r, r, r, r, color);
        return true;
    }

    public static boolean nav(GuiRenderer renderer, double x, double y, double width, double height, Color original) {
        if (!enabled() || width < 6.0 || height < 6.0) return false;

        double b = boost(original);
        double r = Math.max(4.0, Math.min(height * 0.45, 12.0));

        flush(renderer);
        GuiGlass.panel(x, y, width, height, r, r, r, r,
            (int) Math.round(188 + 46 * b), mix(NAVY, BLUE, 0.35 + 0.5 * b));
        RoundRectRenderer.border(x, y, width, height, r, 1.0, RIM);
        return true;
    }
}
