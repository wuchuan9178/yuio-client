package dev.sherii.yuio.utils;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

public final class ChunkBuffer {
   public static final ChunkBuffer INSTANCE = new ChunkBuffer();
   private static final Map<Long, WorldChunk> buf = new LinkedHashMap<>();

   // kotlin.Pair 没了, 拿个坐标壳顶上
   public record ChunkPos(int x, int z) {
   }

   private ChunkBuffer() {
   }

   private long encode(int x, int z) {
      return (long)x << 32 | z & 4294967295L;
   }

   private ChunkPos decode(long key) {
      return new ChunkPos((int)(key >> 32), (int)(key & 4294967295L));
   }

   // @PreInit 扫不到这个包, 自己挂上去
   static {
      MeteorClient.EVENT_BUS.subscribe(ChunkBuffer.class);
   }

   public final ChunkPos toChunkPos(int x, int z) {
      return new ChunkPos(x >> 4, z >> 4);
   }

   public final ChunkPos toChunkPos(double x, double z) {
      return this.toChunkPos((int)Math.floor(x), (int)Math.floor(z));
   }

   public final ChunkPos toChunkPos(BlockPos pos) {
      return this.toChunkPos(pos.getX(), pos.getZ());
   }

   public final WorldChunk getChunk(int chunkX, int chunkZ) {
      if (MeteorClient.mc.world == null) return null;

      ClientChunkManager cm = MeteorClient.mc.world.getChunkManager();
      long index = this.encode(chunkX, chunkZ);
      WorldChunk chunk = buf.get(index);
      if (chunk != null) return chunk;

      if (cm.isChunkLoaded(chunkX, chunkZ)) {
         chunk = cm.getWorldChunk(chunkX, chunkZ, false);
         buf.put(index, chunk);
      }

      return chunk;
   }

   public final WorldChunk getChunk(ChunkPos chunkXZ) {
      return this.getChunk(chunkXZ.x(), chunkXZ.z());
   }

   public final BlockState getBlockState(BlockPos pos) {
      WorldChunk chunk = this.getChunk(this.toChunkPos(pos));
      return chunk == null ? null : chunk.getBlockState(pos);
   }

   @EventHandler(priority = 866)
   private static void onTick(Pre event) {
      if (MeteorClient.mc.world == null) return;

      ClientChunkManager cm = MeteorClient.mc.world.getChunkManager();
      Iterator<Map.Entry<Long, WorldChunk>> iterator = buf.entrySet().iterator();

      while (iterator.hasNext()) {
         ChunkPos xz = INSTANCE.decode(iterator.next().getKey());
         if (!cm.isChunkLoaded(xz.x(), xz.z())) iterator.remove();
      }
   }

   @EventHandler(priority = 201)
   private static void onOpenScreen(OpenScreenEvent event) {
      if (event.screen instanceof LevelLoadingScreen) buf.clear();
   }

   @EventHandler(priority = 200)
   private static void onGameJoined(GameJoinedEvent event) {
      buf.clear();
   }
}
