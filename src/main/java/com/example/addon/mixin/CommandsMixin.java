package com.example.addon.mixin;

import com.example.addon.utils.ForcingFriendCommand;
import meteordevelopment.meteorclient.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Commands.class, remap = false)
public abstract class CommandsMixin {
    @Inject(method = "dispatch", at = @At("HEAD"), cancellable = true)
    private static void yuio$dispatch(String message, CallbackInfo ci) {
        if (ForcingFriendCommand.handle(message)) ci.cancel();
    }
}
