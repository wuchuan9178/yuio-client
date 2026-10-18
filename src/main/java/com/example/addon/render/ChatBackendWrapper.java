package com.example.addon.render;

import com.example.addon.utils.BlurRenderer;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.text.OrderedText;
import org.joml.Matrix3x2f;

import java.util.function.Consumer;

public final class ChatBackendWrapper implements ChatHud.Backend {
    private final ChatHud.Backend delegate;

    public ChatBackendWrapper(ChatHud.Backend delegate) {
        this.delegate = delegate;
        BlurRenderer.request();
    }

    @Override
    public void updatePose(Consumer<Matrix3x2f> transformer) {
        delegate.updatePose(transformer);
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int color) {
        if (!ChatPanel.acceptFill(x1, y1, x2, y2, color)) {
            delegate.fill(x1, y1, x2, y2, color);
        }
    }

    @Override
    public boolean text(int y, float opacity, OrderedText text) {
        ChatPanel.flush();
        return delegate.text(y, opacity, text);
    }

    @Override
    public void indicator(int x1, int y1, int x2, int y2, float opacity, MessageIndicator indicator) {
        delegate.indicator(x1, y1, x2, y2, opacity, indicator);
    }

    @Override
    public void indicatorIcon(int left, int bottom, boolean forceDraw, MessageIndicator indicator, MessageIndicator.Icon icon) {
        delegate.indicatorIcon(left, bottom, forceDraw, indicator, icon);
    }
}
