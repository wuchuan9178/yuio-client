package com.example.addon.utils;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public final class YuioTag {
    // 可见小标记, 服务器不会当成隐藏字符清洗掉
    public static final String MARKER = " \u1D5B";
    public static final String DEV_NAME = "yeai_QQ";

    private static final int[] PINK = {255, 118, 186};
    private static final int[] PINK_END = {255, 196, 222};
    private static final int[] GOLD = {255, 206, 84};
    private static final int[] GOLD_END = {255, 146, 26};

    private YuioTag() {
    }

    public static String mark(String message) {
        return message.endsWith(MARKER) ? message : message + MARKER;
    }

    public static String strip(String raw) {
        return raw.endsWith(MARKER) ? raw.substring(0, raw.length() - MARKER.length()) : raw;
    }

    public static Text decorate(Text message) {
        String raw = message.getString();
        if (!raw.endsWith(MARKER)) return message;
        return Text.empty().append(label(raw.contains(DEV_NAME))).append(message);
    }

    private static MutableText label(boolean dev) {
        String text = dev ? "[yuio Dev] " : "[yuio] ";
        int[] from = dev ? GOLD : PINK;
        int[] to = dev ? GOLD_END : PINK_END;

        MutableText out = Text.empty();
        for (int i = 0; i < text.length(); i++) {
            float t = text.length() == 1 ? 1f : i / (float) (text.length() - 1);
            int r = Math.round(from[0] + (to[0] - from[0]) * t);
            int g = Math.round(from[1] + (to[1] - from[1]) * t);
            int b = Math.round(from[2] + (to[2] - from[2]) * t);
            out.append(Text.literal(String.valueOf(text.charAt(i)))
                .styled(style -> style.withColor((r << 16) | (g << 8) | b).withBold(true)));
        }
        return out;
    }
}
