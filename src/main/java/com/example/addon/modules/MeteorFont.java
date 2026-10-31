package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.renderer.Fonts;
import meteordevelopment.meteorclient.renderer.text.BuiltinFontFace;
import meteordevelopment.meteorclient.renderer.text.FontFace;
import meteordevelopment.meteorclient.renderer.text.FontFamily;
import meteordevelopment.meteorclient.renderer.text.FontInfo;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.FontUtils;

import java.io.InputStream;

public class MeteorFont extends Module {
    public static MeteorFont INSTANCE;

    public enum FontChoice {
        GoogleSansCode("GoogleSansCode", "Google Sans Code"),
        GoogleSansBold("GoogleSansBold", "Google Sans Bold"),
        HarmonyOSBold("HarmonyOSBold", "HarmonyOS Sans SC Bold");

        private final String file;
        private final String label;

        FontChoice(String file, String label) {
            this.file = file;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static FontFace face;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<FontChoice> font = sgGeneral.add(new EnumSetting.Builder<FontChoice>()
        .name("font")
        .description("Which font replaces Meteor's default one.")
        .defaultValue(FontChoice.GoogleSansCode)
        .onChanged(choice -> { if (isActive()) apply(); })
        .build());

    private final Setting<Boolean> force = sgGeneral.add(new BoolSetting.Builder()
        .name("force")
        .description("Force Meteor to use the selected font. Off = only register it, pick it in Meteor's settings.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> reload = sgGeneral.add(new BoolSetting.Builder()
        .name("reload")
        .description("Re-apply the font after a resource reload.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug")
        .description("Log font loading details.")
        .defaultValue(false)
        .build());

    public MeteorFont() {
        super(AddonTemplate.SETTINGS, "meteor-font", "Replace Meteor's font with a custom TTF.");
        INSTANCE = this;
    }

    public static void register(FontChoice choice) {
        if (face != null) return;

        try (InputStream in = MeteorFont.class.getResourceAsStream("/assets/meteor-client/fonts/" + choice.file + ".ttf")) {
            if (in == null) {
                AddonTemplate.LOG.warn("[yuio-font] {} not found", choice.file);
                return;
            }

            FontInfo info = FontUtils.getFontInfo(in);
            FontFamily family = Fonts.getFamily(info.family());

            if (family != null && family.get(info.type()) != null) {
                face = family.get(info.type());
                return;
            }
            if (family == null) {
                family = new FontFamily(info.family());
                Fonts.FONT_FAMILIES.add(family);
            }

            BuiltinFontFace builtin = new BuiltinFontFace(info, choice.file);
            face = builtin;
            family.addFont(builtin);

            if (INSTANCE != null && INSTANCE.debug.get()) {
                AddonTemplate.LOG.info("[yuio-font] registered {} as '{}' ({})", choice.file, info.family(), info.type());
            }
        } catch (Exception e) {
            AddonTemplate.LOG.warn("[yuio-font] failed to load {}: {}", choice.file, e.getMessage());
        }
    }

    private void apply() {
        face = null;
        register(font.get());

        if (face != null) Fonts.load(face);
    }

    public static void onFontsRefresh() {
        FontChoice choice = INSTANCE != null ? INSTANCE.font.get() : FontChoice.GoogleSansCode;

        // 字体表被重建，旧 family 已失效
        face = null;
        register(choice);

        if (face != null && INSTANCE != null && INSTANCE.isActive() && INSTANCE.force.get() && INSTANCE.reload.get()) {
            Fonts.load(face);
        }
    }

    @Override
    public void onActivate() {
        apply();
    }

    @Override
    public void onDeactivate() {
        if (face != null) Fonts.load(Fonts.DEFAULT_FONT);
    }
}
