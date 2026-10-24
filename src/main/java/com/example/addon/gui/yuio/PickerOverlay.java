package com.example.addon.gui.yuio;

import com.example.addon.hud.Anim;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.block.Block;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// 覆盖式选择面板: 枚举下拉 / 实体·方块·物品·字符串列表 / 颜色选择
public final class PickerOverlay {
    private enum Kind { NONE, ENUM, ENTITY, BLOCK, ITEM, STRING, COLOR }

    private static final double ROW_H = 22;

    private Kind kind = Kind.NONE;
    private Setting<?> owner;
    private String title = "";
    private boolean searchable;

    private final List<Entry> entries = new ArrayList<>();
    private String query = "";

    private final Anim anim = new Anim(0, 18);
    private double scroll;
    private double scrollTarget;

    private double panelX, panelY, panelW, panelH;
    private double listX, listY, listW, listH;

    private double anchorX, anchorY, anchorW, anchorH;

    // 下拉锚点: 面板贴在触发控件下方(空间不足时向上)
    public PickerOverlay at(double x, double y, double w, double h) {
        this.anchorX = x;
        this.anchorY = y;
        this.anchorW = w;
        this.anchorH = h;
        return this;
    }

    private float hue, sat, val;
    private int colorAlpha = 255;
    private double svX, svY, svSize, hueX, hueY, hueH, albX, albY, albW;
    private int dragging = -1;

    public boolean active() {
        return kind != Kind.NONE;
    }

    public Setting<?> owner() {
        return owner;
    }

    public void close() {
        kind = Kind.NONE;
        owner = null;
        entries.clear();
        query = "";
        scroll = 0;
        scrollTarget = 0;
        dragging = -1;
        anim.reset(0);
    }

    // ---------- 打开 ----------

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void openEnum(EnumSetting<?> setting) {
        reset(setting, setting.title, false);
        kind = Kind.ENUM;
        Object current = setting.get();
        Object[] constants = current instanceof Enum<?> value ? value.getDeclaringClass().getEnumConstants() : null;
        if (constants == null) return;
        for (Object constant : constants) {
            entries.add(new Entry(String.valueOf(constant), constant == current, () -> {
                ((EnumSetting) setting).set(constant);
                close();
            }));
        }
    }

    public void openList(Setting<?> setting) {
        if (setting instanceof EntityTypeListSetting s) {
            reset(setting, setting.title, true);
            kind = Kind.ENTITY;
            Set<EntityType<?>> selected = s.get();
            for (EntityType<?> type : Registries.ENTITY_TYPE) {
                String name = Registries.ENTITY_TYPE.getId(type).getPath();
                entries.add(new Entry(name, selected.contains(type), () -> {
                    Set<EntityType<?>> next = new LinkedHashSet<>(s.get());
                    if (!next.remove(type)) next.add(type);
                    s.set(next);
                }));
            }
        } else if (setting instanceof BlockListSetting s) {
            reset(setting, setting.title, true);
            kind = Kind.BLOCK;
            List<Block> selected = s.get();
            for (Block block : Registries.BLOCK) {
                entries.add(new Entry(block.getName().getString(), selected.contains(block), () -> {
                    List<Block> next = new ArrayList<>(s.get());
                    if (!next.remove(block)) next.add(block);
                    s.set(next);
                }));
            }
        } else if (setting instanceof ItemListSetting s) {
            reset(setting, setting.title, true);
            kind = Kind.ITEM;
            List<Item> selected = s.get();
            for (Item item : Registries.ITEM) {
                entries.add(new Entry(item.getName().getString(), selected.contains(item), () -> {
                    List<Item> next = new ArrayList<>(s.get());
                    if (!next.remove(item)) next.add(item);
                    s.set(next);
                }));
            }
        } else if (setting instanceof StringListSetting s) {
            reset(setting, setting.title, true);
            kind = Kind.STRING;
            rebuildStrings(s);
        }
    }

    private void rebuildStrings(StringListSetting s) {
        entries.clear();
        for (String value : s.get()) {
            entries.add(new Entry(value, true, () -> {
                List<String> next = new ArrayList<>(s.get());
                next.remove(value);
                s.set(next);
                rebuildStrings(s);
            }));
        }
    }

