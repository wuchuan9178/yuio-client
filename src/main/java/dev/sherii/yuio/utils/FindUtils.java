package dev.sherii.yuio.utils;

import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.PlantBlock;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;

public final class FindUtils {
   public static final FindUtils INSTANCE = new FindUtils();
   private static final float CRYSTAL_POWER = 12.0F;
   private static final float BED_POWER = 10.0F;
   private static final float ANCHOR_POWER = 10.0F;

   private FindUtils() {
   }

   public final float getCRYSTAL_POWER() {
      return CRYSTAL_POWER;
   }

   public final float getBED_POWER() {
      return BED_POWER;
   }

   public final float getANCHOR_POWER() {
      return ANCHOR_POWER;
   }

   public final Entity findTarget(
      Set<? extends EntityType<?>> entities, boolean nofriend, boolean nocreative, Predicate<Entity> other
   ) {
      if (MeteorClient.mc.world == null) return null;

      return TargetUtils.get(entity -> {
         if (entity == MeteorClient.mc.player || entity == MeteorClient.mc.getCameraEntity()) return false;
         if (!(entity.isAlive() || entity instanceof PlayerEntity p && p.hasVehicle())) return false;
         if (entity instanceof LivingEntity le && (le.isDead() || le.getHealth() <= 0.0F)) return false;
         if (nofriend && entity instanceof PlayerEntity p && !Friends.get().shouldAttack(p)) return false;
         if (nocreative && entity instanceof PlayerEntity p && p.isCreative()) return false;
         if (!entities.contains(entity.getType())) return false;
         return other.test(entity);
      }, SortPriority.LowestDistance);
   }

   public final Entity findTarget(Set<? extends EntityType<?>> entities, Predicate<Entity> other) {
      return this.findTarget(entities, true, true, other);
   }

   public final boolean isAir(BlockPos pos, BlockState state) {
      if (MeteorClient.mc.world == null) return false;

      ClientWorld world = MeteorClient.mc.world;
      if (!world.getWorldBorder().contains(pos)) {
         return false;
      } else if (state.isAir() || state.getBlock() instanceof PlantBlock) {
         return true;
      } else if (!state.getFluidState().isEmpty()) {
         return true;
      } else {
         return state.isReplaceable() || state.getCollisionShape((BlockView)world, pos).isEmpty();
      }
   }

   public final boolean isTruthAir(BlockPos pos, BlockState state) {
      if (MeteorClient.mc.world == null) return false;

      return MeteorClient.mc.world.getWorldBorder().contains(pos) && state.isAir();
   }

   public final Integer getPlayerHeight() {
      if (MeteorClient.mc.player == null) return null;

      return (int)Math.floor(MeteorClient.mc.player.getBoundingBox().getLengthY()) + 1;
   }

   public final Double findVClip(Vec3d from, Vec3d to) {
      if (MeteorClient.mc.world == null) return from.y;

      ClientWorld world = MeteorClient.mc.world;
      Integer playerHeight = this.getPlayerHeight();
      if (playerHeight == null) return null;

      int bottomY = world.getBottomY();
      int maxY = (world.getHeight() - bottomY) / 2;

      for (int y = 0; y <= maxY; y++) {
         double up = from.y + y;
         if (this.isVClipSafe(up, from, to)) return Math.floor(up);

         double down = from.y - y;
         if (this.isVClipSafe(down, from, to)) return Math.floor(down);
      }

      return null;
   }

   private boolean isVClipSafe(double y, Vec3d from, Vec3d to) {
      Vec3d a = new Vec3d(from.x, y, from.z);
      Vec3d b = new Vec3d(to.x, y, to.z);
      return TpUtils.INSTANCE.isPlayerSafe(BlockPos.ofFloored(a))
         && TpUtils.INSTANCE.isPlayerSafe(BlockPos.ofFloored(b))
         && TpUtils.INSTANCE.isPathMiss(a, b);
   }

