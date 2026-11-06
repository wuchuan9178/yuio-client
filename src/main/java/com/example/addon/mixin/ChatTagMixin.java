package com.example.addon.mixin;

import com.example.addon.utils.YuioTag;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ClientPlayNetworkHandler.class)
public class ChatTagMixin {
    // 发出去的消息尾巴挂一个不可见标记
    @ModifyVariable(method = "sendChatMessage", at = @At("HEAD"), argsOnly = true)
    private String yuio$markChat(String message) {
        return YuioTag.mark(message);
    }
}
