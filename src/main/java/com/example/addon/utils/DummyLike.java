package com.example.addon.utils;

import net.minecraft.entity.player.PlayerEntity;

public interface DummyLike {
    DummyLogic logic();

    void hurtBy(PlayerEntity attacker);
}
