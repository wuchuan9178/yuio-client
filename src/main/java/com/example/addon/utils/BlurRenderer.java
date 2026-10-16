package com.example.addon.utils;

import com.example.addon.AddonTemplate;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import meteordevelopment.meteorclient.mixininterface.IGameRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.MinecraftClient;

import java.nio.ByteBuffer;

/**
 * 背景模糊 v2 —— "拷贝 + 着色器内模糊"。
 *
 * <p>v1 是一条 6 级降采样/升采样链, 每一级都要用 {@code MeshRenderer} 往自己的 FBO 里画一个全屏四边形。
 * 实测结果是那些 pass <b>一个字节都没写进去</b>: GPU 回读整条链 (含 Meteor 自己的 BLUR_PASSTHROUGH
 * 管线) 全是 {@code (0,0,0) a=0}, 而同时对同一个纹理做一次 {@code clearColorTexture} 却能写进去
 * —— 也就是"往自建 FBO 里画 pass"在这套环境/时机下不生效, 却又不报任何错。
 * 与其继续猜, 不如把这条依赖整个砍掉:
 *
 * <ol>
 *   <li>每帧在 {@code InGameHud.render} 的 HEAD 用 {@code CommandEncoder.copyTextureToTexture}
 *       把主帧缓冲 1:1 拷进一张自己的纹理(与探针里已经验证可用的 {@code copyTextureToBuffer} 同类命令);</li>
 *   <li>玻璃面板照旧走 ROUNDED 管线画在主帧缓冲上, 但 {@code MODE_GLASS} 分支改成对这张副本做
 *      24 次采样的圆盘模糊, 半径 = {@code blur-strength} x {@link #RADIUS_PER_STRENGTH} 帧缓冲像素。</li>
 * </ol>
 *
 * <p>新方案里用到的每一个操作都在这个客户端上被实际验证过能用: 抓屏副本(命令拷贝)、
 * 把纹理当 sampler 采样(MODE_TEXTURED 的头像/图标一直在用)、ROUNDED 管线画在主帧缓冲上(面板本身)。
 */
public final class BlurRenderer {
    /** 每帧一份的整屏副本, 玻璃面板采样它。 */
    private static final BlurFbo BACKDROP = new BlurFbo("yuio - blur backdrop");

    /**
     * 模糊强度 -> 着色器里的模糊半径(帧缓冲像素)。
     * 这是玩家唯一需要理解的旋钮: blur-strength 每 1 点 = 3 像素半径, 默认 12 => 36px,
     * 上限 30 => 90px。太小(18px 以下)在 1080p 下几乎看不出来。
     */
    public static final int MAX_STRENGTH = 30;

    private static final float RADIUS_PER_STRENGTH = 3.0F;

    private static int builtWidth = -1;
    private static int builtHeight = -1;
    private static boolean ready;

    private static boolean requested;
    private static int requestedStrength = 12;
    private static int capturedStrength = -1;
    private static float blurRadius;
    private static boolean capturedThisFrame;

    private static long frames;
    private static long captures;
    private static long skipped;
    private static long lastCaptureNanos = -1L;
    private static long lastCaptureFrame = -1L;
    private static String lastError;
    private static String lastErrorStage;
    private static String loggedError;

    // TODO(临时诊断, 定位完成后删除): 关键状态写进 latest.log, 不依赖游戏内调试模块。
    private static boolean loggedHook;
    private static boolean loggedFirstCapture;
    private static boolean loggedFirstSkip;
    private static boolean loggedPanelState;
    private static boolean lastPanelUsedBlur;

    public static boolean debugDisable;
    public static boolean debugFreeze;
    public static boolean debugForceFallback;
    public static boolean debugFlipV;
    public static boolean debugFlushGuiState;
    public static boolean debugNearest;
    public static int debugStrength = -1;
    public static int debugOpacity = -1;
    public static int debugTint = -1;

