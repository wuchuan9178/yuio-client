package dev.sherii.yuio.utils;

import dev.sherii.yuio.modules.settings.PredictSetting;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class PredictUtil {
   public static final PredictUtil INSTANCE = new PredictUtil();

   private PredictUtil() {
   }

   public final Vec3d predictEntity(Entity entity) {
      Vec3d cur = entity.getEntityPos();
      Vec3d old = new Vec3d(entity.lastX, entity.lastY, entity.lastZ);
      Vec3d vel = cur.subtract(old);
      double mul = PredictSetting.INSTANCE.getResultMul();
      Vec3d scaledVel = vel.multiply(mul);
      Vec3d posVec = cur;
      int ticks = PredictSetting.INSTANCE.getPredictTicks();

      for (int i = 0; i < ticks; i++) {
         Vec3d adjusted = entity.adjustMovementForCollisions(scaledVel);
         posVec = posVec.add(adjusted);
         if (adjusted.lengthSquared() == 0.0) {
            break;
         }
      }

      return posVec;
   }

   public final Box predictBox(Entity entity) {
      Vec3d var10000 = this.predictEntity(entity);
      if (var10000 == null) {
         return null;
      }

      Vec3d posVec = var10000;
      return this.createBox(posVec, entity);
   }

   private final Box createBox(Vec3d vec, Entity entity) {
      Box box = entity.getBoundingBox();
      double dx = vec.x - entity.getX();
      double dy = vec.y - entity.getY();
      double dz = vec.z - entity.getZ();
      Box var10000 = box.offset(dx, dy, dz);
      return var10000;
   }
}
