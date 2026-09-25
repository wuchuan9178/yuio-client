package dev.sherii.yuio.utils;

import java.awt.Color;

public final class ColorUtils {
   public static final ColorUtils INSTANCE = new ColorUtils();

   private ColorUtils() {
   }

   public static final Color applyOpacity(Color color, float opacity) {
      float clampedOpacity = net.minecraft.util.math.MathHelper.clamp(opacity, 0.0F, 1.0F);
      return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(color.getAlpha() * clampedOpacity));
   }

   public static final Color interpolateColor(Color color1, Color color2, float fraction) {
      float clamped = net.minecraft.util.math.MathHelper.clamp(fraction, 0.0F, 1.0F);
      int red = net.minecraft.util.math.MathHelper.clamp(
         (int)net.minecraft.util.math.MathHelper.lerp(clamped, color1.getRed(), color2.getRed()), 0, 255
      );
      int green = net.minecraft.util.math.MathHelper.clamp(
         (int)net.minecraft.util.math.MathHelper.lerp(clamped, color1.getGreen(), color2.getGreen()), 0, 255
      );
      int blue = net.minecraft.util.math.MathHelper.clamp(
         (int)net.minecraft.util.math.MathHelper.lerp(clamped, color1.getBlue(), color2.getBlue()), 0, 255
      );
      int alpha = net.minecraft.util.math.MathHelper.clamp(
         (int)net.minecraft.util.math.MathHelper.lerp(clamped, color1.getAlpha(), color2.getAlpha()), 0, 255
      );
      return new Color(red, green, blue, alpha);
   }
}
