package com.example.addon.mixin;

import com.example.addon.render.YuioPipelines;
import net.minecraft.client.gl.ShaderLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自己注册的管线要在着色器加载完之后编译一遍, 挂在这里资源重载时也会跟着重新编译。
 */
@Mixin(ShaderLoader.class)
public class ShaderLoaderMixin {
    @Inject(
        method = "apply(Lnet/minecraft/client/gl/ShaderLoader$Definitions;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V",
        at = @At("TAIL")
    )
    private void yuio$precompilePipelines(CallbackInfo info) {
        YuioPipelines.precompile();
    }
}
