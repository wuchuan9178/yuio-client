package com.example.addon.utils;

import meteordevelopment.meteorclient.mixininterface.IChatHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.Locale;

public final class ForcingFriendCommand {
    private static final String NAME = "forcing-friend";

    private ForcingFriendCommand() {
    }

    public static boolean handle(String command) {
        if (command == null) return false;

        String trimmed = command.trim();
        if (trimmed.isEmpty()) return false;

        String[] parts = trimmed.split("\\s+");
        String head = parts[0].toLowerCase(Locale.ROOT);

        if (!head.equals(NAME) && !head.equals("forcing-friends") && !head.equals("forcingfriend")) return false;

        boolean value = !ForcedFriends.isEnabled();

        if (parts.length > 1) {
            String arg = parts[1].toLowerCase(Locale.ROOT);

            if (arg.equals("on") || arg.equals("true") || arg.equals("enable")) {
                value = true;
            } else if (arg.equals("off") || arg.equals("false") || arg.equals("disable")) {
                value = false;
            } else {
                message("Usage: .forcing-friend on|off");
                return true;
            }
        }

        ForcedFriends.setEnabled(value);
        message(value ? "Forcing friends is turned on" : "Forcing friends is turned off");

        return true;
    }

    private static void message(String text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.inGameHud == null) return;

        Text message = Text.literal(text);
        IChatHud hud = (IChatHud) mc.inGameHud.getChatHud();

        mc.execute(() -> hud.meteor$add(message, 0));
    }
}
