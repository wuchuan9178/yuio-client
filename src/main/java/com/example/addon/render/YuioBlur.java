package com.example.addon.render;

import com.example.addon.mixin.AbstractTextureAccessor;
import com.example.addon.mixin.PostEffectProcessorAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectPipeline;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.ProjectionMatrix2;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;

import java.util.Set;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class YuioBlur {
    public static final Identifier JSON_ID = Identifier.of("yuio", "post_effect/yuio_blur");
    public static final Identifier TEXTURE_ID = Identifier.of("yuio", "blur");
    private static final Identifier OUT = Identifier.of("yuio", "yuio_blur_b");

    private static PostEffectProcessor processor;
    private static boolean ready;

    public static boolean isReady() { return ready; }

    public static void init() {
        close();
        try {
            PostEffectPipeline pipeline = PostEffectPipeline.CODEC
                .parse(JsonOps.INSTANCE, JsonHelper.deserialize(mc.getResourceManager().openAsReader(JSON_ID)))
                .resultOrPartial(s -> {})
                .orElseThrow();
            processor = PostEffectProcessor.parseEffect(pipeline, mc.getTextureManager(), Set.of(), JSON_ID, new ProjectionMatrix2("yuio-blur", 0.05f, 1000.0f, false));
            Framebuffer out = ((PostEffectProcessorAccessor) processor).getFramebuffers().get(OUT);
            if (out == null) { close(); return; }

            AbstractTexture tex = new AbstractTexture() {};
            ((AbstractTextureAccessor) tex).setGlTexture(out.getColorAttachment());
            ((AbstractTextureAccessor) tex).setGlTextureView(out.getColorAttachmentView());
            ((AbstractTextureAccessor) tex).setSampler(RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
            mc.getTextureManager().registerTexture(TEXTURE_ID, tex);
            ready = true;
        }
        catch (Exception e) {
            close();
        }
    }

    public static void process() {
        if (!ready) return;
        try {
            processor.render(mc.getFramebuffer(), ObjectAllocator.TRIVIAL);
        }
        catch (Exception ignored) {}
    }

    public static void close() {
        if (processor != null) {
            processor.close();
            processor = null;
        }
        ready = false;
    }
}
