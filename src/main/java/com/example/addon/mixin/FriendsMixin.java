package com.example.addon.mixin;

import com.example.addon.utils.ForcedFriends;
import meteordevelopment.meteorclient.systems.friends.Friend;
import meteordevelopment.meteorclient.systems.friends.Friends;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Friends.class, remap = false)
public abstract class FriendsMixin {
    @Inject(method = "isFriend(Lnet/minecraft/class_1657;)Z", at = @At("HEAD"), cancellable = true)
    private void yuio$isFriend(PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
        if (player != null && ForcedFriends.isForced(player.getName().getString())) cir.setReturnValue(true);
    }

    @Inject(method = "shouldAttack(Lnet/minecraft/class_1657;)Z", at = @At("HEAD"), cancellable = true)
    private void yuio$shouldAttack(PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
        if (player != null && ForcedFriends.isForced(player.getName().getString())) cir.setReturnValue(false);
    }

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void yuio$remove(Friend friend, CallbackInfoReturnable<Boolean> cir) {
        if (friend != null && ForcedFriends.isForced(friend.name)) cir.setReturnValue(false);
    }
}
