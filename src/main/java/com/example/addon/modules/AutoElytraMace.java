package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

import java.util.Comparator;

public class AutoElytraMace extends Module {
    public static AutoElytraMace INSTANCE;

    public enum State { Idle, Takeoff, FlyTo, Dive, SwapChest, Attack, Recycle }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range").defaultValue(60.0).min(5).sliderRange(5, 200).build());

    public final Setting<Double> aboveHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("above-height").defaultValue(10.0).min(2).sliderRange(2, 30).build());

    public final Setting<Double> attackDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("attack-distance").defaultValue(2.0).min(0.5).sliderRange(0.5, 4).build());

    public final Setting<Double> rotationSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("rotation-speed").defaultValue(12.0).min(1).sliderRange(1, 45).build());

    public final Setting<Integer> fireworkDelay = sgGeneral.add(new IntSetting.Builder()
        .name("firework-delay").defaultValue(25).min(5).sliderRange(5, 60).build());

    public final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-switch").defaultValue(true).build());

    private State state = State.Idle;
    private PlayerEntity target = null;
    private int fireworkTimer = 0;
    private int actionTimer = 0;

    public AutoElytraMace() {
        super(AddonTemplate.MAIN, "auto-elytra-mace", "Elytra fly, dive and mace the target.");
        INSTANCE = this;
    }

    @Override
    public void onActivate() {
        state = State.Idle;
        target = null;
        fireworkTimer = 0;
        actionTimer = 0;
    }

    @Override
    public void onDeactivate() {
        target = null;
        state = State.Idle;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (target == null || target.isDead() || target.getHealth() <= 0) {
            target = findTarget();
            if (target == null) return;
            state = State.Takeoff;
        }

        switch (state) {
            case Takeoff    -> handleTakeoff();
            case FlyTo      -> handleFlyTo();
            case Dive       -> handleDive();
            case SwapChest  -> handleSwapChest();
            case Attack     -> handleAttack();
            case Recycle    -> handleRecycle();
            default -> {}
        }

        if (fireworkTimer > 0) fireworkTimer--;
        if (actionTimer > 0) actionTimer--;
    }

    private void handleTakeoff() {
        if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) {
            if (!equipArmor(Items.ELYTRA)) {
                error("No elytra found.");
                toggle();
            }
            return;
        }

        if (mc.player.isOnGround()) {
            mc.player.jump();
            return;
        }

        if (!mc.player.isGliding()) {
            useFirework();
        } else if (mc.player.getVelocity().lengthSquared() > 0.05) {
            state = State.FlyTo;
        }
    }

    private void handleFlyTo() {
        double dx = target.getX() - mc.player.getX();
        double dz = target.getZ() - mc.player.getZ();
        double dy = (target.getY() + aboveHeight.get()) - mc.player.getY();
        double horiz = Math.sqrt(dx * dx + dz * dz);

        smoothLook(
            (float) Math.toDegrees(Math.atan2(-dx, dz)),
            (float) Math.toDegrees(-Math.atan2(dy, horiz))
        );

        if (fireworkTimer <= 0 && mc.player.isGliding()) {
            useFirework();
        }

        if (Math.abs(dy) < 3 && horiz < 8) {
            state = State.Dive;
        }
    }

    private void handleDive() {
        double dx = target.getX() - mc.player.getX();
        double dy = target.getY() + target.getHeight() * 0.5 - mc.player.getEyePos().y;
        double dz = target.getZ() - mc.player.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        double dist = Math.sqrt(horiz * horiz + dy * dy);

        smoothLook(
            (float) Math.toDegrees(Math.atan2(-dx, dz)),
            (float) Math.toDegrees(-Math.atan2(dy, horiz))
        );

        if (dist <= attackDistance.get() + 1.5) state = State.SwapChest;
    }

    private void handleSwapChest() {
        if (!autoSwitch.get()) { state = State.Attack; return; }

        if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
            if (!equipArmor(Items.NETHERITE_CHESTPLATE)
                && !equipArmor(Items.DIAMOND_CHESTPLATE)
                && !equipArmor(Items.IRON_CHESTPLATE)
                && !equipArmor(Items.CHAINMAIL_CHESTPLATE)
                && !equipArmor(Items.GOLDEN_CHESTPLATE)
                && !equipArmor(Items.LEATHER_CHESTPLATE)) {
                error("No chestplate found.");
                toggle();
                return;
            }
        }
        state = State.Attack;
    }

    private void handleAttack() {
        if (target == null) return;

        double dx = target.getX() - mc.player.getX();
        double dy = target.getY() + target.getHeight() * 0.5 - mc.player.getEyePos().y;
        double dz = target.getZ() - mc.player.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);

        smoothLook(
            (float) Math.toDegrees(Math.atan2(-dx, dz)),
            (float) Math.toDegrees(-Math.atan2(dy, horiz))
        );

        FindItemResult mace = InvUtils.findInHotbar(Items.MACE);
        if (!mace.found()) { error("No mace in hotbar."); toggle(); return; }
        InvUtils.swap(mace.slot(), false);

        if (horiz < 4.5 && Math.abs(dy) < 4.0) {
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);
            actionTimer = 6;
            state = State.Recycle;
        }
    }

    private void handleRecycle() {
        if (actionTimer > 0) return;
        if (autoSwitch.get()
            && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) {
            equipArmor(Items.ELYTRA);
        }
        state = State.Takeoff;
    }

    private boolean equipArmor(net.minecraft.item.Item item) {
        FindItemResult hot = InvUtils.findInHotbar(item);
        if (hot.found()) {
            InvUtils.move().from(hot.slot()).toArmor(2);
            return true;
        }

        FindItemResult inv = InvUtils.find(item);
        if (!inv.found()) return false;

        int slot = findEmptyHotbarSlot();
        if (slot < 0) return false;

        InvUtils.move().from(inv.slot()).toHotbar(slot);
        InvUtils.move().from(slot).toArmor(2);
        return true;
    }

    // 找快捷栏空格
    private int findEmptyHotbarSlot() {
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        return -1;
    }

    private void useFirework() {
        FindItemResult fw = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
        if (!fw.found()) { error("No firework rocket."); toggle(); return; }
        InvUtils.swap(fw.slot(), false);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        fireworkTimer = fireworkDelay.get();
    }

    private void smoothLook(float targetYaw, float targetPitch) {
        float maxStep = rotationSpeed.get().floatValue();
        float curYaw = mc.player.getYaw();
        float curPitch = mc.player.getPitch();

        float dYaw = MathHelper.wrapDegrees(targetYaw - curYaw);
        float dPitch = targetPitch - curPitch;

        if (Math.abs(dYaw) > maxStep) dYaw = Math.signum(dYaw) * maxStep;
        if (Math.abs(dPitch) > maxStep) dPitch = Math.signum(dPitch) * maxStep;

        mc.player.setYaw(curYaw + dYaw);
        mc.player.setPitch(MathHelper.clamp(curPitch + dPitch, -90f, 90f));
    }

    private PlayerEntity findTarget() {
        return mc.world.getPlayers().stream()
            .filter(p -> p != mc.player && !p.isDead() && p.getHealth() > 0)
            .filter(p -> p.distanceTo(mc.player) <= range.get())
            .min(Comparator.comparingDouble(p -> p.distanceTo(mc.player)))
            .orElse(null);
    }

    @Override
    public String getInfoString() {
        return state.name();
    }
}
