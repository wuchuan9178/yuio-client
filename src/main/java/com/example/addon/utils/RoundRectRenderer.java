package com.example.addon.utils;

import com.example.addon.AddonTemplate;
import com.example.addon.render.YuioPipelines;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import meteordevelopment.meteorclient.renderer.MeshBuilder;
import meteordevelopment.meteorclient.renderer.MeshRenderer;
import meteordevelopment.meteorclient.renderer.MeteorVertexFormats;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.DynamicUniformStorage;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.texture.NativeImage;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;


public final class RoundRectRenderer {
    private static final int UNIFORM_SIZE = new Std140SizeCalculator()
        .putVec4().putVec4().putVec4().putVec4().putVec4().putVec4()
        .get();

    private static final int UNIFORM_BASE_CAPACITY = 256;

    private static DynamicUniformStorage<RectData> uniforms;

    private static MeshBuilder quad;
    private static GpuTextureView whiteTexture;
    private static boolean unavailable;
    private static boolean clearedThisFrame;

    private RoundRectRenderer() {
    }

    public static boolean isUnavailable() {
        return unavailable;
    }

    public static void onFrameStart() {
        clearedThisFrame = false;
    }

    // 每帧只清一次, 多个面板各自调用也安全
    public static void beginFrame() {
        if (unavailable || clearedThisFrame) return;
        clearedThisFrame = true;
        try {
            uniforms().clear();
        } catch (Throwable t) {
            if (!unavailable) {
                unavailable = true;
                AddonTemplate.LOG.error("[yuio] rounded rect rendering disabled after a failure", t);
            }
        }
    }

    public static void fill(double x, double y, double w, double h, double radius, Color color) {
        fill(x, y, w, h, radius, radius, radius, radius, color);
    }

    public static void fill(double x, double y, double w, double h,
                            double topLeft, double topRight, double bottomRight, double bottomLeft,
                            Color color) {
        if (color.a <= 0) return;
        submit(x, y, w, h, topLeft, topRight, bottomRight, bottomLeft,
            color, color, YuioPipelines.MODE_SOLID, 0,
            null, 0, 0, 0, 0);
    }

    public static void gradient(double x, double y, double w, double h, double radius, Color top, Color bottom) {
        submit(x, y, w, h, radius, radius, radius, radius,
            top, bottom, YuioPipelines.MODE_GRADIENT, 0,
            null, 0, 0, 0, 0);
    }

    public static void border(double x, double y, double w, double h,
                              double radius, double thickness, Color color) {
        border(x, y, w, h, radius, radius, radius, radius, thickness, color);
    }

    public static void border(double x, double y, double w, double h,
                              double topLeft, double topRight, double bottomRight, double bottomLeft,
                              double thickness, Color color) {
        submit(x, y, w, h, topLeft, topRight, bottomRight, bottomLeft,
            color, color, YuioPipelines.MODE_STROKE, thickness,
            null, 0, 0, 0, 0);
    }


    public static void textured(double x, double y, double w, double h,
                                double topLeft, double topRight, double bottomRight, double bottomLeft,
                                GpuTextureView texture, double u1, double v1, double u2, double v2,
                                Color tint, int opacity) {
        textured(x, y, w, h, topLeft, topRight, bottomRight, bottomLeft,
            texture, u1, v1, u2, v2, tint, opacity, 0);
    }

    public static void textured(double x, double y, double w, double h,
                                double topLeft, double topRight, double bottomRight, double bottomLeft,
                                GpuTextureView texture, double u1, double v1, double u2, double v2,
                                Color tint, int opacity, double rotationDegrees) {
        if (texture == null) return;
        submit(x, y, w, h, topLeft, topRight, bottomRight, bottomLeft,
            tint, new Color(255, 255, 255, (int) clamp(opacity, 0, 255)), YuioPipelines.MODE_TEXTURED, 0,
            texture, u1, v1, u2, v2, Math.toRadians(rotationDegrees), linearSampler());
    }