   public final Double findVClip(Vec3d pos, double length) {
      if (MeteorClient.mc.world == null) return pos.y;

      int maxY = (int)Math.floor(length);

      for (int y = 0; y <= maxY; y++) {
         double up = pos.y + y;
         if (TpUtils.INSTANCE.isPlayerSafe(BlockPos.ofFloored(pos.x, up, pos.z))) return Math.floor(up);

         double down = pos.y - y;
         if (TpUtils.INSTANCE.isPlayerSafe(BlockPos.ofFloored(pos.x, down, pos.z))) return Math.floor(down);
      }

      return null;
   }

   public final <T> T blockIter(BlockPos pos, int hori, int vert, BiFunction<BlockPos, BlockState, T> func) {
      if (MeteorClient.mc.world == null) return null;

      ClientWorld world = MeteorClient.mc.world;
      Mutable blockPos = new Mutable();

      for (int x = pos.getX() - hori; x <= pos.getX() + hori; x++) {
         for (int z = pos.getZ() - hori; z <= pos.getZ() + hori; z++) {
            for (int y = pos.getY() - vert; y <= pos.getY() + vert; y++) {
               blockPos.set(x, y, z);
               BlockState state = ChunkBuffer.INSTANCE.getBlockState(blockPos);
               if (state != null) {
                  T result = func.apply(blockPos, state);
                  if (result != null) return result;
               }
            }
         }
      }

      return null;
   }

   public final <T> T radiusBlockIter(BlockPos pos, double radius, BiFunction<BlockPos, BlockState, T> func) {
      Vec3d center = pos.toCenterPos();
      int range = (int)Math.ceil(radius);
      return this.blockIter(pos, range, range, (blockPos, state) ->
         center.distanceTo(blockPos.toCenterPos()) > radius ? null : func.apply(blockPos, state));
   }

   public final Vec3d minExplDmgPos(
      LivingEntity target,
      Vec3d targetPos,
      Vec3d explosionPos,
      double radius,
      float power,
      BiFunction<BlockPos, BlockState, Boolean> condition,
      float maxDamage
   ) {
      Vec3d posDiff = targetPos.subtract(target.getEntityPos());
      Box targetBox = target.getBoundingBox().offset(posDiff);
      BlockPos blockTargetPos = BlockPos.ofFloored(targetPos);
      Vec3d[] result = {null};
      float[] prevDamage = {Float.MAX_VALUE};

      Vec3d iterResult = this.radiusBlockIter(blockTargetPos, radius, (blockPos, blockState) -> {
         if (!condition.apply(blockPos, blockState)) return null;

         Vec3d pos = blockPos.toCenterPos().subtract(0.0, 0.5, 0.0);
         Box box = targetBox.offset(pos.subtract(targetPos));
         float damage = DamageUtils.explosionDamage(target, pos, box, explosionPos, power, DamageUtils.HIT_FACTORY);
         if (damage <= maxDamage) return pos;

         if (damage < prevDamage[0]) {
            result[0] = pos;
            prevDamage[0] = damage;
         }

         return null;
      });

      return iterResult != null ? iterResult : result[0];
   }

   public final Vec3d maxExplDmgPos(
      LivingEntity target,
      Vec3d targetPos,
      double radius,
      float power,
      BiFunction<BlockPos, BlockState, Boolean> condition,
      float minDamage
   ) {
      if (MeteorClient.mc.world == null) return null;

      Vec3d posDiff = targetPos.subtract(target.getEntityPos());
      Box targetBox = target.getBoundingBox().offset(posDiff);
      BlockPos blockTargetPos = BlockPos.ofFloored(targetPos);
      Vec3d[] result = {null};
      float[] prevDamage = {-1.0F};

      Vec3d iterResult = this.radiusBlockIter(blockTargetPos, radius, (blockPos, blockState) -> {
         if (!condition.apply(blockPos, blockState)) return null;

         Vec3d pos = blockPos.toCenterPos().subtract(0.0, 0.5, 0.0);
         float damage = DamageUtils.explosionDamage(target, targetPos, targetBox, pos, power, DamageUtils.HIT_FACTORY);
         if (damage >= minDamage) return pos;

         if (damage > prevDamage[0]) {
            result[0] = pos;
            prevDamage[0] = damage;
         }

         return null;
      });

      return iterResult != null ? iterResult : result[0];
   }
}
