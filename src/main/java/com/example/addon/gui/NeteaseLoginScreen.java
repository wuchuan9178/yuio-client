package com.example.addon.gui;

import com.example.addon.music.NeteaseLogin;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

// 扫码登录界面: 二维码 + 状态码 + cookie 兜底
public class NeteaseLoginScreen extends Screen {
    private static final int QR_SIZE = 220;
    private static final int QR_Y = 40;

    private BitMatrix matrix;
    private String cachedPayload = "";
    private TextFieldWidget cookieField;

    public NeteaseLoginScreen() {
        super(net.minecraft.client.MinecraftClient.getInstance(), net.minecraft.client.MinecraftClient.getInstance().textRenderer,
            Text.literal("NetEase Login"));
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int formY = QR_Y + QR_SIZE + 66;

        addDrawableChild(ButtonWidget.builder(Text.literal("Refresh"), button -> {
            NeteaseLogin.start();
            matrix = null;
            cachedPayload = "";
        }).dimensions(centerX - 162, height - 56, 96, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Logout"), button -> {
            NeteaseLogin.logout();
            matrix = null;
            cachedPayload = "";
            NeteaseLogin.start();
        }).dimensions(centerX - 48, height - 56, 96, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> close())
            .dimensions(centerX + 66, height - 56, 96, 20).build());

        cookieField = new TextFieldWidget(textRenderer, centerX - 210, formY, 420, 20, Text.literal("Cookie"));
        cookieField.setMaxLength(8192);
        cookieField.setPlaceholder(Text.literal("MUSIC_U=..."));
        addDrawableChild(cookieField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Cookie Login"), button ->
                NeteaseLogin.loginWithCookie(cookieField.getText()))
            .dimensions(centerX - 60, formY + 26, 120, 20).build());

        if (NeteaseLogin.status() == NeteaseLogin.Status.IDLE) {
            NeteaseLogin.start();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        int centerX = width / 2;
        int qrX = centerX - QR_SIZE / 2;
        int qrY = QR_Y;

        context.fill(qrX - 6, qrY - 6, qrX + QR_SIZE + 6, qrY + QR_SIZE + 6, 0xFFFFFFFF);

        BitMatrix current = matrix();
        if (current != null) {
            drawMatrix(context, current, qrX, qrY);
        }

        NeteaseLogin.Status status = NeteaseLogin.status();
        int color = status == NeteaseLogin.Status.SUCCESS ? 0xFF55FF55
            : status == NeteaseLogin.Status.ERROR || status == NeteaseLogin.Status.EXPIRED ? 0xFFFF5555
            : 0xFFFFFF55;

        String line = "code " + NeteaseLogin.code() + "  " + status.name().toLowerCase();
        context.drawTextWithShadow(textRenderer, Text.literal(line), centerX - textRenderer.getWidth(line) / 2, qrY + QR_SIZE + 10, color);

        String detail = NeteaseLogin.detail();
        if (!detail.isEmpty()) {
            context.drawTextWithShadow(textRenderer, Text.literal(detail), centerX - textRenderer.getWidth(detail) / 2, qrY + QR_SIZE + 24, 0xFFAAAAAA);
        }

        String hint = "f12 -> network -> any music.163.com request -> copy Cookie header";
        context.drawTextWithShadow(textRenderer, Text.literal(hint), centerX - textRenderer.getWidth(hint) / 2, qrY + QR_SIZE + 40, 0xFF888888);

        if (NeteaseLogin.loggedIn()) {
            String name = "logged in: " + (NeteaseLogin.nickname().isEmpty() ? "?" : NeteaseLogin.nickname());
            context.drawTextWithShadow(textRenderer, Text.literal(name), centerX - textRenderer.getWidth(name) / 2, qrY + QR_SIZE + 52, 0xFF55FF55);
        }

        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private BitMatrix matrix() {
        String payload = NeteaseLogin.payload();
        if (payload.isEmpty()) return null;

        if (!payload.equals(cachedPayload)) {
            try {
                matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 33, 33);
                cachedPayload = payload;
            } catch (Throwable ignored) {
                matrix = null;
            }
        }

        return matrix;
    }

    private void drawMatrix(DrawContext context, BitMatrix bitMatrix, int x, int y) {
        int size = bitMatrix.getWidth();
        float scale = QR_SIZE / (float) size;

        for (int row = 0; row < size; row++) {
            int run = -1;

            for (int col = 0; col <= size; col++) {
                boolean black = col < size && bitMatrix.get(col, row);

                if (black && run < 0) {
                    run = col;
                } else if (!black && run >= 0) {
                    int x1 = x + Math.round(run * scale);
                    int x2 = x + Math.round(col * scale);
                    int y1 = y + Math.round(row * scale);
                    int y2 = y + Math.round((row + 1) * scale);
                    context.fill(x1, y1, x2, y2, 0xFF000000);
                    run = -1;
                }
            }
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
