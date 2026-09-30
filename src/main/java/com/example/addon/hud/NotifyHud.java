package com.example.addon.hud;

import com.example.addon.AddonTemplate;
import com.example.addon.utils.Assets;
import com.example.addon.utils.RoundRectRenderer;
import com.mojang.blaze3d.textures.GpuTextureView;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.meteor.ActiveModulesChangedEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.listeners.ConsumerListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class NotifyHud extends HudElement {
    public static final HudElementInfo<NotifyHud> INFO = new HudElementInfo<>(
        AddonTemplate.HUD_GROUP, "notify-hud", "Rounded notification cards, text wraps automatically.", NotifyHud::new);

    private static final int KIND_TITLE = 0;
    private static final int KIND_ON = 1;
    private static final int KIND_OFF = 2;
    private static final int KIND_INFO = 3;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum Style {
        A("style a", "notify/background_a.png"),
        B("style b", "notify/background_b.png");

        private final String label;
        public final String path;

        Style(String label, String path) {
            this.label = label;
            this.path = path;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final Setting<Style> style = sgGeneral.add(new EnumSetting.Builder<Style>()
        .name("style").description("Card background. Files live in <game folder>/meteor/yuio/notify/ and can be replaced.")
        .defaultValue(Style.A).build());

    private final Setting<String> titleFormat = sgGeneral.add(new StringSetting.Builder()
        .name("title-format")
        .description("Placeholders: %module% %state% %desc% %count% %category%")
        .defaultValue("%module%")
        .build());

    private final Setting<String> textFormat = sgGeneral.add(new StringSetting.Builder()
        .name("text-format")
        .description("Placeholders: %module% %state% %desc% %count% %category%. Only %state% is tinted by on/off color.")
        .defaultValue("%state%  (%count% active)")
        .build());

    private final Setting<String> infoFormat = sgGeneral.add(new StringSetting.Builder()
        .name("info-format")
        .description("Layout used by plain push() messages. Placeholders: %title% %text%")
        .defaultValue("%text%")
        .build());

    private final Setting<Integer> cardWidth = sgGeneral.add(new IntSetting.Builder()
        .name("card-width").defaultValue(230).min(80).sliderRange(80, 480).build());

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale").defaultValue(1).min(0.5).sliderRange(0.5, 2).build());

    private final Setting<Double> textScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("text-scale").defaultValue(1).min(0.5).sliderRange(0.5, 2).build());

    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("radius").defaultValue(12).min(0).sliderRange(0, 30).build());

    private final Setting<Integer> backgroundAlpha = sgGeneral.add(new IntSetting.Builder()
        .name("background-alpha").defaultValue(190).min(0).sliderRange(0, 255).build());

    private final Setting<Integer> duration = sgGeneral.add(new IntSetting.Builder()
        .name("duration").defaultValue(4000).min(500).sliderRange(500, 20000).build());

    private final Setting<Integer> maxCards = sgGeneral.add(new IntSetting.Builder()
        .name("max-cards").description("Repeated notifications reuse their own card instead of stacking.")
        .defaultValue(4).min(1).sliderRange(1, 10).build());

    private final Setting<Double> gap = sgGeneral.add(new DoubleSetting.Builder()
        .name("gap").defaultValue(6).min(0).sliderRange(0, 30).build());

    private final Setting<Boolean> multiline = sgGeneral.add(new BoolSetting.Builder()
        .name("wrap-text").description("Wrap long text instead of cutting it off.")
        .defaultValue(true).build());

    private final Setting<Boolean> showIcon = sgGeneral.add(new BoolSetting.Builder()
        .name("icon").defaultValue(true).build());

    private final Setting<Boolean> showProgress = sgGeneral.add(new BoolSetting.Builder()
        .name("progress-bar").defaultValue(true).build());

    private final Setting<Boolean> showTime = sgGeneral.add(new BoolSetting.Builder()
        .name("timestamp").defaultValue(true).build());

    private final Setting<Boolean> moduleNotifs = sgGeneral.add(new BoolSetting.Builder()
        .name("module-notifications").defaultValue(true).build());

    private final Setting<SettingColor> titleColor = sgGeneral.add(new ColorSetting.Builder()
        .name("title-color").defaultValue(new SettingColor(255, 255, 255)).build());

    private final Setting<SettingColor> onColor = sgGeneral.add(new ColorSetting.Builder()
        .name("on-color").description("Only the %state% word turns this colour.")
        .defaultValue(new SettingColor(97, 222, 134)).build());

    private final Setting<SettingColor> offColor = sgGeneral.add(new ColorSetting.Builder()
        .name("off-color").description("Only the %state% word turns this colour.")
        .defaultValue(new SettingColor(240, 100, 100)).build());

    private final Setting<SettingColor> infoColor = sgGeneral.add(new ColorSetting.Builder()
        .name("info-color").defaultValue(new SettingColor(205, 210, 220)).build());

    private final Setting<SettingColor> timeColor = sgGeneral.add(new ColorSetting.Builder()
        .name("time-color").defaultValue(new SettingColor(165, 172, 185)).build());

    private final Setting<SettingColor> barColor = sgGeneral.add(new ColorSetting.Builder()
        .name("bar-color").defaultValue(new SettingColor(255, 255, 255, 200)).build());

    private static final class Token {
        final String text;
        final int kind;

        Token(String text, int kind) {
            this.text = text;
            this.kind = kind;
        }
    }

    private static final class Card {
        final String key;
        List<Token> title;
        List<Token> body;
        Color accent;
        long born;
        long life;
        final Anim fade = new Anim(0, 10);
        final Anim slide = new Anim(12, 11);

        Card(String key, List<Token> title, List<Token> body, Color accent, long life) {
            this.key = key;
            this.title = title;
            this.body = body;
            this.accent = accent;
            this.born = System.currentTimeMillis();
            this.life = life;
        }
    }

    private static final Queue<Card> INCOMING = new ConcurrentLinkedQueue<>();

    private final List<Card> cards = new ArrayList<>();
    private final List<Module> lastActive = new ArrayList<>();

    private final ConsumerListener<ActiveModulesChangedEvent> modulesListener =
        new ConsumerListener<>(ActiveModulesChangedEvent.class, event -> {
            if (!moduleNotifs.get()) return;

            List<Module> active = Modules.get().getActive();
            long life = duration.get();
            String count = String.valueOf(active.size());

            for (Module module : active) {
                if (!lastActive.contains(module)) offer(module, "Enabled", KIND_ON, count, life);
            }

            for (Module module : lastActive) {
                if (!active.contains(module)) offer(module, "Disabled", KIND_OFF, count, life);
            }

            lastActive.clear();
            lastActive.addAll(active);
        });

    private void offer(Module module, String state, int kind, String count, long life) {
        String category = module.category == null ? "" : module.category.name;
        List<Token> title = tokens(titleFormat.get(), module.title, state, kind, module.description, count, category, module.title);
        List<Token> body = tokens(textFormat.get(), module.title, state, kind, module.description, count, category, module.title);

        push(module.name, title, body, kind == KIND_ON ? new Color(onColor.get().r, onColor.get().g, onColor.get().b) : new Color(offColor.get().r, offColor.get().g, offColor.get().b), life);
    }

    public NotifyHud() {
        super(INFO);
        MeteorClient.EVENT_BUS.subscribe(modulesListener);
        lastActive.addAll(Modules.get().getActive());
    }

    @Override
    public void remove() {
        MeteorClient.EVENT_BUS.unsubscribe(modulesListener);
        super.remove();
    }

    public static void push(String key, String title, String text, long life) {
        push(key,
            List.of(new Token(title, KIND_TITLE)),
            tokens("%text%", title, "", KIND_INFO, "", "", "", text),
            new Color(190, 200, 215),
            life);
    }

    private static void push(String key, List<Token> title, List<Token> body, Color accent, long life) {
        INCOMING.add(new Card(key, title, body, accent, life));
    }

    private static List<Token> tokens(String format, String module, String state, int stateKind,
                                      String desc, String count, String category, String text) {

        List<Token> out = new ArrayList<>();
        String source = format == null ? "" : format;
        int index = 0;

        while (index < source.length()) {
            int at = source.indexOf('%', index);

            if (at < 0) {
                addWords(out, source.substring(index), KIND_INFO);
                break;
            }

            if (at > index) addWords(out, source.substring(index, at), KIND_INFO);

            int end = source.indexOf('%', at + 1);

            if (end < 0) {
                addWords(out, source.substring(at), KIND_INFO);
                break;
            }

            String name = source.substring(at + 1, end).toLowerCase(Locale.ROOT);

            switch (name) {
                case "module" -> addWords(out, module, KIND_TITLE);
                case "state" -> addWords(out, state, stateKind);
                case "desc" -> addWords(out, desc, KIND_INFO);
                case "count" -> addWords(out, count, KIND_INFO);
                case "category" -> addWords(out, category, KIND_INFO);
                case "title" -> addWords(out, module, KIND_TITLE);
                case "text" -> addWords(out, text, KIND_INFO);
                default -> addWords(out, "%" + name + "%", KIND_INFO);
            }

            index = end + 1;
        }

        return out;
    }

    private static void addWords(List<Token> out, String text, int kind) {
        if (text == null) return;

        for (String word : text.trim().split("\\s+")) {
            if (!word.isEmpty()) out.add(new Token(word, kind));
        }
    }

    private Color colorOf(int kind) {
        return switch (kind) {
            case KIND_TITLE -> new Color(titleColor.get().r, titleColor.get().g, titleColor.get().b);
            case KIND_ON -> new Color(onColor.get().r, onColor.get().g, onColor.get().b);
            case KIND_OFF -> new Color(offColor.get().r, offColor.get().g, offColor.get().b);
            default -> new Color(infoColor.get().r, infoColor.get().g, infoColor.get().b);
        };
    }

    private List<List<Token>> wrap(HudRenderer renderer, List<Token> tokens, double scaleValue, double maxWidth) {
        List<List<Token>> lines = new ArrayList<>();
        List<Token> current = new ArrayList<>();
        double width = 0;
        double space = IslandRenderer.textWidth(renderer, " ", scaleValue);

        for (Token token : tokens) {
            double wordWidth = IslandRenderer.textWidth(renderer, token.text, scaleValue);

            if (wordWidth > maxWidth) {
                if (!current.isEmpty()) {
                    lines.add(current);
                    current = new ArrayList<>();
                    width = 0;
                }

                StringBuilder chunk = new StringBuilder();

                for (int i = 0; i < token.text.length(); i++) {
                    String candidate = chunk.toString() + token.text.charAt(i);

                    if (chunk.length() > 0 && IslandRenderer.textWidth(renderer, candidate, scaleValue) > maxWidth) {
                        lines.add(List.of(new Token(chunk.toString(), token.kind)));
                        chunk.setLength(0);
                    }

                    chunk.append(token.text.charAt(i));
                }

                if (chunk.length() > 0) {
                    current.add(new Token(chunk.toString(), token.kind));
                    width = IslandRenderer.textWidth(renderer, chunk.toString(), scaleValue);
                }

                continue;
            }

            if (!current.isEmpty() && width + space + wordWidth > maxWidth) {
                lines.add(current);
                current = new ArrayList<>();
                width = 0;
            }

            if (!current.isEmpty()) width += space;

            current.add(token);
            width += wordWidth;
        }

        if (!current.isEmpty()) lines.add(current);
        return lines;
    }

    private double lineWidth(HudRenderer renderer, List<Token> line, double scaleValue) {
        double width = 0;
        double space = IslandRenderer.textWidth(renderer, " ", scaleValue);

        for (Token token : line) {
            if (width > 0) width += space;
            width += IslandRenderer.textWidth(renderer, token.text, scaleValue);
        }

        return width;
    }

    private void drawLine(HudRenderer renderer, List<Token> line, double startX, double y, double scaleValue, double fade) {
        double cursor = startX;
        double space = IslandRenderer.textWidth(renderer, " ", scaleValue);

        for (Token token : line) {
            if (cursor > startX) cursor += space;

            IslandRenderer.text(renderer, token.text, cursor, y,
                IslandRenderer.alpha(colorOf(token.kind), (int) (255 * fade)), true, scaleValue);

            cursor += IslandRenderer.textWidth(renderer, token.text, scaleValue);
        }
    }

    @Override
    public void render(HudRenderer renderer) {
        Assets.poll();

        double base = scale.get();
        double font = base * textScale.get();
        double padding = 10 * base;
        double iconSize = 20 * base;
        double titleScale = 0.95 * font;
        double bodyScale = 0.8 * font;
        double timeScale = 0.72 * font;
        double bar = 2.5 * base;

        long now = System.currentTimeMillis();

        Card fresh;
        while ((fresh = INCOMING.poll()) != null) {
            Card existing = null;

            for (Card card : cards) {
                if (card.key.equals(fresh.key)) existing = card;
            }

            if (existing != null) {
                existing.title = fresh.title;
                existing.body = fresh.body;
                existing.accent = fresh.accent;
                existing.born = fresh.born;
                existing.life = fresh.life;
            } else {
                cards.add(fresh);
                while (cards.size() > maxCards.get()) cards.remove(0);
            }
        }

        for (Card card : new ArrayList<>(cards)) {
            boolean expiring = now - card.born > card.life - 300;
            card.fade.set(expiring ? 0.0 : 1.0);
            card.slide.set(0.0);
            card.fade.update(Math.min(0.05, renderer.delta));
            card.slide.update(Math.min(0.05, renderer.delta));

            if (now - card.born > card.life) cards.remove(card);
        }

        double width = cardWidth.get() * base;
        double indent = padding + 6 * base + (showIcon.get() ? iconSize + 8 * base : 0);
        double textWidth = Math.max(20 * base, width - indent - padding);

        List<List<List<Token>>> titleLines = new ArrayList<>();
        List<List<List<Token>>> bodyLines = new ArrayList<>();
        double total = 0;
        double titleLineHeight = IslandRenderer.textHeight(renderer, titleScale) + 2 * base;
        double bodyLineHeight = IslandRenderer.textHeight(renderer, bodyScale) + 1.5 * base;

        for (Card card : cards) {
            List<List<Token>> title = multiline.get() ? wrap(renderer, card.title, titleScale, textWidth)
                : List.of(card.title);
            List<List<Token>> body = multiline.get() ? wrap(renderer, card.body, bodyScale, textWidth)
                : List.of(card.body);

            titleLines.add(title);
            bodyLines.add(body);

            total += padding * 2 + title.size() * titleLineHeight + 3 * base + body.size() * bodyLineHeight
                + (showTime.get() ? titleLineHeight * 0.9 : 0)
                + (showProgress.get() ? bar + 4 * base : 0)
                + gap.get() * base;
        }

        if (!cards.isEmpty()) total -= gap.get() * base;

        setSize(width, Math.max(1, total));

        GpuTextureView background = Assets.texture(style.get().path);
        GpuTextureView icon = showIcon.get() ? Assets.texture("notify/icon.png") : null;

        double cursorY = y;

        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            double fade = card.fade.get();
            if (fade <= 0.01) continue;

            List<List<Token>> title = titleLines.get(i);
            List<List<Token>> body = bodyLines.get(i);

            double cardHeight = padding * 2 + title.size() * titleLineHeight + 3 * base + body.size() * bodyLineHeight
                + (showTime.get() ? titleLineHeight * 0.9 : 0)
                + (showProgress.get() ? bar + 4 * base : 0);

            double cardY = cursorY + card.slide.get();

            if (background != null) {
                RoundRectRenderer.textured(x, cardY, width, cardHeight,
                    radius.get() * base, radius.get() * base, radius.get() * base, radius.get() * base,
                    background, 0f, 0f, 1f, 1f, new Color(255, 255, 255, 0), (int) (backgroundAlpha.get() * fade));
            } else {
                IslandRenderer.round(x, cardY, width, cardHeight, radius.get() * base,
                    new Color(18, 20, 26, (int) (200 * fade)));
            }

            IslandRenderer.round(x + padding * 0.5, cardY + padding * 0.6, 2.5 * base, cardHeight - padding * 1.2,
                1.2 * base, IslandRenderer.alpha(card.accent, (int) (230 * fade)));

            if (icon != null) {
                RoundRectRenderer.textured(x + padding + 2 * base, cardY + padding * 0.8, iconSize, iconSize,
                    0, 0, 0, 0, icon, 0f, 0f, 1f, 1f, new Color(255, 255, 255, 0), (int) (255 * fade));
            }

            double textY = cardY + padding * 0.8;

            for (List<Token> line : title) {
                drawLine(renderer, line, x + indent, textY, titleScale, fade);
                textY += titleLineHeight;
            }

            if (showTime.get()) {
                String stamp = java.time.LocalTime.now().withNano(0).toString();
                double stampWidth = IslandRenderer.textWidth(renderer, stamp, timeScale);

                if (x + width - padding - stampWidth > x + indent + lineWidth(renderer, title.get(0), titleScale)) {
                    IslandRenderer.text(renderer, stamp, x + width - padding - stampWidth, cardY + padding * 0.8,
                        IslandRenderer.alpha(timeColor.get(), (int) (200 * fade)), true, timeScale);
                } else {
                    IslandRenderer.text(renderer, stamp, x + indent, textY,
                        IslandRenderer.alpha(timeColor.get(), (int) (200 * fade)), true, timeScale);
                    textY += titleLineHeight * 0.9;
                }
            }

            textY += 3 * base;

            for (List<Token> line : body) {
                drawLine(renderer, line, x + indent, textY, bodyScale, fade);
                textY += bodyLineHeight;
            }

            if (showProgress.get()) {
                double left = (card.life - (now - card.born)) / (double) card.life;
                left = Math.max(0, Math.min(1, left));

                double barY = cardY + cardHeight - padding * 0.6 - bar;
                IslandRenderer.round(x + padding, barY, width - padding * 2, bar, bar * 0.5,
                    new Color(255, 255, 255, (int) (40 * fade)));
                IslandRenderer.round(x + padding, barY, (width - padding * 2) * left, bar, bar * 0.5,
                    IslandRenderer.alpha(barColor.get(), (int) (barColor.get().a * fade)));
            }

            cursorY += cardHeight + gap.get() * base;
        }
    }
}
