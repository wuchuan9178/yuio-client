package com.example.addon.mixin;

import com.example.addon.events.KeyboardInputEvent;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void yuio$keyboardTick(CallbackInfo ci) {
        Input self = (Input) (Object) this;
        PlayerInput input = self.playerInput;
        if (input == null) return;

        float forward = (input.forward() ? 1.0f : 0.0f) - (input.backward() ? 1.0f : 0.0f);
        float strafe = (input.left() ? 1.0f : 0.0f) - (input.right() ? 1.0f : 0.0f);

        KeyboardInputEvent event = MeteorClient.EVENT_BUS.post(new KeyboardInputEvent(
                forward,
                strafe,
                input.jump(),
                input.sneak(),
                input.sprint()
        ));

        self.playerInput = event.toNewInput();
    }
}
