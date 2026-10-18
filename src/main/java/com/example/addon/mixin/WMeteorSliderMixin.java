package com.example.addon.mixin;

import com.example.addon.render.GuiScreenGlass;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.input.WMeteorSlider;
import meteordevelopment.meteorclient.utils.render.color.Color;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = WMeteorSlider.class, remap = false)
public class WMeteorSliderMixin {
    @Redirect(
        method = "renderBar",
        at = @At(
            value = "INVOKE",
            target = "Lmeteordevelopment/meteorclient/gui/renderer/GuiRenderer;quad(DDDDLmeteordevelopment/meteorclient/utils/render/color/Color;)V",
            ordinal = 0
        )
    )
    private void yuio$barLeft(GuiRenderer renderer, double x, double y, double width, double height, Color color) {
        if (!GuiScreenGlass.sliderBar(renderer, x, y, width, height, true)) {
            renderer.quad(x, y, width, height, color);
        }
    }

    @Redirect(
        method = "renderBar",
        at = @At(
            value = "INVOKE",
            target = "Lmeteordevelopment/meteorclient/gui/renderer/GuiRenderer;quad(DDDDLmeteordevelopment/meteorclient/utils/render/color/Color;)V",
            ordinal = 1
        )
    )
    private void yuio$barRight(GuiRenderer renderer, double x, double y, double width, double height, Color color) {
        if (!GuiScreenGlass.sliderBar(renderer, x, y, width, height, false)) {
            renderer.quad(x, y, width, height, color);
        }
    }
}
