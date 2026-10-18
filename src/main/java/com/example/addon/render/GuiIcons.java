package com.example.addon.render;

import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import meteordevelopment.meteorclient.renderer.Texture;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public final class GuiIcons {
    public static final String HOME = "home";
    public static final String MODULES = "tab_modules";
    public static final String CONFIG = "tab_config";
    public static final String GUI = "tab_gui";
    public static final String HUD = "tab_hud";
    public static final String FRIENDS = "tab_friends";
    public static final String MACROS = "tab_macros";
    public static final String PROFILES = "tab_profiles";

    public static final String BACK = "back";
    public static final String MP_ADD = "mp_add";
    public static final String MP_CONNECT = "mp_connect";
    public static final String MP_JOIN = "mp_join";
    public static final String MP_EDIT = "mp_edit";
    public static final String MP_DELETE = "mp_delete";
    public static final String MP_REFRESH = "mp_refresh";
    public static final String MP_BACK = "mp_back";

    public static final String SP_ADD = "sp_add";
    public static final String SP_CONNECT = "sp_connect";
    public static final String SP_JOIN = "sp_join";
    public static final String SP_EDIT = "sp_edit";
    public static final String SP_DELETE = "sp_delete";
    public static final String SP_REFRESH = "sp_refresh";
    public static final String SP_BACK = "sp_back";

    private static final Map<String, GpuTextureView> CACHE = new HashMap<>();

    private GuiIcons() {
    }

    public static void preload() {
        get(HOME);
        get(MODULES);
        get(CONFIG);
        get(GUI);
        get(HUD);
        get(FRIENDS);
        get(MACROS);
        get(PROFILES);
        get(BACK);
    }

    public static Identifier id(String name) {
        return Identifier.of("yuio", "textures/gui/" + name + ".png");
    }

    public static GpuTextureView get(String name) {
        if (CACHE.containsKey(name)) return CACHE.get(name);

        Texture texture = Texture.readResource("/assets/yuio/textures/gui/" + name + ".png", true, FilterMode.LINEAR);
        GpuTextureView view = texture == null ? null : texture.getGlTextureView();
        CACHE.put(name, view);

        return view;
    }
}
