package dev.sherii.yuio.utils;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class PlayerUtil {
   public enum PlayerState {
      Normal,
      Sneak,
      Creep;

      public Box getBBox(Vec3d vec) {
         if (vec == null) throw new IllegalArgumentException("vec cannot be null");

         return this.getBBox(vec.x, vec.y, vec.z);
      }

      public Box getBBox(double x, double y, double z) {
         return switch (this) {
            case Normal -> new Box(x + 0.3, y + 1.8, z + 0.3, x - 0.3, y, z - 0.3);
            case Sneak -> new Box(x + 0.3, y + 1.5, z + 0.3, x - 0.3, y, z - 0.3);
            case Creep -> new Box(x + 0.3, y + 0.6, z + 0.3, x - 0.3, y, z - 0.3);
         };
      }

      public double getEyeY() {
         return switch (this) {
            case Normal -> 1.62;
            case Sneak -> 1.27;
            case Creep -> 0.4;
         };
      }
   }

   public class PlayerStateHelper {
      public static final PlayerUtil.PlayerStateHelper.Companion companion = new PlayerUtil.PlayerStateHelper.Companion();

      public static Box getBBox(PlayerUtil.PlayerState state, Vec3d vec) {
         return getBBox(state, vec.x, vec.y, vec.z);
      }

      // 原字节码里 Creep 是 0.6, 跟枚举自己那个 0.4 不一致, 按字节码来
      public static Box getBBox(PlayerUtil.PlayerState state, double x, double y, double z) {
         return switch (state) {
            case Normal -> new Box(x + 0.3, y + 1.8, z + 0.3, x - 0.3, y, z - 0.3);
            case Sneak -> new Box(x + 0.3, y + 1.5, z + 0.3, x - 0.3, y, z - 0.3);
            case Creep -> new Box(x + 0.3, y + 0.6, z + 0.3, x - 0.3, y, z - 0.3);
         };
      }

      public final double getEyeY(PlayerUtil.PlayerState state) {
         return switch (state) {
            case Normal -> 1.62;
            case Sneak -> 1.27;
            case Creep -> 0.6;
         };
      }

      public static final class Companion {
         public final PlayerUtil.PlayerState getStateFromPlayer(PlayerEntity player) {
            return !player.isCrawling() && !player.isSwimming()
               ? (player.isSneaking() ? PlayerUtil.PlayerState.Sneak : PlayerUtil.PlayerState.Normal)
               : PlayerUtil.PlayerState.Creep;
         }
      }
   }
}
