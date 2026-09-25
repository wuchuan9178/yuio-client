// AutoSex.java — 修复 noClip 编译错误，使用反射设置无碰撞，保持浮空抽插
package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

public class AutoSex extends Module {
    private final SettingGroup sgGeneral, sgPos, sgMessage, sgRender;
    private final Setting<Mode> targetMode;
    private final Setting<Double> range;
    private final Setting<Boolean> sexPos, randomCum, message, isRender;
    private final Setting<Integer> sexDelay, delay;
    private final Setting<Style> sexStyle;
    private final Setting<Boolean> random;
    private final Setting<List<String>> messages;
    private final Setting<Double> behindDistance;

    private final List<Entity> targets = new ArrayList<>();
    private final String regex = "[A-Za-z0-9_]+";
    private int messageI, timer, timerSex, sexI;
    private static double renderY;
    private double addition;
    private Entity target;

    public AutoSex() {
        super(AddonTemplate.OTHER, "AutoSex", "sex sex sex");

        sgGeneral = settings.getDefaultGroup();
        sgPos = settings.createGroup("Sex Position");
        sgMessage = settings.createGroup("Message");
        sgRender = settings.createGroup("Render");

        targetMode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("target-mode").defaultValue(Mode.Automatic).build());

        range = sgGeneral.add(new DoubleSetting.Builder()
            .name("range").defaultValue(6).min(0).sliderMax(6).build());

        sexPos = sgGeneral.add(new BoolSetting.Builder()
            .name("sex-position").defaultValue(false).build());

        randomCum = sgGeneral.add(new BoolSetting.Builder()
            .name("random-cum").defaultValue(false).build());

        message = sgGeneral.add(new BoolSetting.Builder()
            .name("message").defaultValue(false).build());

        behindDistance = sgPos.add(new DoubleSetting.Builder()
            .name("behind-distance")
            .defaultValue(0.65)
            .min(0.2)
            .max(1.5)
            .build());

        sexDelay = sgPos.add(new IntSetting.Builder()
            .name("delay").defaultValue(3).sliderRange(0, 20).visible(sexPos::get).build());

        sexStyle = sgPos.add(new EnumSetting.Builder<Style>()
            .name("style").defaultValue(Style.GulpGulp).visible(sexPos::get).build());

        delay = sgMessage.add(new IntSetting.Builder()
            .name("delay").defaultValue(80).min(0).sliderMax(200).visible(message::get).build());

        random = sgMessage.add(new BoolSetting.Builder()
            .name("randomize").defaultValue(true).visible(message::get).build());

        messages = sgMessage.add(new StringListSetting.Builder()
            .name("messages")
            .defaultValue(List.of("I want you to make me your filthy slut~", "Fuck me so hard I can't walk straight~"))
            .visible(message::get).build());

