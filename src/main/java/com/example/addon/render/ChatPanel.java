package com.example.addon.render;

import com.example.addon.modules.UiBlur;
import com.example.addon.utils.RoundRectRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

public final class ChatPanel {
    private static DrawContext context;
    private static boolean pending;
    private static double minX;
    private static double minY;
    private static double maxX;
    private static double maxY;
    private static int alpha;

    private ChatPanel() {
    }

    public static void reset(DrawContext drawContext) {
        context = drawContext;
        pending = false;
        minX = Double.MAX_VALUE;
        minY = Double.MAX_VALUE;
        maxX = -Double.MAX_VALUE;
        maxY = -Double.MAX_VALUE;
        alpha = 0;
    }

    public static boolean acceptFill(int x1, int y1, int x2, int y2, int color) {
        UiBlur active = UiBlur.active();
        if (active == null || !active.chat.get()) return false;

        int a = (color >>> 24) & 255;
        if (x2 <= x1 || y2 <= y1) return false;

        if (y2 - y1 < 6 || x2 - x1 < 12) return false;

        pending = true;
        minX = Math.min(minX, x1);
        minY = Math.min(minY, y1);
        maxX = Math.max(maxX, x2);
        maxY = Math.max(maxY, y2);
        alpha = Math.max(alpha, a);
        return true;
    }

    public static void flush() {
        if (!pending) return;
        pending = false;

        UiBlur module = UiBlur.active();
        if (module == null || !module.chat.get() || context == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        Matrix3x2f matrix = new Matrix3x2f(context.getMatrices());
        Vector2f from = new Vector2f();
        Vector2f to = new Vector2f();
        matrix.transformPosition((float) minX, (float) minY, from);
        matrix.transformPosition((float) maxX, (float) maxY, to);

        double padding = module.chatPadding.get();
        double screenWidth = mc.getWindow().getScaledWidth();
        double screenHeight = mc.getWindow().getScaledHeight();
        double x1 = Math.max(0.0, Math.min(from.x, to.x) - padding);
        double y1 = Math.max(0.0, Math.min(from.y, to.y) - padding);
        double x2 = Math.min(screenWidth, Math.max(from.x, to.x) + padding);
        double y2 = Math.min(screenHeight, Math.max(from.y, to.y) + padding);
        if (x2 - x1 < 2.0 || y2 - y1 < 2.0) return;

        double fade = alpha <= 0 ? 1.0 : Math.max(0.5, Math.min(1.0, alpha / 127.0));
        int panelAlpha = (int) Math.round(module.chatAlpha.get() * fade);

        RoundRectRenderer.beginFrame();
        GuiGlass.panel(x1, y1, x2 - x1, y2 - y1, module.radius.get(), panelAlpha);
    }
}
