package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.font.Alignment;
import net.minecraft.client.font.DrawnTextConsumer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.OrderedText;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class AutoCopyChat extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> showNotification = sgGeneral.add(new BoolSetting.Builder()
        .name("show-notification")
        .description("Show a chat notification after copying text.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> prefix = sgGeneral.add(new StringSetting.Builder()
        .name("prefix")
        .description("Notification prefix shown after copying.")
        .defaultValue("copied: ")
        .visible(showNotification::get)
        .build()
    );

    private boolean wasPressed = false;

    public AutoCopyChat() {
        super(AddonTemplate.ASSIST, "auto-copy-chat",
            "Hover over a chat message and press Ctrl+C to copy it.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        boolean cDown = Input.isKeyPressed(GLFW.GLFW_KEY_C);
        boolean ctrlDown = Input.isKeyPressed(GLFW.GLFW_KEY_LEFT_CONTROL)
            || Input.isKeyPressed(GLFW.GLFW_KEY_RIGHT_CONTROL);

        boolean pressed = cDown && ctrlDown;
        if (pressed && !wasPressed) {
            wasPressed = true;
            tryCopy();
        } else if (!pressed) {
            wasPressed = false;
        }
    }

    private void tryCopy() {
        if (!(mc.currentScreen instanceof ChatScreen) || mc.player == null) return;

        try {
            ChatHud chat = mc.inGameHud.getChatHud();
            Collector collector = new Collector(mc.textRenderer);
            chat.render(collector, mc.getWindow().getScaledHeight(), mc.inGameHud.getTicks(), false);

            if (collector.lines.isEmpty()) return;

            double mouseX = mc.mouse.getX() / mc.getWindow().getScaleFactor();
            double mouseY = mc.mouse.getY() / mc.getWindow().getScaleFactor();

            for (Line line : collector.lines) {
                if (line.text.isEmpty()) continue;
                if (mouseY < line.y || mouseY > line.y + line.height) continue;
                if (mouseX < line.x - 2 || mouseX > line.endX + 2) continue;

                mc.keyboard.setClipboard(line.text);
                if (showNotification.get()) info("%s%s", prefix.get(), line.text);
                return;
            }
        } catch (Exception e) {
            error("copy failed: %s", e.getMessage());
        }
    }

    private static final class Line {
        private final double x;
        private final double y;
        private final double endX;
        private final double height;
        private final String text;

        private Line(double x, double y, double endX, double height, String text) {
            this.x = x;
            this.y = y;
            this.endX = endX;
            this.height = height;
            this.text = text;
        }
    }

    private static final class Collector implements DrawnTextConsumer {
        private static final DrawnTextConsumer.Transformation IDENTITY =
            new DrawnTextConsumer.Transformation(new Matrix3x2f());

        private final List<Line> lines = new ArrayList<>();
        private final TextRenderer textRenderer;
        private DrawnTextConsumer.Transformation transformation = IDENTITY;

        private Collector(TextRenderer textRenderer) {
            this.textRenderer = textRenderer;
        }

        @Override
        public DrawnTextConsumer.Transformation getTransformation() {
            return transformation;
        }

        @Override
        public void setTransformation(DrawnTextConsumer.Transformation transformation) {
            this.transformation = transformation;
        }

        @Override
        public void text(Alignment alignment, int x, int y, DrawnTextConsumer.Transformation transformation, OrderedText text) {
            StringBuilder builder = new StringBuilder();
            text.accept((index, style, codePoint) -> {
                builder.appendCodePoint(codePoint);
                return true;
            });

            DrawnTextConsumer.Transformation active = transformation != null ? transformation : this.transformation;
            Matrix3x2fc pose = active == null ? null : active.pose();

            double scale = pose == null ? 1.0 : Math.abs(pose.m11());
            double screenX = pose == null ? x : pose.m00() * x + pose.m10() * y + pose.m20();
            double screenY = pose == null ? y : pose.m01() * x + pose.m11() * y + pose.m21();
            double width = textRenderer.getWidth(text) * scale;
            double height = Math.max(1.0, textRenderer.fontHeight * scale);

            lines.add(new Line(screenX, screenY, screenX + width, height, builder.toString()));
        }

        @Override
        public void text(Alignment alignment, int x, int y, DrawnTextConsumer.Transformation transformation, net.minecraft.text.Text text) {
            text(alignment, x, y, transformation, text.asOrderedText());
        }

        @Override
        public void marqueedText(net.minecraft.text.Text text, int x, int y, int width, int height, int ticks, DrawnTextConsumer.Transformation transformation) {
            text(Alignment.LEFT, x, y, transformation, text.asOrderedText());
        }
    }
}
