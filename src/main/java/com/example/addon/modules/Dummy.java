package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.utils.DummyEntity;
import com.example.addon.utils.DummyLike;
import com.example.addon.utils.DummyLogic;
import com.example.addon.utils.RealDummyEntity;
import dev.sherii.yuio.AModule;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

public class Dummy extends AModule {
    public enum Mode {
        Still,
        Follow,
        Wander,
        Circle
    }

    public enum Appearance {
        Bot,
        Player
    }

    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Random RNG = new Random();

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgEquip = settings.createGroup("Equip");
    private final SettingGroup sgLogs = settings.createGroup("Logs");

    private final Setting<String> dummyName = sgGeneral.add(new StringSetting.Builder()
        .name("name")
        .description("Name shown above the dummy.")
        .defaultValue("yeai_QQ")
        .build());

    private final Setting<Appearance> appearance = sgGeneral.add(new EnumSetting.Builder<Appearance>()
        .name("appearance")
        .description("Bot = meteor fake player, Player = real player entity (targetable).")
        .defaultValue(Appearance.Player)
        .build());

    private final Setting<Integer> health = sgGeneral.add(new IntSetting.Builder()
        .name("health")
        .description("Max health of the dummy.")
        .defaultValue(20)
        .build());

    private final Setting<Integer> totems = sgGeneral.add(new IntSetting.Builder()
        .name("totems")
        .description("How many totems the dummy carries.")
        .defaultValue(3)
        .build());

    private final Setting<Boolean> infiniteTotems = sgGeneral.add(new BoolSetting.Builder()
        .name("infinite-totems")
        .description("Dummy never runs out of totems.")
        .defaultValue(false)
        .build());

    private final Setting<Integer> hurtInterval = sgGeneral.add(new IntSetting.Builder()
        .name("hurt-interval")
        .description("Ticks between two accepted hits (vanilla is 10).")
        .defaultValue(10)
        .build());

