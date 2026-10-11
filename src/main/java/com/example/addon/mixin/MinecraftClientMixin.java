package com.example.addon.mixin;

import com.example.addon.AddonTemplate;
import com.example.addon.utils.YuioIconPack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Icons;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Unique
    private static final int YUIO_ICON_TRIES = 10;

    @Unique
    private static final int YUIO_ICON_INTERVAL = 20;

    @Unique
    private static boolean yuio$iconDone = false;

    @Unique
    private static int yuio$ticks = 0;

    @Inject(method = "tick", at = @At("HEAD"))
    private void yuio$tick(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        Window window = mc.getWindow();
        if (window == null) return;

        window.setTitle("千恋万花");

        if (yuio$iconDone) return;

        yuio$ticks++;
        if (yuio$ticks % YUIO_ICON_INTERVAL != 0) return;

        int attempt = yuio$ticks / YUIO_ICON_INTERVAL;
        if (attempt > YUIO_ICON_TRIES) {
            yuio$iconDone = true;
            AddonTemplate.LOG.warn("[yuio] gave up on the window icon after {} attempts", YUIO_ICON_TRIES);
            return;
        }

        try {
            window.setIcon(YuioIconPack.INSTANCE, Icons.RELEASE);
            yuio$iconDone = true;
            AddonTemplate.LOG.info("[yuio] window icon applied through Window.setIcon, {} sizes", YuioIconPack.sizeCount());
        } catch (Throwable error) {
            AddonTemplate.LOG.warn("[yuio] window icon attempt {} failed: {}", attempt, error.toString());
        }
    }
}