    private BlurRenderer() {
    }

    /** 面板每帧调用: 只登记"这一帧需要模糊"和强度, 真正的抓屏由下一帧开头的 captureBackdrop() 做。 */
    public static void request(int strength) {
        requestedStrength = Math.max(0, Math.min(MAX_STRENGTH, strength));
        if (requestedStrength > 0) requested = true;
    }

    /**
     * 由 {@code com.example.addon.mixin.InGameHudMixin} 在 {@code InGameHud.render} 的 HEAD 调用。
     * 此时帧缓冲里只有世界, 拷到的才是干净的背景(不含原版 HUD, 也不含我们自己的面板)。
     */
    public static void captureBackdrop() {
        frames++;
        capturedThisFrame = false;

        if (!loggedHook) {
            loggedHook = true;
            AddonTemplate.LOG.info("[yuio] blur: capture hook active (InGameHud HEAD, copy mode)");
        }

        if (!requested) return;
        requested = false;

        if (debugDisable || debugFreeze || debugForceFallback) {
            skipped++;
            if (!loggedFirstSkip) {
                loggedFirstSkip = true;
                AddonTemplate.LOG.info("[yuio] blur: skip #1 flags disable={} freeze={} forceFallback={}",
                    debugDisable, debugFreeze, debugForceFallback);
            }
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.getFramebuffer() == null) {
            skipped++;
            if (!loggedFirstSkip) {
                loggedFirstSkip = true;
                AddonTemplate.LOG.info("[yuio] blur: skip #1 no world/framebuffer mc={} world={} fb={}",
                    mc != null, mc != null && mc.world != null, mc != null && mc.getFramebuffer() != null);
            }
            return;
        }

        try {
            init();
            if (!ready || !BACKDROP.isReady()) {
                fail("backdrop", null);
                return;
            }

            int strength = debugStrength >= 0 ? debugStrength : requestedStrength;
            long start = System.nanoTime();
            capture(strength);
            lastCaptureNanos = System.nanoTime() - start;
            lastCaptureFrame = frames;
            capturedStrength = strength;
            capturedThisFrame = true;
            captures++;
            lastError = null;
            lastErrorStage = null;

            if (!loggedFirstCapture) {
                loggedFirstCapture = true;
                AddonTemplate.LOG.info("[yuio] blur: first copy ok frames={} backdrop={}x{} cost={}ms strength={} radius={}px",
                    frames, builtWidth, builtHeight,
                    String.format("%.2f", lastCaptureNanos / 1.0E6), strength, blurRadius);
            }

            probeOnce();

            if (frames % 600 == 0) {
                AddonTemplate.LOG.info("[yuio] blur status: {}", status());
            }
        } catch (Throwable t) {
            fail("capture", t);
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

        builtWidth = width;
        builtHeight = height;
        ready = BACKDROP.isReady();
    }

    /** 把主帧缓冲 1:1 拷进副本, 只发一条命令, 不开任何 render pass。 */
    private static void capture(int strength) {
        MinecraftClient mc = MinecraftClient.getInstance();
        GpuTexture source = mc.getFramebuffer().getColorAttachment();
        if (source == null) {
            fail("framebuffer", null);
            return;
        }

        int width = Math.min(BACKDROP.width(), source.getWidth(0));
        int height = Math.min(BACKDROP.height(), source.getHeight(0));
        if (width <= 0 || height <= 0) {
            fail("backdrop size", null);
            return;
        }

        RenderSystem.getDevice().createCommandEncoder()
            .copyTextureToTexture(source, BACKDROP.texture(), 0, 0, 0, 0, 0, width, height);

        blurRadius = Math.max(0, Math.min(MAX_STRENGTH, strength)) * RADIUS_PER_STRENGTH;
    }

    public static int width() {
        return builtWidth;
    }

    public static int height() {
        return builtHeight;
    }

    public static boolean isReady() {
        return ready && captures > 0 && !debugForceFallback && BACKDROP.isReady();
    }

    public static void close() {
        BACKDROP.close();
        ready = false;
        builtWidth = -1;
        builtHeight = -1;
    }

    public static GpuTextureView texture() {
        return BACKDROP.isReady() ? BACKDROP.view() : null;
    }

    /** 面板坐标 -> 纹理 U。坐标与帧缓冲纹理 1:1, 不要乘 GUI scale。 */
    public static float u(double screenX) {
        int width = framebufferWidth();
        if (width <= 0) return 0.0F;
        return (float) (screenX / width);
    }

    /** 面板坐标 -> 纹理 V。帧缓冲纹理的 v=1 在屏幕上方, 所以要翻一下。 */
    public static float v(double screenY) {
        int height = framebufferHeight();
        if (height <= 0) return 0.0F;
        if (debugFlipV) return (float) (screenY / height);
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

        int tintStrength = debugTint >= 0 ? debugTint : tint.a;
        int alpha = debugOpacity >= 0 ? debugOpacity : panelAlpha(opacity);

        RoundRectRenderer.glass(
            x, y, width, height,
            radius, radius, radius, radius,
            texture,
            u(x), v(y), u(x + width), v(y + height),
            new Color(tint.r, tint.g, tint.b, Math.max(0, Math.min(255, tintStrength))),
            alpha,
            blurRadius,
            debugNearest
        );
    }

    public static void drawOrFill(double x, double y, double width, double height, double radius,
                                  Color tint, int opacity, Color fallback) {
        boolean useBlur = !debugDisable && isReady();
        if (!loggedPanelState || useBlur != lastPanelUsedBlur) {
            loggedPanelState = true;
            lastPanelUsedBlur = useBlur;
            AddonTemplate.LOG.info("[yuio] blur panel: {}",
                (useBlur ? "GLASS copy <- " : "FLAT FALLBACK <- ") + status());
        }

        if (useBlur) draw(x, y, width, height, radius, tint, opacity);
        else RoundRectRenderer.fill(x, y, width, height, radius, fallback);
    }

    /**
     * background-alpha -> 真模糊生效时的面板不透明度, 只允许在 0.72~1.0 之间微调。
     * 否则按面板原值(默认 165/255 = 0.65)会有 35% 的清晰背景直接透出来, 模糊等于白做。
     */
    private static int panelAlpha(int opacity) {
        double t = Math.max(0.0, Math.min(1.0, opacity / 255.0));
        return (int) Math.round(255.0 * (0.72 + 0.28 * t));
    }

    /** 全屏预览用: 把副本铺满整个屏幕, 用来验证抓屏内容与 UV 方向。 */
    public static void drawPreview(int opacity) {
        GpuTextureView texture = texture();
        if (texture == null) return;

        int width = framebufferWidth();
        int height = framebufferHeight();
        if (width <= 0 || height <= 0) return;

        RoundRectRenderer.glass(0, 0, width, height, 0, 0, 0, 0,
            texture, 0.0F, debugFlipV ? 0.0F : 1.0F, 1.0F, debugFlipV ? 1.0F : 0.0F,
            new Color(255, 255, 255, 0), opacity, blurRadius, debugNearest);
    }

    public static String status() {
        StringBuilder sb = new StringBuilder(160);
        sb.append(isReady() ? "ready" : "NOT READY");
        sb.append(" backdrop=").append(builtWidth).append('x').append(builtHeight);
        sb.append(" frames=").append(frames);
        sb.append(" captures=").append(captures);
        sb.append(" skipped=").append(skipped);
        if (lastCaptureNanos >= 0L) {
            sb.append(" cost=").append(String.format("%.2f", lastCaptureNanos / 1.0E6)).append("ms");
        }
        sb.append(" strength=").append(capturedStrength);
        sb.append(" radius=").append(String.format("%.1f", blurRadius)).append("px");
        if (capturedThisFrame) sb.append(" (fresh)");
        if (lastError != null) sb.append(" ERROR[").append(lastErrorStage).append("] ").append(lastError);
        return sb.toString();
    }

    public static void resetStats() {
        frames = 0;
        captures = 0;
        skipped = 0;
        lastCaptureNanos = -1L;
        lastCaptureFrame = -1L;
        lastError = null;
        lastErrorStage = null;
        loggedError = null;
    }

    public static long frames() {
        return frames;
    }

    public static long captures() {
        return captures;
    }

    public static boolean capturedThisFrame() {
        return capturedThisFrame;
    }

    public static String lastError() {
        return lastError;
    }

    private static void fail(String stage, Throwable t) {
        lastErrorStage = stage;
        lastError = t == null ? "unavailable" : t.getClass().getSimpleName() + ": " + t.getMessage();

        if (!lastError.equals(loggedError)) {
            loggedError = lastError;
            AddonTemplate.LOG.error("[yuio] blur capture failed at {}: {}", stage, lastError);
        }
    }

    // TODO(临时诊断, 定位完成后删除): 一次性把主帧缓冲与副本读回来, 打印平均色, 确认拷贝真的落盘。
    private static long lastProbeFrame = Long.MIN_VALUE / 2;

    private static void probeOnce() {
        if (frames - lastProbeFrame < 600) return;
        lastProbeFrame = frames;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.getFramebuffer() != null && mc.getFramebuffer().getColorAttachment() != null) {
            readback("main-fb", mc.getFramebuffer().getColorAttachment());
        }

        if (BACKDROP.isReady()) readback("backdrop-copy", BACKDROP.texture());
    }

