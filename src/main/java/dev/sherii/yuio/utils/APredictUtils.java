package dev.sherii.yuio.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.GameMode;

public class APredictUtils {
   public static boolean enablePrediction = false;
   public static int predictionTicks = 4;
   public static int smooth = 1;
   public static boolean pingSync = false;
   private static Map<PlayerEntity, List<Vec3d>> motions = new HashMap<>();
   private static Map<PlayerEntity, Vec3d> lastPositions = new HashMap<>();

   @EventHandler(priority = 1000000)
   private static void onTick(Post event) {
      if (MeteorClient.mc.player != null && MeteorClient.mc.world != null && !MeteorClient.mc.world.getPlayers().isEmpty()) {
         Map<PlayerEntity, List<Vec3d>> newMotions = new HashMap<>();

         for (PlayerEntity player : MeteorClient.mc.world.getPlayers()) {
            Vec3d currentPos = new Vec3d(player.getX(), player.getY(), player.getZ());
            Vec3d lastPos = lastPositions.get(player);
            Vec3d vec = lastPos != null ? currentPos.subtract(lastPos) : Vec3d.ZERO;
            List<Vec3d> v = motions.getOrDefault(player, new ArrayList<>());
            v.add(0, vec);
            if (v.size() > 20) {
               v.subList(20, v.size()).clear();
            }

            newMotions.put(player, v);
            lastPositions.put(player, currentPos);
         }

         motions = newMotions;
         lastPositions.keySet().removeIf(p -> !MeteorClient.mc.world.getPlayers().contains(p));
      }
   }

   public static PlayerEntity createPredict(PlayerEntity p) {
      if (!enablePrediction) {
         return p;
      }

      int ticks = pingSync ? (int)Math.ceil((getPing() + 1) / 25.0F) : predictionTicks;
      Box future = extrapolate(p, ticks, smooth);
      if (future == null) {
         return p;
      }

      Vec3d center = new Vec3d((future.maxX + future.minX) / 2.0, future.minY, (future.maxZ + future.minZ) / 2.0);
      return createCopy(p, center);
   }

   public static Box extrapolate(PlayerEntity player, int extrapolation, int smoothening) {
      List<Vec3d> m = motions.get(player);
      return m == null ? null : extrapolate(player, m, extrapolation, smoothening);
   }

   private static Box extrapolate(PlayerEntity player, List<Vec3d> m, int extrapolation, int smoothening) {
      Vec3d motion = getMotion(m, smoothening);
      double x = motion.getX();
      double y = motion.getY();
      double z = motion.getZ();
      double stepHeight = 0.6;
      Box box = new Box(
         player.getX() - 0.3,
         player.getY(),
         player.getZ() - 0.3,
         player.getX() + 0.3,
         player.getY() + (player.getBoundingBox().maxY - player.getBoundingBox().minY),
         player.getZ() + 0.3
      );
      boolean onGround = inside(player, box.offset(0.0, -0.04, 0.0));

      for (int i = 0; i < extrapolation; i++) {
         Iterable<VoxelShape> iterable = MeteorClient.mc.world.getCollisions(player, box.offset(x, y, z));
         List<VoxelShape> list = new ArrayList<>();
         iterable.forEach(list::add);
         Vec3d movement = new Vec3d(x, y, z);
         Vec3d vec3d = movement.lengthSquared() == 0.0 ? movement : Entity.adjustMovementForCollisions(player, movement, box, MeteorClient.mc.world, list);
         boolean canStep = (onGround || y < 0.0 && vec3d.getY() != y) && (vec3d.getX() != x || vec3d.getZ() != z);
         if (canStep) {
            Vec3d vec3d2 = Entity.adjustMovementForCollisions(player, new Vec3d(x, stepHeight, z), box, MeteorClient.mc.world, list);
            Vec3d vec3d3 = Entity.adjustMovementForCollisions(player, new Vec3d(0.0, stepHeight, 0.0), box.offset(x, 0.0, z), MeteorClient.mc.world, list);
            if (vec3d3.getY() < stepHeight) {
               Vec3d vec3d4 = Entity.adjustMovementForCollisions(
                     player, new Vec3d(movement.getX(), 0.0, movement.getZ()), box.offset(vec3d3), MeteorClient.mc.world, list
                  )
                  .add(vec3d3);
               if (vec3d4.lengthSquared() > vec3d2.lengthSquared()) {
                  vec3d2 = vec3d4;
               }
            }

            if (vec3d2.lengthSquared() > vec3d.lengthSquared()) {
               Vec3d vec = vec3d2.add(
                  Entity.adjustMovementForCollisions(
                     player, new Vec3d(0.0, -vec3d2.getY() + movement.getY(), 0.0), box.offset(vec3d2), MeteorClient.mc.world, list
                  )
               );
               box = box.offset(vec);
               onGround = true;
               continue;
            }
         }

         box = box.offset(vec3d);
         onGround = inside(player, box.offset(0.0, -0.04, 0.0));
         if (onGround) {
            y = 0.0;
         }

         y = (y - 0.08) * 0.98;
      }

      return box;
   }

   private static Vec3d getMotion(List<Vec3d> vecs, int max) {
      Vec3d avg = new Vec3d(0.0, (vecs.get(0).getY() - 0.08) * 0.98, 0.0);
      int s = Math.min(vecs.size(), max);

      for (int i = 0; i < s; i++) {
         avg = avg.add(vecs.get(i).getX(), 0.0, vecs.get(i).getZ());
      }

      return avg.multiply(1.0F / s, 1.0, 1.0F / s);
   }

   public static int getPing() {
      if (MeteorClient.mc.getNetworkHandler() != null && MeteorClient.mc.player != null) {
         PlayerListEntry playerListEntry = MeteorClient.mc.getNetworkHandler().getPlayerListEntry(MeteorClient.mc.player.getUuid());
         return playerListEntry == null ? 0 : playerListEntry.getLatency();
      } else {
         return 0;
      }
   }

   public static PlayerEntity createCopy(PlayerEntity original, Vec3d posVec) {
      PlayerEntity copyEntity = new PlayerEntity(MeteorClient.mc.world, original.getGameProfile()) {
         public boolean isSpectator() {
            return original.isSpectator();
         }

         public boolean isCreative() {
            return original.isCreative();
         }

         public GameMode getGameMode() {
            return original.getGameMode();
         }
      };
      copyEntity.setCustomName(original.getDisplayName());
      copyEntity.setPosition(posVec);
      copyEntity.setVelocity(original.getVelocity());
      copyEntity.setHealth(original.getHealth());
      copyEntity.setAbsorptionAmount(original.getAbsorptionAmount());
      copyEntity.setAir(original.getAir());
      copyEntity.setOnGround(original.isOnGround());
      copyEntity.setUuid(original.getUuid());
      copyEntity.getAbilities().setFlySpeed(original.getAbilities().getFlySpeed());
      copyEntity.setPose(original.getPose());
      copyEntity.getInventory().clone(original.getInventory());

      for (StatusEffectInstance se : original.getActiveStatusEffects().values()) {
         copyEntity.addStatusEffect(se);
      }

      return copyEntity;
   }

   public static Box createBox(Vec3d vec, Entity entity) {
      Vec3d entityPos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
      return entity.getBoundingBox().offset(entityPos.negate()).offset(vec);
   }

   public static boolean inside(PlayerEntity en, Box bb) {
      return MeteorClient.mc.world.getEntityCollisions(en, bb).iterator().hasNext();
   }

   static {
      MeteorClient.EVENT_BUS.subscribe(APredictUtils.class);
   }
}
