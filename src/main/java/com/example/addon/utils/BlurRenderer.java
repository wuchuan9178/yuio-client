package com.example.addon.utils;

import com.example.addon.render.YuioPipelines;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import meteordevelopment.meteorclient.renderer.FixedUniformStorage;
import meteordevelopment.meteorclient.renderer.MeshRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.DynamicUniformStorage;

import java.nio.ByteBuffer;

public final class BlurRenderer {
    private static final int LEVELS = 6;

    private static final int[] ITERATIONS = {1, 1, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5};
    private static final float[] OFFSETS = {
        1.25F, 2.25F, 2.0F, 3.0F, 4.25F,
        2.5F, 3.25F, 4.25F, 5.5F,
        3.25F, 4.0F, 5.0F, 6.0F, 7.25F, 8.25F,
        4.5F, 5.25F, 6.25F, 7.25F, 8.5F
    };

    private static final BlurFbo[] CHAIN = new BlurFbo[LEVELS];

    private static final int UNIFORM_SIZE = new Std140SizeCalculator().putVec4().get();

    private static final FixedUniformStorage<BlurData> UNIFORMS =
        new FixedUniformStorage<>("yuio - Blur", UNIFORM_SIZE, LEVELS);

    private static GpuBufferSlice[] slices;
    private static float lastOffset = -1.0F;

    private static int builtWidth = -1;
    private static int builtHeight = -1;
    private static boolean ready;

    private static volatile int requestedStrength;
    private static volatile boolean requested;
    private static boolean hooked;

    private static final meteordevelopment.orbit.listeners.ConsumerListener<
        meteordevelopment.meteorclient.events.render.Render2DEvent> CAPTURE =
        new meteordevelopment.orbit.listeners.ConsumerListener<>(
            meteordevelopment.meteorclient.events.render.Render2DEvent.class, event -> {
                if (!requested) return;
                requested = false;
                capture(requestedStrength);
            });

    public static void request(int strength) {
        requestedStrength = strength;
        requested = true;

        if (!hooked) {
            hooked = true;
            meteordevelopment.meteorclient.MeteorClient.EVENT_BUS.subscribe(CAPTURE);
        }
    }

    static {
        for (int i = 0; i < LEVELS; i++) {
            CHAIN[i] = new BlurFbo("yuio - blur L" + i);
        }
    }

    private BlurRenderer() {
    }

    public static void init() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        int width = mc.getWindow().getFramebufferWidth();
        int height = mc.getWindow().getFramebufferHeight();
        if (width <= 0 || height <= 0) return;
        if (ready && width == builtWidth && height == builtHeight) return;

        close();

        for (int i = 0; i < LEVELS; i++) {
            int divisor = 1 << i;
            CHAIN[i].resize(Math.max(1, width / divisor), Math.max(1, height / divisor));
        }

        builtWidth = width;
        builtHeight = height;
        ready = true;
    }

    public static int width() {
        return builtWidth;
    }

    public static int height() {
        return builtHeight;
    }

    public static boolean isReady() {
        return ready && CHAIN[0].isReady();
    }

    public static void close() {
        for (BlurFbo fbo : CHAIN) {
            fbo.close();
        }
        ready = false;
        builtWidth = -1;
        builtHeight = -1;
        lastOffset = -1.0F;
        slices = null;
    }

    public static GpuTextureView texture() {
        return isReady() ? CHAIN[0].view() : null;
    }

    public static float uvLeft(double screenX) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return (float) (screenX * mc.getWindow().getScaleFactor() / mc.getWindow().getFramebufferWidth());
    }

    public static float uvRight(double screenX, double width) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return (float) ((screenX + width) * mc.getWindow().getScaleFactor() / mc.getWindow().getFramebufferWidth());
    }

    public static float uvTop(double screenY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return (float) (1.0 - screenY * mc.getWindow().getScaleFactor() / mc.getWindow().getFramebufferHeight());
    }

    public static float uvBottom(double screenY, double height) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return (float) (1.0 - (screenY + height) * mc.getWindow().getScaleFactor() / mc.getWindow().getFramebufferHeight());
    }

    public static void capture(int strength) {
        init();
        if (!ready) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getFramebuffer() == null) return;

        GpuTextureView screen = mc.getFramebuffer().getColorAttachmentView();
        if (screen == null) return;

        int index = Math.max(0, Math.min(ITERATIONS.length - 1, strength - 1));
        int iterations = Math.min(ITERATIONS[index], LEVELS - 1);
        float offset = OFFSETS[index];

        if (offset != lastOffset || slices == null) {
            UNIFORMS.clear();

            BlurData[] data = new BlurData[LEVELS];
            for (int i = 0; i < LEVELS; i++) {
                data[i] = new BlurData(0.5F / Math.max(1, CHAIN[i].width()),
                    0.5F / Math.max(1, CHAIN[i].height()), offset);
            }

            slices = UNIFORMS.writeAll(data);
            lastOffset = offset;
        }

        renderToFbo(CHAIN[0].view(), screen, YuioPipelines.BLUR_DOWN, slices[0]);

        for (int i = 0; i < iterations; i++) {
            renderToFbo(CHAIN[i + 1].view(), CHAIN[i].view(), YuioPipelines.BLUR_DOWN, slices[i + 1]);
        }

        for (int i = iterations; i >= 1; i--) {
            renderToFbo(CHAIN[i - 1].view(), CHAIN[i].view(), YuioPipelines.BLUR_UP, slices[i - 1]);
        }

    }

    public static void draw(double x, double y, double width, double height, double radius, Color tint, int opacity) {
        GpuTextureView texture = texture();
        if (texture == null) return;

        RoundRectRenderer.textured(
            x, y, width, height,
            radius, radius, radius, radius,
            texture,
            uvLeft(x), uvTop(y), uvRight(x, width), uvBottom(y, height),
            tint, opacity
        );
    }

    public static void drawOrFill(double x, double y, double width, double height, double radius,
                                  Color tint, int opacity, Color fallback) {
        if (isReady()) draw(x, y, width, height, radius, tint, opacity);
        else RoundRectRenderer.fill(x, y, width, height, radius, fallback);
    }

    private static void renderToFbo(GpuTextureView target, GpuTextureView source,
                                    RenderPipeline pipeline, GpuBufferSlice ubo) {
        if (target == null || source == null || ubo == null) return;

        MeshRenderer.begin()
            .attachments(target, null)
            .pipeline(pipeline)
            .fullscreen()
            .uniform("BlurData", ubo)
            .sampler("u_Texture", source, RenderSystem.getSamplerCache().get(FilterMode.LINEAR))
            .end();
    }

    private record BlurData(float halfTexelX, float halfTexelY, float offset)
        implements DynamicUniformStorage.Uploadable {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer).putVec4(halfTexelX, halfTexelY, offset, 0.0F);
        }
    }
}

