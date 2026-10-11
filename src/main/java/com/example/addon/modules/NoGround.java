package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import dev.sherii.yuio.utils.TpUtils;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.mixin.PlayerMoveC2SPacketAccessor;
import meteordevelopment.meteorclient.mixininterface.IPlayerMoveC2SPacket;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public class NoGround extends Module {
    private static final int SKIP_TAG = 1337;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> restoreOnDisable = sgGeneral.add(new BoolSetting.Builder()
        .name("restore-on-disable")
        .description("Send an on-ground move packet when the module is turned off.")
        .defaultValue(true)
        .build()
    );

    public NoGround() {
        super(AddonTemplate.ASSIST, "no-ground",
            "Always tells the server that the player is not on the ground.");
    }

    @EventHandler(priority = EventPriority.LOWEST - 666)
    private void onPacketSend(PacketEvent.Send event) {
        if (!(event.packet instanceof PlayerMoveC2SPacket packet)) return;
        if (packet instanceof IPlayerMoveC2SPacket tagged && tagged.meteor$getTag() == SKIP_TAG) return;

        ((PlayerMoveC2SPacketAccessor) packet).meteor$setOnGround(false);
    }

    @Override
    public void onDeactivate() {
        if (!restoreOnDisable.get() || mc.player == null) return;

        TpUtils.INSTANCE.sendMovePacket(mc.player.getX(), mc.player.getY() + 1.0E-4, mc.player.getZ(), false, null);
        TpUtils.INSTANCE.sendMovePacket(mc.player.getX(), mc.player.getY(), mc.player.getZ(), true, null);
    }
}
