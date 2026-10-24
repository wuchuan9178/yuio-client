package com.example.addon.gui.yuio;

import com.example.addon.AddonTemplate;
import com.example.addon.hud.Anim;
import com.example.addon.render.GuiIcons;
import com.example.addon.utils.BlurRenderer;
import com.example.addon.utils.RoundRectRenderer;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.friends.Friend;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.macros.Macro;
import meteordevelopment.meteorclient.systems.macros.Macros;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.profiles.Profile;
import meteordevelopment.meteorclient.systems.profiles.Profiles;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public class YuioClickGui extends Screen {
    static final Color TEXT = new Color(240, 243, 248);
    static final Color TEXT_DIM = new Color(154, 160, 172);
    static final Color TEXT_FAINT = new Color(108, 114, 126);
    static final Color ACCENT = new Color(78, 140, 255);
    static final Color ACCENT_SOFT = new Color(78, 140, 255, 46);
    static final Color CARD = new Color(17, 19, 25, 186);
    static final Color CARD_HOVER = new Color(30, 34, 44, 208);
    static final Color CARD_ACTIVE = new Color(21, 28, 42, 200);
    static final Color RIM = new Color(255, 255, 255, 16);
    static final Color RIM_HOT = new Color(255, 255, 255, 40);
    static final Color FIELD = new Color(8, 9, 12, 130);
    static final Color TRACK = new Color(58, 62, 72);
    static final Color KNOB = new Color(246, 249, 253);

    static final double CARD_H = 58;
    static final double CARD_GAP = 8;

    private static final String[] CLIENT_PAGES = {"Config", "GUI", "HUD", "Friends", "Macros", "Profiles"};

    static double uiScale = 1.0;
    static double uiOpacity = 1.0;
    static double uiAnim = 1.0;
    static boolean uiGlass = true;

    private final List<Category> addonCategories = List.of(
        AddonTemplate.ATTACK, AddonTemplate.ASSIST, AddonTemplate.SURVIVAL, AddonTemplate.OTHER, AddonTemplate.SETTINGS);

    private final List<NavHit> navHits = new ArrayList<>();
    private final List<CardHit> cardHits = new ArrayList<>();
    private final List<TabHit> tabHits = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final Map<Module, CardState> cards = new HashMap<>();
    private final Map<Module, IosSwitch> switches = new HashMap<>();
    private final Map<HudElement, IosSwitch> hudSwitches = new HashMap<>();
    private final YuioSettingsPage page = new YuioSettingsPage(this);
    private final PickerOverlay pagePicker = new PickerOverlay();

    private final Anim openAnim = new Anim(0, 14);
    private final Anim selAnim = new Anim(0, 18);
    private final Anim scrollAnim = new Anim(0, 16);
    private final Anim sbAnim = new Anim(0, 16);
    private final Anim settingsAnim = new Anim(0, 18);
    private final Anim bindAnim = new Anim(0, 18);
    private final Anim searchAnim = new Anim(0, 18);

    private int selectedCat = 0;
    private int pageIndex = 0;
    private double scrollTarget;
    private double sbScrollTarget;
    private long lastNanos = System.nanoTime();
    private double lastDt = 0.016;

    private boolean searching;
    private String query = "";
    private Module binding;
    private boolean searchFocused;

    private boolean mouseDown;
    private boolean draggingWindow;
    private double dragStartX, dragStartY, dragBaseX, dragBaseY;
    private double winOffsetX, winOffsetY;

    private int draggingSlider = -1;

    private boolean addingFriend;
    private String friendBuffer = "";

    double winX, winY, winW, winH, topH, sbX, sbY, sbW, sbH, areaX, areaY, areaW, areaH, headH;

    public YuioClickGui() {
        super(Text.literal("yuio click gui"));
    }

    // ----------

    @Override
    public void init() {
        super.init();
        openAnim.reset(0);
        settingsAnim.reset(page.isOpen() ? 1 : 0);
        lastNanos = System.nanoTime();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    protected void applyBlur(DrawContext context) {
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    // ----------

    private void layout() {
        winW = Math.min(700 * uiScale, width - 36);
        winH = Math.min(440 * uiScale, height - 36);
        winX = (width - winW) * 0.5 + winOffsetX;
        winY = (height - winH) * 0.5 + winOffsetY;
        winX = Math.max(8, Math.min(width - winW - 8, winX));
        winY = Math.max(8, Math.min(height - winH - 8, winY));
        topH = 44 * uiScale;

        double pad = 14 * uiScale;
        sbW = Math.max(132 * uiScale, Math.min(178 * uiScale, winW * 0.255));
        sbX = winX + pad;
        sbY = winY + topH + 8;
        sbH = winH - topH - 8 - pad;
        headH = 34 * uiScale;
        areaX = sbX + sbW + pad;
        areaY = sbY + headH;
        areaW = winX + winW - pad - areaX;
        areaH = winY + winH - pad - areaY;
    }

    private double frameDelta() {
        long now = System.nanoTime();
        double dt = (now - lastNanos) / 1_000_000_000.0;
        lastNanos = now;
        if (dt < 0) dt = 0;
        lastDt = Math.min(0.1, dt) * Math.max(0.25, uiAnim);
        return lastDt;
    }

    // ---------- 婵炴挸寮堕悡?----------

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        RoundRectRenderer.beginFrame();
        BlurRenderer.request();

        double dt = frameDelta();
        layout();
        GuiDraw.textScale = uiScale;

        openAnim.set(1);
        openAnim.update(dt);
        double open = ease(openAnim.get());

        selAnim.set(selectedCat);
        selAnim.update(dt);
        settingsAnim.set(page.isOpen() ? 1 : 0);
        settingsAnim.update(dt);
        bindAnim.set(binding != null ? 1 : 0);
        bindAnim.update(dt);
        searchAnim.set(searching ? 1 : 0);
        searchAnim.update(dt);

        GuiDraw g = new GuiDraw(ctx);

        g.fill(0, 0, width, height, 0, new Color(3, 4, 7, (int) Math.round(88 * open)));

        if (uiGlass) {
            g.glass(winX, winY, winW, winH, 16, (int) Math.round(200 * open * uiOpacity));
        }
        g.fill(winX, winY, winW, winH, 16, new Color(10, 11, 15, (int) Math.round(196 * open * uiOpacity)));
        g.border(winX, winY, winW, winH, 16, 1, alpha(RIM, open));

        renderTopBar(g, mouseX, mouseY, open);
        renderSidebar(g, mouseX, mouseY, dt, open);

        double settingsT = ease(settingsAnim.get());

        if (page.isOpen() && settingsT > 0.01) {
            GuiDraw content = g.clip(areaX, areaY - headH, areaW, areaH + headH);
            page.render(content, areaX, areaY - headH, areaW, areaH + headH, open * settingsT,
                1 - settingsT, mouseX, mouseY, dt);
        } else {
            renderContentHeader(g, mouseX, mouseY, open);
            GuiDraw content = g.clip(areaX, areaY, areaW, areaH);

            if (pageIndex == 0) {
                renderCards(content, g, mouseX, mouseY, dt, open);
            } else {
                renderClientPage(content, g, mouseX, mouseY, dt, open);
            }
            renderFooter(g, content, mouseX, mouseY, open);
        }

        renderBindOverlay(g, open);

        if (pagePicker.active()) {
            pagePicker.render(g, winX, winY, winW, winH, open, mouseX, mouseY, dt);
        }
    }

    private void renderTopBar(GuiDraw g, int mouseX, int mouseY, double open) {
        double textY = winY + (topH - g.textHeight()) * 0.5 - 1;

        double logoX = winX + 18;
        g.fill(logoX, winY + (topH - 20) * 0.5, 20, 20, 6, alpha(ACCENT_SOFT, open));
        g.icon(GuiIcons.HOME, logoX + 3, winY + (topH - 14) * 0.5, 14, alpha(TEXT, open));

        double brandX = logoX + 28;
        g.text("yuio", brandX, textY, alpha(TEXT, open), false);
        g.text("v1.0", brandX + g.textWidth("yuio") + 7, textY + 1, alpha(TEXT_FAINT, open), false);

        tabHits.clear();
        renderSearch(g, mouseX, mouseY, open, searchBoxX(), searchBoxW());
        renderClose(g, mouseX, mouseY, open, closeCenterX());
    }

    private void renderClose(GuiDraw g, int mouseX, int mouseY, double open, double cx) {
        double cy = winY + topH * 0.5;
        boolean hover = mouseX >= cx - 10 && mouseX < cx + 10 && mouseY >= winY && mouseY < winY + topH;
        Color color = alpha(hover ? TEXT : TEXT_FAINT, open);
        for (int i = 0; i < 7; i++) {
            g.fill(cx - 3 + i, cy - 3 + i, 1.2, 1.2, 0, color);
            g.fill(cx + 3 - i, cy - 3 + i, 1.2, 1.2, 0, color);
        }
    }

    private void renderSearch(GuiDraw g, int mouseX, int mouseY, double open, double boxX, double boxW) {
        double boxH = 26;
        double boxY = winY + (topH - boxH) * 0.5;
        boolean hot = searchFocused;

        g.fill(boxX, boxY, boxW, boxH, boxH * 0.5, alpha(hot ? new Color(255, 255, 255, 24) : FIELD, open));

        double iconSize = 13;
        g.icon(GuiIcons.SEARCH, boxX + 11, boxY + (boxH - iconSize) * 0.5, iconSize, alpha(TEXT_DIM, open));

        String shown = query.isEmpty() ? "Search modules..." : query;
        Color color = query.isEmpty() ? alpha(TEXT_FAINT, open) : alpha(TEXT, open);
        double textX = boxX + 11 + iconSize + 8;
        g.textTrim(shown, textX, boxY + (boxH - g.textHeight()) * 0.5, boxX + boxW - textX - 10, color, false);

        if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
            double caretX = Math.min(boxX + boxW - 9, textX + g.textWidth(query) + 1);
            g.fill(caretX, boxY + 7, 1, boxH - 14, 0, alpha(TEXT, open));
        }
    }

    private double closeCenterX() {
        return winX + winW - 26;
    }

    private double searchBoxW() {
        return Math.max(120, Math.min(180, winW * 0.22));
    }

    private double searchBoxX() {
        return closeCenterX() - 16 - searchBoxW();
    }

    private void renderSidebar(GuiDraw g, int mouseX, int mouseY, double dt, double open) {
        navHits.clear();

        GuiDraw side = g.clip(sbX, sbY, sbW, sbH - 16);

        double headerH = 22;
        double entryH = 26;
        double y = sbY - sbScrollAnim();

        side.text("METEOR", sbX + 12, y + 7, alpha(TEXT_FAINT, open), false);
        y += headerH;

        List<Category> meteors = meteorCategories();
        for (int i = 0; i < meteors.size(); i++) {
            y = navEntry(side, meteors.get(i).name, iconOf(meteors.get(i)), i, mouseX, mouseY, y, entryH, open);
        }

        y += 8;
        side.text("ADDON", sbX + 12, y + 7, alpha(new Color(255, 96, 160), open), false);
        y += headerH;

        for (int i = 0; i < addonCategories.size(); i++) {
            y = navEntry(side, addonCategories.get(i).name, -1, meteors.size() + i, mouseX, mouseY, y, entryH, open);
        }

        y += 8;
        side.text("CLIENT", sbX + 12, y + 7, alpha(TEXT_FAINT, open), false);
        y += headerH;

        for (int i = 0; i < CLIENT_PAGES.length; i++) {
            y = navPage(side, CLIENT_PAGES[i], i, mouseX, mouseY, y, entryH, open);
        }

        double contentHeight = y - (sbY - sbScrollAnim());
        double max = Math.max(0, contentHeight - (sbH - 16));
        if (sbScrollTarget > max) sbScrollTarget = max;
        if (sbScrollTarget < 0) sbScrollTarget = 0;

        double hintY = winY + winH - 22;
        g.text("yuio v1.0", sbX + 12, hintY, alpha(TEXT_FAINT, open), false);
    }

    private double sbScrollAnim() {
        sbAnim.set(sbScrollTarget);
        sbAnim.update(lastDelta());
        return sbAnim.get();
    }

    private double lastDelta() {
        return Math.max(0.001, lastDt);
    }

    private final java.util.Map<Integer, Anim> sliderVis = new java.util.HashMap<>();

    private final java.util.Map<String, Anim> hoverAnims = new java.util.HashMap<>();

    private double hoverT(String key, boolean over) {
        Anim anim = hoverAnims.computeIfAbsent(key, k -> new Anim(0, 18));
        anim.set(over ? 1 : 0);
        anim.update(lastDelta());
        return anim.get();
    }

    private double navEntry(GuiDraw g, String name, int iconIndex, int catIndex, int mouseX, int mouseY,
                            double y, double height, double open) {
        boolean selected = pageIndex == 0 && catIndex == selectedCat && !page.isOpen();
        boolean hover = mouseX >= sbX && mouseX < sbX + sbW && mouseY >= y && mouseY < y + height;
        double hoverT = hoverT("nav:" + name, hover);

        if (selected) {
            g.fill(sbX, y, sbW, height, 7, alpha(new Color(255, 255, 255, 20), open));
            g.fill(sbX, y + 6, 2, height - 12, 1, alpha(ACCENT, open));
        }
        if (!selected && hoverT > 0.01) {
            g.fill(sbX, y, sbW, height, 7,
                alpha(new Color(255, 255, 255, (int) Math.round(14 * hoverT)), open));
        }

        double iconSize = 15;
        double textX = sbX + 16;
        double centerY = y + height * 0.5;

        if (iconIndex >= 0) {
            String icon = categoryIcon(iconIndex);
            if (icon != null) {
                g.icon(icon, sbX + 13, centerY - iconSize * 0.5, iconSize,
                    alpha(selected ? ACCENT : TEXT_DIM, open));
                textX = sbX + 13 + iconSize + 9;
            }
        } else {
            g.fill(sbX + 16, centerY - 1.6, 3.2, 3.2, 1.6, alpha(selected ? ACCENT : TEXT_FAINT, open));
            textX = sbX + 25;
        }

        Color color = selected ? alpha(TEXT, open) : alpha(hover ? TEXT : TEXT_DIM, open);
        g.text(name, textX, centerY - g.textHeight() * 0.5, color, false);

        navHits.add(new NavHit(sbX, y, sbW, height, catIndex));
        return y + height + 1;
    }

    private double navPage(GuiDraw g, String name, int index, int mouseX, int mouseY,
                           double y, double height, double open) {
        boolean selected = pageIndex == index + 1 && !page.isOpen();
        boolean hover = mouseX >= sbX && mouseX < sbX + sbW && mouseY >= y && mouseY < y + height;
        double hoverT = hoverT("page:" + name, hover);

        if (selected) {
            g.fill(sbX, y, sbW, height, 7, alpha(new Color(255, 255, 255, 20), open));
            g.fill(sbX, y + 6, 2, height - 12, 1, alpha(ACCENT, open));
        }
        if (!selected && hoverT > 0.01) {
            g.fill(sbX, y, sbW, height, 7,
                alpha(new Color(255, 255, 255, (int) Math.round(14 * hoverT)), open));
        }

        double centerY = y + height * 0.5;
        String icon = pageIcon(index);
        double iconSize = 15;
        double textX = sbX + 25;
        if (icon != null) {
            g.icon(icon, sbX + 13, centerY - iconSize * 0.5, iconSize, alpha(selected ? ACCENT : TEXT_DIM, open));
            textX = sbX + 13 + iconSize + 9;
        } else {
            g.fill(sbX + 16, centerY - 1.6, 3.2, 3.2, 1.6, alpha(selected ? ACCENT : TEXT_FAINT, open));
        }
        g.text(name, textX, centerY - g.textHeight() * 0.5,
            alpha(selected ? TEXT : (hover ? TEXT : TEXT_DIM), open), false);

        navHits.add(new NavHit(sbX, y, sbW, height, -(index + 2)));
        return y + height + 1;
    }

    private static String pageIcon(int index) {
        return switch (index) {
            case 0 -> GuiIcons.CONFIG;
            case 1 -> GuiIcons.GUI;
            case 2 -> GuiIcons.HUD;
            case 3 -> GuiIcons.FRIENDS;
            case 4 -> GuiIcons.MACROS;
            case 5 -> GuiIcons.PROFILES;
            default -> null;
        };
    }

    private void renderContentHeader(GuiDraw g, int mouseX, int mouseY, double open) {
        String title;
        String subtitle;

        if (searching && !query.isBlank()) {
            title = "Search";
            subtitle = currentModules().size() + " results";
        } else if (pageIndex == 0) {
            Category category = currentCategory();
            title = category != null ? category.name : "Modules";
            subtitle = currentModules().size() + " modules   -   "
                + (category != null && addonCategories.contains(category) ? "addon" : "meteor");
        } else {
            title = CLIENT_PAGES[pageIndex - 1];
            subtitle = pageSubtitle(pageIndex);
        }

        double y = sbY + 4;

        if (pageIndex == 0 && !searching) {
            Category category = currentCategory();
            int iconIndex = category != null ? iconOf(category) : -1;
            String icon = categoryIcon(iconIndex);
            if (icon != null) {
                g.icon(icon, areaX, y - 2, 17, alpha(ACCENT, open));
                g.text(title, areaX + 24, y, alpha(TEXT, open), false);
            } else {
                g.text(title, areaX, y, alpha(TEXT, open), false);
            }
        } else {
            g.text(title, areaX, y, alpha(TEXT, open), false);
        }

        g.text(subtitle, areaX + (pageIndex == 0 && !searching && categoryIcon(iconOf(currentCategory())) != null ? 24 : 0),
            y + g.textHeight() + 3, alpha(TEXT_FAINT, open), false);
    }

    private String pageSubtitle(int index) {
        return switch (index) {
            case 1 -> "client settings";
            case 2 -> "interface";
            case 3 -> "hud elements";
            case 4 -> Friends.get().count() + " friends";
            case 5 -> "key macros";
            case 6 -> "profiles";
            default -> "";
        };
    }

    private void renderFooter(GuiDraw g, GuiDraw content, int mouseX, int mouseY, double open) {
        double y = winY + winH - 20;
        String hint = "LMB toggle   RMB settings";
        g.textRight(hint, winX + winW - 18, y, alpha(TEXT_FAINT, open), false);

        if (pageIndex == 0 && !searching) {
            g.text(currentModules().size() + " items", areaX, y, alpha(TEXT_FAINT, open), false);
        }
    }

    private void renderCards(GuiDraw clipped, GuiDraw g, int mouseX, int mouseY, double dt, double open) {
        cardHits.clear();

        List<Module> modules = currentModules();
        int cols = cardColumns();
        double cardW = (areaW - CARD_GAP * (cols - 1)) / cols;
        double cardH = CARD_H * uiScale;
        int rowCount = (modules.size() + cols - 1) / cols;
        double contentH = rowCount <= 0 ? 0 : rowCount * (cardH + CARD_GAP) - CARD_GAP;
        double maxScroll = Math.max(0, contentH - areaH);

        if (scrollTarget > maxScroll) scrollTarget = maxScroll;
        if (scrollTarget < 0) scrollTarget = 0;
        scrollAnim.set(scrollTarget);
        scrollAnim.update(dt);
        double scroll = scrollAnim.get();

        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            int col = i % cols;
            int row = i / cols;
            double x = areaX + col * (cardW + CARD_GAP);
            double y = areaY + row * (cardH + CARD_GAP) - scroll;
            if (y + cardH < areaY - 2 || y > areaY + areaH + 2) continue;

            CardState state = cards.computeIfAbsent(module, m -> new CardState());
            IosSwitch sw = switches.computeIfAbsent(module, m -> new IosSwitch(m.isActive()));

            boolean hover = mouseX >= x && mouseX < x + cardW && mouseY >= y && mouseY < y + cardH
                && mouseY >= areaY && mouseY < areaY + areaH;
            state.hover.set(hover ? 1 : 0);
            state.hover.update(dt);

            boolean active = module.isActive();
            sw.update(active, dt);

            double hoverT = state.hover.get();
            double a = open;

            Color base = mix(active ? CARD_ACTIVE : CARD, CARD_HOVER, hoverT);
            boolean addon = addonCategories.contains(module.category);
            clipped.fill(x, y, cardW, cardH, 9, alpha(base, a));

            if (addon) {
                clipped.fill(x, y, cardW, cardH, 9, alpha(new Color(255, 96, 160, 12 + (int) Math.round(10 * hoverT)), a));
            }

            if (hoverT > 0.01) {
                clipped.fill(x, y, cardW, cardH, 9, alpha(new Color(255, 255, 255, (int) Math.round(6 * hoverT)), a));
            }

            double swH = 14 * uiScale;
            double swW = IosSwitch.width(swH);
            double swX = x + cardW - 16 - swW;
            double swY = y + (cardH - swH) * 0.5;
            boolean swHover = mouseX >= swX - 2 && mouseX < swX + swW + 2 && mouseY >= swY - 3 && mouseY < swY + swH + 3;
            sw.updateHover(swHover, dt);
            sw.render(clipped, swX, swY, swH, a);

            String keyText = module.keybind.isSet() ? module.keybind.toString() : "";
            double chipW = Math.max(20, Math.min(46, g.textWidth(keyText) + 12));
            double chipH = 18 * uiScale;
            double chipX = swX - 8 - 14 - 8 - chipW;
            double chipY = y + (cardH - chipH) * 0.5;
            boolean chipHover = mouseX >= chipX && mouseX < chipX + chipW && mouseY >= chipY && mouseY < chipY + chipH;

            clipped.fill(chipX, chipY, chipW, chipH, 5,
                alpha(new Color(255, 255, 255, chipHover ? 30 : 14), a));
            clipped.textCentered(keyText, chipX + chipW * 0.5, chipY + (chipH - g.textHeight()) * 0.5,
                alpha(chipHover ? TEXT : TEXT_DIM, a), false);

            double gearSize = 14 * uiScale;
            double gearX = swX - 8 - gearSize;
            double gearY = y + (cardH - gearSize) * 0.5;
            boolean gearHover = mouseX >= gearX - 2 && mouseX < gearX + gearSize + 2 && mouseY >= gearY - 2 && mouseY < gearY + gearSize + 2;
            if (gearHover) {
                clipped.fill(gearX - 3, gearY - 3, gearSize + 6, gearSize + 6, 5,
                    alpha(new Color(255, 255, 255, 22), a));
            }
            clipped.icon(GuiIcons.GEAR, gearX, gearY, gearSize, alpha(gearHover ? TEXT : TEXT_DIM, a));

            double textRight = chipX - 12;
            double nameX = x + 13;
            if (addon) {
                clipped.fill(nameX, y + 13 * uiScale + g.textHeight() * 0.42, 4, 4, 2,
                    alpha(new Color(255, 96, 160), a));
                nameX += 9;
            }
            clipped.textTrim(module.name, nameX, y + 12 * uiScale, textRight - nameX, alpha(TEXT, a), false);
            clipped.textTrim(module.description, x + 13, y + 31 * uiScale, textRight - x - 13, alpha(TEXT_FAINT, a), false);

            cardHits.add(new CardHit(module, x, y, cardW, cardH, chipX, chipY, chipW, chipH,
                gearX, gearY, gearSize, swX, swY, swW, swH));
        }
    }

    private int cardColumns() {
        return areaW >= 430 ? 2 : 1;
    }

    // ----------

    private void renderClientPage(GuiDraw content, GuiDraw g, int mouseX, int mouseY, double dt, double open) {
        rows.clear();
        buildPageRows(g, mouseX, mouseY, open);

        double total = 0;
        for (Row row : rows) total += row.h + 4;

        double maxScroll = Math.max(0, total - areaH + 6);
        if (scrollTarget > maxScroll) scrollTarget = maxScroll;
        if (scrollTarget < 0) scrollTarget = 0;
        scrollAnim.set(scrollTarget);
        scrollAnim.update(dt);

        double scroll = scrollAnim.get();
        double y = areaY + 2 - scroll;

        for (Row row : rows) {
            row.x = areaX;
            row.y = y;
            row.w = areaW;
            y += row.h + 4;

            if (row.y + row.h < areaY - 2 || row.y > areaY + areaH + 2) continue;
            drawRow(content, row, mouseX, mouseY, open);
        }
    }

    private double buildPageRows(GuiDraw g, int mouseX, int mouseY, double open) {
        double y = 0;

        switch (pageIndex) {
            case 1 -> y = configRows(y);
            case 2 -> y = guiRows(y);
            case 3 -> y = hudRows(y);
            case 4 -> y = friendRows(y);
            case 5 -> y = macroRows(y);
            case 6 -> y = profileRows(y);
            default -> {
            }
        }

        return y + (areaY + 6 - areaY) + 6;
    }

    private double configRows(double y) {
        rows.add(Row.action("Save config", () -> Config.get().save(), y));
        y += 34;
        rows.add(Row.action("Reload config", () -> Config.get().load(), y));
        y += 34;
        rows.add(Row.action("Save & reload", () -> {
            Config.get().save();
            Config.get().load();
        }, y));
        y += 34;
        rows.add(Row.label("Meteor config file: " + Config.get().getFile().getName(), y));
        y += 26;
        return y;
    }

    private double guiRows(double y) {
        GuiTheme current = GuiThemes.get();
        rows.add(Row.chip("Theme: " + (current != null ? current.name : "?"), y,
            () -> {
                java.util.List<String> names = new ArrayList<>();
                java.util.Collections.addAll(names, GuiThemes.getNames());
                String active = GuiThemes.get() != null ? GuiThemes.get().name : "";
                pagePicker.openValues("Theme", names, active, name -> {
                    GuiThemes.select(name);
                    if (name == null || com.example.addon.gui.Yuio2GuiTheme.NAME.equalsIgnoreCase(name)) return;
                    close();
                    meteordevelopment.meteorclient.gui.tabs.Tabs.get().getFirst()
                        .openScreen(GuiThemes.get());
                });
            }));
        y += 34;

        rows.add(Row.slider("Window scale", y, 0.8, 1.4, () -> uiScale, v -> uiScale = v));
        y += 34;
        rows.add(Row.slider("Panel opacity", y, 0.5, 1.0, () -> uiOpacity, v -> uiOpacity = v));
        y += 34;
        rows.add(Row.slider("Animation speed", y, 0.4, 2.0, () -> uiAnim, v -> uiAnim = v));
        y += 34;
        rows.add(Row.toggle("Blur glass", y, () -> uiGlass, v -> uiGlass = v));
        y += 34;
        rows.add(Row.label("Middle click top bar: drag window   Scroll: resize", y));
        y += 26;
        return y;
    }

    private double hudRows(double y) {
        for (HudElement element : Hud.get()) {
            Row row = Row.toggle(element.info.name, y, element::isActive, v -> element.toggle());
            row.hud = element;
            rows.add(row);
            y += 32;
        }
        return y;
    }

    private double friendRows(double y) {
        rows.add(Row.field(addingFriend ? friendBuffer : "Add friend...", y, () -> {
            addingFriend = true;
            friendBuffer = "";
        }));
        y += 36;

        for (Friend friend : Friends.get()) {
            rows.add(Row.rowWithButton(friend.getName(), "Remove", y, () -> {
                Friends.get().remove(friend);
                Friends.get().save();
            }));
            y += 34;
        }
        return y;
    }

    private double macroRows(double y) {
        for (Macro macro : Macros.get()) {
            String key = macro.keybind.get().isSet() ? macro.keybind.get().toString() : "-";
            rows.add(Row.rowWithButton(macro.name.get() + "   [" + key + "]", "Remove", y, () -> {
                Macros.get().remove(macro);
                Macros.get().save();
            }));
            y += 34;
        }
        return y;
    }

    private double profileRows(double y) {
        for (Profile profile : Profiles.get()) {
            rows.add(Row.rowWithButton(profile.name.get(), "Load", y, () -> {
                profile.load();
                close();
            }));
            y += 34;
        }
        return y;
    }

    private void drawRow(GuiDraw g, Row row, int mouseX, int mouseY, double open) {
        double y = row.y;
        double w = row.w;
        boolean hover = mouseX >= row.x && mouseX < row.x + w && mouseY >= y && mouseY < y + row.h - 4
            && mouseY >= areaY && mouseY < areaY + areaH;
        double hoverT = hoverT("row:" + row.kind + ":" + row.text, hover);

        if (hoverT > 0.01 && row.kind != Row.LABEL) {
            g.fill(row.x, y, w, row.h - 4, 7,
                alpha(new Color(255, 255, 255, (int) Math.round(14 * hoverT)), open));
        }

        switch (row.kind) {
            case Row.ACTION, Row.CHIP, Row.FIELD -> {
                g.text(row.text, row.x + 12, y + (row.h - 4 - g.textHeight()) * 0.5, alpha(TEXT, open), false);
                if (row.kind == Row.FIELD) {
                    g.textRight("RETURN", row.x + w - 12, y + (row.h - 4 - g.textHeight()) * 0.5, alpha(TEXT_FAINT, open), false);
                }
            }
            case Row.LABEL -> g.text(row.text, row.x + 4, y + (row.h - 4 - g.textHeight()) * 0.5, alpha(TEXT_FAINT, open), false);
            case Row.TOGGLE -> {
                g.text(row.text, row.x + 12, y + (row.h - 4 - g.textHeight()) * 0.5, alpha(TEXT, open), false);
                IosSwitch sw = row.switchAnim;
                sw.update(row.bool.getAsBoolean(), lastDelta());
                sw.render(g, row.x + w - 16 - IosSwitch.width(14), y + (row.h - 4 - 14) * 0.5, 14, open);
            }
            case Row.SLIDER -> {
                g.text(row.text, row.x + 12, y + (row.h - 4 - g.textHeight()) * 0.5, alpha(TEXT, open), false);
                double value = row.value.getAsDouble();
                double t = (value - row.min) / (row.max - row.min);
                Anim vis = sliderVis.computeIfAbsent(row.sliderId, k -> new Anim(t, 18));
                vis.set(t);
                vis.update(lastDelta());
                double shown = vis.get();
                double trackW = 130;
                double trackX = row.x + w - 16 - trackW;
                double trackY = y + (row.h - 4) * 0.5;
                g.fill(trackX, trackY - 1, trackW, 2.5, 1.2, alpha(TRACK, open));
                g.fill(trackX, trackY - 1, trackW * shown, 2.5, 1.2, alpha(ACCENT, open));
                g.fill(trackX + trackW * shown - 4.5, trackY - 4.5, 9, 9, 4.5, alpha(KNOB, open));
                String valueText = String.format(Locale.ROOT, "%.2f", value);
                g.textRight(valueText, trackX - 10, y + (row.h - 4 - g.textHeight()) * 0.5, alpha(TEXT_DIM, open), false);
            }
            case Row.BUTTON -> {
                g.text(row.text, row.x + 12, y + (row.h - 4 - g.textHeight()) * 0.5, alpha(TEXT, open), false);
                double bw = g.textWidth("Remove") + 18;
                double bx = row.x + w - 12 - bw;
                g.fill(bx, y + (row.h - 4 - 18) * 0.5, bw, 18, 5, alpha(new Color(255, 255, 255, hover ? 26 : 14), open));
                g.textCentered("Remove", bx + bw * 0.5, y + (row.h - 4 - g.textHeight()) * 0.5, alpha(TEXT_DIM, open), false);
            }
            default -> {
            }
        }
    }

    private void renderBindOverlay(GuiDraw g, double open) {
        double alphaMul = ease(bindAnim.get());
        if (alphaMul <= 0.01 || binding == null) return;

        g.fill(areaX, areaY - headH, areaW, areaH + headH, 12,
            new Color(8, 10, 14, (int) Math.round(216 * alphaMul * open)));

        String text = "Please press the hotkey you want to bind:";
        double centerY = areaY + areaH * 0.5 - 12;
        g.text(text, areaX + (areaW - g.textWidth(text)) * 0.5, centerY, alpha(TEXT, open * alphaMul), false);

        String name = binding.name;
        g.text(name, areaX + (areaW - g.textWidth(name)) * 0.5, centerY + 20, alpha(ACCENT, open * alphaMul), false);
    }

    // ----------

    List<Category> meteorCategories() {
        List<Category> list = new ArrayList<>();
        for (Category category : Modules.loopCategories()) {
            if (addonCategories.contains(category)) continue;
            list.add(category);
        }
        return list;
    }

    private List<Category> allCategories() {
        List<Category> list = new ArrayList<>(meteorCategories());
        list.addAll(addonCategories);
        return list;
    }

    private Category currentCategory() {
        List<Category> list = allCategories();
        if (list.isEmpty()) return null;
        if (selectedCat < 0) selectedCat = 0;
        if (selectedCat >= list.size()) selectedCat = list.size() - 1;
        return list.get(selectedCat);
    }

    private List<Module> currentModules() {
        if (searching && !query.isBlank()) {
            String q = query.trim().toLowerCase(Locale.ROOT);
            List<Module> result = new ArrayList<>();
            for (Module module : Modules.get().getAll()) {
                if (module.name.toLowerCase(Locale.ROOT).contains(q)
                    || module.description.toLowerCase(Locale.ROOT).contains(q)) {
                    result.add(module);
                    if (result.size() >= 80) break;
                }
            }
            return result;
        }

        Category category = currentCategory();
        if (category == null) return List.of();
        return Modules.get().getGroup(category);
    }

    private static String categoryIcon(int index) {
        return switch (index) {
            case 0 -> GuiIcons.CAT_COMBAT;
            case 1 -> GuiIcons.CAT_PLAYER;
            case 2 -> GuiIcons.CAT_MOVEMENT;
            case 3 -> GuiIcons.CAT_RENDER;
            case 4 -> GuiIcons.CAT_WORLD;
            case 5 -> GuiIcons.CAT_MISC;
            default -> null;
        };
    }

    private static int iconOf(Category category) {
        return switch (category.name) {
            case "Combat" -> 0;
            case "Player" -> 1;
            case "Movement" -> 2;
            case "Render" -> 3;
            case "World" -> 4;
            case "Misc" -> 5;
            default -> -1;
        };
    }

    // ----------

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x();
        double my = click.y();
        int button = click.button();
        mouseDown = true;

        if (pagePicker.active()) return pagePicker.mouseClicked(mx, my, button);

        if (binding != null) {
            binding = null;
            return true;
        }

        double closeCx = closeCenterX();
        if (mx >= closeCx - 10 && mx < closeCx + 10 && my >= winY && my < winY + topH) {
            close();
            return true;
        }

        if (mx < winX || mx > winX + winW || my < winY || my > winY + winH) {
            close();
            return true;
        }

        for (TabHit hit : tabHits) {
            if (hit.contains(mx, my)) {
                pageIndex = hit.index;
                page.close();
                searching = false;
                searchFocused = false;
                scrollTarget = 0;
                scrollAnim.reset(0);
                return true;
            }
        }

        if (inSearchBox(mx, my)) {
            searchFocused = true;
            searching = true;
            return true;
        }

        if (my < winY + topH) {
            draggingWindow = true;
            dragStartX = mx;
            dragStartY = my;
            dragBaseX = winOffsetX;
            dragBaseY = winOffsetY;
            return true;
        }

        if (page.isOpen() && page.mouseClicked(mx, my, button)) {
            return true;
        }

        for (NavHit entry : navHits) {
            if (!entry.contains(mx, my)) continue;

            page.close();
            searching = false;
            searchFocused = false;
            query = "";
            scrollTarget = 0;
            scrollAnim.reset(0);

            if (entry.index >= 0) {
                selectedCat = entry.index;
                pageIndex = 0;
            } else {
                pageIndex = -entry.index - 1;
            }
            return true;
        }

        if (page.isOpen()) return true;

        if (pageIndex == 0) {
            for (CardHit hit : cardHits) {
                if (!hit.contains(mx, my)) continue;

                if (hit.inSwitch(mx, my)) {
                    hit.module.toggle();
                    return true;
                }
                if (hit.inChip(mx, my)) {
                    binding = hit.module;
                    return true;
                }
                if (hit.inGear(mx, my)) {
                    page.open(hit.module);
                    return true;
                }
                if (button == 1) {
                    page.open(hit.module);
                    return true;
                }
                hit.module.toggle();
                return true;
            }
        } else {
            for (Row row : rows) {
                if (!row.contains(mx, my)) continue;
                handleRowClick(row, mx, my);
                return true;
            }
        }

        return super.mouseClicked(click, doubled);
    }

    private void handleRowClick(Row row, double mx, double my) {
        switch (row.kind) {
            case Row.ACTION -> row.action.run();
            case Row.CHIP -> {
                pagePicker.at(row.x, row.y, row.w, row.h);
                row.action.run();
            }
            case Row.TOGGLE -> {
                if (row.hud != null) {
                    double swW = IosSwitch.width(14);
                    double swX = row.x + row.w - 16 - swW;
                    double swY = row.y + (row.h - 4 - 14) * 0.5;
                    if (mx < swX - 4 || mx > swX + swW + 4 || my < swY - 4 || my > swY + 14 + 4) {
                        page.openHud(row.hud);
                        return;
                    }
                }
                if (row.setBool != null) row.setBool.accept(!row.bool.getAsBoolean());
                else row.action.run();
            }
            case Row.SLIDER -> {
                draggingSlider = row.sliderId;
                applySlider(row, mx);
            }
            case Row.FIELD -> {
                addingFriend = true;
                friendBuffer = "";
            }
            case Row.BUTTON -> row.action.run();
            default -> {
            }
        }
    }

    private void applySlider(Row row, double mx) {
        double trackW = 130;
        double trackX = row.x + row.w - 16 - trackW;
        double t = Math.max(0, Math.min(1, (mx - trackX) / trackW));
        row.setValue.accept(row.min + (row.max - row.min) * t);
    }

    private boolean inSearchBox(double mx, double my) {
        double boxH = 26;
        double boxY = winY + (topH - boxH) * 0.5;
        double boxX = searchBoxX();
        return mx >= boxX && mx < boxX + searchBoxW() && my >= boxY && my < boxY + boxH;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        boolean overTop = my >= winY && my < winY + topH;

        if (pagePicker.active()) return pagePicker.mouseScrolled(vertical);

        if (overTop && mouseDown) {
            uiScale = Math.max(0.8, Math.min(1.4, uiScale + vertical * 0.04));
            return true;
        }

        if (page.isOpen()) {
            if (page.mouseScrolled(vertical)) return true;
        }

        if (mx >= sbX && mx < sbX + sbW && my >= sbY && my < sbY + sbH) {
            sbScrollTarget -= vertical * 28;
            return true;
        }

        if (mx >= areaX && mx < areaX + areaW && my >= areaY && my < areaY + areaH) {
            scrollTarget -= vertical * 48;
            double max = Math.max(0, contentHeight() - areaH);
            scrollTarget = Math.max(0, Math.min(max, scrollTarget));
            return true;
        }

        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    private double contentHeight() {
        if (pageIndex == 0) {
            List<Module> modules = currentModules();
            int cols = cardColumns();
            int rowCount = (modules.size() + cols - 1) / cols;
            double cardH = CARD_H * uiScale;
            return rowCount <= 0 ? 0 : rowCount * (cardH + CARD_GAP) - CARD_GAP;
        }

        double h = 0;
        for (Row row : rows) h += row.h + 4;
        return h;
    }
    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        double mx = click.x();
        double my = click.y();

        if (pagePicker.active()) return pagePicker.mouseDragged(mx, my);

        if (draggingWindow) {
            winOffsetX = dragBaseX + (mx - dragStartX);
            winOffsetY = dragBaseY + (my - dragStartY);
            return true;
        }

        if (draggingSlider >= 0) {
            for (Row row : rows) {
                if (row.kind == Row.SLIDER && row.sliderId == draggingSlider) {
                    applySlider(row, mx);
                    break;
                }
            }
            return true;
        }

        if (page.isOpen() && page.mouseDragged(mx, my)) return true;
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        mouseDown = false;
        draggingWindow = false;
        draggingSlider = -1;
        if (pagePicker.active()) return pagePicker.mouseReleased();
        if (page.isOpen() && page.mouseReleased()) return true;
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (pagePicker.active()) return pagePicker.keyPressed(input);
        if (binding != null) {
            if (input.isEscape()) {
                binding = null;
                return true;
            }
            int key = input.key();
            if (key != GLFW.GLFW_KEY_UNKNOWN) {
                binding.keybind.set(true, key, 0);
                binding = null;
            }
            return true;
        }

        if (addingFriend) {
            if (input.isEscape()) {
                addingFriend = false;
                friendBuffer = "";
                return true;
            }
            if (input.isEnter()) {
                String name = friendBuffer.trim();
                if (!name.isEmpty()) {
                    Friends.get().add(new Friend(name));
                    Friends.get().save();
                }
                addingFriend = false;
                friendBuffer = "";
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (!friendBuffer.isEmpty()) friendBuffer = friendBuffer.substring(0, friendBuffer.length() - 1);
                return true;
            }
        }

        if (page.isOpen() && page.keyPressed(input)) return true;

        if (searchFocused) {
            if (input.isEscape()) {
                searchFocused = false;
                if (query.isBlank()) searching = false;
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (!query.isEmpty()) query = query.substring(0, query.length() - 1);
                return true;
            }
            if (input.isEnter()) {
                searchFocused = false;
                return true;
            }
        }

        if (input.isEscape()) {
            if (searching) {
                searching = false;
                query = "";
                searchFocused = false;
                return true;
            }
            if (page.isOpen()) {
                page.close();
                return true;
            }
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (addingFriend && input.isValidChar()) {
            friendBuffer += input.asString();
            return true;
        }

        if (pagePicker.active()) return pagePicker.charTyped(input);

        if (page.isOpen() && page.charTyped(input)) return true;

        if (searchFocused && input.isValidChar()) {
            query += input.asString();
            searching = true;
            return true;
        }

        return super.charTyped(input);
    }

    // ---------- 鐎规悶鍎遍崣?----------

    static double ease(double value) {
        double t = Math.max(0, Math.min(1, value));
        return 1 - Math.pow(1 - t, 3);
    }

    static Color alpha(Color color, double factor) {
        int a = (int) Math.round(color.a * Math.max(0, Math.min(1, factor)));
        return new Color(color.r, color.g, color.b, a);
    }

    static Color mix(Color a, Color b, double factor) {
        double f = Math.max(0, Math.min(1, factor));
        return new Color(
            (int) Math.round(a.r + (b.r - a.r) * f),
            (int) Math.round(a.g + (b.g - a.g) * f),
            (int) Math.round(a.b + (b.b - a.b) * f),
            (int) Math.round(a.a + (b.a - a.a) * f));
    }

    Module settingsModule() {
        return page.module();
    }

    double areaX() { return areaX; }
    double areaY() { return areaY; }
    double areaW() { return areaW; }
    double areaH() { return areaH; }
    void setScrollTarget(double value) { scrollTarget = value; }

    // ----------

    private static final class NavHit {
        final double x, y, w, h;
        final int index;

        NavHit(double x, double y, double w, double h, int index) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.index = index;
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private static final class TabHit {
        final double x, y, w, h;
        final int index;

        TabHit(double x, double y, double w, double h, int index) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.index = index;
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private static final class CardHit {
        final Module module;
        final double x, y, w, h;
        final double chipX, chipY, chipW, chipH;
        final double gearX, gearY, gearSize;
        final double swX, swY, swW, swH;

        CardHit(Module module, double x, double y, double w, double h,
                double chipX, double chipY, double chipW, double chipH,
                double gearX, double gearY, double gearSize,
                double swX, double swY, double swW, double swH) {
            this.module = module;
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.chipX = chipX; this.chipY = chipY; this.chipW = chipW; this.chipH = chipH;
            this.gearX = gearX; this.gearY = gearY; this.gearSize = gearSize;
            this.swX = swX; this.swY = swY; this.swW = swW; this.swH = swH;
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        boolean inChip(double mx, double my) {
            return mx >= chipX && mx < chipX + chipW && my >= chipY && my < chipY + chipH;
        }

        boolean inGear(double mx, double my) {
            return mx >= gearX - 2 && mx < gearX + gearSize + 2 && my >= gearY - 2 && my < gearY + gearSize + 2;
        }

        boolean inSwitch(double mx, double my) {
            return mx >= swX - 2 && mx < swX + swW + 2 && my >= swY - 3 && my < swY + swH + 3;
        }
    }

    private static final class CardState {
        final Anim hover = new Anim(0, 16);
    }

    static final class Row {
        static final int ACTION = 0;
        static final int CHIP = 1;
        static final int TOGGLE = 2;
        static final int SLIDER = 3;
        static final int LABEL = 4;
        static final int BUTTON = 5;
        static final int FIELD = 6;

        static int nextSliderId = 1;

        final int kind;
        double x, y, w, h;
        String text;
        Runnable action;
        BooleanSupplier bool;
        java.util.function.Consumer<Boolean> setBool;
        DoubleSupplier value;
        DoubleConsumer setValue;
        double min, max;
        int sliderId;
        final IosSwitch switchAnim;
        meteordevelopment.meteorclient.systems.hud.HudElement hud;
        static final java.util.Map<String, IosSwitch> SWITCHES = new java.util.HashMap<>();

        private Row(int kind, String text) {
            this.kind = kind;
            this.text = text;
            this.h = 30;
            this.switchAnim = SWITCHES.computeIfAbsent(kind + ":" + text, k -> new IosSwitch(false));
        }

        static Row action(String text, Runnable action, double y) {
            Row row = new Row(ACTION, text);
            row.action = action;
            row.y = y;
            return row;
        }

        static Row chip(String text, double y, Runnable action) {
            Row row = new Row(CHIP, text);
            row.action = action;
            row.y = y;
            return row;
        }

        static Row label(String text, double y) {
            Row row = new Row(LABEL, text);
            row.y = y;
            row.h = 22;
            return row;
        }

        static Row toggle(String text, double y, BooleanSupplier bool, java.util.function.Consumer<Boolean> setBool) {
            Row row = new Row(TOGGLE, text);
            row.bool = bool;
            row.setBool = setBool;
            row.y = y;
            row.h = 28;
            return row;
        }

        static Row slider(String text, double y, double min, double max, DoubleSupplier value, DoubleConsumer setValue) {
            Row row = new Row(SLIDER, text);
            row.min = min;
            row.max = max;
            row.value = value;
            row.setValue = setValue;
            row.sliderId = text.hashCode();
            row.y = y;
            row.h = 30;
            return row;
        }

        static Row rowWithButton(String text, String label, double y, Runnable action) {
            Row row = new Row(BUTTON, text);
            row.action = action;
            row.y = y;
            row.h = 30;
            return row;
        }

        static Row field(String text, double y, Runnable action) {
            Row row = new Row(FIELD, text);
            row.action = action;
            row.y = y;
            row.h = 32;
            return row;
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }
}
