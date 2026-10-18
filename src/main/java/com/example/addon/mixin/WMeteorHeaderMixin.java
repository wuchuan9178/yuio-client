package com.example.addon.mixin;

import com.example.addon.render.GuiScreenGlass;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.utils.render.color.Color;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "meteordevelopment.meteorclient.gui.themes.meteor.widgets.WMeteorWindow$WMeteorHeader", remap = false)
public abstract class WMeteorHeaderMixin {
    @Redirect(method = "onRender", at = @At(value = "INVOKE",
        target = "Lmeteordevelopment/meteorclient/gui/renderer/GuiRenderer;quad(Lmeteordevelopment/meteorclient/gui/widgets/WWidget;Lmeteordevelopment/meteorclient/utils/render/color/Color;)V"))
    private void yuio$header(GuiRenderer renderer, WWidget widget, Color accent) {

        boolean collapsed = widget.parent != null && widget.parent.height <= widget.height + 2.0;

        if (!GuiScreenGlass.header(renderer, widget.x, widget.y, widget.width, widget.height,
            GuiScreenGlass.radius(widget), accent, collapsed)) {
            renderer.quad(widget, accent);
        }
    }
}
