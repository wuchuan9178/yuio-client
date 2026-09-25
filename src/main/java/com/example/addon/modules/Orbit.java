package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;
import java.util.Random;

public class Orbit extends Module {
    Random r = new Random();
    private SettingGroup sgGeneral = settings.getDefaultGroup();

    private Setting<Double> orbitRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("orbit-range")
        .description("Mosquito???")
        .defaultValue(50.0)
        .min(1.0)
        .sliderMax(128.0)
        .build());

    private Setting<Boolean> random = sgGeneral.add(new BoolSetting.Builder()
        .name("random-speed")
        .description("Randomize speed")
        .defaultValue(false)
        .build());

    private Setting<Double> orbitSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("orbit-speed")
        .description("The speed of the orbit in blocks per second")
        .defaultValue(1.0)
        .min(0.1)
        .sliderMax(50.0)
        .build());

    private Setting<Boolean> onlyPopped = sgGeneral.add(new BoolSetting.Builder()
        .name("only-popped")
        .description("Only circle strafe while popped totems")
        .defaultValue(false)
        .build());

    private Setting<Boolean> showCenter = sgGeneral.add(new BoolSetting.Builder()
        .name("show-center")
        .description("Show the center of the orbit")
        .defaultValue(false)
        .build());

    public final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .visible(() -> showCenter.get())
        .build());

    private final Setting<SettingColor> sideColor = sgGeneral.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Color of sides")
        .defaultValue(new SettingColor(255, 255, 255))
        .visible(() -> showCenter.get())
        .build());

    private final Setting<SettingColor> lineColor = sgGeneral.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Color of lines")
        .defaultValue(new SettingColor(255, 255, 255))
        .visible(() -> showCenter.get())
        .build());

    private Vec3d centerVec3d;
    private double orbitAngle;
    private boolean isPopped = false;

    public Orbit() {
        super(AddonTemplate.ASSIST, "Orbit", "Orbiting around the player position");
    }

    @EventHandler
    public void onGameJoined(GameJoinedEvent event) {
        orbitAngle = 0.0;
        isPopped = false;
        toggle();
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (!onlyPopped.get()) return;
        if (event.packet instanceof EntityStatusS2CPacket statusPacket) {
            if (getPacketStatus(statusPacket) == 35) {
                Entity entity = mc.world != null ? mc.world.getEntityById(getPacketEntityId(statusPacket)) : null;
                if (entity instanceof PlayerEntity && entity == mc.player) {
                    isPopped = true;
                }
            }
        }
    }

    @EventHandler
    public void onGameLeft(GameLeftEvent event) {
        orbitAngle = 0.0;
        isPopped = false;
        toggle();
    }

    @Override
    public void onActivate() {
        isPopped = false;
        centerVec3d = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        orbitAngle = 0.0;
    }

    @Override
    public void onDeactivate() {
        isPopped = false;
        centerVec3d = null;
        orbitAngle = 0.0;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (showCenter.get() && centerVec3d != null) {
            double x = centerVec3d.x;
            double y = centerVec3d.y;
            double z = centerVec3d.z;
            Box box = new Box(x - 0.2, y - 0.2, z - 0.2, x + 0.2, y + 0.2, z + 0.2);
            event.renderer.box(box, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (centerVec3d == null) return;

        if (!onlyPopped.get() || isPopped) {
            if (random.get()) {
                orbitAngle += r.nextDouble(1.0, 50.0);
            } else {
                orbitAngle += orbitSpeed.get();
            }

            double x = centerVec3d.x + orbitRange.get() * Math.cos(orbitAngle);
            double y = centerVec3d.y;
            double z = centerVec3d.z + orbitRange.get() * Math.sin(orbitAngle);

            mc.player.setPosition(x, y, z);
        }
    }

    private int getPacketStatus(EntityStatusS2CPacket packet) {
        try {
            try { return (int) EntityStatusS2CPacket.class.getMethod("getStatus").invoke(packet); } catch (NoSuchMethodException ignored) {}
            try { return (int) EntityStatusS2CPacket.class.getMethod("status").invoke(packet); } catch (NoSuchMethodException ignored) {}
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (Field f : EntityStatusS2CPacket.class.getDeclaredFields()) {
            if (f.getType() == int.class) {
                f.setAccessible(true);
                try {
                    return f.getInt(packet);
                } catch (IllegalAccessException ignored) {}
            }
        }
        return -1;
    }

    private int getPacketEntityId(EntityStatusS2CPacket packet) {
        try {
            try { return (int) EntityStatusS2CPacket.class.getMethod("getEntityId").invoke(packet); } catch (NoSuchMethodException ignored) {}
            try { return (int) EntityStatusS2CPacket.class.getMethod("entityId").invoke(packet); } catch (NoSuchMethodException ignored) {}
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (Field f : EntityStatusS2CPacket.class.getDeclaredFields()) {
            if (f.getType() == int.class) {
                f.setAccessible(true);
                try {
                    return f.getInt(packet);
                } catch (IllegalAccessException ignored) {}
            }
        }
        return -1;
    }
}
