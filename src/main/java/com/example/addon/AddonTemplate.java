package com.example.addon;

import com.example.addon.commands.CommandExample;
import com.example.addon.hud.FriendHud;
import com.example.addon.hud.ImageHud;
import com.example.addon.hud.KeyListHud;
import com.example.addon.hud.RadarHud;
import com.example.addon.hud.YuioIsland;
import com.example.addon.modules.*;
import com.mojang.logging.LogUtils;
import dev.sherii.yuio.settings.DoubleListSetting;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.gui.utils.SettingsWidgetFactory;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();

    // 模块分类
    public static final Category MAIN = new Category("main");
    public static final Category ASSIST = new Category("assist");
    public static final Category OTHER = new Category("Other");

    // hud分类
    public static final HudGroup HUD_GROUP = new HudGroup("yuio");

    @Override
    public void onInitialize() {
        LOG.info("Initializing yuio Meteor Addon");

        SettingsWidgetFactory.registerCustomFactory(DoubleListSetting.class,
            theme -> (table, setting) -> DoubleListSetting.doubleListW(table, (DoubleListSetting) setting));

        MeteorClient.EVENT_BUS.registerLambdaFactory("dev.sherii.yuio",
            (lookupInMethod, klass) -> (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));

        //Modules
        Modules.get().add(new AutoDick());
        Modules.get().add(new AutoSex());
        Modules.get().add(new KeepBouncy());
        Modules.get().add(new TPbot());
        Modules.get().add(new AutoEz());
        Modules.get().add(MaceMissAura.INSTANCE);
        Modules.get().add(new MaceMissAurav2());
        Modules.get().add(new SpearArmorBreaker());
        Modules.get().add(new AntiMiss());
        Modules.get().add(new Orbit());
        Modules.get().add(new SuperVClip());
        Modules.get().add(new MaceAura());
        Modules.get().add(new LacrymiraAura());
        Modules.get().add(new Tracking());
        Modules.get().add(new IQBooster());
        Modules.get().add(new CustomPrefix());
        Modules.get().add(new AntiLag());
        Modules.get().add(new PacketLimiter());
        Modules.get().add(new PacketLimitTester());
        Modules.get().add(new CustomSoundAlerts());
        Modules.get().add(new Animations());
        Modules.get().add(new HorizontalVClip());
        Modules.get().add(new CustomFonts());
        Modules.get().add(new AutoKouzi());
        Modules.get().add(new TotemParticle());
        Modules.get().add(new SmoothCamera());
        Modules.get().add(new WebGui());
        Modules.get().add(new AutoElytraMace());
        Modules.get().add(new AutoCopyChat());

        // MaceMissAura依赖
        Modules.get().add(dev.sherii.yuio.modules.settings.TpSetting.INSTANCE);
        Modules.get().add(dev.sherii.yuio.modules.settings.PredictSetting.INSTANCE);

        //Commands
        Commands.add(new CommandExample());

        // HUD
        Hud.get().register(YuioIsland.INFO);
        Hud.get().register(ImageHud.INFO);
        Hud.get().register(FriendHud.INFO);
        Hud.get().register(RadarHud.INFO);
        Hud.get().register(KeyListHud.INFO);

    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(MAIN);
        Modules.registerCategory(ASSIST);
        Modules.registerCategory(OTHER);
    }

    @Override
    public String getPackage() {
        return "com.example.addon.modules";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("MeteorDevelopment", "meteor-addon-yuio");
    }
}
