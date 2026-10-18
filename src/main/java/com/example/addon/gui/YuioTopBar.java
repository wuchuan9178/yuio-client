package com.example.addon.gui;

import com.example.addon.render.GuiGlass;
import com.example.addon.render.GuiIcons;
import com.example.addon.render.GuiScreenGlass;
import com.example.addon.utils.RoundRectRenderer;
import com.mojang.blaze3d.textures.GpuTextureView;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorWidget;
import meteordevelopment.meteorclient.gui.widgets.WTopBar;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class YuioTopBar extends WTopBar implements MeteorWidget {
    private static final String[] TAB_NAMES = {"Modules", "Config", "GUI", "HUD", "Friends", "Macros", "Profiles"};
    private static final String[] TAB_ICONS = {
        GuiIcons.MODULES, GuiIcons.CONFIG, GuiIcons.GUI, GuiIcons.HUD,
        GuiIcons.FRIENDS, GuiIcons.MACROS, GuiIcons.PROFILES
    };

    private final List<Tab> tabs = new ArrayList<>();
    private final double[] itemWidths = new double[TAB_NAMES.length];
    private final double[] itemX = new double[TAB_NAMES.length];
    private final double[] itemHover = new double[TAB_NAMES.length];
    private final double[] itemSelected = new double[TAB_NAMES.length];
    private final double[] itemPress = new double[TAB_NAMES.length];

    private double progress;
    private boolean open;
    private int hovered = -1;
    private boolean hoveredStyle;
    private double styleHover;
    private double stylePress;
    private double circleHover;
    private double circlePress;

    // 圆心固定, 条目在它左右两侧展开
    private double circleCenterX;
    private double circleY;
    private double rowTop;
    private double rowLeftFinal;
    private double rowRightFinal;
    private double styleX;
    private double styleWidth;
    private double rowLeftNow;
    private double rowRightNow;

    public YuioTopBar() {
        for (String name : TAB_NAMES) {
            for (Tab tab : Tabs.get()) {
                if (tab.name.equals(name)) {
                    tabs.add(tab);
                    break;
                }
            }
        }
    }

    @Override
    public void init() {
        GuiIcons.preload();
    }

    // 尺寸全部从主题字号推导, 换分辨率/缩放自动跟着变
    private double line() {
        return Math.max(1.0, theme.textHeight());
    }

    private double gap() {
        return line() * 0.24;
    }

    private double itemHeight() {
        return line() * 1.85;
    }

    private double circleDiameter() {
        return itemHeight() * 1.12;
    }

    private double plateHeight() {
        return itemHeight() * 0.92;
    }

    private double iconSize() {
        return line() * 1.02;
    }

    private double padX() {
        return line() * 0.6;
    }

    private String styleLabel() {
        return YuioGuiTheme.isActive() ? YuioGuiTheme.NAME : "meteor";
    }

    private double lastNanos;
    private double frameTime = 0.016;

    // 时间常数做平滑, 不受帧率/传入 delta 影响
    private double smooth(double current, double target, double tau) {
        return current + (target - current) * (1.0 - Math.exp(-frameTime / tau));
    }

    @Override
    protected void onCalculateSize() {
        double diameter = theme == null ? 32.0 : circleDiameter();
        width = diameter;
        height = diameter;
    }

    // 算出完全展开后的位置, 绘制时再按 progress 从圆心往外插值
    private void layout() {
        double diameter = circleDiameter();
        circleCenterX = x + width * 0.5;
        circleY = y;
        rowTop = y + (diameter - itemHeight()) * 0.5;

        int count = tabs.size();
        int leftCount = (count + 1) / 2;

        for (int i = 0; i < count; i++) {
            itemWidths[i] = padX() + iconSize() + line() * 0.32 + theme.textWidth(tabs.get(i).name) + padX();
        }
        styleWidth = padX() + theme.textWidth(styleLabel()) + padX();

        double cursor = circleCenterX - diameter * 0.5 - gap();
        for (int i = leftCount - 1; i >= 0; i--) {
            itemX[i] = cursor - itemWidths[i];
            cursor = itemX[i] - gap();
        }
        rowLeftFinal = cursor + gap();

        cursor = circleCenterX + diameter * 0.5 + gap();
        for (int i = leftCount; i < count; i++) {
            itemX[i] = cursor;
            cursor += itemWidths[i] + gap();
        }
        styleX = cursor;
        rowRightFinal = styleX + styleWidth;
    }

    private static boolean inside(double mx, double my, double x, double y, double width, double height) {
        return mx >= x && mx <= x + width && my >= y && my <= y + height;
    }

    private static double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }

    private void drawIcon(String name, double x, double y, double size, int opacity) {
        GpuTextureView view = GuiIcons.get(name);
        if (view == null || opacity <= 0) return;

        RoundRectRenderer.textured(x, y, size, size, 0, 0, 0, 0,
            view, 0, 1, 1, 0, new Color(0, 0, 0, 0), opacity);
    }

    private int selectedTab() {
        Screen screen = mc.currentScreen;
        if (!(screen instanceof TabScreen tabScreen)) return -1;

        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i) == tabScreen.tab) return i;
        }
        return -1;
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (theme == null || tabs.isEmpty()) return;

        GuiScreenGlass.flush(renderer);

        long now = System.nanoTime();
        if (lastNanos != 0) frameTime = Math.min(0.1, (now - lastNanos) / 1.0e9);
        lastNanos = now;

        layout();

        double diameter = circleDiameter();
        rowLeftNow = lerp(circleCenterX - diameter * 0.5, rowLeftFinal, progress);
        rowRightNow = lerp(circleCenterX + diameter * 0.5, rowRightFinal, progress);

        boolean overCircle = inside(mouseX, mouseY, circleCenterX - diameter * 0.5, circleY, diameter, diameter);
        boolean overRow = progress > 0.02
            && inside(mouseX, mouseY, rowLeftNow, rowTop, rowRightNow - rowLeftNow, itemHeight());
        boolean target = overCircle || overRow || open;

        progress = smooth(progress, target ? 1.0 : 0.0, 0.11);
        if (progress < 0.002) progress = 0.0;
        if (progress > 0.998) progress = 1.0;

        double p = progress;
        rowLeftNow = lerp(circleCenterX - diameter * 0.5, rowLeftFinal, p);
        rowRightNow = lerp(circleCenterX + diameter * 0.5, rowRightFinal, p);

        hovered = -1;
        hoveredStyle = false;
        if (p > 0.5) {
            for (int i = 0; i < tabs.size(); i++) {
                if (inside(mouseX, mouseY, itemX[i], rowTop, itemWidths[i], itemHeight())) hovered = i;
            }
            hoveredStyle = inside(mouseX, mouseY, styleX, rowTop, styleWidth, itemHeight());
        }

        int selected = selectedTab();
        for (int i = 0; i < tabs.size(); i++) {
            itemHover[i] = smooth(itemHover[i], hovered == i ? 1.0 : 0.0, 0.06);
            itemSelected[i] = smooth(itemSelected[i], selected == i ? 1.0 : 0.0, 0.16);
            itemPress[i] = smooth(itemPress[i], 0.0, 0.14);
        }
        styleHover = smooth(styleHover, hoveredStyle ? 1.0 : 0.0, 0.06);
        stylePress = smooth(stylePress, 0.0, 0.14);
        circleHover = smooth(circleHover, overCircle ? 1.0 : 0.0, 0.06);
        circlePress = smooth(circlePress, 0.0, 0.14);

        if (p > 0.01) {
            double plateY = rowTop + (itemHeight() - plateHeight()) * 0.5;
            GuiGlass.panel(rowLeftNow - gap() * 0.5, plateY,
                rowRightNow - rowLeftNow + gap(), plateHeight(),
                plateHeight() * 0.5, plateHeight() * 0.5, plateHeight() * 0.5, plateHeight() * 0.5,
                (int) Math.round(132 * p), GuiScreenGlass.tint(0.24));
        }

        for (int i = 0; i < tabs.size(); i++) {
            double hover = itemHover[i];
            double sel = itemSelected[i];
            double press = itemPress[i];
            int alpha = (int) Math.round((194 + 42 * hover + 14 * sel) * p);
            if (alpha <= 0) continue;

            double sx = 1 + 0.06 * press;
            double sy = 1 + 0.05 * press;
            double w = itemWidths[i] * sx;
            double h = itemHeight() * sy;
            double drawX = lerp(circleCenterX - w * 0.5, itemX[i] + itemWidths[i] * 0.5 - w * 0.5, p);
            double drawY = rowTop + itemHeight() * 0.5 - h * 0.5;

            GuiGlass.panel(drawX, drawY, w, h,
                h * 0.5, h * 0.5, h * 0.5, h * 0.5,
                alpha, GuiScreenGlass.tint(0.3 + 0.24 * hover + 0.16 * sel));

            if (sel > 0.01) {
                RoundRectRenderer.border(drawX, drawY, w, h, h * 0.5, 1.4,
                    new Color(214, 232, 255, (int) Math.round(130 * sel * p)));
            }

            int textAlpha = (int) Math.round((234 + 21 * hover) * p);
            double iconX = drawX + padX() + (w - itemWidths[i]) * 0.5;
            drawIcon(TAB_ICONS[i], iconX, drawY + (h - iconSize()) * 0.5, iconSize(), textAlpha);

            renderer.text(tabs.get(i).name, iconX + iconSize() + line() * 0.32,
                drawY + (h - line()) * 0.5,
                new Color((int) (234 + 21 * hover), (int) (242 + 13 * hover), 255, textAlpha), false);
        }

        if (p > 0.01) {
            int styleAlpha = (int) Math.round((194 + 42 * styleHover) * p);
            if (styleAlpha > 0) {
                double sx = 1 + 0.06 * stylePress;
                double w = styleWidth * sx;
                double drawX = lerp(circleCenterX - w * 0.5, styleX + styleWidth * 0.5 - w * 0.5, p);
                GuiGlass.panel(drawX, rowTop, w, itemHeight(),
                    itemHeight() * 0.5, itemHeight() * 0.5, itemHeight() * 0.5, itemHeight() * 0.5,
                    styleAlpha, GuiScreenGlass.tint(0.62 + 0.24 * styleHover));

                renderer.text(styleLabel(), drawX + padX() + (w - styleWidth) * 0.5,
                    rowTop + (itemHeight() - line()) * 0.5,
                    new Color(240, 246, 255, (int) Math.round(255 * p)), false);
            }
        }

        // 圆最后画, 收起时条目从它背后滑出
        double circleScale = 1 + 0.05 * circleHover + 0.03 * circlePress;
        double cd = diameter * circleScale;
        double circleX = circleCenterX - cd * 0.5;
        double circleTop = circleY + (diameter - cd) * 0.5;
        int circleAlpha = (overCircle || open) ? 232 : 214;
        GuiGlass.panel(circleX, circleTop, cd, cd,
            cd * 0.5, cd * 0.5, cd * 0.5, cd * 0.5,
            circleAlpha, GuiScreenGlass.tint(0.34 + 0.1 * circleHover));
        RoundRectRenderer.border(circleX, circleTop, cd, cd, cd * 0.5, 1.6,
            new Color(206, 224, 248, 92));

        double iconOffset = cd * 0.26;
        drawIcon(GuiIcons.HOME, circleX + iconOffset, circleTop + iconOffset, cd * 0.48, 255);
    }

    @Override
    public boolean onMouseClicked(Click click, boolean doubled) {
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;

        layout();
        double mx = click.x();
        double my = click.y();
        double diameter = circleDiameter();

        if (progress > 0.5) {
            for (int i = 0; i < tabs.size(); i++) {
                if (inside(mx, my, itemX[i], rowTop, itemWidths[i], itemHeight())) {
                    itemPress[i] = 1.0;
                    open = false;
                    openTab(tabs.get(i));
                    return true;
                }
            }

            if (inside(mx, my, styleX, rowTop, styleWidth, itemHeight())) {
                stylePress = 1.0;
                switchTheme();
                return true;
            }
        }

        if (inside(mx, my, circleCenterX - diameter * 0.5, circleY, diameter, diameter)) {
            circlePress = 1.0;
            open = !open;
            return true;
        }

        return false;
    }

    private void openTab(Tab tab) {
        Screen screen = mc.currentScreen;
        if (screen instanceof TabScreen tabScreen && tabScreen.tab == tab) return;

        double mouseX = mc.mouse.getX();
        double mouseY = mc.mouse.getY();

        tab.openScreen(theme);
        GLFW.glfwSetCursorPos(mc.getWindow().getHandle(), mouseX, mouseY);
    }

    private void switchTheme() {
        YuioGuiTheme.select(!YuioGuiTheme.isActive());

        Screen screen = mc.currentScreen;
        if (screen == null) return;

        for (Tab tab : Tabs.get()) {
            if (tab.isScreen(screen)) {
                tab.openScreen(GuiThemes.get());
                return;
            }
        }
    }

    @Override
    protected Color getButtonColor(boolean pressed, boolean hovered) {
        return theme().backgroundColor.get(pressed, hovered);
    }

    @Override
    protected Color getNameColor() {
        return theme().textColor.get();
    }
}
