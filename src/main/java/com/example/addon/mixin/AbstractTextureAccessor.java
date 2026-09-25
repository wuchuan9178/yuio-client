package com.example.addon.mixin;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractTexture.class)
public interface AbstractTextureAccessor {
    @Accessor("glTexture")
    void setGlTexture(GpuTexture texture);

    @Accessor("glTextureView")
    void setGlTextureView(GpuTextureView view);

    @Accessor("sampler")
    void setSampler(GpuSampler sampler);
}
