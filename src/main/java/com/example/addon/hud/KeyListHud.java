package com.example.addon.hud;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class KeyListHud extends GlassHud {
    public static final HudElementInfo<KeyListHud> INFO = new HudElementInfo<>(
        AddonTemplate.HUD_GROUP, "key-list-hud", "Modules that have a key bound to them.", KeyListHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum Sort {
        Name("name"),
        Key("key");

        private final String name;

        Sort(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private final Setting<Sort> sort = sgGeneral.add(new EnumSetting.Builder<Sort>()
        .name("sort").defaultValue(Sort.Name).build());

    private final Setting<Boolean> activeFirst = sgGeneral.add(new BoolSetting.Builder()
        .name("active-first").defaultValue(true).build());

    private final Setting<Boolean> header = sgGeneral.add(new BoolSetting.Builder()
        .name("header").defaultValue(false).build());

    private final Setting<Boolean> keysLeft = sgGeneral.add(new BoolSetting.Builder()
        .name("keys-left").defaultValue(false).build());

    private final Setting<Integer> max = sgGeneral.add(new IntSetting.Builder()
        .name("max-entries").defaultValue(8).min(1).sliderRange(1, 40).build());

    private final Setting<SettingColor> enabledColor = sgGeneral.add(
        new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
            .name("enabled-color").defaultValue(new SettingColor(255, 255, 255)).build());

    private final Setting<SettingColor> keyColor = sgGeneral.add(
        new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
            .name("key-color").defaultValue(new SettingColor(150, 210, 255)).build());

    private record Row(String name, String key, boolean active) {
    }

    public KeyListHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        List<Row> rows = collect();
        double base = unit();
        double padding = 7 * base;
        double rowHeight = IslandRenderer.textHeight(renderer, base) + 3 * base;
        double columnGap = 10 * base;

        double nameWidth = 0;
        double keyWidth = 0;

        for (Row row : rows) {
            nameWidth = Math.max(nameWidth, IslandRenderer.textWidth(renderer, row.name(), base));
            keyWidth = Math.max(keyWidth, IslandRenderer.textWidth(renderer, row.key(), base));
        }

        double headerHeight = header.get() ? IslandRenderer.textHeight(renderer, 0.75 * base) + 3 * base : 0;
        double width = padding * 2 + nameWidth + columnGap + keyWidth;
        double height = padding * 2 + headerHeight + Math.max(1, rows.size()) * rowHeight;

        setSize(width, height);
        panel(x, y, width, height);

        double cursorY = y + padding;

        if (header.get()) {
            IslandRenderer.text(renderer, "Keys " + rows.size(), x + padding, cursorY, dimColor.get(), true, 0.75 * base);
            cursorY += headerHeight;
        }

        if (rows.isEmpty()) {
            IslandRenderer.text(renderer, "nothing bound", x + padding, cursorY, faded(dimColor.get(), 0.7), true, base);
            return;
        }

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            Color nameColor = row.active() ? enabledColor.get() : dimColor.get();
            Color boundColor = row.active() ? keyColor.get() : faded(keyColor.get(), 0.6);

            double leftX = x + padding;
            double rightX = x + width - padding;

            if (keysLeft.get()) {
                IslandRenderer.text(renderer, row.key(), leftX, cursorY, boundColor, true, base);
                IslandRenderer.text(renderer, row.name(), rightX - IslandRenderer.textWidth(renderer, row.name(), base),
                    cursorY, nameColor, true, base);
            } else {
                IslandRenderer.text(renderer, row.name(), leftX, cursorY, nameColor, true, base);
                IslandRenderer.text(renderer, row.key(), rightX - IslandRenderer.textWidth(renderer, row.key(), base),
                    cursorY, boundColor, true, base);
            }

            cursorY += rowHeight;
            if (i < rows.size() - 1) divider(x + padding, cursorY - rowHeight * 0.5 + 1, width - padding * 2);
        }
    }

    private List<Row> collect() {
        List<Row> rows = new ArrayList<>();

        for (Module module : Modules.get().getAll()) {
            if (!module.keybind.isSet()) continue;
            rows.add(new Row(module.title, module.keybind.toString(), module.isActive()));
        }

        if (sort.get() == Sort.Key) rows.sort(Comparator.comparing(Row::key, String.CASE_INSENSITIVE_ORDER));
        else rows.sort(Comparator.comparing(Row::name, String.CASE_INSENSITIVE_ORDER));

        if (activeFirst.get()) rows.sort(Comparator.comparing(Row::active).reversed());

        return rows.size() > max.get() ? new ArrayList<>(rows.subList(0, max.get())) : rows;
    }
}
