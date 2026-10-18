package com.example.addon.mixin;

import com.example.addon.render.GuiScreenGlass;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.WMeteorTooltip;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.utils.render.color.Color;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = WMeteorTooltip.class, remap = false)
public abstract class WMeteorTooltipMixin {
    @Redirect(method = "onRender", at = @At(value = "INVOKE",
        target = "Lmeteordevelopment/meteorclient/gui/renderer/GuiRenderer;quad(Lmeteordevelopment/meteorclient/gui/widgets/WWidget;Lmeteordevelopment/meteorclient/utils/render/color/Color;)V"))
    private void yuio$tooltip(GuiRenderer renderer, WWidget widget, Color color) {
        if (!GuiScreenGlass.floating(renderer, widget.x, widget.y, widget.width, widget.height, GuiScreenGlass.radius(widget))) {
            renderer.quad(widget, color);
        }
    }
}
