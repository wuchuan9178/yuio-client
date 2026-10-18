package com.example.addon.render;

import com.example.addon.modules.UiBlur;
import com.example.addon.utils.BlurRenderer;
import com.example.addon.utils.RoundRectRenderer;
import net.minecraft.client.MinecraftClient;

public final class ScoreboardPanel {
    private static boolean headerSeen;
    private static double headerTop;

    private ScoreboardPanel() {
    }

    public static void header(int y1) {
        headerSeen = true;
        headerTop = y1;
    }

    public static void body(UiBlur module, int x1, int y1, int x2, int y2) {
        double top = headerSeen ? headerTop : y1;
        headerSeen = false;

        if (!module.scoreboard.get()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        double padding = module.scoreboardPadding.get();
        double screenWidth = mc.getWindow().getScaledWidth();
        double screenHeight = mc.getWindow().getScaledHeight();
        double left = Math.max(0.0, x1 - padding);
        double right = Math.min(screenWidth, x2 + padding);
        double upper = Math.max(0.0, top - padding);
        double lower = Math.min(screenHeight, y2 + padding);
        if (right - left < 2.0 || lower - upper < 2.0) return;

        BlurRenderer.request();

        RoundRectRenderer.beginFrame();
        GuiGlass.panel(left, upper, right - left, lower - upper, module.radius.get(),
            module.scoreboardAlpha.get());
    }
}
