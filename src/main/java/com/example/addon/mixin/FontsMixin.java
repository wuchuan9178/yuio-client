package com.example.addon.mixin;

import com.example.addon.modules.MeteorFont;
import meteordevelopment.meteorclient.renderer.Fonts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Fonts.class)
public class FontsMixin {

    @Inject(method = "refresh", at = @At("TAIL"))
    private static void yuio$refresh(CallbackInfo ci) {
        // 重载资源后 Mega 字体表会被重建，重新塞回去
        MeteorFont.onFontsRefresh();
    }
}
