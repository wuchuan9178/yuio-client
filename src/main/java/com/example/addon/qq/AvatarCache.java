package com.example.addon.qq;

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

/**
 * QQ 头像的下载与上传。
 *
 * <p>下载在后台线程做, 建纹理必须在渲染线程, 所以两边用一个队列交接:
 * 渲染时 {@link #poll()} 取货, 还没好的这帧先不画头像。
 *
 * <p>解码走 JDK 的 ImageIO (PNG / JPEG 都认, qlogo 两种都用), 不用 NativeImage,
 * 免得各版本对格式和上下翻转的处理不一样。上传出来的第 0 行就是图片顶行。
 */
public final class AvatarCache {
    /** 头像 100x100 也就 40KB, 到量就不再加新的, 免得长时间挂机吃内存。 */
    private static final int MAX = 64;

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "yuio-qq-avatar");
        thread.setDaemon(true);
        return thread;
    });

    private static final Map<String, GpuTextureView> READY = new ConcurrentHashMap<>();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
    private static final Queue<Downloaded> DONE = new ConcurrentLinkedQueue<>();

    private record Downloaded(String url, ByteBuffer pixels, int width, int height) {
    }

    private AvatarCache() {
    }

    /** 拿头像; 没下载好返回 null, 下一帧再问。 */
    public static GpuTextureView get(String url) {
        if (url == null || url.isEmpty()) return null;

        GpuTextureView view = READY.get(url);
        if (view != null) return view;
        if (READY.size() >= MAX) return null;

        if (PENDING.add(url)) POOL.submit(() -> download(url));
        return null;
    }

    /** 每帧在渲染线程调一次。 */
    public static void poll() {
        Downloaded done;
        while ((done = DONE.poll()) != null) {
            if (READY.containsKey(done.url())) continue;

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

                READY.put(done.url(), device.createTextureView(texture));
                AddonTemplate.LOG.info("[yuio-qq] avatar ready {} ({}x{})",
                    done.url(), done.width(), done.height());
            } catch (Throwable error) {
                AddonTemplate.LOG.warn("[yuio-qq] avatar upload failed: {} ({})",
                    done.url(), error.toString());
            }
        }
    }

    private static void download(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();

            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                AddonTemplate.LOG.warn("[yuio-qq] avatar http {} ({})", response.statusCode(), url);
                return;
            }

            try (InputStream in = response.body()) {
                BufferedImage image = ImageIO.read(in);
                if (image == null) {
                    AddonTemplate.LOG.warn("[yuio-qq] avatar not an image ({})", url);
                    return;
                }

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
            AddonTemplate.LOG.warn("[yuio-qq] avatar download failed: {} ({})", url, error.toString());
        }
    }
}
