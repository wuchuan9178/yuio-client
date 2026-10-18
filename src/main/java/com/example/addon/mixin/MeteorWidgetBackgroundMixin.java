package com.example.addon.mixin;

import com.example.addon.AddonTemplate;
import com.example.addon.render.GuiScreenGlass;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorWidget;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.utils.render.color.Color;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MeteorWidget.class, remap = false)
public interface MeteorWidgetBackgroundMixin {
    @Inject(
        method = "renderBackground(Lmeteordevelopment/meteorclient/gui/renderer/GuiRenderer;Lmeteordevelopment/meteorclient/gui/widgets/WWidget;Lmeteordevelopment/meteorclient/utils/render/color/Color;Lmeteordevelopment/meteorclient/utils/render/color/Color;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void yuio$renderBackground(GuiRenderer renderer, WWidget widget, Color outlineColor, Color backgroundColor,
                                       CallbackInfo ci) {
        if (GuiScreenGlass.background(renderer, widget, backgroundColor)) {
            AddonTemplate.LOG.debug("[yuio] gui widget glass hooked");
            ci.cancel();
        }
    }
}
