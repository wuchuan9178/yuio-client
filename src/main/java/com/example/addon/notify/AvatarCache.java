package com.example.addon.notify;

import com.example.addon.AddonTemplate;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.texture.NativeImage;
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

public final class AvatarCache {
    private static final int MAX = 64;

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "yuio-avatar");
        thread.setDaemon(true);
        return thread;
    });

    private static final Map<String, GpuTextureView> READY = new ConcurrentHashMap<>();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
    private static final Queue<Downloaded> DONE = new ConcurrentLinkedQueue<>();

    private record Downloaded(String key, ByteBuffer pixels, int width, int height) {
    }

    private AvatarCache() {
    }

    public static GpuTextureView get(String path) {
        if (path == null || path.isEmpty()) return null;

        GpuTextureView view = READY.get(path);
        if (view != null) return view;
        if (READY.size() >= MAX) return null;

        if (PENDING.add(path)) POOL.submit(() -> load(path));
        return null;
    }

    public static void poll() {
        Downloaded done;
        while ((done = DONE.poll()) != null) {
            if (READY.containsKey(done.key())) continue;

            try {
                GpuDevice device = RenderSystem.getDevice();
                GpuTexture texture = device.createTexture(
                    "yuio - qq avatar",
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    TextureFormat.RGBA8, done.width(), done.height(), 1, 1
                );

                device.createCommandEncoder().writeToTexture(
                    texture, done.pixels(), NativeImage.Format.RGBA,
                    0, 0, 0, 0, done.width(), done.height()
                );

                READY.put(done.key(), device.createTextureView(texture));
            } catch (Throwable error) {
                AddonTemplate.LOG.warn("[yuio-qq] avatar upload failed: {}", error.toString());
            }
        }
    }

    private static void load(String path) {
        try (InputStream in = Files.newInputStream(Path.of(path))) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) return;

            int width = image.getWidth();
            int height = image.getHeight();
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
            DONE.add(new Downloaded(path, pixels, width, height));
        } catch (Throwable ignored) {
        }
    }
}

