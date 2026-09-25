package dev.sherii.yuio.utils;

import java.util.LinkedHashMap;
import java.util.Map;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;

public class BlockUtil {
   public static final BlockUtil INSTANCE = new BlockUtil();
   private static final Map<BlockPos, BlockState> bufferMap = new LinkedHashMap<>();

   public static boolean isSafeBlock(BlockPos pos) {
      BlockState blockState = MeteorClient.mc.world.getBlockState(pos);
      FluidState fluidState = MeteorClient.mc.world.getFluidState(pos);
      return blockState.isReplaceable() && fluidState.isEmpty() && !blockState.isOf(Blocks.POWDER_SNOW);
   }

   public static boolean checkNoPosCollie(Vec3d vec, Map<BlockPos, BlockState> extraPosMap, PlayerUtil.PlayerState state) {
      return collidesWithAnyBlock(state.getBBox(vec), extraPosMap, state);
   }

   public static boolean collidesWithAnyBlock(Box bbox, Map<BlockPos, BlockState> extraPosMap, PlayerUtil.PlayerState condition) {
      Mutable pos = new Mutable();
      int minX = (int)Math.floor(bbox.minX);
      int minY = (int)Math.floor(bbox.minY) - 1;
      int minZ = (int)Math.floor(bbox.minZ);
      int maxX = (int)Math.floor(bbox.maxX);
      int maxY = (int)Math.floor(bbox.maxY);
      int maxZ = (int)Math.floor(bbox.maxZ);

      for (int x = minX; x <= maxX; x++) {
         for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
               pos.set(x, y, z);
            }
         }
      }

      return true;
   }

   public static boolean isBlinkVec(Vec3d vec) {
      if (MeteorClient.mc.player != null && MeteorClient.mc.world != null) {
         Box originalBox = MeteorClient.mc.player.getBoundingBox();
         double width = originalBox.maxX - originalBox.minX;
         double height = originalBox.maxY - originalBox.minY;
         double depth = originalBox.maxZ - originalBox.minZ;
         Box targetBox = new Box(
            vec.x - width / 2.0,
            vec.y,
            vec.z - depth / 2.0,
            vec.x + width / 2.0,
            vec.y + height,
            vec.z + depth / 2.0
         );
         Mutable pos = new Mutable();
         int minX = (int)Math.floor(targetBox.minX);
         int minY = (int)Math.floor(targetBox.minY);
         int minZ = (int)Math.floor(targetBox.minZ);
         int maxX = (int)Math.floor(targetBox.maxX);
         int maxY = (int)Math.floor(targetBox.maxY);
         int maxZ = (int)Math.floor(targetBox.maxZ);

         for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
               for (int z = minZ; z <= maxZ; z++) {
                  pos.set(x, y, z);
                  BlockState state = MeteorClient.mc.world.getBlockState(pos);
                  if (!state.isAir() && !state.isReplaceable()) {
                     return false;
                  }
               }
            }
         }

         return true;
      } else {
         return false;
      }
   }
}
