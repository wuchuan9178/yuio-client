package dev.sherii.yuio.utils;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.mixininterface.IPlayerInteractEntityC2SPacket;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket.InteractType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class InteractUtils {
   public static final InteractUtils INSTANCE = new InteractUtils();

   private InteractUtils() {
   }

   public final InteractType getType(PlayerInteractEntityC2SPacket packet) {
      return ((IPlayerInteractEntityC2SPacket)packet).meteor$getType();
   }

   public final boolean isAttack(PlayerInteractEntityC2SPacket packet) {
      return ((IPlayerInteractEntityC2SPacket)packet).meteor$getType() == InteractType.ATTACK;
   }

   public final Entity getEntity(PlayerInteractEntityC2SPacket packet) {
      return ((IPlayerInteractEntityC2SPacket)packet).meteor$getEntity();
   }

   public final void attack(Entity entity, boolean rotation, boolean swing) {
      if (rotation) {
         Rotations.rotate(Rotations.getYaw(entity), Rotations.getPitch(entity, Target.Head));
      }

      PacketUtils.INSTANCE.sendPacket(PlayerInteractEntityC2SPacket.attack(
         entity, MeteorClient.mc.player != null && MeteorClient.mc.player.isSneaking()
      ));
      if (swing) {
         if (MeteorClient.mc.player != null) {
            ClientPlayerEntity player = MeteorClient.mc.player;
            int var5 = 0;
            player.swingHand(Hand.MAIN_HAND);
         }
      }
   }

   public final void attack(Entity entity) {
      this.attack(entity, false, false);
   }

   public final boolean isInRange(Entity attacker, Entity target, double range) {
      Vec3d pos = attacker.getEntityPos();
      Box hitbox = target.getBoundingBox();
      return this.isInRange(pos, hitbox, range);
   }

   public final boolean isInRange(Vec3d pos, Box hitbox, double range) {
      return PlayerUtils.isWithin(
         net.minecraft.util.math.MathHelper.clamp(pos.x, hitbox.minX, hitbox.maxX),
         net.minecraft.util.math.MathHelper.clamp(pos.y, hitbox.minY, hitbox.maxY),
         net.minecraft.util.math.MathHelper.clamp(pos.z, hitbox.minZ, hitbox.maxZ),
         range
      );
   }
}
