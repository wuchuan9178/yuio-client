package com.example.addon.mixin;

import com.example.addon.render.GuiScreenGlass;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.WMeteorWindow;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.utils.render.color.Color;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = WMeteorWindow.class, remap = false)
public abstract class WMeteorWindowMixin {
    @Redirect(method = "onRender", at = @At(value = "INVOKE",
        target = "Lmeteordevelopment/meteorclient/gui/renderer/GuiRenderer;quad(DDDDLmeteordevelopment/meteorclient/utils/render/color/Color;)V"))
    private void yuio$windowBody(GuiRenderer renderer, double x, double y, double width, double height, Color color) {
        WWidget self = (WWidget) (Object) this;

        if (!GuiScreenGlass.window(renderer, self.x, self.y, self.width, self.height, GuiScreenGlass.radius(self))) {
            renderer.quad(x, y, width, height, color);
        }
    }
}
