package com.example.addon.mixin;

import com.example.addon.hud.YuioIsland;
import com.example.addon.modules.UiBlur;
import net.minecraft.client.gui.hud.PlayerListHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void yuio$setState(CallbackInfo ci) {
        UiBlur blur = UiBlur.get();
        if (blur != null && blur.tab()) UiBlur.HUD_CTX.set(3);
    }

    /**
     * 灵动岛自己在画玩家列表, 原版那份就别画了。岛没开时 tabActive 一直是 false, 原版照常。
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void yuio$hideVanillaTab(CallbackInfo ci) {
        if (YuioIsland.tabActive) ci.cancel();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void yuio$clearState(CallbackInfo ci) {
        UiBlur.HUD_CTX.set(0);
    }
}
