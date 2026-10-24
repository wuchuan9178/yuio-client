package com.example.addon.utils;

import com.example.addon.mixin.ClientPlayNetworkHandlerAccessor;
import com.example.addon.mixin.PlayerListEntryAccessor;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.UUID;

// 真人形态的本地假人：真正的玩家实体，并且注册进 tab 列表让其它模块能索敌
public class RealDummyEntity extends OtherClientPlayerEntity implements DummyLike {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private final DummyLogic logic;
    private boolean spawned;

    public RealDummyEntity(ClientWorld world, String name, float health, int totems, int respawnDelay) {
        super(world, new GameProfile(UUID.randomUUID(), name));
        this.logic = new DummyLogic(this, health, totems, respawnDelay);
    }

    @Override
    public DummyLogic logic() {
        return logic;
    }

    public void setLogSink(java.util.function.Consumer<String> sink) {
        logic.setLogSink(sink);
    }

    public void setHome(Vec3d pos) {
        logic.setHome(pos);
    }

    public Vec3d getHome() {
        return logic.getHome();
    }

    public boolean isDummyDead() {
        return logic.isDead();
    }

    public int getTotems() {
        return logic.getTotems();
    }

    public void configure(int hurtInterval, boolean infiniteTotems, DummyLogic.Gear gear, int protection, int unbreaking, Item mainHand) {
        logic.configure(hurtInterval, infiniteTotems, gear, protection, unbreaking, mainHand);
    }

    @Override
    public void hurtBy(PlayerEntity attacker) {
        logic.hurtBy(attacker);
    }

    public void stepTowards(Vec3d target, double speed) {
        logic.stepTowards(target, speed);
    }

    public void spawn() {
        if (spawned) return;
        registerTabEntry();
        mc.world.addEntity(this);
        spawned = true;
    }

    public void despawn() {
        if (!spawned) return;
        this.discard();
        unregisterTabEntry();
        spawned = false;
    }

    private void registerTabEntry() {
        ClientPlayNetworkHandler handler = mc.getNetworkHandler();
        if (handler == null) return;

        PlayerListEntry entry = new PlayerListEntry(this.getGameProfile(), true);
        ((PlayerListEntryAccessor) entry).yuio$setGameMode(GameMode.SURVIVAL);
        ((ClientPlayNetworkHandlerAccessor) handler).yuio$playerListEntries().put(this.getUuid(), entry);
    }

    private void unregisterTabEntry() {
        ClientPlayNetworkHandler handler = mc.getNetworkHandler();
        if (handler == null) return;

        ((ClientPlayNetworkHandlerAccessor) handler).yuio$playerListEntries().remove(this.getUuid());
    }

    @Override
    public boolean isCreative() {
        return false;
    }

    @Override
    public GameMode getGameMode() {
        return GameMode.SURVIVAL;
    }

    @Override
    public void tick() {
        super.tick();
        logic.tick();
    }
}
