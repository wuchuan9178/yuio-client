package com.example.addon.mixin;

import com.example.addon.modules.SmoothCamera;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow private Vec3d pos;

    private long yuio$lastFrameNs = 0;

    @Inject(method = "update", at = @At("TAIL"))
    private void onUpdate(World area, Entity focusedEntity,
                          boolean thirdPerson, boolean inverseView, float tickDelta,
                          CallbackInfo info) {
        SmoothCamera mod = Modules.get().get(SmoothCamera.class);
        if (mod == null || !mod.isActive()) {
            yuio$lastFrameNs = 0;
            return;
        }

        long now = System.nanoTime();
        double dt = yuio$lastFrameNs == 0 ? 1.0 / 60.0 : (now - yuio$lastFrameNs) / 1_000_000_000.0;
        yuio$lastFrameNs = now;
        if (dt > 0.1) dt = 0.1; //掉帧保护

        this.pos = mod.update(this.pos, dt);
    }
}
