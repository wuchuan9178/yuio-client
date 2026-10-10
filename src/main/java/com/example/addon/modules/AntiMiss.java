package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;

public class AntiMiss extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode").description("Silent keeps local slot free.").defaultValue(Mode.Silent).build());
    private final Setting<Boolean> autoSelect = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-slot").description("Auto-pick a totem/empty hotbar slot, fallback to slot 9.").defaultValue(true).build());
    private final Setting<Integer> lockSlot = sgGeneral.add(new IntSetting.Builder()
        .name("lock-slot").description("Hotbar slot to lock when auto-slot is off.").defaultValue(0).min(0).max(8)
        .visible(() -> !autoSelect.get()).build());

    private int slot = -1;

    public AntiMiss() {
        super(AddonTemplate.ASSIST, "anti-miss", "Locks a hotbar slot to totems, auto-refills.");
    }

    @Override
    public void onActivate() {
        slot = autoSelect.get() ? findSlot() : lockSlot.get();
    }

    @EventHandler
    private void onTick(TickEvent.Pre e) {
        if (mc.player == null) return;
        if (!autoSelect.get()) slot = lockSlot.get();
        if (slot == -1) slot = autoSelect.get() ? findSlot() : lockSlot.get();

        //兜底槽有杂物就先换走
        if (slot == 8 && !isTotem(8)) {
            int t = findTotem(8);
            if (t != -1) InvUtils.move().from(8).to(t);
            else {
                int free = firstFreeBackpack();
                if (free != -1) InvUtils.move().from(8).to(free);
            }
        }
        //补货
        if (!isTotem(slot)) {
            int t = findTotem(slot);
            if (t != -1) InvUtils.move().from(t).to(slot);
        }
        //锁主手
        if (mode.get() == Mode.Normal) mc.player.getInventory().setSelectedSlot(slot);
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
    }

    //优先级
    private int findSlot() {
        for (int i = 0; i < 9; i++) if (isTotem(i)) return i;
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        return 8;
    }

    private boolean isTotem(int i) {
        return mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING);
    }

    private int findTotem(int exclude) {
        for (int i = 9; i < 36; i++) if (i != exclude && isTotem(i)) return i;
        for (int i = 0; i < 9; i++) if (i != exclude && isTotem(i)) return i;
        return -1;
    }

    private int firstFreeBackpack() {
        for (int i = 9; i < 36; i++) if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        return -1;
    }

    @Override
    public String getInfoString() {
        return mc.player != null && slot != -1 && isTotem(slot) ? "totem" : "refilling";
    }

    public enum Mode { Normal, Silent }
}
