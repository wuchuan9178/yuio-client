package com.example.addon.utils;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;


public final class BlurFbo implements AutoCloseable {
    private static final int USAGE = GpuTexture.USAGE_COPY_DST
        | GpuTexture.USAGE_COPY_SRC
        | GpuTexture.USAGE_TEXTURE_BINDING
        | GpuTexture.USAGE_RENDER_ATTACHMENT;

    private final String label;
    private GpuTexture texture;
    private GpuTextureView view;
    private int width;
    private int height;

    public BlurFbo(String label) {
        this.label = label;
    }

    public void resize(int newWidth, int newHeight) {
        if (newWidth <= 0 || newHeight <= 0) return;
        if (view != null && newWidth == width && newHeight == height) return;

        close();

        GpuDevice device = RenderSystem.getDevice();
        texture = device.createTexture(label, USAGE, TextureFormat.RGBA8, newWidth, newHeight, 1, 1);
        view = device.createTextureView(texture);
        width = newWidth;
        height = newHeight;
    }

    public GpuTextureView view() {
        return view;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean isReady() {
        return view != null && !view.isClosed();
    }

    @Override
    public void close() {
        if (view != null) {
            view.close();
            view = null;
        }
        if (texture != null) {
            texture.close();
            texture = null;
        }
        width = 0;
        height = 0;
    }
}
