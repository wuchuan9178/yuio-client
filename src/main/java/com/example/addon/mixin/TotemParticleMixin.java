package com.example.addon.mixin;

import com.example.addon.modules.TotemParticle;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

@Mixin(ParticleManager.class)
public class TotemParticleMixin {

    // 字段在父类里，逐层往上找
    private static Field findField(Class<?> c, String name) {
        while (c != null) {
            try { return c.getDeclaredField(name); }
            catch (NoSuchFieldException e) { c = c.getSuperclass(); }
        }
        return null;
    }

    @ModifyVariable(
        method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
        at = @At("RETURN")
    )
    private Particle modifyTotemColor(Particle particle) {
        if (!(particle instanceof net.minecraft.client.particle.TotemParticle)) return particle;

        TotemParticle mod = Modules.get().get(TotemParticle.class);
        if (mod == null || !mod.isActive()) return particle;
        if (mod.mode.get() != TotemParticle.Mode.Color) return particle;

        var c = mod.particleColor.get();
        try {
            Field rf = findField(particle.getClass(), "red");
            Field gf = findField(particle.getClass(), "green");
            Field bf = findField(particle.getClass(), "blue");
            if (rf == null || gf == null || bf == null) return particle;
            rf.setAccessible(true);
            gf.setAccessible(true);
            bf.setAccessible(true);
            rf.setFloat(particle, c.r / 255f);
            gf.setFloat(particle, c.g / 255f);
            bf.setFloat(particle, c.b / 255f);
        } catch (Exception ignored) {}
        return particle;
    }

    @Inject(
        method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onAddParticle(ParticleEffect parameters, double x, double y, double z,
                               double vx, double vy, double vz,
                               CallbackInfoReturnable<Particle> info) {
        if (parameters.getType() != ParticleTypes.TOTEM_OF_UNDYING) return;

        TotemParticle mod = Modules.get().get(TotemParticle.class);
        if (mod == null || !mod.isActive()) return;
        if (mod.mode.get() != TotemParticle.Mode.Text) return;

        info.cancel();
        TotemParticle.addSpot(x, y, z);
    }
}
