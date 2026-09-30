package com.example.addon.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Unique
    private static final String[] ICON_SIZES = {"16", "32", "48", "64", "128", "256"};

    @Unique
    private static final int REAPPLY_TICKS = 1200;

    @Unique
    private static boolean yuio$iconSet = false;
    @Unique
    private static int yuio$ticks = 0;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void yuio$init(CallbackInfo ci) {
        yuio$setTitle();
        yuio$setIcon();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void yuio$tick(CallbackInfo ci) {
        yuio$setTitle();

        if (yuio$ticks < REAPPLY_TICKS) {
            yuio$ticks++;
            if (yuio$iconSet && yuio$ticks % 40 == 0) yuio$setIcon();
        }
    }

    @Unique
    private void yuio$setTitle() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        Window window = mc.getWindow();
        if (window == null) return;

        window.setTitle("千恋万花");
    }

    @Unique
    private void yuio$setIcon() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            Window window = mc.getWindow();
            if (window == null) return;

            Field handleField = Window.class.getDeclaredField("handle");
            handleField.setAccessible(true);
            long handle = handleField.getLong(window);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                GLFWImage.Buffer images = GLFWImage.malloc(ICON_SIZES.length, stack);
                ByteBuffer[] pixels = new ByteBuffer[ICON_SIZES.length];
                int count = 0;

                for (int i = 0; i < ICON_SIZES.length; i++) {
                    ByteBuffer loaded = yuio$loadIcon("/assets/yuio/icon/icon_" + ICON_SIZES[i] + ".png", stack);
                    if (loaded == null) continue;

                    pixels[count] = loaded;
                    images.position(count);
                    IntBuffer size = stack.mallocInt(2);
                    size.put(0, Integer.parseInt(ICON_SIZES[i]));
                    size.put(1, Integer.parseInt(ICON_SIZES[i]));
                    images.width(size.get(0));
                    images.height(size.get(1));
                    images.pixels(loaded);
                    count++;
                }

                if (count == 0) return;

                images.position(0);
                images.limit(count);
                GLFW.glfwSetWindowIcon(handle, images);

                for (ByteBuffer buffer : pixels) {
                    if (buffer != null) STBImage.stbi_image_free(buffer);
                }
            }

            if (!yuio$iconSet) {
                yuio$iconSet = true;
                System.out.println("[yuio] window icons set");
            }
        } catch (Throwable error) {
            System.err.println("[yuio] failed to set the window icon: " + error);
        }
    }

    @Unique
    private static ByteBuffer yuio$loadIcon(String path, MemoryStack stack) {
        try (InputStream in = MinecraftClientMixin.class.getResourceAsStream(path)) {
            if (in == null) return null;

            byte[] bytes = in.readAllBytes();
            ByteBuffer raw = stack.malloc(bytes.length);
            raw.put(bytes).flip();

            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);

            return STBImage.stbi_load_from_memory(raw, width, height, channels, 4);
        } catch (Throwable ignored) {
            return null;
        }
    }
}

