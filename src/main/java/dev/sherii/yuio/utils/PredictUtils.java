package dev.sherii.yuio.utils;

import dev.sherii.yuio.modules.settings.PredictSetting;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

public final class PredictUtils {
   public static final PredictUtils INSTANCE = new PredictUtils();

   private PredictUtils() {
   }

   public final Vec3d predictPos(Vec3d old, Vec3d newPos) {
      Vec3d var10000 = newPos.add(newPos.subtract(old).multiply(PredictSetting.INSTANCE.getResultMul()));
      return var10000;
   }

   public final int getPing() {
      ClientPlayNetworkHandler var10000 = MeteorClient.mc.getNetworkHandler();
      if (var10000 == null) {
         return 0;
      }

      ClientPlayNetworkHandler networkHandler = var10000;
      if (MeteorClient.mc.player == null) {
         return 0;
      }

      ClientPlayerEntity player = MeteorClient.mc.player;
      PlayerListEntry playerListEntry = networkHandler.getPlayerListEntry(player.getUuid());
      return playerListEntry != null ? playerListEntry.getLatency() : 0;
   }

   public final Vec3d predictEntity(Entity entity) {
      Vec3d entityPos = entity.getEntityPos();
      Vec3d var10001 = new Vec3d(entity.lastX, entity.lastY, entity.lastZ);
      Vec3d pos = this.predictPos(var10001, entityPos);
      Vec3d result = entityPos;
      Vec3d velocity = pos.subtract(entityPos);
      int var10000;
      if (PredictSetting.INSTANCE.getPingSync()) {
         int ping = this.getPing();
         var10000 = Math.max(1, (int)Math.ceil((ping + 1) / 50.0));
      } else {
         var10000 = 1;
      }

      int ticks = var10000;

      for (int i = 0; i < ticks; i++) {
         Vec3d adjusted = entity.adjustMovementForCollisions(velocity);
         result = result.add(adjusted);
      }

      Vec3d var10 = result;
      return var10;
   }
}
