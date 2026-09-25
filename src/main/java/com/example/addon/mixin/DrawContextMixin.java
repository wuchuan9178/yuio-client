package com.example.addon.mixin;

import com.example.addon.modules.UiBlur;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(DrawContext.class)
public class DrawContextMixin {

    @ModifyVariable(
        method = "fill(IIIII)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 4
    )
    private int yuio$recolorHudBg(int color) {
        if (UiBlur.HUD_CTX.get() == 0) return color;

        UiBlur blur = UiBlur.get();
        if (blur == null || !blur.isActive()) return color;

        int alpha = (color >>> 24) & 0xFF;
        int rgb = color & 0xFFFFFF;
        if (alpha < 0x20 || alpha > 0xE0) return color;
        if (rgb > 0x303030) return color;

        return blur.colorArgb();
    }
}
