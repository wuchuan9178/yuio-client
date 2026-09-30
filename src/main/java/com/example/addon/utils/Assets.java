package com.example.addon.utils;

import com.example.addon.AddonTemplate;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;
import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Assets {
    private static final Path ROOT = Path.of("meteor", "yuio");
    private static final int MAX = 64;

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "yuio-assets");
        thread.setDaemon(true);
        return thread;
    });

    private static final Map<String, GpuTextureView> READY = new ConcurrentHashMap<>();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
    private static final Queue<Loaded> DONE = new ConcurrentLinkedQueue<>();

    private record Loaded(String key, ByteBuffer pixels, int width, int height) {
    }

    private Assets() {
    }

    public static Path folder() {
        return ROOT.toAbsolutePath();
    }

    public static GpuTextureView texture(String path) {
        if (path == null || path.isEmpty()) return null;

        GpuTextureView view = READY.get(path);
        if (view != null) return view;
        if (READY.size() >= MAX) return null;

        if (PENDING.add(path)) POOL.submit(() -> load(path));
        return null;
    }

    public static boolean isReady(String path) {
        return path != null && READY.containsKey(path);
    }

    public static void poll() {
        Loaded done;
        while ((done = DONE.poll()) != null) {
            if (READY.containsKey(done.key())) continue;

            try {
                GpuDevice device = RenderSystem.getDevice();
                GpuTexture texture = device.createTexture(
                    "yuio - asset",
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    TextureFormat.RGBA8, done.width(), done.height(), 1, 1
                );

                device.createCommandEncoder().writeToTexture(
                    texture, done.pixels(), NativeImage.Format.RGBA,
                    0, 0, 0, 0, done.width(), done.height()
                );

                READY.put(done.key(), device.createTextureView(texture));
            } catch (Throwable error) {
                AddonTemplate.LOG.warn("[yuio] asset upload failed: {}", error.toString());
            }
        }
    }

    public static void reload() {
        READY.clear();
        PENDING.clear();
        DONE.clear();
    }

    private static void load(String path) {
        Loaded loaded = decode(path, externalStream(path));
        if (loaded == null) loaded = decode(path, jarStream(path));
        if (loaded != null) DONE.add(loaded);
    }

    private static Loaded decode(String path, InputStream stream) {
        if (stream == null) return null;

        try (InputStream in = stream) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) return null;

            int width = image.getWidth();
            int height = image.getHeight();
            if (width <= 0 || height <= 0) return null;

            ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = image.getRGB(x, y);
                    pixels.put((byte) ((argb >> 16) & 0xFF));
                    pixels.put((byte) ((argb >> 8) & 0xFF));
                    pixels.put((byte) (argb & 0xFF));
                    pixels.put((byte) ((argb >>> 24) & 0xFF));
                }
            }

            pixels.flip();
            return new Loaded(path, pixels, width, height);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static InputStream externalStream(String path) {
        try {
            Path file = ROOT.resolve(path);
            return Files.isRegularFile(file) ? Files.newInputStream(file) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static InputStream jarStream(String path) {
        try {
            var manager = MinecraftClient.getInstance().getResourceManager();
            var resource = manager.getResource(Identifier.of("yuio", path));
            if (resource.isEmpty()) resource = manager.getResource(Identifier.of("yuio", "textures/" + path));
            return resource.orElseThrow().getInputStream();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
