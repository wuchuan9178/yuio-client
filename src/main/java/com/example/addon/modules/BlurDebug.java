package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.utils.BlurRenderer;
import com.example.addon.utils.RoundRectRenderer;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 临时调试模块: 背景模糊。
 *
 * <p>用法: 打开之后屏幕上会铺满"模糊链抓到的画面", 左上角给出抓屏状态。
 * <ul>
 *   <li>预览是清晰的游戏画面 = 抓屏时机正确, 模糊链没跑或强度为 0。</li>
 *   <li>预览里有自己的面板/原版血条 = 抓屏时机不对(挂了别的注入点)。</li>
 *   <li>预览上下颠倒 = 打开 flip-v。</li>
 *   <li>captures 一直不涨 = 抓屏根本没被调用, 看 frames 是否在涨(不涨说明 HUD mixin 没生效)。</li>
 *   <li>ERROR[...] = 抓屏过程抛异常, 具体消息就在后面。</li>
 * </ul>
 * 验证完就删掉这个模块。
 */
public class BlurDebug extends Module {
    private final SettingGroup sgCapture = settings.createGroup("Capture");
    private final SettingGroup sgGlass = settings.createGroup("Glass");
    private final SettingGroup sgOverlay = settings.createGroup("Overlay");

    private final Setting<Boolean> disable = sgCapture.add(new BoolSetting.Builder()
        .name("disable-blur")
        .description("Skip the blur entirely, every glass panel falls back to a flat tint.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> freeze = sgCapture.add(new BoolSetting.Builder()
        .name("freeze")
        .description("Keep refreshing nothing: the backdrop stays at the last captured frame.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> forceFallback = sgCapture.add(new BoolSetting.Builder()
        .name("force-fallback")
        .description("Pretend the blur is unavailable, so panels draw the old flat tint.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> flushGui = sgCapture.add(new BoolSetting.Builder()
        .name("flush-gui-state")
        .description("Flush Meteor's pending GUI state before capturing.")
        .defaultValue(false)
        .build());

    private final Setting<Integer> strength = sgCapture.add(new IntSetting.Builder()
        .name("strength-override")
        .description("-1 keeps whatever each panel asks for.")
        .defaultValue(-1)
        .min(-1)
        .max(30)
        .sliderRange(-1, 30)
        .build());

    private final Setting<Integer> opacity = sgGlass.add(new IntSetting.Builder()
        .name("opacity-override")
        .description("Panel opacity, -1 keeps the panel setting.")
        .defaultValue(-1)
        .min(-1)
        .max(255)
        .sliderRange(-1, 255)
        .build());

    private final Setting<Integer> tint = sgGlass.add(new IntSetting.Builder()
        .name("tint-override")
        .description("Glass tint strength, -1 keeps the panel setting.")
        .defaultValue(-1)
        .min(-1)
        .max(255)
        .sliderRange(-1, 255)
        .build());

    private final Setting<Boolean> flipV = sgGlass.add(new BoolSetting.Builder()
        .name("flip-v")
        .description("Flip the vertical texture coordinate, in case the backdrop is upside down.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> nearest = sgGlass.add(new BoolSetting.Builder()
        .name("nearest-sampling")
        .description("Nearest neighbour sampling, which makes the texel grid visible.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> preview = sgOverlay.add(new BoolSetting.Builder()
        .name("preview")
        .description("Draw the captured backdrop across the whole screen.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> previewOpacity = sgOverlay.add(new IntSetting.Builder()
        .name("preview-opacity")
        .description("How opaque the fullscreen preview is.")
        .defaultValue(210)
        .min(0)
        .max(255)
        .sliderRange(0, 255)
        .build());

    private final Setting<Boolean> info = sgOverlay.add(new BoolSetting.Builder()
        .name("info")
        .description("Draw the capture state as text in the top left corner.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> logStatus = sgOverlay.add(new BoolSetting.Builder()
        .name("log-status")
        .description("Print the capture state into chat once, then reset this setting.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> resetStats = sgOverlay.add(new BoolSetting.Builder()
        .name("reset-stats")
        .description("Zero the frame and capture counters once, then reset this setting.")
        .defaultValue(false)
        .build());

    private final List<String> lines = new ArrayList<>();

    public BlurDebug() {
        super(AddonTemplate.OTHER, "blur-debug", "Temporary background blur debugger.");
    }

    @Override
    public void onActivate() {
        apply();
        BlurRenderer.resetStats();
    }

    @Override
    public void onDeactivate() {
        BlurRenderer.debugDisable = false;
        BlurRenderer.debugFreeze = false;
        BlurRenderer.debugForceFallback = false;
        BlurRenderer.debugFlipV = false;
        BlurRenderer.debugFlushGuiState = false;
        BlurRenderer.debugNearest = false;
        BlurRenderer.debugStrength = -1;
        BlurRenderer.debugOpacity = -1;
        BlurRenderer.debugTint = -1;
    }

    private void apply() {
        BlurRenderer.debugDisable = disable.get();
        BlurRenderer.debugFreeze = freeze.get();
        BlurRenderer.debugForceFallback = forceFallback.get();
        BlurRenderer.debugFlipV = flipV.get();
        BlurRenderer.debugFlushGuiState = flushGui.get();
        BlurRenderer.debugNearest = nearest.get();
        BlurRenderer.debugStrength = strength.get();
        BlurRenderer.debugOpacity = opacity.get();
        BlurRenderer.debugTint = tint.get();
    }

    private int requestedStrength() {
        return strength.get() >= 0 ? strength.get() : 12;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        apply();

        if (resetStats.get()) {
            resetStats.set(false);
            BlurRenderer.resetStats();
            info("blur stats reset");
        }

        if (logStatus.get()) {
            logStatus.set(false);
            info("%s", BlurRenderer.status());
        }
    }

    // 2000 = 排在 Meteor 的 Hud.onRender(默认 1000) 之后, 画在所有 HUD 之上,
    // 而且此时 HudRenderer.begin/end 的窗口已经关掉, 自己开 MeshRenderer 不会撞车。
    @EventHandler(priority = 2000)
    private void onRender2D(Render2DEvent event) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        BlurRenderer.request(requestedStrength());
        RoundRectRenderer.beginFrame();

        if (preview.get()) {
            BlurRenderer.drawPreview(previewOpacity.get());
        }

        if (info.get()) {
            drawInfo(mc);
        }
    }

    private void drawInfo(MinecraftClient mc) {
        lines.clear();
        lines.add("yuio blur debug");
        lines.add(BlurRenderer.status());
        lines.add(String.format("framebuffer %dx%d, gui scale %d",
            mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight(), mc.getWindow().getScaleFactor()));
        lines.add(String.format("overrides strength=%d opacity=%d tint=%d", strength.get(), opacity.get(), tint.get()));
        lines.add(String.format("flags%s%s%s%s",
            disable.get() ? " disable" : "",
            freeze.get() ? " freeze" : "",
            forceFallback.get() ? " fallback" : "",
            flipV.get() ? " flip-v" : ""));

        TextRenderer text = TextRenderer.get();
        boolean own = !text.isBuilding();
        if (own) text.begin(1.0, false, false);

        double y = 6.0;
        for (String line : lines) {
            text.render(line, 6.0, y, Color.WHITE, true);
            y += text.getHeight(true) + 2.0;
        }

        if (own) text.end();
    }
}
