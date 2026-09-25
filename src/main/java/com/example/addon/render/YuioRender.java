package com.example.addon.render;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

import static meteordevelopment.meteorclient.MeteorClient.mc;

// 自研 UI 渲染器:只用 Minecraft 官方 API,不依赖任何客户端渲染库
public class YuioRender {
    private final DrawContext ctx;
    private final double sf;

    public YuioRender(DrawContext ctx) {
        this.ctx = ctx;
        this.sf = mc.getWindow().getScaleFactor();
    }

    public DrawContext ctx() { return ctx; }

    // ---------- 矩形 / 渐变 ----------

    public void rect(double x, double y, double w, double h, int argb) {
        ctx.fill((int) x, (int) y, (int) (x + w), (int) (y + h), argb);
    }

    public void gradient(double x, double y, double w, double h, int topArgb, int bottomArgb) {
        ctx.fillGradient((int) x, (int) y, (int) (x + w), (int) (y + h), topArgb, bottomArgb);
    }

    public void lineH(double x, double y, double w, int argb) {
        ctx.fill((int) x, (int) y, (int) (x + w), (int) (y + 1), argb);
    }

    public void lineV(double x, double y, double h, int argb) {
        ctx.fill((int) x, (int) y, (int) (x + 1), (int) (y + h), argb);
    }

    // ---------- 圆角矩形(软件扫描线光栅化) ----------

    public void roundRect(double x, double y, double w, double h, double rad, int argb) {
        int r = (int) Math.min(rad, Math.min(w, h) / 2);
        if (r <= 0) { rect(x, y, w, h, argb); return; }
        // 主体
        ctx.fill((int) (x + r), (int) y, (int) (x + w - r), (int) (y + h), argb);
        // 左右条
        ctx.fill((int) x, (int) (y + r), (int) (x + w), (int) (y + h - r), argb);
        // 四角逐行扫描线:每行按圆弧偏移
        for (int dy = 0; dy < r; dy++) {
            int dx = (int) (r - Math.sqrt(r * r - dy * dy));
            // 左上
            ctx.fill((int) (x + dx), (int) (y + dy), (int) (x + r), (int) (y + dy + 1), argb);
            // 右上
            ctx.fill((int) (x + w - r), (int) (y + dy), (int) (x + w - dx), (int) (y + dy + 1), argb);
            // 左下
            ctx.fill((int) (x + dx), (int) (y + h - dy - 1), (int) (x + r), (int) (y + h - dy), argb);
            // 右下
            ctx.fill((int) (x + w - r), (int) (y + h - dy - 1), (int) (x + w - dx), (int) (y + h - dy), argb);
        }
    }

    // 垂直渐变圆角(角取底部色)
    public void roundGradient(double x, double y, double w, double h, double rad, int topArgb, int bottomArgb) {
        int r = (int) Math.min(rad, Math.min(w, h) / 2);
        if (r <= 0) { gradient(x, y, w, h, topArgb, bottomArgb); return; }
        ctx.fillGradient((int) (x + r), (int) y, (int) (x + w - r), (int) (y + h), topArgb, bottomArgb);
        ctx.fillGradient((int) x, (int) (y + r), (int) (x + r), (int) (y + h - r), topArgb, bottomArgb);
        ctx.fillGradient((int) (x + w - r), (int) (y + r), (int) (x + w), (int) (y + h - r), topArgb, bottomArgb);
        for (int dy = 0; dy < r; dy++) {
            int dx = (int) (r - Math.sqrt(r * r - dy * dy));
            ctx.fill((int) (x + dx), (int) (y + dy), (int) (x + r), (int) (y + dy + 1), bottomArgb);
            ctx.fill((int) (x + w - r), (int) (y + dy), (int) (x + w - dx), (int) (y + dy + 1), bottomArgb);
            ctx.fill((int) (x + dx), (int) (y + h - dy - 1), (int) (x + r), (int) (y + h - dy), bottomArgb);
            ctx.fill((int) (x + w - r), (int) (y + h - dy - 1), (int) (x + w - dx), (int) (y + h - dy), bottomArgb);
        }
    }

    // ---------- 文本 / 物品 ----------

    public void text(String s, double x, double y, int argb, boolean shadow) {
        ctx.drawText(mc.textRenderer, s, (int) x, (int) y, argb, shadow);
    }

    public void text(Text t, double x, double y, int argb, boolean shadow) {
        ctx.drawText(mc.textRenderer, t, (int) x, (int) y, argb, shadow);
    }

    public double textWidth(String s) {
        return mc.textRenderer.getWidth(s);
    }

    public double textHeight() {
        return mc.textRenderer.fontHeight;
    }

    public void item(ItemStack stack, double x, double y, double scale) {
        if (stack.isEmpty()) return;
        Matrix3x2fStack ms = ctx.getMatrices();
        ms.pushMatrix();
        ms.scale((float) scale, (float) scale);
        ctx.drawItem(stack, (int) (x / scale), (int) (y / scale));
        ms.popMatrix();
    }

    // ---------- 模糊纹理 ----------

    // 把模糊图的一整块区域画到屏幕 (sx,sy,sw,sh),uv 为像素坐标
    public void blurred(double sx, double sy, double sw, double sh, Identifier tex, int texW, int texH) {
        int x = (int) sx, y = (int) sy, w = (int) sw, h = (int) sh;
        float u = (float) (sx * sf);
        float v = texH - (float) ((sy + sh) * sf);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, (int) (sw * sf), (int) (sh * sf), texW, texH);
    }
}
