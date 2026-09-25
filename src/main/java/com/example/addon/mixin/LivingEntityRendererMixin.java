package com.example.addon.mixin;

import com.example.addon.modules.KeepBouncy;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderHead(LivingEntityRenderState state, MatrixStack matrices,
                              OrderedRenderCommandQueue commands, CameraRenderState cameraState,
                              CallbackInfo ci) {
        LivingEntity entity = getEntityFromState(state);
        if (entity != null && KeepBouncy.INSTANCE != null && KeepBouncy.INSTANCE.isBouncy(entity)) {
            matrices.push();
            float sx = (float) KeepBouncy.INSTANCE.getScaleX(entity);
            float sy = (float) KeepBouncy.INSTANCE.getScaleY(entity);
            float sz = (float) KeepBouncy.INSTANCE.getScaleZ(entity);
            matrices.scale(sx, sy, sz);
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderReturn(LivingEntityRenderState state, MatrixStack matrices,
                                OrderedRenderCommandQueue commands, CameraRenderState cameraState,
                                CallbackInfo ci) {
        LivingEntity entity = getEntityFromState(state);
        if (entity != null && KeepBouncy.INSTANCE != null && KeepBouncy.INSTANCE.isBouncy(entity)) {
            matrices.pop();
        }
    }

    private LivingEntity getEntityFromState(LivingEntityRenderState state) {
        if (state == null) return null;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return null;

        Class<?> clazz = state.getClass();
        while (clazz != null) {
            for (Field field : clazz.getDeclaredFields()) {
                String name = field.getName().toLowerCase();
                if (field.getType() == int.class || field.getType() == Integer.class) {
                    if (name.contains("id") || name.contains("entity")) {
                        field.setAccessible(true);
                        try {
                            int id = field.getInt(state);
                            if (id != 0) {
                                Entity entity = mc.world.getEntityById(id);
                                if (entity instanceof LivingEntity) return (LivingEntity) entity;
                            }
                        } catch (IllegalAccessException ignored) {}
                    }
                }
            }
            for (Field field : clazz.getDeclaredFields()) {
                if (Entity.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    try {
                        Object obj = field.get(state);
                        if (obj instanceof LivingEntity) return (LivingEntity) obj;
                    } catch (IllegalAccessException ignored) {}
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }
}