    public void openValues(String title, List<String> values, String current, java.util.function.Consumer<String> onPick) {
        reset(null, title, false);
        kind = Kind.ENUM;
        for (String value : values) {
            entries.add(new Entry(value, value.equals(current), () -> {
                onPick.accept(value);
                close();
            }));
        }
    }

    public void openColor(ColorSetting setting) {
        reset(setting, setting.title, false);
        kind = Kind.COLOR;
        SettingColor color = setting.get();
        float[] hsv = java.awt.Color.RGBtoHSB(color.r, color.g, color.b, null);
        hue = hsv[0];
        sat = hsv[1];
        val = hsv[2];
        colorAlpha = color.a;
    }

    private void reset(Setting<?> setting, String title, boolean searchable) {
        this.owner = setting;
        this.title = title;
        this.searchable = searchable;
        this.query = "";
        this.entries.clear();
        this.scroll = 0;
        this.scrollTarget = 0;
        this.dragging = -1;
        this.anim.reset(0);
        this.anim.set(1);
    }

    // ---------- 渲染 ----------

    public void render(GuiDraw g, double x, double y, double w, double h, double alpha,
                       double mouseX, double mouseY, double dt) {
        if (!active()) return;
        anim.set(1);
        anim.update(dt);
        double a = alpha * Math.min(1, anim.get() * 1.4);

        double rows = Math.max(1, Math.min(entries.size(), 9));
        panelW = kind == Kind.COLOR ? Math.min(320, w - 24) : Math.max(170, Math.min(300, Math.max(anchorW, 170)));
        panelH = kind == Kind.COLOR
            ? Math.min(300, h - 24)
            : Math.min(34 + rows * ROW_H + (searchable ? 30 : 0) + 8, h - 24);

        panelX = Math.max(x + 8, Math.min(anchorX, x + w - panelW - 8));
        double below = anchorY + anchorH + 4;
        double above = anchorY - panelH - 4;
        panelY = (below + panelH <= y + h - 8 || above < y + 8) ? below : above;
        panelY = Math.max(y + 8, Math.min(panelY, y + h - panelH - 8));

        g.fill(panelX, panelY, panelW, panelH, 10, new Color(18, 20, 26, (int) Math.round(248 * a)));
        g.border(panelX, panelY, panelW, panelH, 10, 1, new Color(255, 255, 255, (int) Math.round(30 * a)));

        g.textTrim(title, panelX + 12, panelY + 11, panelW - 24,
            new Color(240, 243, 248, (int) Math.round(255 * a)), false);

        double top = panelY + 30;
        double bottom = panelY + panelH - 8;

        if (kind == Kind.COLOR) {
            renderColor(g, a, mouseX, mouseY);
            return;
        }

        if (searchable) {
            double fieldY = top;
            double fieldH = 22;
            g.fill(panelX + 14, fieldY, panelW - 28, fieldH, 6, new Color(0, 0, 0, (int) Math.round(110 * a)));
            String shown = query.isEmpty() ? "Search..." : query;
            g.textTrim(shown, panelX + 22, fieldY + (fieldH - g.textHeight()) * 0.5, panelW - 44,
                new Color(query.isEmpty() ? 108 : 240, query.isEmpty() ? 114 : 243,
                    query.isEmpty() ? 126 : 248, (int) Math.round(255 * a)), false);
            top += fieldH + 8;
        }

        listX = panelX + 14;
        listY = top;
        listW = panelW - 28;
        listH = bottom - top;

        List<Entry> visible = visibleEntries();
        double contentH = visible.size() * ROW_H;
        double max = Math.max(0, contentH - listH);
        if (scrollTarget > max) scrollTarget = max;
        if (scrollTarget < 0) scrollTarget = 0;
        double prev = scroll;
        scroll = prev + (scrollTarget - prev) * Math.min(1, dt * 16);
        if (Math.abs(scrollTarget - scroll) < 0.2) scroll = scrollTarget;

        GuiDraw list = g.clip(listX, listY, listW, listH);
        double rowY = listY - scroll;
        for (Entry entry : visible) {
            if (entry.name.equals(kind == Kind.STRING ? "__add__" : "")) {
                rowY += ROW_H;
                continue;
            }
            boolean hover = mouseX >= listX && mouseX < listX + listW && mouseY >= rowY && mouseY < rowY + ROW_H - 2;
            if (hover) {
                list.fill(listX, rowY, listW, ROW_H - 2, 5, new Color(255, 255, 255, (int) Math.round(16 * a)));
            }
            if (entry.selected) {
                list.fill(listX + 4, rowY + ROW_H * 0.5 - 2, 4, 4, 2, new Color(255, 96, 160, (int) Math.round(255 * a)));
            }
            list.textTrim(entry.name, listX + 14, rowY + (ROW_H - 2 - g.textHeight()) * 0.5, listW - 24,
                new Color(entry.selected ? 240 : 190, entry.selected ? 243 : 198, entry.selected ? 248 : 212,
                    (int) Math.round(255 * a)), false);
            rowY += ROW_H;
        }

        if (kind == Kind.STRING) {
            String hint = "type a value and press Enter to add";
            g.textTrim(hint, panelX + 16, bottom - 2, panelW - 32,
                new Color(108, 114, 126, (int) Math.round(255 * a)), false);
        }
    }

