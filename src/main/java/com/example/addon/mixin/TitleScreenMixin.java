package com.example.addon.mixin;

import com.example.addon.gui.YuioButton;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    @Unique
    private static final Identifier BG = Identifier.of("yuio", "textures/title_background.png");

    //像素尺寸
    @Unique
    private static final int TEX_W = 1920;
    @Unique
    private static final int TEX_H = 1080;

    //放大系数
    @Unique
    private static final float SCALE = 2f;

    @Unique
    private static final float SENSITIVITY = 1.4f;
    @Unique
    private static final float SMOOTH = 0.35f;

    @Unique
    private static final Identifier ICON_SINGLE = Identifier.of("yuio", "textures/gui/singleplayer.png");
    @Unique
    private static final Identifier ICON_MULTI = Identifier.of("yuio", "textures/gui/multiplayer.png");
    @Unique
    private static final Identifier ICON_SETTINGS = Identifier.of("yuio", "textures/gui/settings.png");
    @Unique
    private static final Identifier ICON_QUIT = Identifier.of("yuio", "textures/gui/quit.png");

    @Unique
    private static float curX = 0f;
    @Unique
    private static float curY = 0f;

    @Unique
    private final List<YuioButton> yuio$btns = new ArrayList<>();

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void yuio$init(CallbackInfo ci) {
        clearChildren();
        yuio$btns.clear();

        int btnW = 90, btnH = 24, gap = 6;
        int total = btnW * 4 + gap * 3;
        int startX = (width - total) / 2;
        int y = height - 40;

        yuio$btns.add(new YuioButton(startX, y, btnW, btnH, Text.literal("单人游戏"), ICON_SINGLE,
            () -> client.setScreen(new SelectWorldScreen(this))));
        yuio$btns.add(new YuioButton(startX + (btnW + gap), y, btnW, btnH, Text.literal("多人游戏"), ICON_MULTI,
            () -> client.setScreen(new MultiplayerScreen(this))));
        yuio$btns.add(new YuioButton(startX + (btnW + gap) * 2, y, btnW, btnH, Text.literal("设置"), ICON_SETTINGS,
            () -> client.setScreen(new OptionsScreen(this, client.options))));
        yuio$btns.add(new YuioButton(startX + (btnW + gap) * 3, y, btnW, btnH, Text.literal("退出游戏"), ICON_QUIT,
            () -> client.scheduleStop()));
    }

    //干掉整个 ender
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void yuio$render(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        yuio$drawBg(ctx, mouseX, mouseY, delta);
        yuio$drawButtons(ctx, mouseX, mouseY, delta);
        ci.cancel();
    }

    @Unique
    private void yuio$drawBg(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        int drawW = (int)(sw * SCALE);
        int drawH = (int)(sh * SCALE);

        float mx = Math.max(0f, Math.min(1f, (float) mouseX / sw));
        float my = Math.max(0f, Math.min(1f, (float) mouseY / sh));
        float dcx = (mx - 0.5f) * 2f;
        float dcy = (my - 0.5f) * 2f;

        float maxX = (drawW - sw) / 2f;
        float maxY = (drawH - sh) / 2f;
        float tx = Math.max(-maxX, Math.min(maxX, maxX * dcx * SENSITIVITY));
        float ty = Math.max(-maxY, Math.min(maxY, maxY * dcy * SENSITIVITY));

        float t = 1f - (float) Math.pow(1f - SMOOTH, delta * 20f);
        curX += (tx - curX) * t;
        curY += (ty - curY) * t;

        int bx = -(int) curX - (drawW - sw) / 2;
        int by = -(int) curY - (drawH - sh) / 2;

        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, BG,
            bx, by, 0f, 0f, drawW, drawH, TEX_W, TEX_H, 0xFFFFFFFF);
    }

    @Unique
    private void yuio$drawButtons(DrawContext ctx, int mouseX, int mouseY, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        for (YuioButton b : yuio$btns) {
            boolean hovered = mouseX >= b.x && mouseX < b.x + b.w && mouseY >= b.y && mouseY < b.y + b.h;
            b.hover += ((hovered ? 1f : 0f) - b.hover) * Math.min(1f, delta * 15f);

            int bgA = (int)(0xB0 + 0x40 * b.hover) & 0xFF;
            ctx.fill(b.x, b.y, b.x + b.w, b.y + b.h, (bgA << 24) | 0x1E1E1E);

            int hiA = (int)(0x20 + 0x40 * b.hover) & 0xFF;
            ctx.fill(b.x + 1, b.y + 1, b.x + b.w - 1, b.y + 3, (hiA << 24) | 0xFFFFFF);

            int bA = (int)(0x60 + 0x9F * b.hover) & 0xFF;
            int border = (bA << 24) | 0xFFFFFF;
            ctx.fill(b.x, b.y, b.x + b.w, b.y + 1, border);
            ctx.fill(b.x, b.y + b.h - 1, b.x + b.w, b.y + b.h, border);
            ctx.fill(b.x, b.y, b.x + 1, b.y + b.h, border);
            ctx.fill(b.x + b.w - 1, b.y, b.x + b.w, b.y + b.h, border);

            int iconSize = 16;
            int iconX = b.x + 8;
            int iconY = b.y + (b.h - iconSize) / 2;
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, b.icon,
                iconX, iconY, 0f, 0f, iconSize, iconSize, iconSize, iconSize);

            int textColor = 0xFF000000 | (hovered ? 0xFFFFFF : 0xE0E0E0);
            int textX = iconX + iconSize + 6;
            int textY = b.y + (b.h - 8) / 2;
            ctx.drawTextWithShadow(mc.textRenderer, b.text, textX, textY, textColor);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void yuio$click(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        double mouseX = click.x();
        double mouseY = click.y();
        for (YuioButton b : yuio$btns) {
            if (mouseX >= b.x && mouseX < b.x + b.w && mouseY >= b.y && mouseY < b.y + b.h) {
                b.action.run();
                cir.setReturnValue(true);
                return;
            }
        }
    }
}
