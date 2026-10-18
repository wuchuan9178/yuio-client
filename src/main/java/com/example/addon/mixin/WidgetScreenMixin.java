package com.example.addon.mixin;

import com.example.addon.render.GuiGlass;
import com.example.addon.render.GuiScreenGlass;
import com.example.addon.render.ScreenTransition;
import com.example.addon.utils.BlurRenderer;
import com.example.addon.utils.RoundRectRenderer;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WidgetScreen.class, remap = false)
public abstract class WidgetScreenMixin {
    @Inject(method = "renderCustom", at = @At("HEAD"))
    private void yuio$captureBackdrop(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        RoundRectRenderer.beginFrame();
        ScreenTransition.startIfNew(this);

        if (!GuiScreenGlass.enabled()) return;

        BlurRenderer.request();
        BlurRenderer.captureBackdrop();
    }

    @Inject(method = "renderCustom", at = @At("TAIL"))
    private void yuio$fadeIn(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        float alpha = ScreenTransition.alpha();
        if (alpha <= 0f) return;

        double width = RoundRectRenderer.uiWidth();
        double height = RoundRectRenderer.uiHeight();
        if (width <= 0 || height <= 0) return;

        if (GuiScreenGlass.enabled() && BlurRenderer.isReady()) {
            GuiGlass.panel(0, 0, width, height, 0, (int) Math.round(alpha * 150f));
        } else {
            RoundRectRenderer.fill(0, 0, width, height, 0,
                new meteordevelopment.meteorclient.utils.render.color.Color(7, 11, 18, (int) Math.round(alpha * 140f)));
        }
    }
}
