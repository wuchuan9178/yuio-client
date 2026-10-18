package com.example.addon.mixin;

import com.example.addon.render.GuiScreenGlass;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = meteordevelopment.meteorclient.gui.themes.meteor.widgets.WMeteorModule.class, remap = false)
public class WMeteorModuleMixin {
    @Redirect(
        method = "onRender",
        at = @At(
            value = "INVOKE",
            target = "Lmeteordevelopment/meteorclient/gui/renderer/GuiRenderer;quad(DDDDLmeteordevelopment/meteorclient/utils/render/color/Color;)V",
            ordinal = 0
        )
    )
    private void yuio$moduleRow(GuiRenderer renderer, double x, double y, double width, double height, Color color) {
        if (!GuiScreenGlass.row(renderer, x, y, width, height, color)) {
            renderer.quad(x, y, width, height, color);
        }
    }
}