    /** 像素图(皮肤头像/物品图标)必须用 NEAREST, 否则放大后会被插值糊掉。 */
    public static void texturedPixel(double x, double y, double w, double h,
                                     double topLeft, double topRight, double bottomRight, double bottomLeft,
                                     GpuTextureView texture, double u1, double v1, double u2, double v2,
                                     Color tint, int opacity, double rotationDegrees) {
        if (texture == null) return;
        submit(x, y, w, h, topLeft, topRight, bottomRight, bottomLeft,
            tint, new Color(255, 255, 255, (int) clamp(opacity, 0, 255)), YuioPipelines.MODE_TEXTURED, 0,
            texture, u1, v1, u2, v2, Math.toRadians(rotationDegrees), nearestSampler());
    }

    /**
     * 磨砂玻璃: 采样抓屏副本, 在着色器里做圆盘模糊。
     *
     * <p>{@code blurRadiusPixels} 是模糊半径(帧缓冲像素)。MODE_GLASS 复用了 submit 的 stroke 字段
     * 把它传进 {@code u_Params.x} —— 该模式下 stroke 本来就没有意义。
     */
    public static void glass(double x, double y, double w, double h,
                             double topLeft, double topRight, double bottomRight, double bottomLeft,
                             GpuTextureView texture, double u1, double v1, double u2, double v2,
                             Color tint, int opacity, double blurRadiusPixels, boolean nearest) {
        if (texture == null) return;
        submit(x, y, w, h, topLeft, topRight, bottomRight, bottomLeft,
            tint, new Color(255, 255, 255, (int) clamp(opacity, 0, 255)), YuioPipelines.MODE_GLASS,
            Math.max(0.0, blurRadiusPixels),
            texture, u1, v1, u2, v2, 0, nearest ? nearestSampler() : linearSampler());
    }

    public static void glow(double x, double y, double w, double h, double radius, double spread, Color color) {
        if (color.a <= 0) return;
        Color soft = new Color(color.r, color.g, color.b, Math.max(0, (int) (color.a * 0.32)));
        fill(x - spread, y - spread, w + spread * 2, h + spread * 2, radius + spread, soft);
    }

    public static void rim(double x, double y, double w, double h, double radius, double thickness, Color color) {
        border(x, y, w, h, radius, Math.max(1, thickness), color);
    }

    private static void submit(double x, double y, double w, double h,
                               double topLeft, double topRight, double bottomRight, double bottomLeft,
                               Color primary, Color secondary, int mode, double stroke,
                               GpuTextureView texture, double u1, double v1, double u2, double v2) {
        submit(x, y, w, h, topLeft, topRight, bottomRight, bottomLeft, primary, secondary, mode, stroke,
            texture, u1, v1, u2, v2, 0, linearSampler());
    }

    private static void submit(double x, double y, double w, double h,
                               double topLeft, double topRight, double bottomRight, double bottomLeft,
                               Color primary, Color secondary, int mode, double stroke,
                               GpuTextureView texture, double u1, double v1, double u2, double v2,
                               double rotation) {
        submit(x, y, w, h, topLeft, topRight, bottomRight, bottomLeft, primary, secondary, mode, stroke,
            texture, u1, v1, u2, v2, rotation, linearSampler());
    }

    private static void submit(double x, double y, double w, double h,
                               double topLeft, double topRight, double bottomRight, double bottomLeft,
                               Color primary, Color secondary, int mode, double stroke,
                               GpuTextureView texture, double u1, double v1, double u2, double v2,
                               double rotation, GpuSampler sampler) {
        if (unavailable || w <= 0 || h <= 0) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null || mc.getFramebuffer() == null) return;

        double screenWidth = mc.getWindow().getScaledWidth();
        double screenHeight = mc.getWindow().getScaledHeight();
        if (screenWidth <= 0 || screenHeight <= 0) return;

        double maxRadius = Math.min(w, h) * 0.5;
        topLeft = clamp(topLeft, 0, maxRadius);
        topRight = clamp(topRight, 0, maxRadius);
        bottomRight = clamp(bottomRight, 0, maxRadius);
        bottomLeft = clamp(bottomLeft, 0, maxRadius);