    private void renderColor(GuiDraw g, double a, double mouseX, double mouseY) {
        double left = panelX + 16;
        double top = panelY + 40;
        svSize = Math.min(panelW - 92, panelH - 96);
        svX = left;
        svY = top;

        // 色相底 + 饱和度横向渐变 + 明度纵向渐变
        int hueColor = java.awt.Color.HSBtoRGB(hue, 1f, 1f);
        g.fill(svX, svY, svSize, svSize, 6, new Color((hueColor >> 16) & 0xFF, (hueColor >> 8) & 0xFF, hueColor & 0xFF, 255));
        double strips = 18;
        for (int i = 0; i < strips; i++) {
            double p0 = i / strips;
            double p1 = (i + 1) / strips;
            int rgb = java.awt.Color.HSBtoRGB(hue, (float) p0, 1f);
            g.fill(svX + svSize * p0, svY, svSize * (p1 - p0) + 0.6, svSize, 0,
                new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255));
        }
        g.fillGrad(svX, svY, svSize, svSize, 0, new Color(0, 0, 0, 0), new Color(0, 0, 0, 255));

        double knX = svX + svSize * sat;
        double knY = svY + svSize * (1 - val);
        g.border(knX - 4, knY - 4, 8, 8, 4, 1.5, new Color(255, 255, 255, 235));

