package com.example.addon.utils.eps;

import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.slot.SlotActionType;

public class ClickSlotUtils {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static void clickSlot(int containerId, int slot, int button, SlotActionType action) {
        mc.interactionManager.clickSlot(containerId, slot, button, action, mc.player);
    }

    public static void clickSlot(int slot, int button, SlotActionType action) {
        clickSlot(mc.player.currentScreenHandler.syncId, slot, button, action);
    }

    public static void swap(int containerId, int slot, int hotbarSlot) {
        clickSlot(containerId, slot, hotbarSlot, SlotActionType.SWAP);
    }

    public static void swap(int slot, int hotbarSlot) {
        clickSlot(slot, hotbarSlot, SlotActionType.SWAP);
    }
}
