package com.example.addon.mixin;

import com.example.addon.hud.YuioIsland;
import net.minecraft.client.gui.hud.PlayerListHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {

    /**
     * 灵动岛自己在画玩家列表, 原版那份就别画了。岛没开时 tabActive 一直是 false, 原版照常。
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void yuio$hideVanillaTab(CallbackInfo ci) {
        if (YuioIsland.tabActive) ci.cancel();
    }
}
