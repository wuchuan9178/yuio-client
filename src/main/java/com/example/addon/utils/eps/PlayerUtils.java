package com.example.addon.utils.eps;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Hand;

public class PlayerUtils {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static void swingHand(Hand hand) {
        mc.player.swingHand(hand);
    }
}
