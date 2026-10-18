package com.example.addon.utils;

import com.example.addon.AddonTemplate;
import com.example.addon.modules.BlurSettings;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.MinecraftClient;

public final class BlurRenderer {
    // 每帧一份的整屏副本, 玻璃面板采样它
    private static final BlurFbo BACKDROP = new BlurFbo("yuio - blur backdrop");
    private static final BlurFbo PING = new BlurFbo("yuio - blur ping");
    private static final BlurFbo PONG = new BlurFbo("yuio - blur pong");
    public static final int MAX_STRENGTH = 500;
    private static final float RADIUS_PER_STRENGTH = 3.0F;
    private static final float MAX_PASS_RADIUS = 8.0F;
    private static final int MAX_PASSES = 8;
    private static int builtWidth = -1;
    private static int builtHeight = -1;
    private static boolean ready;
    private static boolean captured;
    private static boolean requested;
    private static float blurRadius;
    private static GpuTextureView blurred;
    private static String loggedError;

    private BlurRenderer() {
    }

    public static void request() {
        if (BlurSettings.strength() > 0) requested = true;
    }

    // HUD 最开头调用, 此时帧缓冲里只有世界, 抓到的才是干净背景
    public static void captureBackdrop() {
        if (!requested) return;
        requested = false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.getFramebuffer() == null) return;

        try {
            init();
            if (!ready || !BACKDROP.isReady()) return;
            capture(BlurSettings.strength());
            captured = true;
            loggedError = null;
        } catch (Throwable t) {
            String message = t.toString();
            if (!message.equals(loggedError)) {
                loggedError = message;
                AddonTemplate.LOG.error("[yuio] blur capture failed: {}", message);
            }
        }
    }

    public static void init() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        int width = mc.getWindow().getFramebufferWidth();
        int height = mc.getWindow().getFramebufferHeight();
        if (width <= 0 || height <= 0) return;
        if (ready && width == builtWidth && height == builtHeight) return;

        BACKDROP.resize(width, height);

        PING.resize(Math.max(1, width / 2), Math.max(1, height / 2));
        PONG.resize(Math.max(1, width / 2), Math.max(1, height / 2));

        builtWidth = width;
        builtHeight = height;
        ready = BACKDROP.isReady() && PING.isReady() && PONG.isReady();
        if (!ready) blurred = null;
    }

    // 把主帧缓冲 1:1 拷进副本, 只发一条命令, 不开 render pass
    private static void capture(int strength) {
        MinecraftClient mc = MinecraftClient.getInstance();
        GpuTexture source = mc.getFramebuffer().getColorAttachment();
        if (source == null) return;

        int width = Math.min(BACKDROP.width(), source.getWidth(0));
        int height = Math.min(BACKDROP.height(), source.getHeight(0));
        if (width <= 0 || height <= 0) return;

        RenderSystem.getDevice().createCommandEncoder()
            .copyTextureToTexture(source, BACKDROP.texture(), 0, 0, 0, 0, 0, width, height);

        blurRadius = Math.max(0, Math.min(MAX_STRENGTH, strength)) * RADIUS_PER_STRENGTH;

        runBlur();
    }

    private static void runBlur() {
        if (!BACKDROP.isReady() || !PING.isReady() || !PONG.isReady()) {
            blurred = BACKDROP.isReady() ? BACKDROP.view() : null;
            return;
        }

        if (blurRadius < 1.0F) {
            blurred = BACKDROP.view();
            return;
        }

        int passes = Math.max(1, Math.min(MAX_PASSES, (int) Math.ceil(blurRadius * 0.5F / MAX_PASS_RADIUS)));
        float perPass = blurRadius * 0.5F / passes;

        int fullWidth = BACKDROP.width();
        int fullHeight = BACKDROP.height();
        int halfWidth = Math.max(1, fullWidth / 2);
        int halfHeight = Math.max(1, fullHeight / 2);

        GpuTextureView source = BACKDROP.view();
        int sourceWidth = fullWidth;
        int sourceHeight = fullHeight;

        for (int i = 0; i < passes; i++) {

            float radiusU = perPass * ((float) sourceWidth / halfWidth);
            float radiusV = perPass * ((float) sourceHeight / halfHeight);

            RoundRectRenderer.blurPass(source, PING.view(), radiusU, true, sourceWidth, sourceHeight);
            RoundRectRenderer.blurPass(PING.view(), PONG.view(), radiusV, false, halfWidth, halfHeight);

            source = PONG.view();
            sourceWidth = halfWidth;
            sourceHeight = halfHeight;
        }

        blurred = source;
    }

    public static int width() {
        return builtWidth;
    }

    public static int height() {
        return builtHeight;
    }

    public static boolean isReady() {
        return ready && captured && blurred != null && !blurred.isClosed() && BlurSettings.strength() > 0;
    }

    public static void close() {
        BACKDROP.close();
        PING.close();
        PONG.close();
        blurred = null;
        ready = false;
        captured = false;
        builtWidth = -1;
        builtHeight = -1;
    }

    public static GpuTextureView texture() {
        return blurred != null && !blurred.isClosed() ? blurred : null;
    }

    // 面板坐标 -> 纹理 U, 不要乘 GUI scale
    public static float u(double screenX) {
        float width = RoundRectRenderer.uiWidth();
        if (width <= 0.0F) return 0.0F;
        return (float) (screenX / width);
    }

    // 面板坐标 -> 纹理 V, 帧缓冲纹理的 v=1 在屏幕上方
    public static float v(double screenY) {
        float height = RoundRectRenderer.uiHeight();
        if (height <= 0.0F) return 0.0F;
        return (float) (1.0 - screenY / height);
    }

    public static int framebufferWidth() {
        if (builtWidth > 0) return builtWidth;
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc == null || mc.getWindow() == null ? 0 : mc.getWindow().getFramebufferWidth();
    }

    public static int framebufferHeight() {
        if (builtHeight > 0) return builtHeight;
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc == null || mc.getWindow() == null ? 0 : mc.getWindow().getFramebufferHeight();
    }

    public static void draw(double x, double y, double width, double height, double radius, Color tint, int opacity) {
        GpuTextureView texture = texture();
        if (texture == null) return;

        RoundRectRenderer.glass(
            x, y, width, height,
            radius, radius, radius, radius,
            texture,
            u(x), v(y), u(x + width), v(y + height),
            new Color(tint.r, tint.g, tint.b, Math.max(0, Math.min(255, tint.a))),
            panelAlpha(opacity),

            0.0,
            false
        );
    }

    public static void drawOrFill(double x, double y, double width, double height, double radius,
                                  Color tint, int opacity, Color fallback) {
        if (isReady()) draw(x, y, width, height, radius, tint, opacity);
        else RoundRectRenderer.fill(x, y, width, height, radius, fallback);
    }

    // 面板 alpha -> 真模糊生效时的最终不透明度, 太低会露出清晰背景
    private static int panelAlpha(int opacity) {
        double t = Math.max(0.0, Math.min(1.0, opacity / 255.0));
        return (int) Math.round(255.0 * (0.35 + 0.55 * t));
    }
}
