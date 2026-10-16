package com.example.addon.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatHud.class)
public class ChatHudMixin {
    @Unique
    private static final String YUIO_COPY_BUTTON = "[复制]";

    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), argsOnly = true)
    private Text yuio$appendCopyButton(Text message) {
        return yuio$withCopyButton(message);
    }

    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
        at = @At("HEAD"), argsOnly = true)
    private Text yuio$appendCopyButtonSigned(Text message) {
        return yuio$withCopyButton(message);
    }

    @Unique
    private static Text yuio$withCopyButton(Text message) {
        if (message == null) return null;

        String plain = message.getString();
        if (plain.isEmpty()) return message;
        if (plain.contains(YUIO_COPY_BUTTON)) return message;

        String copy = plain.replaceAll(" \\([0-9]{1,9}\\)$", "");

        MutableText out = message.copy();
        out.append(Text.literal(" ").setStyle(Style.EMPTY));
        out.append(Text.literal(YUIO_COPY_BUTTON).setStyle(Style.EMPTY
            .withColor(Formatting.DARK_GRAY)
            .withClickEvent(new ClickEvent.CopyToClipboard(copy))));
        return out;
    }
}
