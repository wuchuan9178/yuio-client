package dev.sherii.yuio.utils;

import dev.sherii.yuio.modules.settings.TpSetting;
import dev.sherii.yuio.modules.settings.TpSetting.Action;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.mixininterface.IPlayerMoveC2SPacket;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.listeners.ConsumerListener;
import meteordevelopment.orbit.listeners.IListener;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.OnGroundOnly;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class TpUtils {
   public static final TpUtils INSTANCE = new TpUtils();
   private static IntervalledCounter counter = new IntervalledCounter((long)(TpSetting.INSTANCE.getInterval() * 1.0E9));
   private static int tickCounter;

   private TpUtils() {
   }

   private final int getSteps(Vec3d from, Vec3d to) {
      double distance = from.distanceTo(to);
      double md = TpSetting.INSTANCE.getMoveDistance();
      int steps = md <= 0.0 ? 1 : (int)Math.ceil(distance / md);
      return Math.max(0, steps - 1 - tickCounter);
   }

   private final boolean isDrop() {
      return TpSetting.INSTANCE.getAction() == TpSetting.Action.Both || TpSetting.INSTANCE.getAction() == TpSetting.Action.Drop;
   }

   private final boolean isMessage() {
      return TpSetting.INSTANCE.getAction() == TpSetting.Action.Both || TpSetting.INSTANCE.getAction() == TpSetting.Action.Message;
   }

   public final void setInterval(double interval) {
      counter = new IntervalledCounter((long)(interval * 1.0E9));
   }

   public final double getRate() {
      return counter.getRate();
   }

   public final boolean hasRateOut() {
      counter.updateCurrentTime(System.nanoTime());
      return this.isDrop() && counter.getRate() >= TpSetting.INSTANCE.getMaxPacketRate();
   }

   public final boolean hasTickPacketOut() {
      int num = TpSetting.INSTANCE.getInOneTick();
      return this.isDrop() && num > 0.0 && tickCounter > num;
   }

   private final void sendLimitMsg() {
      if (this.isMessage()) {
         ChatUtils.errorPrefix("TpUtils", "Limited packet.(操你妈你怎么用这么多包", new Object[0]);
      }
   }

   public final boolean isPathMiss(Vec3d from, Vec3d to) {
      if (MeteorClient.mc.player == null) {
         return false;
      }

      ClientPlayerEntity player = MeteorClient.mc.player;
      Vec3d playerPos = player.getEntityPos();
      Vec3d diff = to.subtract(from);
      Box originBox = player.getBoundingBox();
      boolean doUpdate = !java.util.Objects.equals(from, playerPos);
      if (doUpdate) {
         this.setPos(from);
      }

      boolean isMiss = java.util.Objects.equals(player.adjustMovementForCollisions(diff), diff);
      if (doUpdate) {
         this.setPos(playerPos);
      }

      return isMiss;
   }

   public final boolean isPlayerSafe(BlockPos pos) {
      if (MeteorClient.mc.player == null) {
         return false;
      }

      ClientPlayerEntity player = MeteorClient.mc.player;
      Integer var10000 = FindUtils.INSTANCE.getPlayerHeight();
      if (var10000 != null) {
         int height = var10000;

         for (int i = 0; i < height; i++) {
            BlockPos newPos = pos.add(0, i, 0);
            ChunkBuffer var7 = ChunkBuffer.INSTANCE;
            BlockState var8 = var7.getBlockState(newPos);
            if (var8 == null) {
               return false;
            }

            BlockState state = var8;
            if (!FindUtils.INSTANCE.isAir(newPos, state)) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   public final void tp(Vec3d from, Vec3d to, float yaw, float pitch, boolean onGround) {
      if (this.hasRateOut()) {
         this.sendLimitMsg();
      } else {
         int steps = this.getSteps(from, to);

         for (int i = 0; i < steps; i++) {
            this.sendMovePacket(from, onGround, null);
         }

         this.sendMovePacket(to, yaw, pitch, onGround, null);
      }
   }

   public final void tp(Vec3d from, Vec3d to, boolean onGround) {
      if (this.hasRateOut()) {
         this.sendLimitMsg();
      } else {
         int steps = this.getSteps(from, to);

         for (int i = 0; i < steps; i++) {
            this.sendMovePacket(from, onGround, null);
         }

         this.sendMovePacket(to, onGround, null);
      }
   }

   public final void tp3rd(Vec3d from, Vec3d to, boolean onGround) {
      if (this.hasRateOut()) {
         this.sendLimitMsg();
      } else if (this.isPathMiss(from, to)) {
         this.tp(from, to, onGround);
      } else {
         Double vclip = FindUtils.INSTANCE.findVClip(from, to);
         if (vclip == null) {
            this.tp(from, to, onGround);
         } else {
            Vec3d fromVClipPos = new Vec3d(from.x, vclip, from.z);
            Vec3d toVClipPos = new Vec3d(to.x, vclip, to.z);
            this.tp(from, fromVClipPos, onGround);
            this.tp(fromVClipPos, toVClipPos, onGround);
            this.tp(toVClipPos, to, onGround);
         }
      }
   }

   public final void setPos(Vec3d pos) {
      if (MeteorClient.mc.player != null) {
         ClientPlayerEntity player = MeteorClient.mc.player;
         player.pos = pos;
         player.setBoundingBox(player.calculateBoundingBox());
      }
   }

   public final void sendMovePacket(double x, double y, double z, float yaw, float pitch, boolean onGround, Integer tag) {
      if (this.hasRateOut() || this.hasTickPacketOut()) {
         this.sendLimitMsg();
      } else if (MeteorClient.mc.player != null) {
         ClientPlayerEntity player = MeteorClient.mc.player;
         player.setSneaking(false);
         Full packet = new Full(x, y, z, yaw, pitch, onGround, false);
         if (tag != null) {
            int it = tag.intValue();
            int var14 = 0;
            ((IPlayerMoveC2SPacket)packet).meteor$setTag(it);
         }

         if (player.networkHandler != null) {
            player.networkHandler.sendPacket((Packet)packet);
         }

         tickCounter++;
      }
   }

   public final void sendMovePacket(Vec3d pos, float yaw, float pitch, boolean onGround, Integer tag) {
      this.sendMovePacket(pos.x, pos.y, pos.z, yaw, pitch, onGround, tag);
   }

   public final void sendMovePacket(double x, double y, double z, boolean onGround, Integer tag) {
      if (this.hasRateOut() || this.hasTickPacketOut()) {
         this.sendLimitMsg();
      } else if (MeteorClient.mc.player != null) {
         ClientPlayerEntity player = MeteorClient.mc.player;
         player.setSneaking(false);
         PositionAndOnGround packet = new PositionAndOnGround(x, y, z, onGround, false);
         if (tag != null) {
            int it = tag.intValue();
            int var12 = 0;
            ((IPlayerMoveC2SPacket)packet).meteor$setTag(it);
         }

         if (player.networkHandler != null) {
            player.networkHandler.sendPacket((Packet)packet);
         }

         tickCounter++;
      }
   }

   public final void sendMovePacket(Vec3d pos, boolean onGround, Integer tag) {
      this.sendMovePacket(pos.x, pos.y, pos.z, onGround, tag);
   }

   public final void sendMovePacket(float yaw, float pitch, boolean onGround, Integer tag) {
      if (!this.hasRateOut() && !this.hasTickPacketOut()) {
         LookAndOnGround packet = new LookAndOnGround(yaw, pitch, onGround, false);
         if (tag != null) {
            int it = tag.intValue();
            int var7 = 0;
            ((IPlayerMoveC2SPacket)packet).meteor$setTag(it);
         }

         if (MeteorClient.mc.player != null && MeteorClient.mc.player.networkHandler != null) {
            MeteorClient.mc.player.networkHandler.sendPacket((Packet)packet);
         }

         tickCounter++;
      } else {
         this.sendLimitMsg();
      }
   }

   public final void sendMovePacket(boolean onGround, Integer tag) {
      if (!this.hasRateOut() && !this.hasTickPacketOut()) {
         OnGroundOnly packet = new OnGroundOnly(onGround, false);
         if (tag != null) {
            int it = tag.intValue();
            int var5 = 0;
            ((IPlayerMoveC2SPacket)packet).meteor$setTag(it);
         }

         if (MeteorClient.mc.player != null && MeteorClient.mc.player.networkHandler != null) {
            MeteorClient.mc.player.networkHandler.sendPacket((Packet)packet);
         }

         tickCounter++;
      } else {
         this.sendLimitMsg();
      }
   }

   private static final void _init_$lambda$0(Send it) {
      counter.updateAndAdd(1L);
   }

   private static final void _init_$lambda$1(Receive it) {
      Packet packet = it.packet;
      if (packet instanceof PlayerPositionLookS2CPacket) {
         tickCounter += -1;
      }
   }

   private static final void _init_$lambda$2(Pre it) {
      tickCounter = 0;
   }

   static {
      MeteorClient.EVENT_BUS.subscribe(new ConsumerListener<Send>(Send.class, -1199, TpUtils::_init_$lambda$0));
      MeteorClient.EVENT_BUS.subscribe(new ConsumerListener<Receive>(Receive.class, 1199, TpUtils::_init_$lambda$1));
      MeteorClient.EVENT_BUS.subscribe(new ConsumerListener<Pre>(Pre.class, 1199, TpUtils::_init_$lambda$2));
   }
}