    private final Setting<Integer> respawnDelay = sgGeneral.add(new IntSetting.Builder()
        .name("respawn-delay")
        .description("Ticks to wait after death before respawning.")
        .defaultValue(40)
        .build());

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("How the dummy moves.")
        .defaultValue(Mode.Follow)
        .build());

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Blocks per tick.")
        .defaultValue(0.12)
        .sliderRange(0.02, 0.6)
        .build());

    private final Setting<Double> followDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("follow-distance")
        .description("Distance kept when following you.")
        .defaultValue(3.0)
        .sliderRange(1.0, 10.0)
        .build());

    private final Setting<DummyLogic.Gear> gear = sgEquip.add(new EnumSetting.Builder<DummyLogic.Gear>()
        .name("gear")
        .description("Armor set the dummy wears.")
        .defaultValue(DummyLogic.Gear.Diamond)
        .build());

    private final Setting<Integer> protection = sgEquip.add(new IntSetting.Builder()
        .name("protection")
        .description("Protection level on every armor piece.")
        .defaultValue(0)
        .build());

    private final Setting<Integer> unbreaking = sgEquip.add(new IntSetting.Builder()
        .name("unbreaking")
        .description("Unbreaking level on every armor piece.")
        .defaultValue(0)
        .build());

    private final Setting<net.minecraft.item.Item> mainHand = sgEquip.add(new ItemSetting.Builder()
        .name("main-hand")
        .description("Item held in the dummy's main hand. Empty = fists.")
        .defaultValue(Items.AIR)
        .build());

    private final Setting<Boolean> logToFile = sgLogs.add(new BoolSetting.Builder()
        .name("log-to-file")
        .description("Write dummy events to the game log.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> logToChat = sgLogs.add(new BoolSetting.Builder()
        .name("log-to-chat")
        .description("Write dummy events to chat.")
        .defaultValue(true)
        .build());

    private final java.util.function.Consumer<String> logSink = this::onDummyLog;

    private void onDummyLog(String message) {
        if (logToFile.get()) AddonTemplate.LOG.info("[yuio-dummy] {}", message);
        if (logToChat.get()) info(message);
    }

    private AbstractClientPlayerEntity dummy;
    private Appearance spawnedAppearance;
    private Vec3d wanderTarget;
    private int wanderTicks;
    private double circleAngle;

    public Dummy() {
        super(AddonTemplate.OTHER, "Dummy", "Local dummy for testing: takes damage, pops totems, dies and can move.");
    }

    @Override
    public void onActivate() {
        spawnDummy();
    }

    @Override
    public void onDeactivate() {
        despawnDummy();
    }

    private void spawnDummy() {
        if (mc.player == null || mc.world == null) {
            error("No world loaded.");
            toggle();
            return;
        }

        Vec3d pos = mc.player.getEntityPos();
        Appearance appearanceValue = appearance.get();

        if (appearanceValue == Appearance.Player) {
            RealDummyEntity entity = new RealDummyEntity(mc.world, dummyName.get(), health.get().floatValue(), totems.get(), respawnDelay.get());
            entity.setHome(pos);
            entity.setPosition(pos);
            entity.setLogSink(logSink);
            entity.spawn();
            dummy = entity;
        } else {
            DummyEntity entity = new DummyEntity(dummyName.get(), health.get().floatValue(), totems.get(), respawnDelay.get());
            entity.setHome(pos);
            entity.setPosition(pos);
            entity.setLogSink(logSink);
            entity.spawn();
            dummy = entity;
        }

        spawnedAppearance = appearanceValue;
        wanderTarget = null;
        wanderTicks = 0;
        circleAngle = 0.0;
        logSink.accept("[spawn] " + appearanceValue + " name " + dummyName.get() + ", hp " + health.get() + ", totems "
            + (infiniteTotems.get() ? "inf" : totems.get()) + ", x " + String.format("%.1f", dummy.getX())
            + " y " + String.format("%.1f", dummy.getY()) + " z " + String.format("%.1f", dummy.getZ()));
    }

    private void despawnDummy() {
        if (dummy != null) {
            logSink.accept("[despawn] " + dummyName.get());
            if (dummy instanceof RealDummyEntity real) {
                real.despawn();
            } else if (dummy instanceof DummyEntity bot) {
                bot.despawn();
            }
            dummy = null;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (dummy == null || dummy.isRemoved() || dummy.getEntityWorld() != mc.world || spawnedAppearance != appearance.get()) {
            if (dummy != null) despawnDummy();
            spawnDummy();
            if (dummy == null) return;
        }

        if (!(dummy instanceof DummyLike like)) return;
        DummyLogic logic = like.logic();
        logic.configure(hurtInterval.get(), infiniteTotems.get(), gear.get(), protection.get(), unbreaking.get(), mainHand.get());

        Vec3d home = logic.getHome();

        switch (mode.get()) {
            case Still -> {
            }
            case Follow -> {
                Vec3d fromPlayer = new Vec3d(dummy.getX() - mc.player.getX(), 0.0, dummy.getZ() - mc.player.getZ());
                double len = Math.sqrt(fromPlayer.x * fromPlayer.x + fromPlayer.z * fromPlayer.z);
                Vec3d dir = len < 0.01 ? new Vec3d(1.0, 0.0, 0.0) : fromPlayer.multiply(1.0 / len);
                Vec3d target = new Vec3d(mc.player.getX(), dummy.getY(), mc.player.getZ()).add(dir.multiply(followDistance.get()));
                logic.stepTowards(target, speed.get());
            }
            case Wander -> {
                Vec3d here = new Vec3d(dummy.getX(), dummy.getY(), dummy.getZ());
                if (wanderTarget == null || wanderTicks <= 0 || here.squaredDistanceTo(wanderTarget) < 0.64) {
                    wanderTarget = home.add((RNG.nextDouble() - 0.5) * 12.0, 0.0, (RNG.nextDouble() - 0.5) * 12.0);
                    wanderTicks = 40 + RNG.nextInt(60);
                }
                wanderTicks--;
                logic.stepTowards(wanderTarget, speed.get());
            }
            case Circle -> {
                circleAngle += speed.get() * 0.08;
                Vec3d target = home.add(Math.cos(circleAngle) * 3.0, 0.0, Math.sin(circleAngle) * 3.0);
                logic.stepTowards(target, speed.get());
            }
        }
    }

    @Override
    public String getInfoString() {
        if (dummy == null || dummy.isRemoved() || !(dummy instanceof DummyLike like)) return null;
        String totemText = infiniteTotems.get() ? "inf" : String.valueOf(like.logic().getTotems());
        return String.format("%.0f/%d hp, %s totem", dummy.getHealth(), health.get(), totemText);
    }
}
