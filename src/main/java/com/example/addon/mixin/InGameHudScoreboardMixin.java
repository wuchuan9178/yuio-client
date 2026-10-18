package com.example.addon.mixin;

import com.example.addon.modules.UiBlur;
import com.example.addon.render.ScoreboardPanel;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(InGameHud.class)
public class InGameHudScoreboardMixin {
    @Redirect(
        method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V", ordinal = 0))
    private void yuio$headerPanel(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        UiBlur module = UiBlur.active();
        if (module == null || !module.scoreboard.get()) {
            context.fill(x1, y1, x2, y2, color);
            return;
        }

        ScoreboardPanel.header(y1);
    }

    @Redirect(
        method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V", ordinal = 1))
    private void yuio$bodyPanel(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        UiBlur module = UiBlur.active();
        if (module == null || !module.scoreboard.get()) {
            context.fill(x1, y1, x2, y2, color);
            return;
        }

        ScoreboardPanel.body(module, x1, y1, x2, y2);
    }
}
