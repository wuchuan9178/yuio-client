package com.example.addon.utils;

import com.example.addon.AddonTemplate;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class YuioSounds {
    public static final Identifier ALCOHOL_ID = Identifier.of("yuio", "alcohol");
    public static final SoundEvent ALCOHOL = SoundEvent.of(ALCOHOL_ID);

    private YuioSounds() {
    }

    public static void register() {
        try {
            Registry.register(Registries.SOUND_EVENT, ALCOHOL_ID, ALCOHOL);
        } catch (Throwable t) {
            // 注册表在建 addon 前已冻结，注册不上不影响直接播 SoundEvent 对象
            AddonTemplate.LOG.debug("Custom sound not registered: {}", t.getMessage());
        }
    }
}
