package com.example.addon.utils.eps;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.function.Predicate;

public class InvUtils {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static int[] invSlots;
    public static int previousSlot = -1;

    public static boolean testInMainHand(Predicate<ItemStack> predicate) {
        return predicate.test(mc.player.getMainHandStack());
    }

    public static boolean testInOffHand(Predicate<ItemStack> predicate) {
        return predicate.test(mc.player.getOffHandStack());
    }

    public static FindItemResult findInHotbar(Item... items) {
        return findInHotbar(itemStack -> {
            for (Item item : items) {
                if (itemStack.getItem() == item) return true;
            }
            return false;
        });
    }

    public static FindItemResult findInHotbar(Predicate<ItemStack> isGood) {
        if (testInOffHand(isGood)) {
            ItemStack offhand = mc.player.getOffHandStack();
            return new FindItemResult(40, offhand.getCount(), offhand.getMaxCount());
        } else if (testInMainHand(isGood)) {
            ItemStack mainHand = mc.player.getMainHandStack();
            return new FindItemResult(mc.player.getInventory().getSelectedSlot(), mainHand.getCount(), mainHand.getMaxCount());
        }

        return find(isGood, 0, 8);
    }

    public static FindItemResult find(Item... items) {
        return find(itemStack -> {
            for (Item item : items) {
                if (itemStack.getItem() == item) return true;
            }
            return false;
        });
    }

    public static FindItemResult find(Predicate<ItemStack> isGood) {
        return find(isGood, 0, mc.player.getInventory().size());
    }

    public static FindItemResult find(Predicate<ItemStack> isGood, int start, int end) {
        int slot = -1, count = 0, maxCount = 0;

        for (int i = start; i <= end; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);

            if (isGood.test(stack)) {
                if (slot == -1) slot = i;
                count += stack.getCount();
                maxCount += stack.getMaxCount();
            }
        }

        return new FindItemResult(slot, count, maxCount);
    }

    public static void swap(int slot, boolean saveSwap) {
        if (slot == 40 || mc.player.getInventory().getSelectedSlot() == slot) {
            return;
        }

        if (saveSwap && previousSlot == -1) {
            previousSlot = mc.player.getInventory().getSelectedSlot();
        } else if (!saveSwap) {
            previousSlot = -1;
        }

        mc.player.getInventory().setSelectedSlot(slot);
    }

    public static void swapBack() {
        if (previousSlot == -1) return;
        swap(previousSlot, false);
        previousSlot = -1;
    }

    public static void invSwap(int slot) {
        int containerSlot = slot;
        if (slot < 9) containerSlot += 36;
        else if (slot == 40) containerSlot = 45;

        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        ClickSlotUtils.swap(mc.player.currentScreenHandler.syncId, containerSlot, selectedSlot);
        invSlots = new int[]{containerSlot, selectedSlot};
    }

    public static void invSwapBack() {
        if (invSlots == null || invSlots.length < 2) return;
        ClickSlotUtils.swap(mc.player.currentScreenHandler.syncId, invSlots[0], invSlots[1]);
    }
}
