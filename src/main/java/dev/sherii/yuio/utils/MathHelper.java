package dev.sherii.yuio.utils;
import org.joml.Vector2d;

public final class MathHelper {
   public static final MathHelper INSTANCE = new MathHelper();

   private MathHelper() {
   }

   public final double getLinearFuncK(double x1, double y1, double x2, double y2) {
      return (y2 - y1) / (x2 - x1);
   }

   public final double getLinearFuncB(double x1, double y1, double k) {
      return y1 - k * x1;
   }

   public final Vector2d getLinearFuncIP(double k1, double b1, double k2, double b2) {
      if (k1 == k2) {
         return null;
      }

      double x = (b2 - b1) / (k1 - k2);
      double y = k1 * (b2 - b1) / (k1 - k2) + b1;
      return new Vector2d(x, y);
   }
}
