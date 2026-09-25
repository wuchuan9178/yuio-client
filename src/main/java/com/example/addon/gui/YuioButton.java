package com.example.addon.gui;

import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class YuioButton {
    public final int x, y, w, h;
    public final Text text;
    public final Identifier icon;
    public final Runnable action;
    public float hover;

    public YuioButton(int x, int y, int w, int h, Text text, Identifier icon, Runnable action) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.text = text;
        this.icon = icon;
        this.action = action;
    }
}
