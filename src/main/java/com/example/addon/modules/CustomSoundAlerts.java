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

    private boolean wasDead = false;
    private boolean playerNearActive = false;
    private int lastTotem = -1;
    private int lastArmorPercent = -1;

    public CustomSoundAlerts() {
        super(AddonTemplate.OTHER, "custom-sound-alerts", "Play some mysterious sound effects");
    }

    @Override
    public void onActivate() {
        wasDead = false;
        playerNearActive = false;
        lastTotem = -1;
        lastArmorPercent = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        checkDeath();
        checkPlayerNear();
        checkTotem();
        checkArmor();
    }

    private void checkDeath() {
        if (!deathEnabled.get()) return;
        boolean dead = mc.player.isDead() || mc.player.getHealth() <= 0;
        if (dead && !wasDead) {
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
            YuioSound.play("player_near.wav", playerVolume.get().floatValue(), playerSpeed.get().floatValue());
            playerNearActive = true;
        } else if (!found) {
            playerNearActive = false;
        }
    }

    private void checkTotem() {
        if (!totemEnabled.get()) return;
        int count = countTotems();
        if (lastTotem > totemThreshold.get() && count <= totemThreshold.get()) {
            YuioSound.play("totem_low.wav", totemVolume.get().floatValue(), totemSpeed.get().floatValue());
        }
        lastTotem = count;
    }

    private void checkArmor() {
        if (!armorEnabled.get()) return;
        int percent = armorPercent();
        if (lastArmorPercent > armorThreshold.get() && percent <= armorThreshold.get() && percent > 0) {
            YuioSound.play("armor_low.wav", armorVolume.get().floatValue(), armorSpeed.get().floatValue());
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
