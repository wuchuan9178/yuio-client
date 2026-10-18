package com.example.addon.render;

/**
 * 界面切换时的淡入遮罩, 让 Tab/主题切换不再硬切。
 */
public final class ScreenTransition {
    private static final long DURATION = 260_000_000L;

    private static Object lastScreen;
    private static long startedAt;

    private ScreenTransition() {}

    public static void startIfNew(Object screen) {
        if (screen == lastScreen) return;

        lastScreen = screen;
        startedAt = System.nanoTime();
    }

    public static float alpha() {
        long elapsed = System.nanoTime() - startedAt;
        if (elapsed >= DURATION) return 0f;

        float t = 1f - (float) elapsed / DURATION;
        return t * t;
    }
}
