package com.example.addon.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.BetterChat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "net.minecraft.client.gui.hud.ChatHud$Forwarder")
public class ChatHudForwarderMixin {
    // 显示时 BetterChat 的玩家头像把文字右移了, 点击检测这条路径也得跟着移, 不然点不中
    @ModifyArg(
        method = "text",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/font/DrawnTextConsumer;text(Lnet/minecraft/client/font/Alignment;IILnet/minecraft/text/OrderedText;)V"),
        index = 1)
    private int yuio$alignClickX(int x) {
        BetterChat betterChat = Modules.get() == null ? null : Modules.get().get(BetterChat.class);
        return betterChat == null ? x : betterChat.modifyChatWidth(x);
    }
}
