/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  meteordevelopment.orbit.EventHandler
 *  net.minecraft.class_1297
 *  net.minecraft.class_1299
 *  net.minecraft.class_1304
 *  net.minecraft.class_1309
 *  net.minecraft.class_1657
 *  net.minecraft.class_1671
 *  net.minecraft.class_1799
 *  net.minecraft.class_1802
 *  net.minecraft.class_238
 *  net.minecraft.class_239$class_240
 *  net.minecraft.class_243
 *  net.minecraft.class_3489
 *  net.minecraft.class_3959
 *  net.minecraft.class_3959$class_242
 *  net.minecraft.class_3959$class_3960
 *  net.minecraft.class_3965
 */
package meteordevelopment.meteorclient.systems.modules.combat;

import java.util.ArrayList;
import java.util.Set;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.Aim;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_1304;
import net.minecraft.class_1309;
import net.minecraft.class_1657;
import net.minecraft.class_1671;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_238;
import net.minecraft.class_239;
import net.minecraft.class_243;
import net.minecraft.class_3489;
import net.minecraft.class_3959;
import net.minecraft.class_3965;

public class FireworkMace
extends Module {
    private final SettingGroup sgGeneral;
    private final SettingGroup sgTargeting;
    private final SettingGroup sgAim;
    private final SettingGroup sgTiming;
    private final Setting<Double> maxRaiseDistance;
    private final Setting<ClimbAngle> closeClimbAngle;
    private final Setting<ClimbAngle> farClimbAngle;
    private final Setting<Double> heightThreshold;
    private final Setting<Double> attackRangeHorizontal;
    private final Setting<Double> attackRangeVertical;
    private final Setting<Boolean> requireNotRising;
    private final Setting<Boolean> swapBack;
    private final Setting<Boolean> disableOnAttack;
    private final Setting<Integer> minAttackTicks;
    private final Setting<Integer> windRisingTimeout;
    private final Setting<Double> windRiseMinMomentum;
    private final Setting<Double> ceilingCheckHeight;
    private final Setting<Double> wallProbeDistance;
    private final Setting<AimTarget> aimTarget;
    private final Setting<Set<class_1299<?>>> entities;
    private final Setting<Boolean> ignoreInvisible;
    private final Setting<Double> targetRange;
    private final Setting<Integer> targetScanCount;
    private final Setting<Double> aimSmooth;
    private final Setting<Integer> actionHoldTicks;
    private final Setting<Integer> minFireworkInterval;
    private final Setting<Integer> diveAimDelayTicks;
    private final Setting<Integer> chestStopTicks;
    private Stage stage;
    private class_1297 target;
    private boolean switching;
    private int actionTicks;
    private int pendingPrevSlot;
    private Runnable pendingOnDone;
    private int fireworkCooldown;
    private boolean weEnabledKillAura;
    private int attackingTicks;
    private int diveTicks;
    private boolean gtJumped;
    private int gtTick;
    private int chestTick;
    private int dteTick;
    private int windRisingTick;
    private boolean windRisingSwitched;
    private int windRisingElytraTick;
    private double windRisingPeakVy;
    private boolean wasLaunchedDuringAttack;
    private double climbTargetY;

    public FireworkMace() {
        super(Categories.Combat, "firework-mace", "\u5168\u81ea\u52a8\u70df\u82b1\u91cd\u9524\uff1a\u5730\u9762\u8d77\u98de\u3001\u62ac\u5347\u3001\u5207\u7532\u5239\u505c\u3001\u91cd\u65b0\u6ed1\u7fd4\u3001\u4fef\u51b2\u3001\u653b\u51fb\u3001\u98ce\u7206\u5faa\u73af\u3002");
        this.sgGeneral = this.settings.getDefaultGroup();
        this.sgTargeting = this.settings.createGroup("Targeting");
        this.sgAim = this.settings.createGroup("Aim");
        this.sgTiming = this.settings.createGroup("Timing");
        this.maxRaiseDistance = this.sgGeneral.add(((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("max-raise-distance")).description("Horizontal distance threshold. Targets closer than this use close-climb-angle; farther targets use far-climb-angle.")).defaultValue(8.0).min(0.0).sliderMax(16.0).build());
        this.closeClimbAngle = this.sgGeneral.add(((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("close-climb-angle")).description("Climb angle used when the target is within max-raise-distance.")).defaultValue(ClimbAngle.StraightUp)).build());
        this.farClimbAngle = this.sgGeneral.add(((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("far-climb-angle")).description("Climb angle used when the target is farther than max-raise-distance.")).defaultValue(ClimbAngle.Diagonal)).build());
        this.heightThreshold = this.sgGeneral.add(((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("height-threshold")).description("Vertical height above the climb-start Y (locked when entering climb) before stopping the climb.")).defaultValue(8.0).min(1.0).sliderMax(24.0).build());
        this.attackRangeHorizontal = this.sgGeneral.add(((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("attack-range-horizontal")).description("Horizontal (XZ) distance to the target below which the module switches to chestplate.")).defaultValue(4.5).min(1.0).sliderMax(16.0).build());
        this.attackRangeVertical = this.sgGeneral.add(((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("attack-range-vertical")).description("Vertical (Y) distance above the target's top surface below which the module switches to chestplate.")).defaultValue(9.0).min(1.0).sliderMax(16.0).build());
        this.requireNotRising = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("require-not-rising")).description("Only switch to chestplate when not rising (vy <= 0). Needed for mace smash damage.")).defaultValue(true)).build());
        this.swapBack = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("swap-back")).description("Return to the previous hotbar slot after equipping armor or using fireworks.")).defaultValue(false)).build());
        this.disableOnAttack = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("disable-on-attack")).description("Automatically disable this module once the attack stage is reached.")).defaultValue(false)).build());
        this.minAttackTicks = this.sgGeneral.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("min-attack-ticks")).description("Minimum ticks to stay in the attack stage before switching to elytra.")).defaultValue(10)).min(1).sliderMax(20).build());
        this.windRisingTimeout = this.sgGeneral.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("wind-rising-timeout")).description("Max ticks to wait for wind-burst lift to end before forcing the next dive.")).defaultValue(30)).min(10).sliderMax(60).build());
        this.windRiseMinMomentum = this.sgGeneral.add(((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("wind-rise-min-momentum")).description("When |vy| drops below this during wind-burst lift, consider the apex reached and switch to elytra.")).defaultValue(2.0).min(0.0).sliderMax(2.0).build());
        this.ceilingCheckHeight = this.sgGeneral.add(((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("ceiling-check-height")).description("Check this many blocks above head. If blocked, use ceiling avoidance.")).defaultValue(8.0).min(1.0).sliderMax(24.0).build());
        this.wallProbeDistance = this.sgGeneral.add(((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("wall-probe-distance")).description("Distance to probe for nearby walls around the target. Used to pick the open direction.")).defaultValue(3.0).min(1.0).sliderMax(3.0).build());
        this.aimTarget = this.sgGeneral.add(((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("aim-target")).description("Which point of the target hitbox to aim at during dive: top face center, hitbox center, or bottom face center.")).defaultValue(AimTarget.BottomCenter)).build());
        this.entities = this.sgTargeting.add(((EntityTypeListSetting.Builder)((EntityTypeListSetting.Builder)new EntityTypeListSetting.Builder().name("entities")).description("Entity types to target.")).onlyAttackable().defaultValue(class_1299.field_6097).build());
        this.ignoreInvisible = this.sgTargeting.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("ignore-invisible")).description("Ignore invisible entities (e.g. players under invisibility potion).")).defaultValue(true)).build());
        this.targetRange = this.sgTargeting.add(((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("target-range")).description("Max distance to search for targets.")).defaultValue(64.0).min(16.0).sliderMax(128.0).build());
        this.targetScanCount = this.sgTargeting.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("target-scan-count")).description("How many nearest entities to check for a clear line of sight before giving up.")).defaultValue(10)).min(1).sliderMax(20).build());
        this.aimSmooth = this.sgAim.add(((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("aim-smooth")).description("Aim smoothing for all phases (0.1 = very smooth, 1.0 = instant).")).defaultValue(0.3).min(0.1).max(1.0).sliderRange(0.1, 1.0).build());
        this.actionHoldTicks = this.sgTiming.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("action-hold-ticks")).description("Ticks to hold right-click for equipping armor / launching fireworks. Keep at 1.")).defaultValue(1)).min(1).sliderMax(2).build());
        this.minFireworkInterval = this.sgTiming.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("min-firework-interval")).description("Minimum ticks between firework launches (debounce).")).defaultValue(10)).min(1).sliderMax(60).build());
        this.diveAimDelayTicks = this.sgTiming.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("dive-aim-delay-ticks")).description("Ticks to wait after entering dive before starting to aim at the target.")).defaultValue(1)).min(1).sliderMax(10).build());
        this.chestStopTicks = this.sgTiming.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("chest-stop-ticks")).description("Ticks to hold the chestplate after reaching climb height (to cancel gliding) before switching back to elytra.")).defaultValue(1)).min(1).sliderMax(10).build());
        this.stage = Stage.Idle;
        this.target = null;
        this.switching = false;
        this.actionTicks = 0;
        this.pendingPrevSlot = -1;
        this.pendingOnDone = null;
        this.fireworkCooldown = 0;
        this.weEnabledKillAura = false;
        this.attackingTicks = 0;
        this.diveTicks = 0;
        this.gtJumped = false;
        this.gtTick = 0;
        this.chestTick = 0;
        this.dteTick = 0;
        this.windRisingTick = 0;
        this.windRisingSwitched = false;
        this.windRisingElytraTick = 0;
        this.windRisingPeakVy = 0.0;
        this.wasLaunchedDuringAttack = false;
        this.climbTargetY = Double.NaN;
    }

    @Override
    public void onActivate() {
        this.resetState();
        if (this.mc.field_1724 == null) {
            return;
        }
        this.target = this.findTarget();
        if (this.target == null) {
            this.stage = Stage.Idle;
            return;
        }
        if (this.mc.field_1724.method_6128()) {
            this.stage = Stage.Climbing;
        } else {
            this.startGroundTakeoff();
        }
    }

    @Override
    public void onDeactivate() {
        Aim.stopLooking();
        this.releaseKeys();
        this.disableKillAura();
        this.resetState();
    }

    private void releaseKeys() {
        if (this.mc.field_1690 == null) {
            return;
        }
        this.mc.field_1690.field_1904.method_23481(false);
        this.mc.field_1690.field_1903.method_23481(false);
    }

    private void resetState() {
        this.stage = Stage.Idle;
        this.target = null;
        this.switching = false;
        this.actionTicks = 0;
        this.pendingPrevSlot = -1;
        this.pendingOnDone = null;
        this.fireworkCooldown = 0;
        this.attackingTicks = 0;
        this.diveTicks = 0;
        this.gtJumped = false;
        this.gtTick = 0;
        this.chestTick = 0;
        this.dteTick = 0;
        this.windRisingTick = 0;
        this.windRisingSwitched = false;
        this.windRisingElytraTick = 0;
        this.windRisingPeakVy = 0.0;
        this.wasLaunchedDuringAttack = false;
        this.climbTargetY = Double.NaN;
    }

    private void startGroundTakeoff() {
        this.gtJumped = false;
        this.gtTick = 0;
        this.climbTargetY = Double.NaN;
        this.stage = Stage.GroundTakeoff;
    }

    private void enterWindRising() {
        this.windRisingTick = 0;
        this.windRisingSwitched = false;
        this.windRisingElytraTick = 0;
        this.windRisingPeakVy = 0.0;
        this.wasLaunchedDuringAttack = false;
        this.stage = Stage.WindRising;
    }

    private void decideNextAfterKillAura() {
        Aim.stopLooking();
        if (this.target == null || !this.isValidTarget(this.target)) {
            this.target = null;
            this.stage = Stage.Idle;
            return;
        }
        if (this.wasLaunchedDuringAttack) {
            this.enterWindRising();
        } else {
            this.mc.field_1690.field_1903.method_23481(false);
            this.startGroundTakeoff();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        boolean needsTarget;
        if (this.mc.field_1724 == null || this.mc.field_1687 == null || this.mc.field_1761 == null || this.mc.field_1690 == null) {
            return;
        }
        if (!this.mc.field_1724.method_5805()) {
            this.resetState();
            return;
        }
        if (this.fireworkCooldown > 0) {
            --this.fireworkCooldown;
        }
        if (this.stage != Stage.GroundTakeoff && this.stage != Stage.Idle && this.mc.field_1724.method_24828()) {
            this.disableKillAura();
            Aim.stopLooking();
            this.releaseKeys();
            this.switching = false;
            this.actionTicks = 0;
            this.pendingOnDone = null;
            this.pendingPrevSlot = -1;
            this.resetState();
            this.target = this.findTarget();
            if (this.target == null) {
                return;
            }
            this.startGroundTakeoff();
            return;
        }
        if (this.switching && this.stage != Stage.GroundTakeoff) {
            if (this.actionTicks > 0) {
                --this.actionTicks;
                if (this.actionTicks == 0) {
                    this.mc.field_1690.field_1904.method_23481(false);
                    if (this.swapBack.get().booleanValue() && this.pendingPrevSlot != -1) {
                        this.mc.field_1724.method_31548().method_61496(this.pendingPrevSlot);
                    }
                    this.switching = false;
                    this.pendingPrevSlot = -1;
                    Runnable cb = this.pendingOnDone;
                    this.pendingOnDone = null;
                    if (cb != null) {
                        try {
                            cb.run();
                        }
                        catch (Exception exception) {
                            // empty catch block
                        }
                    }
                }
            }
            return;
        }
        switch (this.stage.ordinal()) {
            case 1: 
            case 3: 
            case 4: {
                boolean bl = false;
                break;
            }
            default: {
                boolean bl = needsTarget = true;
            }
        }
        if (needsTarget && (this.target == null || !this.isValidTarget(this.target))) {
            this.target = this.findTarget();
            if (this.target == null) {
                if (this.stage == Stage.Climbing || this.stage == Stage.Diving) {
                    Aim.stopLooking();
                }
                if (this.stage != Stage.Attacking) {
                    this.stage = Stage.Idle;
                }
                return;
            }
        }
        switch (this.stage.ordinal()) {
            case 0: {
                this.tickIdle();
                break;
            }
            case 1: {
                this.tickGroundTakeoff();
                break;
            }
            case 2: {
                this.tickClimbing();
                break;
            }
            case 3: {
                this.tickClimbToChest();
                break;
            }
            case 4: {
                this.tickDiveToElytra();
                break;
            }
            case 5: {
                this.tickDiving();
                break;
            }
            case 6: {
                this.tickAttacking();
                break;
            }
            case 7: {
                this.tickWindRising();
            }
        }
    }

    private void tickIdle() {
        if (this.target == null) {
            this.target = this.findTarget();
        }
        if (this.target == null) {
            return;
        }
        if (this.mc.field_1724.method_6128()) {
            this.stage = Stage.Climbing;
        } else {
            this.startGroundTakeoff();
        }
    }

    private void tickGroundTakeoff() {
        if (this.mc.field_1724.method_6128()) {
            this.mc.field_1690.field_1903.method_23481(false);
            this.gtJumped = false;
            this.gtTick = 0;
            this.stage = Stage.Climbing;
            return;
        }
        if (!this.gtJumped && !this.mc.field_1724.method_24828()) {
            this.tickAirborneRestart();
            return;
        }
        if (!this.gtJumped) {
            if (this.isElytraEquipped()) {
                this.mc.field_1690.field_1903.method_23481(true);
            } else {
                int slot = this.findElytraInHotbar();
                if (slot < 0) {
                    this.stage = Stage.Idle;
                    return;
                }
                this.mc.field_1724.method_31548().method_61496(slot);
                this.mc.field_1690.field_1904.method_23481(true);
                this.mc.field_1690.field_1903.method_23481(true);
            }
            this.gtJumped = true;
            this.gtTick = 0;
            return;
        }
        ++this.gtTick;
        if (this.gtTick == 1) {
            this.mc.field_1690.field_1904.method_23481(false);
            this.mc.field_1690.field_1903.method_23481(false);
            return;
        }
        if (!this.isElytraEquipped()) {
            if (this.gtTick % 8 == 1) {
                int slot = this.findElytraInHotbar();
                if (slot >= 0) {
                    this.mc.field_1724.method_31548().method_61496(slot);
                    this.mc.field_1690.field_1904.method_23481(true);
                }
            } else if (this.gtTick % 8 == 2) {
                this.mc.field_1690.field_1904.method_23481(false);
            }
            if (this.gtTick > 40) {
                this.mc.field_1690.field_1904.method_23481(false);
                this.gtJumped = false;
                this.gtTick = 0;
            }
            return;
        }
        if (!this.mc.field_1724.method_24828()) {
            this.mc.field_1690.field_1903.method_23481(true);
        } else if (this.gtTick % 2 == 1) {
            this.mc.field_1690.field_1903.method_23481(true);
        } else {
            this.mc.field_1690.field_1903.method_23481(false);
        }
        if (this.gtTick > 80) {
            this.mc.field_1690.field_1903.method_23481(false);
            this.gtJumped = false;
            this.gtTick = 0;
            this.stage = Stage.Idle;
        }
    }

    private void tickAirborneRestart() {
        ++this.gtTick;
        if (!this.isElytraEquipped()) {
            int phase = this.gtTick % 8;
            if (phase == 1) {
                int slot = this.findElytraInHotbar();
                if (slot < 0) {
                    this.mc.field_1690.field_1904.method_23481(false);
                    this.stage = Stage.Idle;
                    this.gtTick = 0;
                    return;
                }
                if (this.mc.field_1724.method_31548().method_67532() != slot) {
                    this.mc.field_1724.method_31548().method_61496(slot);
                }
                this.mc.field_1690.field_1904.method_23481(true);
            } else if (phase == 2) {
                this.mc.field_1690.field_1904.method_23481(false);
            } else if (this.gtTick > 80) {
                this.mc.field_1690.field_1904.method_23481(false);
                this.stage = Stage.Idle;
                this.gtTick = 0;
            }
            return;
        }
        this.mc.field_1690.field_1904.method_23481(false);
        if (!this.mc.field_1724.method_24828()) {
            this.mc.field_1690.field_1903.method_23481(true);
        } else if (this.gtTick > 10) {
            this.mc.field_1690.field_1903.method_23481(true);
        }
        if (this.gtTick > 120) {
            this.mc.field_1690.field_1903.method_23481(false);
            this.stage = Stage.Idle;
            this.gtTick = 0;
        }
    }

    private void tickClimbing() {
        int slot;
        float targetPitch;
        float targetYaw;
        boolean targetWalled;
        if (this.target == null) {
            return;
        }
        if (!this.mc.field_1724.method_6128()) {
            this.startGroundTakeoff();
            return;
        }
        if (Double.isNaN(this.climbTargetY)) {
            this.climbTargetY = this.target.method_23318();
        }
        double dx = this.target.method_23317() - this.mc.field_1724.method_23317();
        double dz = this.target.method_23321() - this.mc.field_1724.method_23321();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        boolean playerCeiling = this.isCeilingBlocked((class_1297)this.mc.field_1724, this.ceilingCheckHeight.get());
        boolean targetCeiling = this.isCeilingBlocked(this.target, this.ceilingCheckHeight.get());
        class_243 openDir = this.findOpenDirectionFromTarget(this.target);
        boolean bl = targetWalled = openDir != null;
        if (targetCeiling || targetWalled) {
            targetYaw = openDir != null ? (float)Math.toDegrees(Math.atan2(-openDir.field_1352, openDir.field_1350)) : (float)Math.toDegrees(Math.atan2(-dx, dz));
            targetPitch = -45.0f;
        } else if (playerCeiling) {
            targetYaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
            targetPitch = -45.0f;
        } else {
            ClimbAngle angle = horizontalDist < this.maxRaiseDistance.get() ? this.closeClimbAngle.get() : this.farClimbAngle.get();
            targetYaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
            targetPitch = angle == ClimbAngle.StraightUp ? -90.0f : -45.0f;
        }
        this.aimAtDirection(targetYaw, targetPitch, this.aimSmooth.get().floatValue());
        double heightGain = this.mc.field_1724.method_23318() - this.climbTargetY;
        if (heightGain > this.heightThreshold.get()) {
            this.chestTick = 0;
            this.climbTargetY = Double.NaN;
            this.stage = Stage.ClimbToChest;
            return;
        }
        if (this.fireworkCooldown <= 0 && !this.isBeingBoostedByFirework() && (slot = this.findFireworkInHotbar()) >= 0) {
            this.switchAndUse(slot, null);
            this.fireworkCooldown = this.minFireworkInterval.get();
        }
    }

    private class_243 findOpenDirectionFromTarget(class_1297 target) {
        if (this.mc.field_1687 == null || target == null) {
            return null;
        }
        double tx = target.method_23317();
        double ty = target.method_23318() + (double)target.method_17682() * 0.5;
        double tz = target.method_23321();
        class_243 origin = new class_243(tx, ty, tz);
        double probe = this.wallProbeDistance.get();
        double[] angles = new double[]{0.0, 45.0, 90.0, 135.0, 180.0, 225.0, 270.0, 315.0};
        double wallX = 0.0;
        double wallZ = 0.0;
        int wallCount = 0;
        for (double a : angles) {
            double dz;
            double rad = Math.toRadians(a);
            double dx = Math.cos(rad);
            class_243 end = origin.method_1031(dx * probe, 0.0, (dz = Math.sin(rad)) * probe);
            class_3965 hit = this.mc.field_1687.method_17742(new class_3959(origin, end, class_3959.class_3960.field_17558, class_3959.class_242.field_1348, target));
            if (hit.method_17783() == class_239.class_240.field_1333) continue;
            wallX += dx;
            wallZ += dz;
            ++wallCount;
        }
        if (wallCount == 0) {
            return null;
        }
        double openX = -wallX;
        double openZ = -wallZ;
        double len = Math.sqrt(openX * openX + openZ * openZ);
        if (len < 0.001) {
            return new class_243(0.0, 0.0, 1.0);
        }
        return new class_243(openX / len, 0.0, openZ / len);
    }

    private boolean isCeilingBlocked(class_1297 entity, double height) {
        if (this.mc.field_1687 == null || entity == null) {
            return false;
        }
        class_238 box = entity.method_5829();
        double y = box.field_1325 + 0.05;
        double[] xs = new double[]{box.field_1323, box.field_1320};
        double[] zs = new double[]{box.field_1321, box.field_1324};
        for (double x : xs) {
            for (double z : zs) {
                class_243 start = new class_243(x, y, z);
                class_243 end = start.method_1031(0.0, height, 0.0);
                class_3965 hit = this.mc.field_1687.method_17742(new class_3959(start, end, class_3959.class_3960.field_17558, class_3959.class_242.field_1348, entity));
                if (hit.method_17783() == class_239.class_240.field_1333) continue;
                return true;
            }
        }
        return false;
    }

    private void aimAtDirection(float yaw, float pitch, float smooth) {
        if (this.mc.field_1724 == null) {
            return;
        }
        float yawRad = (float)Math.toRadians(yaw);
        float pitchRad = (float)Math.toRadians(pitch);
        double vx = -Math.sin(yawRad) * Math.cos(pitchRad);
        double vy = -Math.sin(pitchRad);
        double vz = Math.cos(yawRad) * Math.cos(pitchRad);
        class_243 eye = this.mc.field_1724.method_33571();
        class_243 aimPoint = eye.method_1031(vx * 128.0, vy * 128.0, vz * 128.0);
        Aim.lookAt(aimPoint, smooth);
    }

    private class_243 getDiveAimPoint(class_1297 entity) {
        class_238 box = entity.method_5829();
        double cx = (box.field_1323 + box.field_1320) * 0.5;
        double cz = (box.field_1321 + box.field_1324) * 0.5;
        return switch (this.aimTarget.get().ordinal()) {
            default -> throw new MatchException(null, null);
            case 0 -> new class_243(cx, box.field_1325, cz);
            case 1 -> new class_243(cx, (box.field_1322 + box.field_1325) * 0.5, cz);
            case 2 -> new class_243(cx, box.field_1322, cz);
        };
    }

    private void tickClimbToChest() {
        if (this.target == null) {
            return;
        }
        Aim.lookAt(this.target, this.aimSmooth.get().floatValue());
        if (!this.isChestplateEquipped()) {
            int slot = this.findChestplateInHotbar();
            if (slot < 0) {
                this.dteTick = 0;
                this.stage = Stage.DiveToElytra;
                return;
            }
            this.switchAndUse(slot, null);
            return;
        }
        ++this.chestTick;
        if (this.chestTick >= this.chestStopTicks.get()) {
            this.dteTick = 0;
            this.stage = Stage.DiveToElytra;
        }
    }

    private void tickDiveToElytra() {
        if (this.mc.field_1724.method_6128()) {
            this.mc.field_1690.field_1903.method_23481(false);
            this.dteTick = 0;
            this.diveTicks = 0;
            this.stage = Stage.Diving;
            return;
        }
        if (this.isElytraEquipped()) {
            ++this.dteTick;
            if (!this.mc.field_1724.method_24828()) {
                this.mc.field_1690.field_1903.method_23481(true);
            } else if (this.dteTick > 10) {
                this.mc.field_1690.field_1903.method_23481(true);
            }
            if (this.dteTick > 80) {
                this.mc.field_1690.field_1903.method_23481(false);
                this.stage = Stage.Idle;
                this.dteTick = 0;
            }
            return;
        }
        int slot = this.findElytraInHotbar();
        if (slot < 0) {
            this.stage = Stage.Idle;
            this.dteTick = 0;
            return;
        }
        this.switchAndUse(slot, null);
    }

    private void tickDiving() {
        boolean risingBlock;
        if (this.target == null) {
            return;
        }
        if (!this.mc.field_1724.method_6128()) {
            this.dteTick = 0;
            this.stage = Stage.DiveToElytra;
            return;
        }
        if (!this.hasLineOfSight(this.target)) {
            class_1297 better = this.findTarget();
            if (better == null || better == this.target) {
                Aim.stopLooking();
                this.target = null;
                this.stage = Stage.Idle;
                return;
            }
            this.target = better;
            return;
        }
        ++this.diveTicks;
        if (this.diveTicks > this.diveAimDelayTicks.get()) {
            Aim.lookAt(this.getDiveAimPoint(this.target), this.aimSmooth.get().floatValue());
        }
        double dx = this.mc.field_1724.method_23317() - this.target.method_23317();
        double dz = this.mc.field_1724.method_23321() - this.target.method_23321();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        double verticalDist = this.mc.field_1724.method_23318() - this.target.method_5829().field_1325;
        boolean horizontalOk = horizontalDist < this.attackRangeHorizontal.get();
        boolean verticalOk = verticalDist < this.attackRangeVertical.get() && verticalDist > -1.0;
        boolean rising = this.mc.field_1724.method_18798().field_1351 > 0.05;
        boolean bl = risingBlock = this.requireNotRising.get() != false && rising;
        if (horizontalOk && verticalOk && !risingBlock) {
            int slot;
            this.attackingTicks = 0;
            this.wasLaunchedDuringAttack = false;
            if (this.isChestplateEquipped()) {
                this.enableKillAura();
                Aim.stopLooking();
                this.stage = Stage.Attacking;
                return;
            }
            if (!this.switching && (slot = this.findChestplateInHotbar()) >= 0) {
                this.enableKillAura();
                Aim.stopLooking();
                this.switchAndUse(slot, null);
            }
            return;
        }
        if (this.fireworkCooldown <= 0 && !this.isBeingBoostedByFirework()) {
            int slot;
            boolean targetAbovePlayer;
            boolean bl2 = targetAbovePlayer = this.target.method_23318() > this.mc.field_1724.method_23318();
            if (!targetAbovePlayer && (slot = this.findFireworkInHotbar()) >= 0) {
                this.switchAndUse(slot, null);
                this.fireworkCooldown = this.minFireworkInterval.get();
            }
        }
    }

    private void tickAttacking() {
        if (this.target == null || !this.isValidTarget(this.target)) {
            Aim.stopLooking();
            this.target = null;
            this.disableKillAura();
            this.stage = Stage.Idle;
            return;
        }
        ++this.attackingTicks;
        if (this.mc.field_1724.method_18798().field_1351 > 0.05) {
            this.wasLaunchedDuringAttack = true;
        }
        if (this.mc.field_1724.method_24828()) {
            this.disableKillAura();
            this.target = null;
            Aim.stopLooking();
            this.stage = Stage.Idle;
            return;
        }
        if (this.attackingTicks < this.minAttackTicks.get()) {
            if (this.disableOnAttack.get().booleanValue()) {
                this.toggle();
            }
            return;
        }
        this.disableKillAura();
        this.decideNextAfterKillAura();
    }

    private void tickWindRising() {
        ++this.windRisingTick;
        if (this.mc.field_1724.method_24828() && this.windRisingTick > 5) {
            this.stage = Stage.Idle;
            return;
        }
        if (this.mc.field_1724.method_6128() && this.windRisingSwitched) {
            this.mc.field_1690.field_1903.method_23481(false);
            this.diveTicks = 0;
            this.stage = Stage.Diving;
            return;
        }
        if (this.target != null) {
            Aim.lookAt(this.target, this.aimSmooth.get().floatValue());
        }
        double vy = this.mc.field_1724.method_18798().field_1351;
        if (!this.windRisingSwitched) {
            boolean timeout;
            if (this.isElytraEquipped() && this.windRisingTick < 5) {
                int chestSlot = this.findChestplateInHotbar();
                if (chestSlot >= 0 && !this.switching) {
                    this.switchAndUse(chestSlot, null);
                }
                return;
            }
            if (Math.abs(vy) > this.windRisingPeakVy) {
                this.windRisingPeakVy = Math.abs(vy);
            }
            boolean everRising = this.windRisingPeakVy > 0.2;
            boolean lowMomentum = everRising && Math.abs(vy) < this.windRiseMinMomentum.get();
            boolean bl = timeout = this.windRisingTick > this.windRisingTimeout.get();
            if (!lowMomentum && !timeout) {
                return;
            }
            if (this.isElytraEquipped()) {
                this.windRisingSwitched = true;
                this.windRisingElytraTick = 0;
                return;
            }
            int slot = this.findElytraInHotbar();
            if (slot < 0) {
                this.stage = Stage.Idle;
                return;
            }
            this.switchAndUse(slot, null);
            this.windRisingSwitched = true;
            this.windRisingElytraTick = 0;
            return;
        }
        ++this.windRisingElytraTick;
        if (!this.isElytraEquipped()) {
            if (this.windRisingElytraTick > 30) {
                this.mc.field_1690.field_1903.method_23481(false);
                this.stage = Stage.Idle;
            }
            return;
        }
        if (this.windRisingElytraTick < 2) {
            return;
        }
        if (!this.mc.field_1724.method_24828()) {
            this.mc.field_1690.field_1903.method_23481(true);
        } else if (this.windRisingElytraTick > 10) {
            this.mc.field_1690.field_1903.method_23481(true);
        }
        if (this.windRisingElytraTick > 80) {
            this.mc.field_1690.field_1903.method_23481(false);
            this.stage = Stage.Idle;
        }
    }

    private void switchAndUse(int slot, Runnable onDone) {
        if (this.switching) {
            return;
        }
        if (this.mc.field_1724 == null || this.mc.field_1690 == null) {
            return;
        }
        this.mc.field_1690.field_1904.method_23481(false);
        this.switching = true;
        this.pendingPrevSlot = this.mc.field_1724.method_31548().method_67532();
        this.pendingOnDone = onDone;
        this.mc.field_1724.method_31548().method_61496(slot);
        this.mc.field_1690.field_1904.method_23481(true);
        this.actionTicks = Math.max(1, this.actionHoldTicks.get());
    }

    private boolean isBeingBoostedByFirework() {
        if (this.mc.field_1724 == null || this.mc.field_1687 == null) {
            return false;
        }
        double maxDistSq = 2.25;
        class_238 search = this.mc.field_1724.method_5829().method_1014(2.0);
        for (class_1297 e : this.mc.field_1687.method_8335((class_1297)this.mc.field_1724, search)) {
            class_1671 firework;
            if (!(e instanceof class_1671) || (firework = (class_1671)e).method_7477() || !(this.mc.field_1724.method_5858((class_1297)firework) <= maxDistSq)) continue;
            return true;
        }
        return false;
    }

    private boolean isElytraEquipped() {
        if (this.mc.field_1724 == null) {
            return false;
        }
        return this.mc.field_1724.method_6118(class_1304.field_6174).method_31574(class_1802.field_8833);
    }

    private boolean isChestplateEquipped() {
        if (this.mc.field_1724 == null) {
            return false;
        }
        class_1799 chest = this.mc.field_1724.method_6118(class_1304.field_6174);
        return !chest.method_7960() && !chest.method_31574(class_1802.field_8833) && chest.method_31573(class_3489.field_48296);
    }

    private int findChestplateInHotbar() {
        if (this.mc.field_1724 == null) {
            return -1;
        }
        for (int i = 0; i < 9; ++i) {
            class_1799 s = this.mc.field_1724.method_31548().method_5438(i);
            if (!s.method_31573(class_3489.field_48296) || s.method_31574(class_1802.field_8833)) continue;
            return i;
        }
        return -1;
    }

    private int findElytraInHotbar() {
        if (this.mc.field_1724 == null) {
            return -1;
        }
        for (int i = 0; i < 9; ++i) {
            if (!this.mc.field_1724.method_31548().method_5438(i).method_31574(class_1802.field_8833)) continue;
            return i;
        }
        return -1;
    }

    private int findFireworkInHotbar() {
        if (this.mc.field_1724 == null) {
            return -1;
        }
        for (int i = 0; i < 9; ++i) {
            if (!this.mc.field_1724.method_31548().method_5438(i).method_31574(class_1802.field_8639)) continue;
            return i;
        }
        return -1;
    }

    private class_1297 findTarget() {
        ArrayList<class_1297> list = new ArrayList<class_1297>();
        TargetUtils.getList(list, this::isValidTarget, SortPriority.LowestDistance, this.targetScanCount.get());
        if (list.isEmpty()) {
            return null;
        }
        for (class_1297 e : list) {
            if (!this.hasLineOfSight(e)) continue;
            return e;
        }
        return (class_1297)list.getFirst();
    }

    private boolean isValidTarget(class_1297 entity) {
        class_1309 le;
        if (this.mc.field_1724 == null) {
            return false;
        }
        if (entity == this.mc.field_1724) {
            return false;
        }
        if (!entity.method_5805()) {
            return false;
        }
        if (entity instanceof class_1309 && (le = (class_1309)entity).method_29504()) {
            return false;
        }
        if (entity instanceof class_1657) {
            class_1657 p = (class_1657)entity;
            if (!Friends.get().shouldAttack(p)) {
                return false;
            }
        }
        if (!this.entities.get().contains(entity.method_5864())) {
            return false;
        }
        if (this.ignoreInvisible.get().booleanValue() && entity.method_5767()) {
            return false;
        }
        return !((double)this.mc.field_1724.method_5739(entity) > this.targetRange.get());
    }

    private boolean hasLineOfSight(class_1297 entity) {
        double targetDist;
        if (this.mc.field_1724 == null || this.mc.field_1687 == null) {
            return false;
        }
        class_243 eye = this.mc.field_1724.method_33571();
        class_238 box = entity.method_5829();
        class_243 center = new class_243((box.field_1323 + box.field_1320) * 0.5, (box.field_1322 + box.field_1325) * 0.5, (box.field_1321 + box.field_1324) * 0.5);
        class_3965 hit = this.mc.field_1687.method_17742(new class_3959(eye, center, class_3959.class_3960.field_17558, class_3959.class_242.field_1348, (class_1297)this.mc.field_1724));
        if (hit.method_17783() == class_239.class_240.field_1333) {
            return true;
        }
        double blockDist = eye.method_1025(hit.method_17784());
        return blockDist >= (targetDist = eye.method_1025(center)) - 0.001;
    }

    private void enableKillAura() {
        KillAura ka = Modules.get().get(KillAura.class);
        if (ka != null && !ka.isActive()) {
            ka.toggle();
            this.weEnabledKillAura = true;
        }
    }

    private void disableKillAura() {
        KillAura ka = Modules.get().get(KillAura.class);
        if (ka != null && ka.isActive()) {
            ka.toggle();
        }
        this.weEnabledKillAura = false;
    }

    public static enum ClimbAngle {
        StraightUp,
        Diagonal;

    }

    public static enum AimTarget {
        TopCenter,
        Center,
        BottomCenter;

    }

    private static enum Stage {
        Idle,
        GroundTakeoff,
        Climbing,
        ClimbToChest,
        DiveToElytra,
        Diving,
        Attacking,
        WindRising;

    }
}