    private static void readback(String label, GpuTexture texture) {
        if (texture == null) return;

        try {
            int pixelSize = texture.getFormat().pixelSize();
            int width = texture.getWidth(0);
            int height = texture.getHeight(0);
            long size = (long) width * height * pixelSize;

            GpuDevice device = RenderSystem.getDevice();
            CommandEncoder encoder = device.createCommandEncoder();
            GpuBuffer buffer = device.createBuffer(() -> "yuio blur probe - " + label,
                GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, size);

            encoder.copyTextureToBuffer(texture, buffer, 0L, () -> {
                try (GpuBuffer.MappedView mapped = encoder.mapBuffer(buffer, true, false)) {
                    ByteBuffer data = mapped.data();

                    int blockWidth = Math.min(64, width);
                    int blockHeight = Math.min(64, height);
                    int startX = (width - blockWidth) / 2;
                    int startY = (height - blockHeight) / 2;

                    long r = 0L;
                    long g = 0L;
                    long b = 0L;
                    long a = 0L;
                    for (int y = 0; y < blockHeight; y++) {
                        for (int x = 0; x < blockWidth; x++) {
                            int index = ((startY + y) * width + (startX + x)) * pixelSize;
                            r += data.get(index) & 0xFF;
                            g += data.get(index + 1) & 0xFF;
                            b += data.get(index + 2) & 0xFF;
                            a += data.get(index + 3) & 0xFF;
                        }
                    }

                    int count = blockWidth * blockHeight;
                    AddonTemplate.LOG.info(
                        "[yuio] probe {}: {}x{} centerAvg=({},{},{}) a={} px0=({},{},{})",
                        label, width, height, r / count, g / count, b / count, a / count,
                        data.get(0) & 0xFF, data.get(1) & 0xFF, data.get(2) & 0xFF);
                } catch (Throwable t) {
                    AddonTemplate.LOG.error("[yuio] probe {} map failed: {}", label, t.toString());
                } finally {
                    buffer.close();
                }
            }, 0);
        } catch (Throwable t) {
            AddonTemplate.LOG.error("[yuio] probe {} setup failed: {}", label, t.toString());
        }
    }
}
