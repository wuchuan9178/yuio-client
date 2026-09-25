package com.example.addon.mixin;

import com.example.addon.modules.UiBlur;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public class ChatHudMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void yuio$setState(CallbackInfo ci) {
        UiBlur blur = UiBlur.get();
        if (blur != null && blur.chat()) UiBlur.HUD_CTX.set(1);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void yuio$clearState(CallbackInfo ci) {
        UiBlur.HUD_CTX.set(0);
    }
}
