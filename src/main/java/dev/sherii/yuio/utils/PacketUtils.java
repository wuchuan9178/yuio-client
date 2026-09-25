package dev.sherii.yuio.utils;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.Packet;

import java.util.function.IntFunction;

public final class PacketUtils {
   public static final PacketUtils INSTANCE = new PacketUtils();
   private static int sequence;

   private PacketUtils() {
   }

   public final void sendPacket(Packet<?> packet) {
      this.sendPacket(packet, 1);
   }

   public final void sendPacket(Packet<?> packet, int times) {
      if (MeteorClient.mc != null) {
         ClientPlayNetworkHandler handler = MeteorClient.mc.getNetworkHandler();
         if (handler != null) {
            for (int i = 0; i < times; i++) {
               handler.sendPacket(packet);
            }
         }
      }
   }

   public final void sendSequencePacket(IntFunction<Packet<?>> func, int times) {
      this.sendPacket(func.apply(sequence++), times);
   }
}
