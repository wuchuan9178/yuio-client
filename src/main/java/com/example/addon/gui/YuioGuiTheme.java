package com.example.addon.gui;

import com.example.addon.render.YuioThemeName;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WTopBar;

public class YuioGuiTheme extends MeteorGuiTheme {
    public static final String NAME = "yuio";
    public static final String METEOR_NAME = "Meteor";

    public YuioGuiTheme() {
        super();

        ((YuioThemeName) (Object) this).yuio$setName(NAME);
    }

    public static boolean isActive() {
        GuiTheme theme = GuiThemes.get();
        return theme != null && (NAME.equals(theme.name) || Yuio2GuiTheme.NAME.equals(theme.name));
    }

    public static void select(boolean yuio) {
        GuiThemes.select(yuio ? NAME : METEOR_NAME);
    }

    @Override
    public WTopBar topBar() {
        return w(new YuioTopBar());
    }
}
