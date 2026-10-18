package com.example.addon.mixin;

import com.example.addon.gui.YuioButton;
import com.example.addon.render.GuiIcons;
import com.example.addon.render.LoadingSound;
import com.example.addon.render.YuioRender;
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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    @Unique
    private static final Identifier BG = Identifier.of("yuio", "textures/title_background.png");

    @Unique
    private static final float SENSITIVITY = 1.2f;
    @Unique
    private static final float DISTANT = 0.22f;
    @Unique
    private static final float SMOOTH = 0.10f;

    @Unique
    private static final Identifier ICON_SINGLE = Identifier.of("yuio", "textures/gui/singleplayer.png");
    @Unique
    private static final Identifier ICON_MULTI = Identifier.of("yuio", "textures/gui/multiplayer.png");
    @Unique
    private static final Identifier ICON_SETTINGS = Identifier.of("yuio", "textures/gui/settings.png");
    @Unique
    private static final Identifier ICON_QUIT = Identifier.of("yuio", "textures/gui/quit.png");

    @Unique
    private static int bgTexW = -1;
    @Unique
    private static int bgTexH = -1;
    @Unique
    private static int bgDrawX, bgDrawY, bgDrawW, bgDrawH;

    @Unique
    private static float curX, curY, lagX, lagY, driftT;

    @Unique
    private static boolean yuio$soundPlayed = false;

    // 原版/yuio 两套标题界面来回切
    @Unique
    private static boolean yuio$vanillaMode = false;

    @Unique
    private YuioButton yuio$returnBtn;

    @Unique
    private final List<YuioButton> yuio$btns = new ArrayList<>();

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void yuio$init(CallbackInfo ci) {
        if (yuio$vanillaMode) {
            yuio$returnBtn = new YuioButton(width - 14 - 96, height - 14 - 26, 96, 26, Text.literal("返回 yuio"),
                GuiIcons.id(GuiIcons.BACK), () -> yuio$switchMode(false));
            return;
        }

        clearChildren();
        yuio$btns.clear();

        if (!yuio$soundPlayed) {
            yuio$soundPlayed = true;
            LoadingSound.play();
        }

        yuio$returnBtn = new YuioButton(width - 14 - 96, height - 14 - 26, 96, 26, Text.literal("返回原版"),
            GuiIcons.id(GuiIcons.BACK), () -> yuio$switchMode(true));

        int btnW = 96, btnH = 26, gap = 8;
        int total = btnW * 4 + gap * 3;
        int startX = (width - total) / 2;
        int y = height - 46;

        yuio$btns.add(new YuioButton(startX, y, btnW, btnH, Text.literal("单人游戏"), ICON_SINGLE,
            () -> client.setScreen(new SelectWorldScreen(this))));
        yuio$btns.add(new YuioButton(startX + (btnW + gap), y, btnW, btnH, Text.literal("多人游戏"), ICON_MULTI,
            () -> client.setScreen(new MultiplayerScreen(this))));
        yuio$btns.add(new YuioButton(startX + (btnW + gap) * 2, y, btnW, btnH, Text.literal("设置"), ICON_SETTINGS,
            () -> client.setScreen(new OptionsScreen(this, client.options))));
        yuio$btns.add(new YuioButton(startX + (btnW + gap) * 3, y, btnW, btnH, Text.literal("退出游戏"), ICON_QUIT,
            () -> client.scheduleStop()));
    }

    @Unique
    private void yuio$switchMode(boolean vanilla) {
        yuio$vanillaMode = vanilla;
        yuio$returnBtn = null;
        clearAndInit();
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void yuio$render(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (yuio$vanillaMode) return;

        YuioRender render = new YuioRender(ctx);

        yuio$drawBg(ctx, mouseX, mouseY, delta);
        yuio$drawButtons(ctx, render, mouseX, mouseY, delta);
        if (yuio$returnBtn != null) {
            yuio$drawButton(ctx, render, yuio$returnBtn, mouseX, mouseY, delta, yuio$mouseDown());
        }
        ci.cancel();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void yuio$renderVanilla(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!yuio$vanillaMode || yuio$returnBtn == null) return;

        yuio$drawButton(ctx, new YuioRender(ctx), yuio$returnBtn, mouseX, mouseY, delta, yuio$mouseDown());
    }

    @Unique
    private void yuio$drawBg(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (bgTexW <= 0) yuio$readPngSize();

        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();

        double cover = Math.max((double) sw / bgTexW, (double) sh / bgTexH) * 1.12;
        bgDrawW = (int) Math.ceil(bgTexW * cover);
        bgDrawH = (int) Math.ceil(bgTexH * cover);

        float mx = yuio$clamp((mouseX / (float) sw - 0.5f) * 2f);
        float my = yuio$clamp((mouseY / (float) sh - 0.5f) * 2f);
        float maxX = (bgDrawW - sw) / 2f;
        float maxY = (bgDrawH - sh) / 2f;

        driftT += delta * 0.5f;
        float driftX = (float) Math.sin(driftT * 0.31f) * 0.30f + (float) Math.sin(driftT * 0.13f) * 0.18f;
        float driftY = (float) Math.cos(driftT * 0.23f) * 0.24f + (float) Math.cos(driftT * 0.09f) * 0.14f;

        float targetX = yuio$clamp(mx * SENSITIVITY + driftX) * maxX;
        float targetY = yuio$clamp(my * SENSITIVITY + driftY) * maxY;

        lagX += (targetX - lagX) * Math.min(1f, delta * DISTANT);
        lagY += (targetY - lagY) * Math.min(1f, delta * DISTANT);
        float t = 1f - (float) Math.pow(1f - SMOOTH, delta * 20f);
        curX += (lagX - curX) * t;
        curY += (lagY - curY) * t;

        bgDrawX = -(int) curX - (bgDrawW - sw) / 2;
        bgDrawY = -(int) curY - (bgDrawH - sh) / 2;

        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, BG,
            bgDrawX, bgDrawY, 0f, 0f, bgDrawW, bgDrawH, bgTexW, bgTexH, bgTexW, bgTexH, 0xFFFFFFFF);
    }

    @Unique
    private static boolean yuio$mouseDown() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.getWindow().getHandle(),
            org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    @Unique
    private void yuio$drawButtons(DrawContext ctx, YuioRender render, int mouseX, int mouseY, float delta) {
        boolean mouseDown = yuio$mouseDown();

        for (YuioButton button : yuio$btns) {
            yuio$drawButton(ctx, render, button, mouseX, mouseY, delta, mouseDown);
        }
    }

    @Unique
    private void yuio$drawButton(DrawContext ctx, YuioRender render, YuioButton button,
                                 int mouseX, int mouseY, float delta, boolean mouseDown) {
        MinecraftClient mc = MinecraftClient.getInstance();

        boolean hovered = mouseX >= button.x && mouseX < button.x + button.w
            && mouseY >= button.y && mouseY < button.y + button.h;

        button.update(delta, hovered, mouseDown && hovered);

        double hover = button.hovered();
        double press = button.pressed();

        double x = button.x;
        double y = button.y;
        double w = button.w;
        double h = button.h;

        int shade = yuio$alpha(yuio$lerp(255, 208, hover) - press * 24);
        render.rect(x, y, w, h, 0xFF000000 | (shade << 16) | (shade << 8) | shade);
        render.rect(x, y, w, 1, 0xFF6E6E6E);
        render.rect(x, y + h - 1, w, 1, 0xFF6E6E6E);
        render.rect(x, y, 1, h, 0xFF6E6E6E);
        render.rect(x + w - 1, y, 1, h, 0xFF6E6E6E);

        if (yuio$iconW <= 0) return;

        int iconSize = 16;
        int iconX = (int) x + 9;
        int iconY = (int) y + (int) ((h - iconSize) / 2);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, button.icon,
            iconX, iconY, 0f, 0f, iconSize, iconSize, yuio$iconW, yuio$iconH, yuio$iconW, yuio$iconH,
            0xFF404040);

        int textX = iconX + iconSize + 7;
        int textY = (int) (y + (h - 8) / 2);
        ctx.drawText(mc.textRenderer, button.text, textX, textY, 0xFF101010, false);
    }


    @Unique
    private static int yuio$iconW = -1, yuio$iconH = -1;

    @Unique
    private static void yuio$readPngSize() {
        int[] background = yuio$pngSize(BG);
        bgTexW = background[0];
        bgTexH = background[1];

        int[] icon = yuio$pngSize(ICON_SINGLE);
        yuio$iconW = icon[0];
        yuio$iconH = icon[1];
    }

    @Unique
    private static int[] yuio$pngSize(Identifier id) {
        try (InputStream in = MinecraftClient.getInstance().getResourceManager()
            .getResource(id).orElseThrow().getInputStream()) {

            byte[] header = in.readNBytes(24);
            if (header.length < 24) throw new IllegalStateException("short png");

            int width = ((header[16] & 0xFF) << 24) | ((header[17] & 0xFF) << 16)
                | ((header[18] & 0xFF) << 8) | (header[19] & 0xFF);
            int height = ((header[20] & 0xFF) << 24) | ((header[21] & 0xFF) << 16)
                | ((header[22] & 0xFF) << 8) | (header[23] & 0xFF);

            if (width <= 0 || height <= 0) throw new IllegalStateException("bad png");
            return new int[]{width, height};
        } catch (Throwable ignored) {
            return new int[]{1920, 1080};
        }
    }

    @Unique
    private static float yuio$clamp(float value) {
        return Math.max(-1f, Math.min(1f, value));
    }

    @Unique
    private static int yuio$alpha(double value) {
        return (int) Math.max(0, Math.min(255, value));
    }

    @Unique
    private static int yuio$lerp(int from, int to, double t) {
        return (int) (from + (to - from) * Math.max(0, Math.min(1, t)));
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void yuio$click(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        double mouseX = click.x();
        double mouseY = click.y();

        if (yuio$returnBtn != null && mouseX >= yuio$returnBtn.x && mouseX < yuio$returnBtn.x + yuio$returnBtn.w
            && mouseY >= yuio$returnBtn.y && mouseY < yuio$returnBtn.y + yuio$returnBtn.h) {
            yuio$returnBtn.action.run();
            cir.setReturnValue(true);
            return;
        }

        for (YuioButton button : yuio$btns) {
            if (mouseX >= button.x && mouseX < button.x + button.w
                && mouseY >= button.y && mouseY < button.y + button.h) {
                button.action.run();
                cir.setReturnValue(true);
                return;
            }
        }
    }
}

