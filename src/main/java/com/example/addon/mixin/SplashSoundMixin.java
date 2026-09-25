package com.example.addon.mixin;

import com.example.addon.render.LoadingSound;
import net.minecraft.client.gui.screen.SplashOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SplashOverlay.class)
public class SplashSoundMixin {

    private static boolean yuio$played = false;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void yuio$playSound(CallbackInfo ci) {
        if (yuio$played) return;
        yuio$played = true;
        LoadingSound.play();
    }
}
