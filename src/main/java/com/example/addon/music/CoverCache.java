package com.example.addon.music;

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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CoverCache {
    private static final int MAX = 32;
    private static final String REFERER = "https://music.163.com/";
    private static final String AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36";

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "yuio-cover");
        thread.setDaemon(true);
        return thread;
    });

    private static final Map<String, GpuTextureView> READY = new ConcurrentHashMap<>();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
    private static final Queue<Downloaded> DONE = new ConcurrentLinkedQueue<>();

    private record Downloaded(String key, ByteBuffer pixels, int width, int height) {
    }

    private CoverCache() {
    }

    public static GpuTextureView get(String url) {
        if (url == null || url.isEmpty()) return null;

        GpuTextureView view = READY.get(url);
        if (view != null) return view;
        if (READY.size() >= MAX) return null;

        if (PENDING.add(url)) POOL.submit(() -> download(url));
        return null;
    }

    public static void poll() {
        Downloaded done;
        while ((done = DONE.poll()) != null) {
            if (READY.containsKey(done.key())) continue;

            try {
                GpuDevice device = RenderSystem.getDevice();
                GpuTexture texture = device.createTexture(
                    "yuio - album cover",
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    TextureFormat.RGBA8, done.width(), done.height(), 1, 1
                );

                device.createCommandEncoder().writeToTexture(
                    texture, done.pixels(), NativeImage.Format.RGBA,
                    0, 0, 0, 0, done.width(), done.height()
                );

                READY.put(done.key(), device.createTextureView(texture));
            } catch (Throwable error) {
                AddonTemplate.LOG.warn("[yuio-music] cover upload failed: {}", error.toString());
            }
        }
    }

    private static void download(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Referer", REFERER)
                .header("User-Agent", AGENT)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                AddonTemplate.LOG.warn("[yuio-music] cover http {}", response.statusCode());
                return;
            }

            try (InputStream in = response.body()) {
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
                DONE.add(new Downloaded(url, pixels, width, height));
            }
        } catch (Throwable error) {
            AddonTemplate.LOG.warn("[yuio-music] cover download failed: {}", error.toString());
        }
    }
}
