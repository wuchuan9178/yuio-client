package com.example.addon.mixin;

import com.example.addon.modules.ModelReplace;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.state.WorldRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    @Inject(method = "fillEntityRenderStates", at = @At("TAIL"))
    private void onFillEntityRenderStates(Camera camera, Frustum frustum, RenderTickCounter tickCounter,
                                          WorldRenderState worldRenderState, CallbackInfo ci) {
        ModelReplace module = ModelReplace.INSTANCE;
        if (module == null || !module.isActive()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        module.apply(mc.getEntityRenderDispatcher(), tickCounter.getTickProgress(true), worldRenderState);
    }
}