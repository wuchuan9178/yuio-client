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

import java.util.List;
import java.util.regex.Pattern;
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
    private static final String YUIO_COPY_BUTTON = "[c]";

    @Unique
    private static final Pattern YUIO_TIMESTAMP = Pattern.compile("^<[0-9]{1,2}:[0-9]{1,2}(:[0-9]{2})?> ");

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
        if (yuio$hasCopyButton(message)) return message;

        String plain = message.getString();
        if (plain.isEmpty()) return message;

        String copy = plain.replaceAll(" \\([0-9]{1,9}\\)$", "");
        copy = copy.replaceFirst("^<[0-9]{1,2}:[0-9]{1,2}(:[0-9]{2})?> ", "");

        MutableText button = Text.literal(YUIO_COPY_BUTTON).setStyle(Style.EMPTY
            .withColor(Formatting.GREEN)
            .withClickEvent(new ClickEvent.CopyToClipboard(copy)));

        // 时间戳在第一个 sibling 时插到它后面, 否则先放在最前等 BetterChat 补时间戳
        MutableText out = Text.empty();
        List<Text> siblings = message.getSiblings();
        if (!siblings.isEmpty() && YUIO_TIMESTAMP.matcher(siblings.get(0).getString()).find()) {
            out.append(siblings.get(0).copy());
            out.append(button);
            out.append(Text.literal(" "));
            for (int i = 1; i < siblings.size(); i++) out.append(siblings.get(i).copy());
        } else {
            out.append(button);
            out.append(Text.literal(" "));
            out.append(message.copy());
        }
        return out;
    }

    @Unique
    private static boolean yuio$hasCopyButton(Text message) {
        if (message.getStyle().getClickEvent() instanceof ClickEvent.CopyToClipboard) return true;
        for (Text sibling : message.getSiblings()) {
            if (yuio$hasCopyButton(sibling)) return true;
        }
        return false;
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
