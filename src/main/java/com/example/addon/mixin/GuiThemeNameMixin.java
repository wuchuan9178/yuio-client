package com.example.addon.mixin;

import com.example.addon.render.YuioThemeName;
import meteordevelopment.meteorclient.gui.GuiTheme;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = GuiTheme.class, remap = false)
public abstract class GuiThemeNameMixin implements YuioThemeName {
    @Shadow
    @Final
    @Mutable
    public String name;

    @Override
    public void yuio$setName(String name) {
        this.name = name;
    }
}
