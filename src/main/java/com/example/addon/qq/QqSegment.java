package com.example.addon.qq;

/**
 * 一段消息文本。
 *
 * @param text 文本内容
 * @param rgb  颜色, 默认 0xFFFFFF; @全体成员 / @你 会被标红
 */
public record QqSegment(String text, int rgb) {
    public static final int DEFAULT = 0xFFFFFF;
    public static final int MENTION_ALL = 0xFF5555;
    public static final int MENTION_ME = 0xFF5555;
    public static final int MENTION_OTHER = 0x66B2FF;
}
