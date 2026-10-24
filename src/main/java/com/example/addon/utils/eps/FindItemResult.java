package com.example.addon.utils.eps;

import net.minecraft.util.Hand;

public record FindItemResult(int slot, int count, int maxCount) {

    public boolean found() {
        return slot != -1;
    }

    public Hand getHand() {
        return slot == 40 ? Hand.OFF_HAND : Hand.MAIN_HAND;
    }

    public boolean isMainHand() {
        return getHand() == Hand.MAIN_HAND;
    }

    public boolean isOffhand() {
        return getHand() == Hand.OFF_HAND;
    }
}
