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
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.OrderedText;
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
            Collector collector = new Collector(mc.textRenderer.fontHeight);
            chat.render(collector, 0, 0, false);

            if (collector.lines.isEmpty()) return;

            double mouseX = mc.mouse.getX() / mc.getWindow().getScaleFactor();
            double mouseY = mc.mouse.getY() / mc.getWindow().getScaleFactor();

            for (Line line : collector.lines) {
                double left = Math.min(line.x, line.endX);
                double right = Math.max(line.x, line.endX);
                if (mouseY < line.y || mouseY > line.y + line.height) continue;
                if (mouseX < left - 2 || mouseX > right + 2) continue;
                if (line.text.isEmpty()) continue;

                mc.keyboard.setClipboard(line.text);
                if (showNotification.get()) info("%s%s", prefix.get(), line.text);
                return;
            }
        } catch (Exception e) {
            error("copy failed: %s", e.getMessage());
        }
    }

    private static final class Line {
        private final int x;
        private final int y;
        private final int endX;
        private final int height;
        private final String text;

        private Line(int x, int y, int endX, int height, String text) {
            this.x = x;
            this.y = y;
            this.endX = endX;
            this.height = height;
            this.text = text;
        }
    }

    private static final class Collector implements DrawnTextConsumer {
        private final List<Line> lines = new ArrayList<>();
        private final int height;
        private DrawnTextConsumer.Transformation transformation;

        private Collector(int height) {
            this.height = height;
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

            int endX = x + (int) Math.ceil(MeteorClientHolder.width(builder.toString()));
            lines.add(new Line(x, y, endX, height, builder.toString()));
        }

        @Override
        public void text(Alignment alignment, int x, int y, DrawnTextConsumer.Transformation transformation, net.minecraft.text.Text text) {
            text(alignment, x, y, transformation, net.minecraft.text.OrderedText.of(text.asOrderedText()));
        }

        @Override
        public void marqueedText(net.minecraft.text.Text text, int x, int y, int width, int height, int ticks, DrawnTextConsumer.Transformation transformation) {
            text(Alignment.LEFT, x, y, transformation, text.asOrderedText());
        }

        @SuppressWarnings("unused")
        private static final class MeteorClientHolder {
            private static double width(String text) {
                return meteordevelopment.meteorclient.MeteorClient.mc.textRenderer.getWidth(text) * 0.5;
            }
        }
    }
}
