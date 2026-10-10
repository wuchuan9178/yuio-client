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
import meteordevelopment.meteorclient.renderer.FixedUniformStorage;
import meteordevelopment.meteorclient.renderer.MeshBuilder;
import meteordevelopment.meteorclient.renderer.MeshRenderer;
import meteordevelopment.meteorclient.renderer.MeteorVertexFormats;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.DynamicUniformStorage;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.texture.NativeImage;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;


public final class RoundRectRenderer {
    private static final int UNIFORM_SIZE = new Std140SizeCalculator()
        .putVec4().putVec4().putVec4().putVec4().putVec4().putVec4()
        .get();

    private static final int UNIFORM_CAPACITY = 512;

    private static FixedUniformStorage<RectData> uniforms;
    private static MeshBuilder quad;
    private static GpuTextureView whiteTexture;
    private static boolean unavailable;

    private RoundRectRenderer() {
    }

    public static void beginFrame() {
        if (unavailable) return;
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
        submit(x, y, w, h, radius, radius, radius, radius,
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

            MeshRenderer.begin()
                .attachments(mc.getFramebuffer())
                .pipeline(YuioPipelines.ROUNDED)
                .mesh(mesh)
                .uniform("RoundedData", slice)
                .sampler("u_Texture", samplerView, sampler)
                .end();
        } catch (Throwable t) {
            if (!unavailable) {
                unavailable = true;
                AddonTemplate.LOG.error("[yuio] rounded rect rendering disabled after a failure", t);
            }
        }
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    private static FixedUniformStorage<RectData> uniforms() {
        if (uniforms == null) {
            uniforms = new FixedUniformStorage<>("yuio - Rounded Rect", UNIFORM_SIZE, UNIFORM_CAPACITY);
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

