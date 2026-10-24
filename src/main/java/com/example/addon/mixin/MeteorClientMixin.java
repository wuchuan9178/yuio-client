package com.example.addon.mixin;

import com.example.addon.gui.Yuio2GuiTheme;
import com.example.addon.gui.yuio.YuioClickGui;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.GuiThemes;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MeteorClient.class, remap = false)
public class MeteorClientMixin {
    @Inject(method = "toggleGui", at = @At("HEAD"), cancellable = true)
    private void yuio$openClickGui(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.currentScreen instanceof YuioClickGui) {
            mc.setScreen(null);
            ci.cancel();
            return;
        }

        // 只有 yuio2 主题用自绘界面, meteor / yuio 主题交回 meteor
        GuiTheme theme = GuiThemes.get();
        if (theme == null || !Yuio2GuiTheme.NAME.equalsIgnoreCase(theme.name)) return;

        mc.setScreen(new YuioClickGui());
        ci.cancel();
    }
}