        hueX = svX + svSize + 14;
        hueY = svY;
        hueH = svSize;
        for (int i = 0; i < 40; i++) {
            double p = i / 40.0;
            int rgb = java.awt.Color.HSBtoRGB((float) p, 1f, 1f);
            g.fill(hueX, hueY + hueH * p, 14, hueH / 40.0 + 0.6, 0,
                new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255));
        }
        double hueKnY = hueY + hueH * (1 - hue);
        g.fill(hueX - 2, hueKnY - 3, 18, 6, 3, new Color(255, 255, 255, 240));

        albX = left;
        albY = svY + svSize + 14;
        albW = svSize;
        int rgbNow = java.awt.Color.HSBtoRGB(hue, sat, val);
        Color solid = new Color((rgbNow >> 16) & 0xFF, (rgbNow >> 8) & 0xFF, rgbNow & 0xFF, 255);
        for (int i = 0; i < 30; i++) {
            double p = i / 30.0;
            g.fill(albX + albW * p, albY, albW / 30.0 + 0.6, 12, 0, new Color(solid.r, solid.g, solid.b, (int) (255 * p)));
        }
        double albKnX = albX + albW * (colorAlpha / 255.0);
        g.fill(albKnX - 2, albY - 2, 4, 16, 2, new Color(255, 255, 255, 240));

        String hex = String.format(Locale.ROOT, "#%02X%02X%02X  %d", solid.r, solid.g, solid.b, colorAlpha);
        g.text(hex, left, albY + 22, new Color(240, 243, 248, (int) Math.round(255 * a)), false);
        g.fill(panelX + panelW - 52, panelY + panelH - 30, 34, 16, 5, new Color(solid.r, solid.g, solid.b, colorAlpha));
    }

    private List<Entry> visibleEntries() {
        if (query.isBlank()) return entries;
        List<Entry> out = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) out.add(entry);
        }
        return out;
    }

    // ---------- 输入 ----------

    public boolean mouseClicked(double mx, double my, int button) {
        if (!active()) return false;

        if (mx < panelX || mx > panelX + panelW || my < panelY || my > panelY + panelH) {
            close();
            return true;
        }

        if (kind == Kind.COLOR) {
            colorClick(mx, my);
            return true;
        }

        if (my < listY) {
            return true;
        }

        List<Entry> visible = visibleEntries();
        for (int i = 0; i < visible.size(); i++) {
            double rowY = listY - scroll + i * ROW_H;
            if (mx >= listX && mx < listX + listW && my >= rowY && my < rowY + ROW_H - 2) {
                int direction = button == 1 ? -1 : 1;
                if (direction < 0) {
                    entrySecondary(visible.get(i));
                } else {
                    visible.get(i).action.run();
                }
                return true;
            }
        }
        return true;
    }

    @SuppressWarnings("unused")
    private void entrySecondary(Entry entry) {
    }

    private void colorClick(double mx, double my) {
        if (mx >= svX && mx < svX + svSize && my >= svY && my < svY + svSize) {
            dragging = 0;
            colorDrag(mx, my);
        } else if (mx >= hueX && mx < hueX + 14 && my >= hueY && my < hueY + hueH) {
            dragging = 1;
            colorDrag(mx, my);
        } else if (mx >= albX && mx < albX + albW && my >= albY - 4 && my < albY + 16) {
            dragging = 2;
            colorDrag(mx, my);
        } else if (mx >= panelX + panelW - 62 && mx < panelX + panelW - 8
            && my >= panelY + panelH - 36 && my < panelY + panelH - 6) {
            close();
        }
    }

    public boolean mouseDragged(double mx, double my) {
        if (dragging < 0) return false;
        colorDrag(mx, my);
        return true;
    }

    private void colorDrag(double mx, double my) {
        if (dragging == 0) {
            sat = (float) Math.max(0, Math.min(1, (mx - svX) / svSize));
            val = (float) Math.max(0, Math.min(1, 1 - (my - svY) / svSize));
        } else if (dragging == 1) {
            hue = (float) Math.max(0, Math.min(1, 1 - (my - hueY) / hueH));
        } else if (dragging == 2) {
            colorAlpha = (int) Math.round(255 * Math.max(0, Math.min(1, (mx - albX) / albW)));
        }
        if (owner instanceof ColorSetting cs) {
            int rgb = java.awt.Color.HSBtoRGB(hue, sat, val);
            cs.set(new SettingColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, colorAlpha));
        }
    }

    public boolean mouseReleased() {
        if (dragging >= 0) {
            dragging = -1;
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double vertical) {
        if (!active() || kind == Kind.COLOR) return false;
        scrollTarget -= vertical * 20;
        return true;
    }

    public boolean keyPressed(KeyInput input) {
        if (!active()) return false;
        if (input.isEscape()) {
            close();
            return true;
        }
        if (kind == Kind.STRING && input.isEnter() && !query.isBlank() && owner instanceof StringListSetting s) {
            List<String> next = new ArrayList<>(s.get());
            next.add(query);
            s.set(next);
            rebuildStrings(s);
            query = "";
            return true;
        }
        if (searchable && input.key() == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
            query = query.substring(0, query.length() - 1);
            return true;
        }
        return true;
    }

    public boolean charTyped(CharInput input) {
        if (!active() || !searchable || !input.isValidChar()) return false;
        query += input.asString();
        return true;
    }

    private record PanelBounds(double x, double y) {
    }

    private static final class Entry {
        final String name;
        final boolean selected;
        final Runnable action;

        Entry(String name, boolean selected, Runnable action) {
            this.name = name;
            this.selected = selected;
            this.action = action;
        }
    }
}
