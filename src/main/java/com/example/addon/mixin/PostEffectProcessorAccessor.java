package com.example.addon.mixin;

import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(PostEffectProcessor.class)
public interface PostEffectProcessorAccessor {
    @Accessor("framebuffers")
    Map<Identifier, Framebuffer> getFramebuffers();
}
