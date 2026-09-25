package dev.sherii.yuio.utils;

import java.util.Arrays;
import org.meteordev.starscript.Starscript;
import org.meteordev.starscript.value.Value;

public final class StarscriptUtils {
   public static final StarscriptUtils INSTANCE = new StarscriptUtils();

   private StarscriptUtils() {
   }

   public final void initColor(Starscript ss) {
      ss.set("rgb", Value.function(StarscriptUtils::initColor$lambda$0));
      ss.set("ss", Value.string("搂"));
   }

   private static final Value initColor$lambda$0(Starscript ss, int args) {
      if (args != 3) {
         Object[] var10 = new Object[]{args};
         ss.error("Args require 3, but is %d.", var10);
         return null;
      } else {
         double b = ss.popNumber("Require number.");
         double g = ss.popNumber("Require number.");
         double r = ss.popNumber("Require number.");
         String var8 = "%02X";
         Object[] var9 = new Object[]{(int)r};
         var9 = var9;
         String var10000 = String.format(var8, Arrays.copyOf(var9, var9.length));
         var8 = "%02X";
         var9 = new Object[]{(int)g};
         var9 = var9;
         String var10001 = String.format(var8, Arrays.copyOf(var9, var9.length));
         var8 = "%02X";
         var9 = new Object[]{(int)b};
         var9 = var9;
         String var10002 = String.format(var8, Arrays.copyOf(var9, var9.length));
         return Value.string("#" + var10000 + var10001 + var10002 + ";");
      }
   }
}
