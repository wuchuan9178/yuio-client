package com.example.addon.utils;

import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

// bot 形态的本地假人：走 meteor 的假人实体
public class DummyEntity extends FakePlayerEntity implements DummyLike {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private final DummyLogic logic;

    public DummyEntity(String name, float health, int totems, int respawnDelay) {
        super(mc.player, name, health, false);
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

    // bot 不参与创造模式过滤
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
