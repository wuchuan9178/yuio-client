package com.example.addon.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChatHud.class)
public interface ChatHudInvoker {
    @Invoker("render")
    void yuio$renderBackend(ChatHud.Backend drawer, int windowHeight, int currentTick, boolean expanded);
}
