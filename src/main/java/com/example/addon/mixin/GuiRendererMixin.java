package com.example.addon.mixin;

import com.example.addon.render.YuioGuiRenderer;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.renderer.Scissor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = GuiRenderer.class, remap = false)
public abstract class GuiRendererMixin implements YuioGuiRenderer {
    @Shadow
    public abstract void endRender(Scissor scissor);

    @Shadow
    public abstract void beginRender();

    @Override
    public void yuio$flushBatch() {
        endRender((Scissor) null);
        beginRender();
    }
}
