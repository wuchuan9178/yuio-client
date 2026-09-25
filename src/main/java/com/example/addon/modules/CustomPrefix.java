package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.lang.reflect.Field;

public class CustomPrefix extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> prefix = sgGeneral.add(new StringSetting.Builder()
        .name("prefix")
        .description("Custom prefix text.")
        .defaultValue(" [yuio] ")
        .build());

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Rainbow flow speed.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderMax(5.0)
        .build());

    private Text originalPrefix;
    private int tickCounter = 0;

    public CustomPrefix() {
        super(AddonTemplate.OTHER, "custom-prefix", "Rainbow flowing prefix.");
    }

    @Override
    public void onActivate() {
        try {
            Field field = ChatUtils.class.getDeclaredField("PREFIX");
            field.setAccessible(true);
            originalPrefix = (Text) field.get(null);
            tickCounter = 0;
            updatePrefix();
            ChatUtils.info("Rainbow prefix enabled.");
        } catch (Exception e) {
            error("Failed to change prefix: " + e.getMessage());
            toggle();
        }
    }

    @Override
    public void onDeactivate() {
        if (originalPrefix != null) {
            try {
                Field field = ChatUtils.class.getDeclaredField("PREFIX");
                field.setAccessible(true);
                field.set(null, originalPrefix);
            } catch (Exception ignored) {}
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;
        tickCounter++;
        updatePrefix();
    }

    private void updatePrefix() {
        try {
            Field field = ChatUtils.class.getDeclaredField("PREFIX");
            field.setAccessible(true);
            field.set(null, buildRainbowPrefix());
        } catch (Exception ignored) {}
    }

    private Text buildRainbowPrefix() {
        String text = prefix.get();
        MutableText result = Text.empty();
        float baseHue = (tickCounter * speed.get().floatValue()) % 360.0f;

        for (int i = 0; i < text.length(); i++) {
            //流体
            float hue = (baseHue + i * 15.0f) % 360.0f;
            int rgb = hsvToRgb(hue, 1.0f, 1.0f);
            TextColor color = TextColor.fromRgb(rgb);
            result.append(Text.literal(String.valueOf(text.charAt(i)))
                .setStyle(Style.EMPTY.withColor(color)));
        }
        return result;
    }

    private int hsvToRgb(float hue, float saturation, float value) {
        float c = value * saturation;
        float x = c * (1 - Math.abs((hue / 60.0f) % 2 - 1));
        float m = value - c;
        float r, g, b;
        if (hue < 60) { r = c; g = x; b = 0; }
        else if (hue < 120) { r = x; g = c; b = 0; }
        else if (hue < 180) { r = 0; g = c; b = x; }
        else if (hue < 240) { r = 0; g = x; b = c; }
        else if (hue < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return (((int)((r + m) * 255) & 0xFF) << 16) |
            (((int)((g + m) * 255) & 0xFF) << 8) |
            ((int)((b + m) * 255) & 0xFF);
    }
}