        try {
            MeshBuilder mesh = quad();
            mesh.begin();
            mesh.ensureQuadCapacity();

            float left = (float) x;
            float right = (float) (x + w);
            float top = (float) y;
            float bottom = (float) (y + h);

            int i1 = mesh.vec2(left, top).next();
            int i2 = mesh.vec2(left, bottom).next();
            int i3 = mesh.vec2(right, bottom).next();
            int i4 = mesh.vec2(right, top).next();
            mesh.quad(i1, i2, i3, i4);
            mesh.end();

            RectData data = new RectData(
                (float) (x + w * 0.5), (float) (y + h * 0.5), (float) (w * 0.5), (float) (h * 0.5),
                (float) topLeft, (float) topRight, (float) bottomRight, (float) bottomLeft,
                primary, secondary,
                (float) u1, (float) v1, (float) u2, (float) v2,
                (float) stroke, mode, (float) rotation, 0f
            );

            GpuBufferSlice slice = uniforms().write(data);
            GpuTextureView samplerView = texture != null ? texture : white();

            Matrix4fStack modelView = RenderSystem.getModelViewStack();
            Matrix4f savedProjection = new Matrix4f(RenderUtils.projection);
            boolean was3d = Utils.rendering3D;
            Utils.rendering3D = false;
            modelView.pushMatrix();
            modelView.identity();

            float spaceWidth = uiWidth();
            float spaceHeight = uiHeight();
            RenderUtils.projection.set(new Matrix4f().setOrtho(0f, spaceWidth, spaceHeight, 0f, -1000f, 1000f));
            try {
                MeshRenderer.begin()
                    .attachments(mc.getFramebuffer())
                    .pipeline(YuioPipelines.ROUNDED)
                    .mesh(mesh)
                    .uniform("RoundedData", slice)
                    .sampler("u_Texture", samplerView, sampler)
                    .end();
            } finally {
                RenderUtils.projection.set(savedProjection);
                modelView.popMatrix();
                Utils.rendering3D = was3d;
            }
        } catch (Throwable t) {
            if (!unavailable) {
                unavailable = true;
                AddonTemplate.LOG.error("[yuio] rounded rect rendering disabled after a failure", t);
            }
        }
    }

    public static void blurPass(GpuTextureView source, GpuTextureView target,
                                float radiusTexels, boolean horizontal,
                                int textureWidth, int textureHeight) {
        if (unavailable || source == null || target == null || radiusTexels <= 0.0F) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        float spaceWidth = uiWidth();
        float spaceHeight = uiHeight();
        if (spaceWidth <= 1.0F || spaceHeight <= 1.0F) return;

        float texelU = textureWidth > 0 ? 1.0F / textureWidth : 0.0F;
        float texelV = textureHeight > 0 ? 1.0F / textureHeight : 0.0F;

        try {
            MeshBuilder mesh = quad();
            mesh.begin();
            mesh.ensureQuadCapacity();

            int i1 = mesh.vec2(0.0F, 0.0F).next();
            int i2 = mesh.vec2(0.0F, spaceHeight).next();
            int i3 = mesh.vec2(spaceWidth, spaceHeight).next();
            int i4 = mesh.vec2(spaceWidth, 0.0F).next();
            mesh.quad(i1, i2, i3, i4);
            mesh.end();

            Color white = new Color(255, 255, 255, 255);
            RectData data = new RectData(
                spaceWidth * 0.5F, spaceHeight * 0.5F, spaceWidth * 0.5F, spaceHeight * 0.5F,
                0.0F, 0.0F, 0.0F, 0.0F,
                white, white,
                0.0F, 1.0F, 1.0F, 0.0F,
                radiusTexels,
                horizontal ? YuioPipelines.MODE_BLUR_H : YuioPipelines.MODE_BLUR_V,
                texelU, texelV
            );

            GpuBufferSlice slice = uniforms().write(data);

            Matrix4fStack modelView = RenderSystem.getModelViewStack();
            Matrix4f savedProjection = new Matrix4f(RenderUtils.projection);
            boolean was3d = Utils.rendering3D;
            Utils.rendering3D = false;
            modelView.pushMatrix();
            modelView.identity();
            RenderUtils.projection.set(new Matrix4f().setOrtho(0f, spaceWidth, spaceHeight, 0f, -1000f, 1000f));
            try {
                MeshRenderer.begin()
                    .attachments(target, null)
                    .pipeline(YuioPipelines.ROUNDED)
                    .mesh(mesh)
                    .uniform("RoundedData", slice)
                    .sampler("u_Texture", source, linearSampler())
                    .end();
            } finally {
                RenderUtils.projection.set(savedProjection);
                modelView.popMatrix();
                Utils.rendering3D = was3d;
            }
        } catch (Throwable t) {
            if (!unavailable) {
                unavailable = true;
                AddonTemplate.LOG.error("[yuio] blur pass failed", t);
            }
        }
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    public static float uiWidth() {
        Matrix4f ambient = RenderUtils.projection;
        if (isOrtho(ambient)) {
            float width = Math.abs(2.0f / ambient.m00());
            if (width > 32.0f) return width;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        return mc == null || mc.getWindow() == null ? 0.0f : mc.getWindow().getScaledWidth();
    }

    public static float uiHeight() {
        Matrix4f ambient = RenderUtils.projection;
        if (isOrtho(ambient)) {
            float height = Math.abs(2.0f / ambient.m11());
            if (height > 32.0f) return height;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        return mc == null || mc.getWindow() == null ? 0.0f : mc.getWindow().getScaledHeight();
    }

    public static float uiUnit() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return 1.0f;

        float space = uiWidth();
        if (space <= 1.0f) return 1.0f;

        return Math.max(1.0f, mc.getWindow().getFramebufferWidth() / space);
    }

    private static boolean isOrtho(Matrix4f matrix) {
        return Math.abs(matrix.m03()) < 1.0e-4f
            && Math.abs(matrix.m13()) < 1.0e-4f
            && Math.abs(matrix.m23()) < 1.0e-4f
            && matrix.m33() > 0.5f
            && Math.abs(matrix.m00()) > 1.0e-6f
            && Math.abs(matrix.m11()) > 1.0e-6f;
    }

    private static DynamicUniformStorage<RectData> uniforms() {
        if (uniforms == null) {
            uniforms = new DynamicUniformStorage<>("yuio - Rounded Rect", UNIFORM_SIZE, UNIFORM_BASE_CAPACITY);
        }
        return uniforms;
    }

    private static MeshBuilder quad() {
        if (quad == null) {
            quad = new MeshBuilder(MeteorVertexFormats.POS2, VertexFormat.DrawMode.TRIANGLES);
        }
        return quad;
    }

    private static GpuSampler linearSampler() {
        return RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
    }

    private static GpuSampler nearestSampler() {
        return RenderSystem.getSamplerCache().get(FilterMode.NEAREST);
    }

    private static GpuTextureView white() {
        if (whiteTexture == null) {
            GpuDevice device = RenderSystem.getDevice();
            GpuTexture texture = device.createTexture(
                "yuio - white",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.RGBA8, 1, 1, 1, 1
            );

            ByteBuffer pixel = BufferUtils.createByteBuffer(4);
            pixel.put((byte) 255).put((byte) 255).put((byte) 255).put((byte) 255).flip();
            device.createCommandEncoder().writeToTexture(
                texture, pixel, NativeImage.Format.RGBA, 0, 0, 0, 0, 1, 1
            );

            whiteTexture = device.createTextureView(texture);
        }
        return whiteTexture;
    }

    private record RectData(
        float centerX, float centerY, float halfWidth, float halfHeight,
        float topLeft, float topRight, float bottomRight, float bottomLeft,
        Color primary, Color secondary,
        float u1, float v1, float u2, float v2,
        float stroke, int mode, float unusedA, float unusedB
    ) implements DynamicUniformStorage.Uploadable {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder builder = Std140Builder.intoBuffer(buffer);
            builder.putVec4(centerX, centerY, halfWidth, halfHeight);
            builder.putVec4(topLeft, topRight, bottomRight, bottomLeft);
            builder.putVec4(primary.r / 255f, primary.g / 255f, primary.b / 255f, primary.a / 255f);
            builder.putVec4(secondary.r / 255f, secondary.g / 255f, secondary.b / 255f, secondary.a / 255f);
            builder.putVec4(u1, v1, u2, v2);
            builder.putVec4(stroke, mode, unusedA, unusedB);
        }
    }
}

