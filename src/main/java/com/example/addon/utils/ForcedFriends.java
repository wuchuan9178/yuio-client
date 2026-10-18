package com.example.addon.utils;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.systems.friends.Friend;
import meteordevelopment.meteorclient.systems.friends.Friends;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class ForcedFriends {
    private static final List<String> NAMES = List.of("yeai_QQ", "iaa");
    private static final String CONFIG_FILE = "yuio-forcing-friend.txt";
    private static boolean enabled = load();
    private static int ticks;

    private ForcedFriends() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        save();

        if (value) ensure();
    }

    public static boolean isForced(String name) {
        if (!enabled || name == null || name.isEmpty()) return false;

        for (String forced : NAMES) {
            if (forced.equalsIgnoreCase(name)) return true;
        }

        return false;
    }

    public static boolean protects(Entity entity) {
        if (!enabled || !(entity instanceof PlayerEntity player)) return false;

        return isForced(player.getName().getString());
    }

    public static void tick() {
        if (!enabled) return;
        if (++ticks % 20 != 0) return;

        ensure();
    }

    public static void ensure() {
        Friends friends = Friends.get();
        if (friends == null) return;

        for (String name : NAMES) {
            if (friends.get(name) != null) continue;

            try {
                friends.add(new Friend(name));
            } catch (Throwable error) {
                AddonTemplate.LOG.warn("[yuio] could not force friend {}: {}", name, error.toString());
            }
        }
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE);
    }

    private static boolean load() {
        try {
            Path path = path();
            if (!Files.isRegularFile(path)) return true;

            String value = Files.readString(path, StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
            return !value.equals("off") && !value.equals("false");
        } catch (Throwable error) {
            AddonTemplate.LOG.warn("[yuio] could not read forcing-friend state: {}", error.toString());
            return true;
        }
    }

    private static void save() {
        try {
            Files.writeString(path(), enabled ? "on" : "off", StandardCharsets.UTF_8);
        } catch (Throwable error) {
            AddonTemplate.LOG.warn("[yuio] could not save forcing-friend state: {}", error.toString());
        }
    }
}
