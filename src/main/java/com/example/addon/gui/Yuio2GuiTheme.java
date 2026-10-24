package com.example.addon.gui;

import com.example.addon.render.YuioThemeName;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.GuiThemes;

// 自绘卡片界面的主题名, 选中它时 GUI 键打开 YuioClickGui
public class Yuio2GuiTheme extends YuioGuiTheme {
    public static final String NAME = "yuio2";

    public Yuio2GuiTheme() {
        super();

        ((YuioThemeName) (Object) this).yuio$setName(NAME);
    }

    public static boolean isActive() {
        GuiTheme theme = GuiThemes.get();
        return theme != null && NAME.equals(theme.name);
    }

    public static void select() {
        GuiThemes.select(NAME);
    }
}
