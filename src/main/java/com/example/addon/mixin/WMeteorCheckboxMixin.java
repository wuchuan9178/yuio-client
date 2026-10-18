package com.example.addon.mixin;

import com.example.addon.render.GuiScreenGlass;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.pressable.WMeteorCheckbox;
import meteordevelopment.meteorclient.utils.render.color.Color;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = WMeteorCheckbox.class, remap = false)
public class WMeteorCheckboxMixin {
    @Redirect(
        method = "onRender",
        at = @At(
            value = "INVOKE",
            target = "Lmeteordevelopment/meteorclient/gui/renderer/GuiRenderer;quad(DDDDLmeteordevelopment/meteorclient/utils/render/color/Color;)V"
        )
    )
    private void yuio$check(GuiRenderer renderer, double x, double y, double width, double height, Color color) {
        if (!GuiScreenGlass.check(renderer, x, y, width, height)) {
            renderer.quad(x, y, width, height, color);
        }
    }
}
