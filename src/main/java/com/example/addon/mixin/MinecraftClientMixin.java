package com.example.addon.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    private static boolean yuio$iconSet = false;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void yuio$init(CallbackInfo ci) {
        yuio$setTitle();
        yuio$setIcon();
    }

    //强制设置标题
    @Inject(method = "tick", at = @At("HEAD"))
    private void yuio$tick(CallbackInfo ci) {
        yuio$setTitle();
    }

    private void yuio$setTitle() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        Window window = mc.getWindow();
        if (window == null) return;
        window.setTitle("千恋万花");
    }

    private void yuio$setIcon() {
        if (yuio$iconSet) return;
        yuio$iconSet = true;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            Window window = mc.getWindow();

            Field handleField = Window.class.getDeclaredField("handle");
            handleField.setAccessible(true);
            long handle = handleField.getLong(window);

            try (InputStream is = MinecraftClientMixin.class.getResourceAsStream("/assets/yuio/icon.png")) {
                if (is == null) {
                    System.err.println("[yuio] icon.png not found");
                    return;
                }
                byte[] bytes = is.readAllBytes();
                ByteBuffer buf = ByteBuffer.allocateDirect(bytes.length);
                buf.put(bytes).flip();

                try (MemoryStack stack = MemoryStack.stackPush()) {
                    IntBuffer w = stack.mallocInt(1);
                    IntBuffer h = stack.mallocInt(1);
                    IntBuffer ch = stack.mallocInt(1);

                    ByteBuffer pixels = STBImage.stbi_load_from_memory(buf, w, h, ch, 4);
                    if (pixels == null) return;

                    GLFWImage.Buffer images = GLFWImage.malloc(1);
                    images.position(0);
                    images.width(w.get(0));
                    images.height(h.get(0));
                    images.pixels(pixels);

                    GLFW.glfwSetWindowIcon(handle, images);

                    STBImage.stbi_image_free(pixels);
                    images.free();
                    System.out.println("[yuio] icon set");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
