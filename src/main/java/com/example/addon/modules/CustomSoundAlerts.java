package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.utils.YuioSound;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class CustomSoundAlerts extends Module {
    private final SettingGroup sgDeath = settings.createGroup("Death Sound");
    private final SettingGroup sgPlayer = settings.createGroup("Player Notify");
    private final SettingGroup sgTotem = settings.createGroup("Totem Warn");
    private final SettingGroup sgArmor = settings.createGroup("Armor Warn");
    private final SettingGroup sgDebug = settings.createGroup("Debug");

    private final Setting<Boolean> debug = sgDebug.add(new BoolSetting.Builder()
        .name("debug")
        .description("Print what every alert check sees, and what the audio backend did.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> test = sgDebug.add(new BoolSetting.Builder()
        .name("test")
        .description("Play every alert sound once so you can check the audio pipeline.")
        .defaultValue(false)
        .build()
    );

    //死亡音效
    private final Setting<Boolean> deathEnabled = sgDeath.add(new BoolSetting.Builder()
        .name("enabled").description("Play when you die.").defaultValue(true).build());
    private final Setting<Double> deathVolume = sgDeath.add(new DoubleSetting.Builder()
        .name("volume").defaultValue(1.0).min(0).sliderMax(2).visible(deathEnabled::get).build());
    private final Setting<Double> deathSpeed = sgDeath.add(new DoubleSetting.Builder()
        .name("speed").defaultValue(1.0).min(0.5).sliderRange(0.5, 3).visible(deathEnabled::get).build());

    //玩家通知
    private final Setting<Boolean> playerEnabled = sgPlayer.add(new BoolSetting.Builder()
        .name("enabled").description("Play when a non-friend player enters range.").defaultValue(true).build());
    private final Setting<Double> playerRange = sgPlayer.add(new DoubleSetting.Builder()
        .name("range").defaultValue(64).min(8).sliderMax(256).visible(playerEnabled::get).build());
    private final Setting<Double> playerVolume = sgPlayer.add(new DoubleSetting.Builder()
        .name("volume").defaultValue(1.0).min(0).sliderMax(2).visible(playerEnabled::get).build());
    private final Setting<Double> playerSpeed = sgPlayer.add(new DoubleSetting.Builder()
        .name("speed").defaultValue(1.0).min(0.5).sliderRange(0.5, 3).visible(playerEnabled::get).build());

    //图腾过少
    private final Setting<Boolean> totemEnabled = sgTotem.add(new BoolSetting.Builder()
        .name("enabled").description("Play when totem count drops to threshold.").defaultValue(true).build());
    private final Setting<Integer> totemThreshold = sgTotem.add(new IntSetting.Builder()
        .name("threshold").description("Trigger when totem count drops to this value.").defaultValue(1).min(0).sliderMax(10).visible(totemEnabled::get).build());
    private final Setting<Double> totemVolume = sgTotem.add(new DoubleSetting.Builder()
        .name("volume").defaultValue(1.0).min(0).sliderMax(2).visible(totemEnabled::get).build());
    private final Setting<Double> totemSpeed = sgTotem.add(new DoubleSetting.Builder()
        .name("speed").defaultValue(1.0).min(0.5).sliderRange(0.5, 3).visible(totemEnabled::get).build());

    // 护甲低耐久
    private final Setting<Boolean> armorEnabled = sgArmor.add(new BoolSetting.Builder()
        .name("enabled").description("Play when armor durability percent is low.").defaultValue(true).build());
    private final Setting<Integer> armorThreshold = sgArmor.add(new IntSetting.Builder()
        .name("threshold").description("Trigger when armor percent drops below this.").defaultValue(20).min(1).sliderMax(100).visible(armorEnabled::get).build());
    private final Setting<Double> armorVolume = sgArmor.add(new DoubleSetting.Builder()
        .name("volume").defaultValue(1.0).min(0).sliderMax(2).visible(armorEnabled::get).build());
    private final Setting<Double> armorSpeed = sgArmor.add(new DoubleSetting.Builder()
        .name("speed").defaultValue(1.0).min(0.5).sliderRange(0.5, 3).visible(armorEnabled::get).build());
    private final Setting<Boolean> armorOnEnable = sgArmor.add(new BoolSetting.Builder()
        .name("alert-on-enable").description("Also play when armor is already below the threshold as the module turns on.")
        .defaultValue(true).visible(armorEnabled::get).build());

    private final Setting<Boolean> totemOnEnable = sgTotem.add(new BoolSetting.Builder()
        .name("alert-on-enable").description("Also play when totems are already at the threshold as the module turns on.")
        .defaultValue(true).visible(totemEnabled::get).build());

    private boolean wasDead = false;
    private boolean playerNearActive = false;
    private int lastTotem = -1;
    private int lastArmorPercent = -1;
    private boolean firstCheck = true;

    public CustomSoundAlerts() {
        super(AddonTemplate.OTHER, "custom-sound-alerts", "Play some mysterious sound effects");
    }

    @Override
    public void onActivate() {
        wasDead = false;
        playerNearActive = false;
        lastTotem = -1;
        lastArmorPercent = -1;
        firstCheck = true;
        YuioSound.drainReports();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (test.get()) {
            test.set(false);
            YuioSound.play("death.wav", deathVolume.get().floatValue(), deathSpeed.get().floatValue());
            YuioSound.play("player_near.wav", playerVolume.get().floatValue(), playerSpeed.get().floatValue());
            YuioSound.play("totem_low.wav", totemVolume.get().floatValue(), totemSpeed.get().floatValue());
            YuioSound.play("armor_low.wav", armorVolume.get().floatValue(), armorSpeed.get().floatValue());
            info("played all four alert sounds once.");
        }

        checkDeath();
        checkPlayerNear();
        checkTotem();
        checkArmor();

        firstCheck = false;

        if (debug.get()) {
            for (String report : YuioSound.drainReports()) info("%s", report);
        }
    }

    private void checkDeath() {
        if (!deathEnabled.get()) return;
        boolean dead = mc.player.isDead() || mc.player.getHealth() <= 0;
        if (dead && !wasDead) {
            if (debug.get()) info("death alert firing (health %.1f)", mc.player.getHealth());
            YuioSound.play("death.wav", deathVolume.get().floatValue(), deathSpeed.get().floatValue());
        }
        wasDead = dead;
    }

    private void checkPlayerNear() {
        if (!playerEnabled.get()) return;
        double range = playerRange.get();
        boolean found = false;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            if (Friends.get().isFriend(p)) continue;
            if (mc.player.distanceTo(p) <= range) {
                found = true;
                break;
            }
        }
        if (found && !playerNearActive) {
            if (debug.get()) info("player-near alert firing");
            YuioSound.play("player_near.wav", playerVolume.get().floatValue(), playerSpeed.get().floatValue());
            playerNearActive = true;
        } else if (!found) {
            playerNearActive = false;
        }
    }

    private void checkTotem() {
        if (!totemEnabled.get()) return;
        int count = countTotems();
        int threshold = totemThreshold.get();
        boolean crossed = lastTotem > threshold && count <= threshold;
        boolean onEnable = firstCheck && totemOnEnable.get() && count <= threshold;

        if (crossed || onEnable) {
            if (debug.get()) info("totem alert firing (%d -> %d, threshold %d)", lastTotem, count, threshold);
            YuioSound.play("totem_low.wav", totemVolume.get().floatValue(), totemSpeed.get().floatValue());
        } else if (debug.get() && count <= threshold) {
            info("totems already at %d (threshold %d), no drop detected.", count, threshold);
        }
        lastTotem = count;
    }

    private void checkArmor() {
        if (!armorEnabled.get()) return;
        int percent = armorPercent();
        int threshold = armorThreshold.get();
        boolean crossed = lastArmorPercent > threshold && percent <= threshold && percent > 0;
        boolean onEnable = firstCheck && armorOnEnable.get() && percent <= threshold && percent > 0;

        if (crossed || onEnable) {
            if (debug.get()) info("armor alert firing (%d%% -> %d%%, threshold %d%%)", lastArmorPercent, percent, threshold);
            YuioSound.play("armor_low.wav", armorVolume.get().floatValue(), armorSpeed.get().floatValue());
        } else if (debug.get() && percent <= threshold) {
            info("armor already at %d%% (threshold %d%%), no drop detected.", percent, threshold);
        }
        lastArmorPercent = percent;
    }

    private int countTotems() {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) count++;
        }
        return count;
    }

    private int armorPercent() {
        int total = 0, max = 0;
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (EquipmentSlot slot : slots) {
            ItemStack stack = mc.player.getEquippedStack(slot);
            if (stack.isEmpty() || !stack.isDamageable()) continue;
            total += stack.getMaxDamage() - stack.getDamage();
            max += stack.getMaxDamage();
        }
        if (max == 0) return 100;
        return (int)((double)total / max * 100);
    }

    @Override
    public String getInfoString() {
        return "alerts";
    }
}
