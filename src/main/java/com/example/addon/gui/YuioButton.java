package com.example.addon.gui;

import com.example.addon.hud.Anim;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** 主界面按钮: 位置 + 悬停/按下两套动画状态。 */
public class YuioButton {
    public final int x, y, w, h;
    public final Text text;
    public final Identifier icon;
    public final Runnable action;

    private final Anim hover = new Anim(0, 14);
    private final Anim press = new Anim(0, 26);

    public YuioButton(int x, int y, int w, int h, Text text, Identifier icon, Runnable action) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.text = text;
        this.icon = icon;
        this.action = action;
    }

    public void update(float delta, boolean hovered, boolean pressed) {
        hover.set(hovered ? 1.0 : 0.0);
        press.set(pressed ? 1.0 : 0.0);
        hover.update(delta);
        press.update(delta);
    }

    /** 0 → 1, 越大越"浮起来"。 */
    public double hovered() {
        return hover.get();
    }

    /** 0 → 1, 按住时是 1。 */
    public double pressed() {
        return press.get();
    }
}
