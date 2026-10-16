package com.example.addon.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import meteordevelopment.meteorclient.renderer.ExtendedRenderPipelineBuilder;
import meteordevelopment.meteorclient.renderer.MeteorVertexFormats;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderSourceGetter;
import net.minecraft.client.gl.UniformType;
import net.minecraft.util.Identifier;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 这个 addon 自己写的渲染管线 —— Meteor 不提供的那些。
 *
 * <p>两条是圆角(SDF), 两条是模糊的降/升采样。模糊刻意不复用 Meteor 的 {@code BLUR_DOWN/UP}:
 * 那两个着色器开头写了 {@code precision lowp float}, 而这里的 texel 偏移只有 1e-3 量级,
 * lowp 会把它量化成 0 让整条链退化成原样拷贝。
 */
public final class YuioPipelines {
    public static final int MODE_SOLID = 0;
    public static final int MODE_STROKE = 1;
    public static final int MODE_GRADIENT = 2;
    public static final int MODE_TEXTURED = 3;
    /** 磨砂玻璃: 采样模糊纹理并做乘法着色, 不能用 MODE_TEXTURED 的 mix(), 那会把模糊细节抹平。 */
    public static final int MODE_GLASS = 4;

    /** 跟 Meteor 的 UI 管线共用 MeshData, 否则顶点会绕过游戏投影矩阵而错位。 */
    private static final RenderPipeline.Snippet MESH_DATA = RenderPipeline.builder()
        .withUniform("MeshData", UniformType.UNIFORM_BUFFER)
        .buildSnippet();

    public static final RenderPipeline ROUNDED = new ExtendedRenderPipelineBuilder(MESH_DATA)
        .withLocation(Identifier.of("yuio", "pipeline/rounded"))
        .withVertexFormat(MeteorVertexFormats.POS2, VertexFormat.DrawMode.TRIANGLES)
        .withVertexShader(Identifier.of("yuio", "shaders/rounded.vert"))
        .withFragmentShader(Identifier.of("yuio", "shaders/rounded.frag"))
        .withSampler("u_Texture")
        .withUniform("RoundedData", UniformType.UNIFORM_BUFFER)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withBlend(BlendFunction.TRANSLUCENT)
        .withCull(false)
        .build();

    /** 模糊用的 UBO 只有一个 vec4, 不存在 vec2+float 的 std140 偏移歧义。 */
    private static final RenderPipeline.Snippet BLUR_DATA = RenderPipeline.builder()
        .withUniform("BlurData", UniformType.UNIFORM_BUFFER)
        .buildSnippet();

    public static final RenderPipeline BLUR_DOWN = new ExtendedRenderPipelineBuilder(BLUR_DATA)
        .withLocation(Identifier.of("yuio", "pipeline/blur_down"))
        .withVertexFormat(MeteorVertexFormats.POS2, VertexFormat.DrawMode.TRIANGLES)
        .withVertexShader(Identifier.of("yuio", "shaders/blur.vert"))
        .withFragmentShader(Identifier.of("yuio", "shaders/blur_down.frag"))
        .withSampler("u_Texture")
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withCull(false)
        .build();

    public static final RenderPipeline BLUR_UP = new ExtendedRenderPipelineBuilder(BLUR_DATA)
        .withLocation(Identifier.of("yuio", "pipeline/blur_up"))
        .withVertexFormat(MeteorVertexFormats.POS2, VertexFormat.DrawMode.TRIANGLES)
        .withVertexShader(Identifier.of("yuio", "shaders/blur.vert"))
        .withFragmentShader(Identifier.of("yuio", "shaders/blur_up.frag"))
        .withSampler("u_Texture")
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withCull(false)
        .build();

    private static final RenderPipeline[] ALL = {ROUNDED, BLUR_DOWN, BLUR_UP};

    private YuioPipelines() {
    }

    /** 必须在用之前调用, 否则管线没有编译好的着色器。资源重载后要再调。 */
    public static void precompile() {
        ShaderSourceGetter getter = (identifier, type) -> {
            var resource = MinecraftClient.getInstance().getResourceManager().getResource(identifier);
            if (resource.isEmpty()) {
                throw new IllegalStateException("Missing shader resource: " + identifier);
            }

            try (InputStream in = resource.get().getInputStream()) {
                return IOUtils.toString(in, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read shader: " + identifier, e);
            }
        };

        for (RenderPipeline pipeline : ALL) {
            RenderSystem.getDevice().precompilePipeline(pipeline, getter);
        }
    }
}
