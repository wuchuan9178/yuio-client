package com.example.addon.gui.yuio;

import com.example.addon.hud.Anim;
import meteordevelopment.meteorclient.utils.render.color.Color;

// 可复用的 iOS 风格开关: 轨道 + 滑块 + 平滑过渡
public final class IosSwitch {
    private static final Color TRACK_OFF = new Color(58, 62, 72);
    private static final Color ACCENT = new Color(255, 96, 160);
    private static final Color KNOB = new Color(246, 249, 253);

    private final Anim anim;
    private final Anim hover;

    public IosSwitch(boolean on) {
        this.anim = new Anim(on ? 1 : 0, 20);
        this.hover = new Anim(0, 14);
        this.anim.reset(on ? 1 : 0);
    }

    public void update(boolean on, double delta) {
        anim.set(on ? 1 : 0);
        anim.update(delta);
    }

    public void updateHover(boolean over, double delta) {
        hover.set(over ? 1 : 0);
        hover.update(delta);
    }

    public double progress() {
        return anim.get();
    }

    public static double width(double height) {
        return height * 1.72;
    }

    public void render(GuiDraw g, double x, double y, double height, double alpha) {
        double t = Math.max(0, Math.min(1, anim.get()));
        double over = hover.get();
        double w = width(height);
        double h = height;
        double r = h * 0.5;

        // 轨道: 关闭灰, 开启粉, 中间平滑过渡
        Color track = mix(TRACK_OFF, ACCENT, t);
        g.fill(x, y, w, h, r, new Color(track.r, track.g, track.b, (int) Math.round(240 * alpha)));

        // 已开启部分从左侧铺满到滑块中心, 宽度不足时退化成圆
        double knobR = r - 2;
        double travel = w - h;
        double knobX = x + r + travel * t;
        if (t > 0.001) {
            double fillW = Math.max(h, knobX - x + r - 1);
            g.fill(x, y, fillW, h, r, new Color(ACCENT.r, ACCENT.g, ACCENT.b, (int) Math.round(255 * alpha)));
        }

        if (over > 0.01) {
            double haloR = knobR + 2.5;
            g.fill(knobX - haloR, y + r - haloR, haloR * 2, haloR * 2, haloR,
                new Color(255, 255, 255, (int) Math.round(40 * over * alpha)));
        }

        g.fill(knobX - knobR, y + r - knobR, knobR * 2, knobR * 2, knobR,
            new Color(KNOB.r, KNOB.g, KNOB.b, (int) Math.round(255 * alpha)));
    }

    private static Color mix(Color a, Color b, double factor) {
        double f = Math.max(0, Math.min(1, factor));
        return new Color(
            (int) Math.round(a.r + (b.r - a.r) * f),
            (int) Math.round(a.g + (b.g - a.g) * f),
            (int) Math.round(a.b + (b.b - a.b) * f),
            255);
    }
}
