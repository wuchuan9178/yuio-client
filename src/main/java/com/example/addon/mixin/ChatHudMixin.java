package com.example.addon.mixin;

import com.example.addon.modules.UiBlur;
import com.example.addon.render.ChatBackendWrapper;
import com.example.addon.render.ChatPanel;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

        MutableText out = Text.empty();
        out.append(Text.literal(YUIO_COPY_BUTTON).setStyle(Style.EMPTY
            .withColor(Formatting.GREEN)
            .withClickEvent(new ClickEvent.CopyToClipboard(copy))));
        out.append(Text.literal(" "));
        out.append(message.copy());
        return out;
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V",
        at = @At("HEAD"))
    private void yuio$beginPanel(DrawContext context, TextRenderer textRenderer, int currentTick,
                                 int mouseX, int mouseY, boolean interactable, boolean bl, CallbackInfo ci) {
        ChatPanel.reset(context);
    }

    @Redirect(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/hud/ChatHud;render(Lnet/minecraft/client/gui/hud/ChatHud$Backend;IIZ)V"))
    private void yuio$wrapBackend(ChatHud chatHud, ChatHud.Backend drawer, int windowHeight, int currentTick, boolean expanded) {
        UiBlur module = UiBlur.active();
        ChatHud.Backend backend = module == null || !module.chat.get() ? drawer : new ChatBackendWrapper(drawer);
        ((ChatHudInvoker) (Object) chatHud).yuio$renderBackend(backend, windowHeight, currentTick, expanded);
    }
}
