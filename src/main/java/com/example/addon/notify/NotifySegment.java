package com.example.addon.notify;

public record NotifySegment(String text, int rgb) {
    public static final int DEFAULT = 0xFFFFFF;
    public static final int SENDER = 0xAAAAAA;
    public static final int MENTION_ALL = 0xFF5555;
    public static final int MENTION_ME = 0xFF5555;
    public static final int MENTION_OTHER = 0x66B2FF;
}

