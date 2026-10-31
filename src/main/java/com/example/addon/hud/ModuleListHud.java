package com.example.addon.hud;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModuleListHud extends GlassHud {
    public static final HudElementInfo<ModuleListHud> INFO = new HudElementInfo<>(
        AddonTemplate.HUD_GROUP, "module-list-hud", "Active modules with a fading list.", ModuleListHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> additionalInfo = sgGeneral.add(new BoolSetting.Builder()
        .name("additional-info")
        .description("Show module info strings.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyBind = sgGeneral.add(new BoolSetting.Builder()
        .name("only-bind")
        .description("Only list modules with a keybind.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> shadow = sgGeneral.add(new BoolSetting.Builder()
        .name("shadow")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> moduleColor = sgGeneral.add(new ColorSetting.Builder()
        .name("module-color")
        .defaultValue(new SettingColor(255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> infoColor = sgGeneral.add(new ColorSetting.Builder()
        .name("info-color")
        .defaultValue(new SettingColor(0, 255, 0))
        .build()
    );

    private final Setting<SettingColor> tagColor = sgGeneral.add(new ColorSetting.Builder()
        .name("tag-color")
        .defaultValue(new SettingColor(0, 150, 255))
        .build()
    );

    private final Map<Module, Double> fades = new HashMap<>();

    public ModuleListHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        double base = unit();
        double padding = padding();
        double rowHeight = IslandRenderer.textHeight(renderer, base) + 3 * base;
        double tagWidth = 3 * base;

        List<Module> modules = new ArrayList<>();
        for (Module module : Modules.get().getAll()) {
            if (!module.isActive()) continue;
            if (onlyBind.get() && !module.keybind.isSet()) continue;
            modules.add(module);
        }
        modules.sort(Comparator.comparingDouble(m -> -IslandRenderer.textWidth(renderer, text(m), base)));

        int rows = Math.max(1, modules.size());
        double height = rows * rowHeight + padding * 2;
        setSize(height <= padding * 2 ? 0 : Math.max(80, width(renderer, modules, base, padding, tagWidth)), height);

        fades.keySet().removeIf(m -> modules.stream().noneMatch(active -> active == m));

        if (!modules.isEmpty()) {
            panel(x, y, width(renderer, modules, base, padding, tagWidth), height);
            IslandRenderer.round(x + padding * 0.35, y + padding * 0.5, tagWidth, height - padding, tagWidth * 0.5, tagColor.get());
        }

        double cursorY = y + padding;
        for (Module module : modules) {
            double fade = fades.merge(module, 0.0, (old, ignored) -> old + (1.0 - old) * 0.15);

            Color nameColor = faded(moduleColor.get(), fade);
            Color moduleInfoColor = faded(infoColor.get(), fade);
            double offset = (1.0 - fade) * 6 * base;

            double textX = x + padding + tagWidth + offset;
            IslandRenderer.text(renderer, module.title, textX, cursorY, nameColor, shadow.get(), base);

            String info = additionalInfo.get() ? safe(module.getInfoString()) : "";
            if (!info.isEmpty()) {
                double nameWidth = IslandRenderer.textWidth(renderer, module.title, base);
                double spaceWidth = IslandRenderer.textWidth(renderer, " ", base);
                IslandRenderer.text(renderer, info, textX + nameWidth + spaceWidth, cursorY, moduleInfoColor, shadow.get(), base);
            }

            cursorY += rowHeight;
        }
    }

    private double width(HudRenderer renderer, List<Module> modules, double base, double padding, double tagWidth) {
        double maxWidth = 0;
        for (Module module : modules) maxWidth = Math.max(maxWidth, IslandRenderer.textWidth(renderer, text(module), base));
        return maxWidth + padding * 2 + tagWidth + 6 * base;
    }

    private String text(Module module) {
        String info = additionalInfo.get() ? safe(module.getInfoString()) : "";
        return module.title + (info.isEmpty() ? "" : " " + info);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
