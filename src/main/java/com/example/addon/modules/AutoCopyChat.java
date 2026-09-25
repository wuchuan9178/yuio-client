package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.ChatScreen;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

public class AutoCopyChat extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> showNotification = sgGeneral.add(new BoolSetting.Builder()
        .name("show-notification")
        .description("Show a chat notification after copying text.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> prefix = sgGeneral.add(new StringSetting.Builder()
        .name("prefix")
        .description("Notification prefix shown after copying.")
        .defaultValue("已复制文本：")
        .visible(showNotification::get)
        .build()
    );

    private static Field visibleMessagesField;
    private static Field scrolledLinesField;
    private static Field chatScaleField;
    private static boolean fieldsScanned = false;

    private boolean wasPressed = false;

    public AutoCopyChat() {
        super(AddonTemplate.ASSIST, "auto-copy-chat",
            "Hover over a chat message and press Ctrl+C to copy it.");
    }

    private static void scanFields(ChatHud chat) {
        if (fieldsScanned) return;
        fieldsScanned = true;

        for (Field f : ChatHud.class.getDeclaredFields()) {
            f.setAccessible(true);
            String n = f.getName();

            if (visibleMessagesField == null && f.getType() == List.class
                && (n.equals("visibleMessages") || n.toLowerCase().contains("visible"))) {
                visibleMessagesField = f;
                continue;
            }
            if (scrolledLinesField == null && f.getType() == int.class
                && (n.equals("scrolledLines") || n.toLowerCase().contains("scroll"))) {
                scrolledLinesField = f;
                continue;
            }
            if (chatScaleField == null
                && (f.getType() == double.class || f.getType() == float.class)
                && Modifier.isPrivate(f.getModifiers())) {
                try {
                    double v = f.getDouble(chat);
                    if (v >= 0.5 && v <= 3.0) chatScaleField = f;
                } catch (Exception ignored) {}
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        boolean cDown = Input.isKeyPressed(GLFW.GLFW_KEY_C);
        boolean ctrlDown = Input.isKeyPressed(GLFW.GLFW_KEY_LEFT_CONTROL)
            || Input.isKeyPressed(GLFW.GLFW_KEY_RIGHT_CONTROL);

        boolean pressed = cDown && ctrlDown;
        if (pressed && !wasPressed) {
            wasPressed = true;
            tryCopy();
        } else if (!pressed) {
            wasPressed = false;
        }
    }

    private void tryCopy() {
        if (!(mc.currentScreen instanceof ChatScreen)) return;

        try {
            ChatHud chat = mc.inGameHud.getChatHud();
            scanFields(chat);

            if (visibleMessagesField == null || scrolledLinesField == null) {
                error("聊天字段找不到");
                return;
            }

            @SuppressWarnings("unchecked")
            List<ChatHudLine.Visible> visibles = (List<ChatHudLine.Visible>) visibleMessagesField.get(chat);
            int scrolled = scrolledLinesField.getInt(chat);
            double chatScale = chatScaleField != null ? chatScaleField.getDouble(chat) : 1.0;
            if (chatScale <= 0) chatScale = 1.0;

            double mouseY = mc.mouse.getY() / mc.getWindow().getScaleFactor();
            double chatBottom = (mc.getWindow().getScaledHeight() - 40) / chatScale;
            double dy = chatBottom - (mouseY / chatScale);
            if (dy < 0) return;

            int row = (int) (dy / 9);
            int index = scrolled + row;
            if (index < 0 || index >= visibles.size()) return;

            ChatHudLine.Visible line = visibles.get(index);
            StringBuilder sb = new StringBuilder();
            line.content().accept((i, style, cp) -> {
                sb.appendCodePoint(cp);
                return true;
            });
            String text = sb.toString();
            if (text.isEmpty()) return;

            mc.keyboard.setClipboard(text);
            if (showNotification.get()) info("%s%s", prefix.get(), text);

        } catch (Exception e) {
            error("复制失败: %s", e.getMessage());
        }
    }
}
