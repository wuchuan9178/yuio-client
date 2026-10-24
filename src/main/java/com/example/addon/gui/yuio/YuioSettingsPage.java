package com.example.addon.gui.yuio;

import com.example.addon.hud.Anim;
import com.example.addon.render.GuiIcons;
import com.example.addon.utils.RoundRectRenderer;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.registry.Registries;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class YuioSettingsPage {
    private static final double HEADER_H = 40;
    private static final double ROW_H = 24;
    private static final double GROUP_H = 22;

    private final YuioClickGui gui;
    private final PickerOverlay picker = new PickerOverlay();
    private final Anim scrollAnim = new Anim(0, 16);
    private final Anim headerAnim = new Anim(0, 16);

    private Module module;
    private Object owner;
    private String headerTitle = "";
    private String headerDescription = "";
    private meteordevelopment.meteorclient.settings.Settings activeSettings;
    private java.util.function.BooleanSupplier enabledCheck;
    private Runnable toggleAction;
    private double scroll;
    private double scrollTarget;
    private double lastDelta = 0.016;

    private final List<RowHit> rows = new ArrayList<>();
    private double backX, backY, backW, backH;
    private double headerSwitchX, headerSwitchY, headerSwitchW, headerSwitchH;
    private final IosSwitch moduleSwitch = new IosSwitch(false);

    private Setting<?> draggingSlider;
    private double sliderX, sliderW;

    private Setting<String> editingString;
    private String editBuffer = "";
    private KeybindSetting capturingKeybind;
    private boolean capturing;

    public YuioSettingsPage(YuioClickGui gui) {
        this.gui = gui;
    }

    public Module module() {
        return module;
    }

    public boolean isOpen() {
        return owner != null;
    }

    public void open(Module module) {
        if (this.owner != module) {
            scroll = 0;
            scrollTarget = 0;
            scrollAnim.reset(0);
            headerAnim.reset(0);
        }
        this.owner = module;
        this.module = module;
        this.headerTitle = module.name;
        this.headerDescription = module.description;
        this.activeSettings = module.settings;
        this.enabledCheck = module::isActive;
        this.toggleAction = module::toggle;
        resetInteraction();
    }

    public void openHud(meteordevelopment.meteorclient.systems.hud.HudElement element) {
        if (this.owner != element) {
            scroll = 0;
            scrollTarget = 0;
            scrollAnim.reset(0);
            headerAnim.reset(0);
        }
        this.owner = element;
        this.module = null;
        this.headerTitle = element.info.name;
        this.headerDescription = "hud element";
        this.activeSettings = element.settings;
        this.enabledCheck = element::isActive;
        this.toggleAction = element::toggle;
        resetInteraction();
    }

    private void resetInteraction() {
        this.editingString = null;
        this.capturingKeybind = null;
        this.capturing = false;
        this.draggingSlider = null;
        picker.close();
        if (enabledCheck != null) moduleSwitch.update(enabledCheck.getAsBoolean(), 0.016);
    }

    public void close() {
        module = null;
        owner = null;
        activeSettings = null;
        enabledCheck = null;
        toggleAction = null;
        picker.close();
        editingString = null;
        capturingKeybind = null;
        capturing = false;
        draggingSlider = null;
        rows.clear();
    }

    // ----------

    public void render(GuiDraw g, double x, double y, double w, double h,
                       double alpha, double slide, double mouseX, double mouseY, double dt) {
        if (owner == null || activeSettings == null) return;

        lastDelta = dt;
        headerAnim.set(1);
        headerAnim.update(dt);

        rows.clear();
        offset = slide * 16;

        double ox = x + offset;

        renderHeader(g, ox, y, w, alpha, mouseX, mouseY, dt);

        double listY = y + HEADER_H;
        double listH = h - HEADER_H;
        double contentH = contentHeight(w);
        double max = Math.max(0, contentH - listH);
        if (scrollTarget > max) scrollTarget = max;
        if (scrollTarget < 0) scrollTarget = 0;
        scrollAnim.set(scrollTarget);
        scrollAnim.update(dt);
        scroll = scrollAnim.get();

        GuiDraw list = g.clip(x, listY, w, listH);
        double rowY = listY - scroll;

        for (SettingGroup group : activeSettings) {
            boolean hasVisible = false;
            for (Setting<?> setting : group) {
                if (setting.isVisible()) {
                    hasVisible = true;
                    break;
                }
            }
            if (!hasVisible) continue;

            if (rowY + GROUP_H > listY - 2 && rowY < listY + listH + 2) {
                String name = group.name == null || group.name.isBlank() ? "General" : group.name;
                list.text(name, ox + 12, rowY + 10, YuioClickGui.alpha(YuioClickGui.TEXT_FAINT, alpha), false);
                list.fill(ox + 12, rowY + GROUP_H - 3, w - 24, 1, 0,
                    YuioClickGui.alpha(new Color(255, 255, 255, 10), alpha));
            }
            rowY += GROUP_H;

            for (Setting<?> setting : group) {
                if (!setting.isVisible()) continue;

                double rowYStart = rowY;
                if (rowYStart + ROW_H > listY - ROW_H && rowYStart < listY + listH + ROW_H) {
                    renderRow(list, setting, ox, rowYStart, w, alpha, mouseX, mouseY);
                }
                rowY += ROW_H;
            }
        }

        renderScrollbar(g, x, y, w, h, alpha);
        picker.render(g, x, y, w, h, alpha, mouseX, mouseY, dt);
    }

    private double offset;

    private void renderHeader(GuiDraw g, double x, double y, double w, double alpha,
                              double mouseX, double mouseY, double dt) {
        double iconSize = 15;
        backX = x + 10;
        backY = y + (HEADER_H - 26) * 0.5;
        backW = 26;
        backH = 26;

        boolean hover = mouseX >= backX && mouseX < backX + backW && mouseY >= backY && mouseY < backY + backH;
        g.fill(backX, backY, backW, backH, 8,
            YuioClickGui.alpha(new Color(255, 255, 255, hover ? 30 : 14), alpha));
        g.icon(GuiIcons.BACK, backX + (backW - iconSize) * 0.5, backY + (backH - iconSize) * 0.5, iconSize,
            YuioClickGui.alpha(hover ? YuioClickGui.TEXT : YuioClickGui.TEXT_DIM, alpha));

        double textX = backX + backW + 10;
        double swH = 12;
        double swW = IosSwitch.width(swH);
        double swX = x + w - 12 - swW;
        double switchetY = y + (HEADER_H - swH) * 0.5;
        headerSwitchX = swX;
        headerSwitchY = switchetY;
        headerSwitchW = swW;
        headerSwitchH = swH;

        double textRight = swX - 10;
        g.textTrim(headerTitle, textX, y + 10, textRight - textX,
            YuioClickGui.alpha(YuioClickGui.TEXT, alpha), false);
        g.textTrim(headerDescription, textX, y + 24, textRight - textX,
            YuioClickGui.alpha(YuioClickGui.TEXT_FAINT, alpha), false);

        if (enabledCheck != null) moduleSwitch.update(enabledCheck.getAsBoolean(), dt);
        moduleSwitch.updateHover(mouseX >= swX - 2 && mouseX < swX + swW + 2
            && mouseY >= switchetY - 3 && mouseY < switchetY + swH + 3, dt);
        moduleSwitch.render(g, swX, switchetY, swH, alpha);

        g.fill(x + 10, y + HEADER_H - 1, w - 20, 1, 0,
            YuioClickGui.alpha(new Color(255, 255, 255, 20), alpha));
    }

    private void renderRow(GuiDraw g, Setting<?> setting, double x, double y, double w,
                           double alpha, double mouseX, double mouseY) {
        double labelX = x + 12;
        double labelW = w * 0.44;
        double controlX = x + labelW;
        double controlW = w - labelW - 14;

        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ROW_H;

        if (hover) {
            g.fill(x + 6, y, w - 12, ROW_H - 2, 7,
                YuioClickGui.alpha(new Color(255, 255, 255, 10), alpha));
        }

        g.textTrim(setting.title, labelX, y + (ROW_H - g.textHeight()) * 0.5, labelW - 10,
            YuioClickGui.alpha(YuioClickGui.TEXT, alpha), false);
        if (setting instanceof BoolSetting bs) {
            double swH = 12;
            double swW = IosSwitch.width(swH);
            double swX = x + w - 14 - swW;
            double swY = y + (ROW_H - swH) * 0.5;
            boolean swHover = mouseX >= swX - 2 && mouseX < swX + swW + 2 && mouseY >= swY - 3 && mouseY < swY + swH + 3;

            IosSwitch sw = switchOf(setting, bs.get());
            sw.update(bs.get(), lastDelta);
            sw.updateHover(swHover, lastDelta);
            sw.render(g, swX, swY, swH, alpha);
            rows.add(new RowHit(setting, RowKind.BOOL, x, y, w, ROW_H, swX, swY, swW, swH, 0, 0));
            return;
        }

        if (setting instanceof IntSetting is) {
            int min = is.sliderMin;
            int max = is.sliderMax;
            double value = is.get();
            String text = String.valueOf(is.get());
            if (!is.noSlider && max > min) {
                double trackY = y + ROW_H * 0.5;
                double trackW = Math.min(120, controlW - 56);
                double trackX = x + w - 14 - trackW;
                double progress = (value - min) / (double) (max - min);
                drawSlider(g, setting, trackX, trackY, trackW, progress, alpha);
                g.textRight(text, trackX - 10, y + (ROW_H - g.textHeight()) * 0.5,
                    YuioClickGui.alpha(YuioClickGui.TEXT_DIM, alpha), false);
                rows.add(new RowHit(setting, RowKind.INT_SLIDER, x, y, w, ROW_H, 0, 0, 0, 0, trackX, trackW));
                RowHit hit = rows.get(rows.size() - 1);
                hit.sliderMin = min;
                hit.sliderMax = max;
            } else {
                g.textRight(text, x + w - 14, y + (ROW_H - g.textHeight()) * 0.5,
                    YuioClickGui.alpha(YuioClickGui.TEXT_DIM, alpha), false);
            }
            return;
        }

        if (setting instanceof DoubleSetting ds) {
            double value = ds.get();
            double min = ds.sliderMin;
            double max = ds.sliderMax;
            String text = String.format(java.util.Locale.ROOT, "%." + Math.max(0, Math.min(6, ds.decimalPlaces)) + "f", value);
            if (!ds.noSlider && max > min) {
                double trackW = Math.min(120, controlW - 56);
                double trackX = x + w - 14 - trackW;
                double progress = (value - min) / (max - min);
                drawSlider(g, setting, trackX, y + ROW_H * 0.5, trackW, progress, alpha);
                g.textRight(text, trackX - 10, y + (ROW_H - g.textHeight()) * 0.5,
                    YuioClickGui.alpha(YuioClickGui.TEXT_DIM, alpha), false);
                rows.add(new RowHit(setting, RowKind.DOUBLE_SLIDER, x, y, w, ROW_H, 0, 0, 0, 0, trackX, trackW));
            } else {
                g.textRight(text, x + w - 14, y + (ROW_H - g.textHeight()) * 0.5,
                    YuioClickGui.alpha(YuioClickGui.TEXT_DIM, alpha), false);
            }
            return;
        }

        if (setting instanceof EnumSetting<?> es) {
            String text = String.valueOf(es.get());
            double pillW = Math.min(controlW, g.textWidth(text) + 18);
            double pillH = 16;
            double pillX = x + w - 14 - pillW;
            double pillY = y + (ROW_H - pillH) * 0.5;
            boolean pillHover = mouseX >= pillX && mouseX < pillX + pillW && mouseY >= pillY && mouseY < pillY + pillH;

            g.fill(pillX, pillY, pillW, pillH, 8,
                YuioClickGui.alpha(new Color(255, 255, 255, pillHover ? 30 : 14), alpha));
            g.textTrim(text, pillX + 9, pillY + (pillH - g.textHeight()) * 0.5, pillW - 18,
                YuioClickGui.alpha(pillHover ? YuioClickGui.TEXT : YuioClickGui.TEXT_DIM, alpha), false);
            rows.add(new RowHit(setting, RowKind.ENUM, x, y, w, ROW_H, pillX, pillY, pillW, pillH, 0, 0));
            return;
        }

        if (setting instanceof StringSetting ss) {
            boolean editing = editingString == setting;
            String text = editing ? editBuffer : ss.get();
            double fieldX = x + w * 0.5;
            double fieldW = w - (fieldX - x) - 14;
            double fieldH = 17;
            double fieldY = y + (ROW_H - fieldH) * 0.5;

            g.fill(fieldX, fieldY, fieldW, fieldH, 7,
                YuioClickGui.alpha(new Color(10, 13, 19, editing ? 220 : 170), alpha));
            g.border(fieldX, fieldY, fieldW, fieldH, 7, 1,
                YuioClickGui.alpha(editing ? YuioClickGui.RIM_HOT : YuioClickGui.RIM, alpha));
            g.textTrim(text.isEmpty() ? ss.placeholder : text, fieldX + 8, fieldY + (fieldH - g.textHeight()) * 0.5,
                fieldW - 16, YuioClickGui.alpha(text.isEmpty() && !editing ? YuioClickGui.TEXT_FAINT : YuioClickGui.TEXT, alpha), false);
            rows.add(new RowHit(setting, RowKind.STRING, x, y, w, ROW_H, fieldX, fieldY, fieldW, fieldH, 0, 0));
            return;
        }

        if (setting instanceof KeybindSetting ks) {
            String text = capturing && capturingKeybind == ks ? "..." : ks.get().isSet() ? ks.get().toString() : "none";
            double pillW = 62;
            double pillH = 16;
            double pillX = x + w - 14 - pillW;
            double pillY = y + (ROW_H - pillH) * 0.5;
            boolean pillHover = mouseX >= pillX && mouseX < pillX + pillW && mouseY >= pillY && mouseY < pillY + pillH;

            g.fill(pillX, pillY, pillW, pillH, 8,
                YuioClickGui.alpha(new Color(255, 255, 255, pillHover || capturing ? 34 : 14), alpha));
            g.textTrim(text, pillX + 8, pillY + (pillH - g.textHeight()) * 0.5, pillW - 16,
                YuioClickGui.alpha(pillHover ? YuioClickGui.TEXT : YuioClickGui.TEXT_DIM, alpha), false);
            rows.add(new RowHit(setting, RowKind.KEYBIND, x, y, w, ROW_H, pillX, pillY, pillW, pillH, 0, 0));
            return;
        }

        if (setting instanceof ColorSetting cs) {
            SettingColor color = cs.get();
            double boxW = 34;
            double boxH = 16;
            double boxX = x + w - 14 - boxW;
            double boxY = y + (ROW_H - boxH) * 0.5;
            g.fill(boxX, boxY, boxW, boxH, 7, YuioClickGui.alpha(new Color(color.r, color.g, color.b, 255), alpha));
            g.border(boxX, boxY, boxW, boxH, 7, 1, YuioClickGui.alpha(YuioClickGui.RIM, alpha));
            rows.add(new RowHit(setting, RowKind.COLOR, x, y, w, ROW_H, boxX, boxY, boxW, boxH, 0, 0));
            return;
        }

        String friendly = friendlyValue(setting);
        if (friendly != null) {
            g.textRight(friendly, x + w - 14, y + (ROW_H - g.textHeight()) * 0.5,
                YuioClickGui.alpha(YuioClickGui.TEXT_DIM, alpha), false);
            rows.add(new RowHit(setting, RowKind.LIST, x, y, w, ROW_H, 0, 0, 0, 0, 0, 0));
            return;
        }

        g.textRight("...", x + w - 14, y + (ROW_H - g.textHeight()) * 0.5,
            YuioClickGui.alpha(YuioClickGui.TEXT_FAINT, alpha), false);
        rows.add(new RowHit(setting, RowKind.READ_ONLY, x, y, w, ROW_H, 0, 0, 0, 0, 0, 0));
    }

    private String friendlyValue(Setting<?> setting) {
        if (setting instanceof EntityTypeListSetting s) {
            java.util.Set<net.minecraft.entity.EntityType<?>> set = s.get();
            if (set.isEmpty()) return "None";
            String first = Registries.ENTITY_TYPE.getId(set.iterator().next()).getPath();
            return set.size() == 1 ? first : first + " +" + (set.size() - 1);
        }
        if (setting instanceof BlockListSetting s) {
            java.util.List<net.minecraft.block.Block> list = s.get();
            if (list.isEmpty()) return "None";
            String first = list.get(0).getName().getString();
            return list.size() == 1 ? first : first + " +" + (list.size() - 1);
        }
        if (setting instanceof ItemListSetting s) {
            java.util.List<net.minecraft.item.Item> list = s.get();
            if (list.isEmpty()) return "None";
            String first = list.get(0).getName().getString();
            return list.size() == 1 ? first : first + " +" + (list.size() - 1);
        }
        if (setting instanceof StringListSetting s) {
            java.util.List<String> list = s.get();
            if (list.isEmpty()) return "None";
            return list.size() == 1 ? list.get(0) : list.get(0) + " +" + (list.size() - 1);
        }
        return null;
    }

    private final java.util.Map<Setting<?>, IosSwitch> switches = new java.util.HashMap<>();

    private IosSwitch switchOf(Setting<?> setting, boolean on) {
        return switches.computeIfAbsent(setting, key -> new IosSwitch(on));
    }

    private final java.util.Map<Setting<?>, Anim> sliderAnims = new java.util.HashMap<>();

    private void drawSlider(GuiDraw g, Setting<?> setting, double x, double y, double width, double progress, double alpha) {
        double trackH = 4;
        double p = Math.max(0, Math.min(1, progress));

        Anim anim = sliderAnims.computeIfAbsent(setting, k -> new Anim(p, 18));
        anim.set(p);
        anim.update(lastDelta);
        double shown = anim.get();

        g.fill(x, y - trackH * 0.5, width, trackH, trackH * 0.5,
            YuioClickGui.alpha(new Color(62, 66, 74, 220), alpha));
        g.fill(x, y - trackH * 0.5, Math.max(trackH, width * shown), trackH, trackH * 0.5,
            YuioClickGui.alpha(YuioClickGui.ACCENT, alpha));
        double knobR = 5.5;
        g.fill(x + width * shown - knobR, y - knobR, knobR * 2, knobR * 2, knobR,
            YuioClickGui.alpha(new Color(244, 248, 255), alpha));
    }

    private void renderScrollbar(GuiDraw g, double x, double y, double w, double h, double alpha) {
        double listY = y + HEADER_H;
        double listH = h - HEADER_H;
        double contentH = contentHeight(w);
        if (contentH <= listH) return;

        double trackW = 3;
        double trackX = x + w - 6;
        double thumbH = Math.max(20, listH * (listH / contentH));
        double travel = listH - thumbH;
        double max = Math.max(1, contentH - listH);
        double thumbY = listY + travel * (scroll / max);

        g.fill(trackX, listY + 4, trackW, listH - 8, trackW * 0.5,
            YuioClickGui.alpha(new Color(255, 255, 255, 18), alpha));
        g.fill(trackX, thumbY, trackW, thumbH, trackW * 0.5,
            YuioClickGui.alpha(new Color(255, 255, 255, 80), alpha));
    }

    private double contentHeight(double w) {
        if (activeSettings == null) return 0;
        double height = 0;
        for (SettingGroup group : activeSettings) {
            boolean hasVisible = false;
            for (Setting<?> setting : group) {
                if (setting.isVisible()) {
                    hasVisible = true;
                    break;
                }
            }
            if (!hasVisible) continue;
            height += GROUP_H;
            for (Setting<?> setting : group) {
                if (setting.isVisible()) height += ROW_H;
            }
        }
        return height + 10;
    }

    // ----------

    public boolean mouseClicked(double mx, double my, int button) {
        if (module == null) return false;

        if (picker.active()) return picker.mouseClicked(mx, my, button);

        if (mx >= backX && mx < backX + backW && my >= backY && my < backY + backH) {
            close();
            return true;
        }

        double swH = 12;
        double swW = IosSwitch.width(swH);
        if (mx >= headerSwitchX - 2 && mx < headerSwitchX + headerSwitchW + 2
            && my >= headerSwitchY - 3 && my < headerSwitchY + headerSwitchH + 3) {
            if (toggleAction != null) toggleAction.run();
            return true;
        }

        for (RowHit row : rows) {
            if (!row.containsControl(mx, my)) continue;

            picker.at(row.x, row.y, row.w, row.h);

            switch (row.kind) {
                case BOOL -> {
                    if (row.setting instanceof BoolSetting bs) bs.set(!bs.get());
                    return true;
                }
                case ENUM -> {
                    if (row.setting instanceof EnumSetting<?> es) picker.openEnum(es);
                    return true;
                }
                case LIST -> {
                    picker.openList(row.setting);
                    return true;
                }
                case COLOR -> {
                    if (row.setting instanceof ColorSetting cs) picker.openColor(cs);
                    return true;
                }
                case STRING -> {
                    if (row.setting instanceof StringSetting ss) {
                        editingString = ss;
                        editBuffer = ss.get();
                    }
                    return true;
                }
                case KEYBIND -> {
                    if (row.setting instanceof KeybindSetting ks) {
                        capturingKeybind = ks;
                        capturing = true;
                        editingString = null;
                    }
                    return true;
                }
                case INT_SLIDER -> {
                    draggingSlider = row.setting;
                    sliderX = row.sliderX;
                    sliderW = row.sliderW;
                    applySlider(mx);
                    return true;
                }
                case DOUBLE_SLIDER -> {
                    draggingSlider = row.setting;
                    sliderX = row.sliderX;
                    sliderW = row.sliderW;
                    applySlider(mx);
                    return true;
                }
                case READ_ONLY -> {
                    return false;
                }
            }
        }

        return false;
    }

    private void applySlider(double mx) {
        double progress = Math.max(0, Math.min(1, (mx - sliderX) / sliderW));
        if (draggingSlider instanceof IntSetting is) {
            int value = (int) Math.round(is.sliderMin + (is.sliderMax - is.sliderMin) * progress);
            is.set(Math.max(is.min, Math.min(is.max, value)));
        } else if (draggingSlider instanceof DoubleSetting ds) {
            double raw = ds.sliderMin + (ds.sliderMax - ds.sliderMin) * progress;
            double step = Math.pow(10, -Math.max(0, Math.min(6, ds.decimalPlaces)));
            double value = Math.round(raw / step) * step;
            ds.set(Math.max(ds.min, Math.min(ds.max, value)));
        }
    }

    public boolean mouseDragged(double mx, double my) {
        if (picker.active()) return picker.mouseDragged(mx, my);
        if (draggingSlider != null) {
            applySlider(mx);
            return true;
        }
        return false;
    }

    public boolean mouseReleased() {
        if (picker.active() && picker.mouseReleased()) return true;
        if (draggingSlider != null) {
            draggingSlider = null;
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double vertical) {
        if (picker.active()) return picker.mouseScrolled(vertical);
        scrollTarget -= vertical * 28;
        return true;
    }

    public boolean keyPressed(KeyInput input) {
        if (picker.active()) return picker.keyPressed(input);
        if (capturing && capturingKeybind != null) {
            if (input.isEscape()) {
                capturing = false;
                capturingKeybind = null;
                return true;
            }
            int key = input.key();
            if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
                capturingKeybind.set(Keybind.none());
                capturing = false;
                capturingKeybind = null;
                return true;
            }
            if (key != GLFW.GLFW_KEY_UNKNOWN) {
                capturingKeybind.set(Keybind.fromKey(key));
                capturing = false;
                capturingKeybind = null;
            }
            return true;
        }

        if (editingString != null) {
            if (input.isEscape()) {
                editingString = null;
                return true;
            }
            if (input.isEnter()) {
                String value = editBuffer;
                editingString.parse(value);
                editingString = null;
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (!editBuffer.isEmpty()) editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
                return true;
            }
            return true;
        }

        return false;
    }

    public boolean charTyped(CharInput input) {
        if (picker.active()) return picker.charTyped(input);
        if (editingString != null && input.isValidChar()) {
            editBuffer += input.asString();
            return true;
        }
        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void cycleEnum(Setting<?> setting) {
        EnumSetting es = (EnumSetting) setting;
        Object current = es.get();
        if (!(current instanceof Enum<?> value)) return;
        Object[] constants = value.getDeclaringClass().getEnumConstants();
        if (constants == null || constants.length == 0) return;
        for (int i = 0; i < constants.length; i++) {
            if (constants[i] == value) {
                es.set(constants[(i + 1) % constants.length]);
                return;
            }
        }
    }

    // ---------- 闂佸憡绋掗崹婵嬫嚈閹寸偟鈹嶉柍鈺佸暕缁?----------

    private enum RowKind { BOOL, INT_SLIDER, DOUBLE_SLIDER, ENUM, LIST, COLOR, STRING, KEYBIND, READ_ONLY }

    private static final class RowHit {
        final Setting<?> setting;
        final RowKind kind;
        final double x, y, w, h;
        final double controlX, controlY, controlW, controlH;
        final double sliderX, sliderW;
        int sliderMin, sliderMax;

        RowHit(Setting<?> setting, RowKind kind, double x, double y, double w, double h,
               double controlX, double controlY, double controlW, double controlH,
               double sliderX, double sliderW) {
            this.setting = setting;
            this.kind = kind;
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.controlX = controlX; this.controlY = controlY;
            this.controlW = controlW; this.controlH = controlH;
            this.sliderX = sliderX; this.sliderW = sliderW;
        }

        boolean containsControl(double mx, double my) {
            if (my < y || my >= y + h) return false;
            if (kind == RowKind.INT_SLIDER || kind == RowKind.DOUBLE_SLIDER) {
                return mx >= sliderX - 6 && mx < sliderX + sliderW + 6;
            }
            return mx >= controlX && mx < controlX + controlW && my >= controlY && my < controlY + controlH;
        }
    }
}
