package com.example.addon.mixin;

import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PlayerListEntry.class)
public interface PlayerListEntryAccessor {
    @Invoker("setGameMode")
    void yuio$setGameMode(GameMode mode);
}
