package dev.sherii.yuio.manager;

import dev.sherii.yuio.modules.settings.PredictSetting;
import java.util.ArrayList;
import net.minecraft.util.math.Vec3d;

public final class PredictManager {
   private final ArrayList<Vec3d> pos = new ArrayList<>();

   public final void push(Vec3d value) {
      int dataSize = PredictSetting.INSTANCE.getDataSize();
      if (this.pos.size() >= dataSize) {
         this.pos.subList(0, this.pos.size() - dataSize + 1).clear();
      }

      this.pos.add(value);
   }

   public final boolean isEmpty() {
      return this.pos.isEmpty();
   }

   public final boolean isNotEmpty() {
      return !this.pos.isEmpty();
   }

   public final Vec3d getDiff() {
      return this.getDiff(PredictSetting.INSTANCE.getResultMul());
   }

   public final Vec3d getDiff(double mul) {
      Vec3d diff = switch (this.pos.size()) {
         case 1 -> (Vec3d)this.pos.get(0);
         case 2 -> this.pos.get(0).add(this.pos.get(1).subtract(this.pos.get(0)));
         case 3 -> this.pos.get(2).multiply(3.0).subtract(this.pos.get(1).multiply(3.0)).add(this.pos.get(0));
         case 4 -> this.pos
            .get(3)
            .multiply(4.0)
            .subtract(this.pos.get(2).multiply(6.0))
            .add(this.pos.get(1).multiply(4.0))
            .subtract(this.pos.get(0));
         default -> throw new IllegalArgumentException("Predict data is not in 1..4.");
      };
      return diff.subtract(this.pos.get(this.pos.size() - 1)).multiply(mul);
   }

   public final void clear() {
      this.pos.clear();
   }
}