        isRender = sgRender.add(new BoolSetting.Builder()
            .name("render").defaultValue(true).build());
    }

    @Override
    public void onActivate() {
        if (targetMode.get() == Mode.Automatic) setTarget();
    }

    private boolean entityCheck(Entity entity) {
        if (entity == null || entity == mc.player) return false;
        if (entity instanceof LivingEntity living && living.isDead()) return false;
        if (!entity.isAlive()) return false;
        if (!PlayerUtils.isWithin(entity, range.get())) return false;
        if (!PlayerUtils.canSeeEntity(entity) && !PlayerUtils.isWithin(entity, range.get())) return false;
        return Pattern.matches(regex, EntityUtils.getName(entity)) || entity.isPlayer();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (target != null && "yeai_QQ".equals(target.getName().getString())) {
            toggle();
            mc.player.sendMessage(Text.literal("yeai_QQ,已自动关闭，草你妈。").formatted(Formatting.RED), false);
            return;
        }

        if (randomCum.get() && shouldCum()) {
            ItemStack milk = new ItemStack(Items.MILK_BUCKET);
            milk.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§4§l" + EntityUtils.getName(mc.player) + "'s §f§lCUM"));
            for (int i = 9; i < 11; i++) mc.player.getInventory().setStack(i, milk);
            for (int i = 9; i < 11; i++) InvUtils.drop().slot(i);
        }

        if (target != null) {
            checkEntity();
            if (sexPos.get()) {
                setNoClip(true);
                freezeAnimation();
                if (timerSex <= 0) {
                    float targetYaw = target.getYaw();
                    double rad = Math.toRadians(targetYaw);
                    double facingX = -Math.sin(rad);
                    double facingZ = Math.cos(rad);
                    double baseDist = behindDistance.get();

                    double dist = (sexI == 0) ? baseDist : baseDist + 0.5;

                    double x, y, z;
                    if (sexStyle.get() == Style.GulpGulp) {
                        double targetHeadY = target.getY() + target.getEyeHeight(target.getPose());
                        x = target.getX() + facingX * dist;
                        y = targetHeadY - 0.8;
                        z = target.getZ() + facingZ * dist;
                        mc.player.setPosition(x, y, z);
                        mc.player.setYaw(targetYaw + 180);
                    } else {
                        x = target.getX() - facingX * dist;
                        y = target.getY();
                        z = target.getZ() - facingZ * dist;
                        mc.player.setPosition(x, y, z);
                        mc.player.setYaw(targetYaw);
                    }

                    mc.player.setPitch(0f);
                    mc.player.setVelocity(0, 0, 0);
                    timerSex = sexDelay.get();
                    sexI = 1 - sexI;
                } else timerSex--;
            } else {
                setNoClip(false);
            }

            if (message.get()) {
                if (messages.get().isEmpty()) return;
                if (timer <= 0) {
                    int i = random.get() ? Utils.random(0, messages.get().size() - 1) : messageI++ % messages.get().size();
                    ChatUtils.sendPlayerMsg(messages.get().get(i).replaceAll("%player%", EntityUtils.getName(target)));
                    timer = delay.get();
                } else timer--;
            }
        }
    }

    // 反射设置 noClip 字段
    private void setNoClip(boolean enabled) {
        if (mc.player == null) return;
        try {
            Field field = Entity.class.getDeclaredField("noClip");
            field.setAccessible(true);
            field.setBoolean(mc.player, enabled);
        } catch (Exception ignored) {}
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (sexPos.get() && event.packet instanceof PlayerMoveC2SPacket) {
            event.cancel();
        }
    }

    private void freezeAnimation() {
        if (mc.player == null) return;
        try {
            Field limbAnimatorField = LivingEntity.class.getDeclaredField("limbAnimator");
            limbAnimatorField.setAccessible(true);
            Object animator = limbAnimatorField.get(mc.player);
            if (animator != null) {
                Field speedField = animator.getClass().getDeclaredField("speed");
                speedField.setAccessible(true);
                speedField.setFloat(animator, 0f);
            }

            for (String name : new String[]{"limbAngle", "lastLimbAngle", "limbDistance", "lastLimbDistance"}) {
                Field field = LivingEntity.class.getDeclaredField(name);
                field.setAccessible(true);
                field.setFloat(mc.player, 0f);
            }
        } catch (Exception ignored) {}
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (target == null || !isRender.get()) return;
        Vec3d last = null;
        if (addition > 360) addition = 0;
        Vec3d tp = new Vec3d(target.getX(), target.getY(), target.getZ());
        for (int i = 0; i < 360; i++) {
            double rad = Math.toRadians(i);
            Vec3d c = new Vec3d(tp.x + Math.sin(rad), tp.y + getRenderY(), tp.z + Math.cos(rad));
            if (last != null) event.renderer.line(last.x, last.y, last.z, c.x, c.y, c.z, new Color(255, 0, 255, 255));
            last = c;
        }
        addition += 1;
    }

    @Override
    public void onDeactivate() {
        target = null;
        if (mc.player != null) {
            mc.player.setSneaking(false);
            setNoClip(false);
            mc.player.setVelocity(0, 0, 0);
        }
    }

    private void checkEntity() {
        if (target == null) return;
        if (mc.world.getPlayerByUuid(target.getUuid()) == null) {
            target = null;
            if (targetMode.get() == Mode.Automatic) setTarget();
        }
    }

    private void setTarget() {
        TargetUtils.getList(targets, this::entityCheck, SortPriority.LowestDistance, 1);
        if (!targets.isEmpty()) {
            target = targets.get(0);
            startMsg();
        }
    }

    private static double getRenderY() {
        Random rand = new Random();
        double randomValue = 0.2 - 0.05 * rand.nextDouble();
        if (renderY >= 0.3) renderY = 0;
        renderY += randomValue;
        return renderY;
    }

    private void startMsg() {
        if (message.get()) ChatUtils.sendPlayerMsg("Come here " + EntityUtils.getName(target) + " I want you uwu");
    }

    private static boolean shouldCum() { return Math.random() <= 0.1; }

    private enum Mode { MiddleClick, Automatic }
    private enum Style { GulpGulp, Doggy }
}
