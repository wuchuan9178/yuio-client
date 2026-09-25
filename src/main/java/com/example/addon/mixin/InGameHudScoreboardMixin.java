package com.example.addon.mixin;

import com.example.addon.modules.UiBlur;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudScoreboardMixin {

    @Inject(method = "renderScoreboardSidebar", at = @At("HEAD"))
    private void yuio$setState(CallbackInfo ci) {
        UiBlur blur = UiBlur.get();
        if (blur != null && blur.scoreboard()) UiBlur.HUD_CTX.set(2);
    }

    @Inject(method = "renderScoreboardSidebar", at = @At("RETURN"))
    private void yuio$clearState(CallbackInfo ci) {
        UiBlur.HUD_CTX.set(0);
    }
}
