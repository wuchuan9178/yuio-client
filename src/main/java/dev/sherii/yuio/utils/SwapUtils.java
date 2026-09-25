package dev.sherii.yuio.utils;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;

public final class SwapUtils {
   public static final SwapUtils INSTANCE = new SwapUtils();
   private static int selectedSlot = -1;
   private static int itemSlot = -1;

   private SwapUtils() {
   }

   public final int getSelectedSlot() {
      return selectedSlot;
   }

   public final void setSelectedSlot(int value) {
      selectedSlot = value;
   }

   public final int getItemSlot() {
      return itemSlot;
   }

   public final void setItemSlot(int value) {
      itemSlot = value;
   }

   public final void swap(int slot, boolean back) {
      if (MeteorClient.mc.player != null) {
         ClientPlayerEntity player = MeteorClient.mc.player;
         selectedSlot = player.getInventory().getSelectedSlot();
         if (slot == selectedSlot) {
            PacketUtils.INSTANCE.sendPacket(new UpdateSelectedSlotC2SPacket(selectedSlot));
         } else {
            if (SlotUtils.isHotbar(slot)) {
               InvUtils.swap(slot, false);
            } else {
               InvUtils.quickSwap().fromId(selectedSlot).to(slot);
            }

            if (back) {
               itemSlot = slot;
            }
         }
      }
   }

   public final void swapBack() {
      if (itemSlot != -1) {
         if (SlotUtils.isHotbar(itemSlot)) {
            InvUtils.swap(selectedSlot, false);
         } else {
            InvUtils.quickSwap().fromId(selectedSlot).to(itemSlot);
         }

         itemSlot = -1;
      }
   }
}
