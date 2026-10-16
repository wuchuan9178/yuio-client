package com.example.addon.mixin;

import com.example.addon.utils.BlurRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 模糊的抓屏点。放在 HUD 渲染的最开头, 这时帧缓冲里只有世界 —— 原版 HUD 和我们自己的玻璃面板都还没画进去。
 * Meteor 的 {@code Render2DEvent} 挂在同一个方法的 TAIL, 在那里抓屏会把自己的面板也抓进去。
 */
@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(
        method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
        at = @At("HEAD")
    )
    private void yuio$captureBlurBackdrop(DrawContext context, RenderTickCounter tickCounter, CallbackInfo info) {
        BlurRenderer.captureBackdrop();
    }
}
