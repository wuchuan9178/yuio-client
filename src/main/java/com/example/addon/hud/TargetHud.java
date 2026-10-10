package com.example.addon.hud;

import com.example.addon.AddonTemplate;
import com.example.addon.utils.Assets;
import com.example.addon.utils.RoundRectRenderer;
import com.mojang.blaze3d.textures.GpuTextureView;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class TargetHud extends HudElement {
    public static final HudElementInfo<TargetHud> INFO = new HudElementInfo<>(
        AddonTemplate.HUD_GROUP, "target-hud", "Rounded target cards with skin, health, armor and held items.",
        TargetHud::new);

    private static final double CARD_WIDTH = 248;
    private static final double CARD_HEIGHT = 86;
    private static final double CARD_GAP = 6;
    private static final double PADDING = 9;
    private static final double FACE_SIZE = 46;
    private static final double INFO_LEFT = PADDING + FACE_SIZE + 8;
    private static final double BAR_HEIGHT = 13;
    private static final double EMPTY_ROW_HEIGHT = 19;
    private static final double ITEM_CELL = 24;
    private static final int MAX_TRACKED = 6;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLayout = settings.createGroup("Layout");
    private final SettingGroup sgBehavior = settings.createGroup("Behavior");
    private final SettingGroup sgColors = settings.createGroup("Colors");

    public enum Background {
        ImageB("image b", "hud/target_b.png"),
        Glass("glass", "");

        private final String label;
        public final String path;

        Background(String label, String path) {
            this.label = label;
            this.path = path;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final Setting<Double> scale = sgLayout.add(new DoubleSetting.Builder()
        .name("scale").defaultValue(1).min(0.5).sliderRange(0.5, 2).build());

    private final Setting<Double> radius = sgLayout.add(new DoubleSetting.Builder()
        .name("radius").defaultValue(13).min(0).sliderRange(0, 30).build());

    private final Setting<Double> rowGap = sgLayout.add(new DoubleSetting.Builder()
        .name("card-gap").defaultValue(6).min(0).sliderRange(0, 20).build());

    private final Setting<Background> background = sgGeneral.add(new EnumSetting.Builder<Background>()
        .name("background").defaultValue(Background.ImageB).build());

    private final Setting<Integer> backgroundOpacity = sgGeneral.add(new IntSetting.Builder()
        .name("background-opacity").defaultValue(210).min(0).sliderRange(0, 255).build());

    private final Setting<Integer> tintAlpha = sgGeneral.add(new IntSetting.Builder()
        .name("tint-alpha").defaultValue(96).min(0).sliderRange(0, 255).build());

    private final Setting<Boolean> border = sgGeneral.add(new BoolSetting.Builder()
        .name("border").defaultValue(true).build());

    private final Setting<YuioIsland.FontMode> fontMode = sgGeneral.add(new EnumSetting.Builder<YuioIsland.FontMode>()
        .name("font")
        .description("Game font or Meteor font. Meteor has no CJK glyphs, so Chinese always falls back to the game font.")
        .defaultValue(YuioIsland.FontMode.Game)
        .build());

    private final Setting<Boolean> showItems = sgGeneral.add(new BoolSetting.Builder()
        .name("show-items").defaultValue(true).build());

    private final Setting<Integer> keepTicks = sgBehavior.add(new IntSetting.Builder()
        .name("keep-ticks")
        .description("Ticks a target stays on screen after the last hit.")
        .defaultValue(60).min(5).sliderRange(5, 300).build());

    private final Setting<Boolean> deathAnimation = sgBehavior.add(new BoolSetting.Builder()
        .name("death-animation")
        .description("Rotate the head and type EZZZZZZZZ when the target dies.")
        .defaultValue(true).build());

    private final Setting<Double> range = sgBehavior.add(new DoubleSetting.Builder()
        .name("range").defaultValue(48).min(1).sliderRange(1, 128).build());

    private final Setting<SettingColor> barColor = sgColors.add(new ColorSetting.Builder()
        .name("bar-color").defaultValue(new SettingColor(228, 52, 62)).build());

    private final Setting<SettingColor> barBackground = sgColors.add(new ColorSetting.Builder()
        .name("bar-background").defaultValue(new SettingColor(12, 14, 20, 205)).build());

    private final Setting<SettingColor> nameColor = sgColors.add(new ColorSetting.Builder()
        .name("name-color").defaultValue(new SettingColor(255, 255, 255)).build());

    private final Setting<SettingColor> dividerColor = sgColors.add(new ColorSetting.Builder()
        .name("divider-color").defaultValue(new SettingColor(150, 156, 168, 190)).build());

    private final Setting<SettingColor> ezColor = sgColors.add(new ColorSetting.Builder()
        .name("ez-color").defaultValue(new SettingColor(255, 74, 74)).build());

    private final Map<String, List<Card>> groups = new HashMap<>();
    private final Map<Identifier, GpuTextureView> skinViews = new HashMap<>();
    private final List<Card> cards = new ArrayList<>();
    private Card previewCard;

    private static final Field ENTITY_ID = lookup("entityId");
    private static final Field INTERACT_TYPE = lookup("type");
    private static final Field ATTACK_TYPE = lookupStatic("ATTACK");

    private static Field lookupStatic(String name) {
        try {
            Field field = PlayerInteractEntityC2SPacket.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private double delta;

    public TargetHud() {
        super(INFO);
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        Entity entity = event.entity;
        if (!(entity instanceof LivingEntity living)) return;
        if (living == mc.player) return;
        if (mc.world == null || living.getEntityWorld() != mc.world) return;
        if (living.isRemoved() || !living.isAlive()) return;
        if (mc.player != null && living.distanceTo(mc.player) > range.get()) return;

        String name = living.getName().getString();
        String key = (living instanceof PlayerEntity ? "p:" : "e:") + name;

        List<Card> group = groups.computeIfAbsent(key, ignored -> new ArrayList<>());

        for (int i = 0; i < group.size(); i++) {
            Card card = group.get(i);
            if (card.identity == living) {
                group.remove(i);
                group.add(0, card);
                card.lastAttack = System.currentTimeMillis();
                return;
            }
        }

        Card card = new Card(living, group);
        card.lastAttack = System.currentTimeMillis();
        group.add(0, card);

        while (group.size() > MAX_TRACKED) {
            Card dropped = group.remove(group.size() - 1);
            dropped.stage = Card.STAGE_GONE;
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!(event.packet instanceof PlayerInteractEntityC2SPacket packet)) return;
        if (mc.world == null || mc.player == null) return;
        if (!isAttack(packet)) return;

        int id;
        try {
            id = ENTITY_ID.getInt(packet);
        } catch (Throwable ignored) {
            return;
        }

        if (id == mc.player.getId()) return;

        Entity entity = mc.world.getEntityById(id);
        if (entity == null) return;

        onAttack(AttackEntityEvent.get(entity));
    }

    private static Field lookup(String name) {
        try {
            Field field = PlayerInteractEntityC2SPacket.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isAttack(PlayerInteractEntityC2SPacket packet) {
        if (INTERACT_TYPE == null || ATTACK_TYPE == null) return true;

        try {
            return INTERACT_TYPE.get(packet) == ATTACK_TYPE.get(null);
        } catch (Throwable ignored) {
            return true;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        long now = System.currentTimeMillis();

        for (List<Card> group : groups.values()) {
            for (int i = 0; i < group.size(); i++) {
                Card card = group.get(i);
                if (card.stage != Card.STAGE_REMOVE && card.stage != Card.STAGE_GONE) continue;
                if (now - card.lastAttack > 3000L) group.remove(i--);
            }
        }

        groups.values().removeIf(List::isEmpty);
    }

    @Override
    public void render(HudRenderer renderer) {
        Assets.poll();
        delta = renderer.delta;
        IslandRenderer.setVanillaFont(fontMode.get() == YuioIsland.FontMode.Game);

        cards.clear();
        for (List<Card> group : groups.values()) {
            for (Card card : group) {
                if (card.update(renderer, delta)) cards.add(card);
            }
        }
        cards.sort((a, b) -> Long.compare(b.lastAttack, a.lastAttack));

        boolean preview = false;
        if (cards.isEmpty() && isInEditor()) {
            if (previewCard == null) previewCard = new Card(null, new ArrayList<>());
            cards.add(previewCard);
            preview = true;
        }

        double scaleValue = scale.get();
        double cardWidth = CARD_WIDTH * scaleValue;
        double cardHeight = CARD_HEIGHT * scaleValue;
        double gap = rowGap.get() * scaleValue;

        double total = cards.isEmpty() ? 0 : cards.size() * (cardHeight + gap) - gap;
        setSize(cardWidth, total);

        if (cards.isEmpty()) return;

        IslandRenderer.beginFrame();

        double cursorY = y;
        for (Card card : cards) {
            card.render(renderer, x, cursorY, cardWidth, cardHeight, scaleValue, preview);
            cursorY += cardHeight + gap;
        }
    }

    private static String fit(HudRenderer renderer, String text, double maxWidth, double textScale) {
        if (IslandRenderer.textWidth(renderer, text, textScale) <= maxWidth) return text;

        String trimmed = text;
        while (trimmed.length() > 1 && IslandRenderer.textWidth(renderer, trimmed + "...", textScale) > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "...";
    }

    private GpuTextureView faceView(Identifier id) {
        if (id == null) return null;

        GpuTextureView cached = skinViews.get(id);
        if (cached != null) return cached;

        try {
            AbstractTexture texture = mc.getTextureManager().getTexture(id);
            GpuTextureView view = texture == null ? null : texture.getGlTextureView();
            if (view != null && skinViews.size() < 128) skinViews.put(id, view);
            return view;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean hasArmor(ItemStack[] armor) {
        for (ItemStack stack : armor) {
            if (stack != null && !stack.isEmpty()) return true;
        }
        return false;
    }

    private static ItemStack slot(LivingEntity entity, EquipmentSlot equipmentSlot) {
        try {
            ItemStack stack = entity.getEquippedStack(equipmentSlot);
            return stack == null ? ItemStack.EMPTY : stack;
        } catch (Throwable ignored) {
            return ItemStack.EMPTY;
        }
    }

    private final class Card {
        static final int STAGE_ENTER = 0;
        static final int STAGE_ACTIVE = 1;
        static final int STAGE_DEATH = 2;
        static final int STAGE_EXIT = 3;
        static final int STAGE_GONE = 4;
        static final int STAGE_REMOVE = 5;

        final String name;
        final Entity identity;
        final List<Card> group;
        final Anim fade = new Anim(0, 9);

        Entity entity;
        int stage = STAGE_ENTER;
        long lastAttack;
        long stageStart = System.currentTimeMillis();
        float health;
        float maxHealth = 20;
        float absorption;
        boolean wasAlive = true;
        boolean dying;
        Identifier faceId;
        ItemStack[] armor = new ItemStack[]{
            ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
        };
        ItemStack offhand = ItemStack.EMPTY;
        ItemStack mainhand = ItemStack.EMPTY;

        Card(Entity entity, List<Card> group) {
            this.entity = entity;
            this.group = group;
            this.name = entity == null ? "Target" : entity.getName().getString();
            this.identity = entity;
            snapshot();
        }

        void snapshot() {
            if (entity == null) return;
            if (!(entity instanceof LivingEntity living) || entity.isRemoved()) return;

            health = living.getHealth();
            maxHealth = Math.max(1, living.getMaxHealth());
            absorption = living.getAbsorptionAmount();
            wasAlive = living.isAlive();

            armor = new ItemStack[]{
                slot(living, EquipmentSlot.HEAD),
                slot(living, EquipmentSlot.CHEST),
                slot(living, EquipmentSlot.LEGS),
                slot(living, EquipmentSlot.FEET)
            };
            offhand = slot(living, EquipmentSlot.OFFHAND);
            mainhand = slot(living, EquipmentSlot.MAINHAND);

            try {
                if (living instanceof AbstractClientPlayerEntity player) {
                    faceId = player.getSkin().body().texturePath();
                }
            } catch (Throwable ignored) {
            }
        }

        void enter(int next) {
            stage = next;
            stageStart = System.currentTimeMillis();
        }

        boolean update(HudRenderer renderer, double delta) {
            fade.set(stage == STAGE_REMOVE || stage == STAGE_GONE ? 0 : 1);
            fade.update(delta);

            long elapsed = System.currentTimeMillis() - stageStart;

            if (entity == null) {
                health = 14;
                maxHealth = 20;
                absorption = 0;
                armor = new ItemStack[]{
                    new ItemStack(net.minecraft.item.Items.DIAMOND_HELMET),
                    new ItemStack(net.minecraft.item.Items.DIAMOND_CHESTPLATE),
                    new ItemStack(net.minecraft.item.Items.DIAMOND_LEGGINGS),
                    new ItemStack(net.minecraft.item.Items.DIAMOND_BOOTS)
                };
                offhand = new ItemStack(net.minecraft.item.Items.SHIELD);
                mainhand = new ItemStack(net.minecraft.item.Items.DIAMOND_SWORD);
                return true;
            }

            if (stage == STAGE_ENTER) {
                snapshot();
                if (elapsed > 450L) enter(STAGE_ACTIVE);
            } else if (stage == STAGE_ACTIVE) {
                snapshot();

                if (entity.isRemoved() || !entity.isAlive()) {
                    dying = wasAlive;
                    enter(wasAlive && deathAnimation.get() ? STAGE_DEATH : STAGE_EXIT);
                } else if (System.currentTimeMillis() - lastAttack > keepTicks.get() * 50L) {
                    enter(STAGE_EXIT);
                }
            } else if (stage == STAGE_DEATH) {
                if (elapsed > 1780L) enter(STAGE_GONE);
            } else if (stage == STAGE_EXIT) {
                if (elapsed > 750L) enter(STAGE_GONE);
            }

            if (stage == STAGE_GONE || stage == STAGE_REMOVE) {
                return fade.get() >= 0.02;
            }

            return true;
        }

        void render(HudRenderer renderer, double originX, double originY, double cardWidth, double cardHeight,
                    double unit, boolean preview) {
            double radiusValue = radius.get() * unit;
            double alpha = fade.get();
            if (alpha <= 0.01) return;

            long elapsed = System.currentTimeMillis() - stageStart;
            boolean death = stage == STAGE_DEATH;

            double vanish = 1.0;
            double faceAlpha = 1.0;
            double faceRotation = 0;
            double ezAlpha = 0;

            if (death) {
                double first = stageProgress(elapsed, 0, 340);
                double second = stageProgress(elapsed, 340, 760);
                double typing = stageProgress(elapsed, 760, 1380);
                double hold = stageProgress(elapsed, 1380, 1720);

                vanish = 1.0 - first;
                faceAlpha = 1.0 - second;
                faceRotation = 20.0 * second;
                if (typing > 0) ezAlpha = Math.min(1.0, typing * 5);
                alpha = alpha * (1.0 - hold);
            }

            Color white = new Color(255, 255, 255, (int) (255 * alpha));

            Background style = background.get();
            if (style == Background.Glass) {
                IslandRenderer.glass(originX, originY, cardWidth, cardHeight, radiusValue,
                    12, backgroundOpacity.get(), (int) (170 * alpha), border.get(), true);
            } else {
                GpuTextureView view = Assets.texture(style.path);
                if (view != null) {
                    RoundRectRenderer.textured(originX, originY, cardWidth, cardHeight,
                        radiusValue, radiusValue, radiusValue, radiusValue,
                        view, 0.0, 0.18, 1.0, 0.80, new Color(255, 255, 255, 0),
                        (int) (backgroundOpacity.get() * alpha));

                    RoundRectRenderer.fill(originX, originY, cardWidth, cardHeight,
                        radiusValue, radiusValue, radiusValue, radiusValue,
                        new Color(8, 10, 16, (int) (tintAlpha.get() * alpha)));
                }
            }

            if (border.get()) {
                IslandRenderer.border(originX, originY, cardWidth, cardHeight, radiusValue, 1,
                    new Color(255, 255, 255, (int) (46 * alpha)));
            }

            double faceSize = FACE_SIZE * unit;
            double faceX = originX + PADDING * unit;
            double faceY = originY + (cardHeight - faceSize) * 0.5;

            if (faceAlpha > 0.01) {
                GpuTextureView face = faceView(faceId);
                double faceColor = alpha * faceAlpha;

                if (face != null) {
                    RoundRectRenderer.texturedPixel(faceX, faceY, faceSize, faceSize,
                        faceSize * 0.32, faceSize * 0.32, faceSize * 0.32, faceSize * 0.32,
                        face, 0.125, 0.125, 0.25, 0.25, new Color(255, 255, 255, 0),
                        (int) (255 * faceColor), faceRotation);
                } else {
                    IslandRenderer.round(faceX, faceY, faceSize, faceSize, faceSize * 0.32,
                        new Color(24, 27, 36, (int) (235 * faceColor)));
                }
            }

            if (vanish <= 0.01) return;

            double infoX = originX + INFO_LEFT * unit;
            double infoWidth = cardWidth - INFO_LEFT * unit - PADDING * unit;
            double nameScale = 1.15 * unit;
            double barFontScale = 0.9 * unit;

            Color nameTint = new Color(nameColor.get().r, nameColor.get().g, nameColor.get().b,
                (int) (nameColor.get().a * alpha * vanish));
            Color barTint = new Color(barColor.get().r, barColor.get().g, barColor.get().b,
                (int) (barColor.get().a * alpha * vanish));
            Color trackTint = new Color(barBackground.get().r, barBackground.get().g, barBackground.get().b,
                (int) (barBackground.get().a * alpha * vanish));

            double nameHeight = IslandRenderer.textHeight(renderer, nameScale);
            double barWidth = infoWidth;
            double barHeight = BAR_HEIGHT * unit;
            double barRadius = barHeight * 0.5;
            double rowHeight = EMPTY_ROW_HEIGHT * unit;
            double nameGap = 4 * unit;
            double rowGapPixels = 7 * unit;
            boolean withItems = showItems.get();

            double blockHeight = nameHeight + nameGap + barHeight + (withItems ? rowGapPixels + rowHeight : 0);
            double blockTop = originY + (cardHeight - blockHeight) * 0.5;

            IslandRenderer.text(renderer, fit(renderer, name, infoWidth, nameScale), infoX, blockTop,
                nameTint, true, nameScale);

            double barY = blockTop + nameHeight + nameGap;

            IslandRenderer.round(infoX, barY, barWidth, barHeight, barRadius, trackTint);

            double ratio = Math.max(0, Math.min(1, (health + absorption) / maxHealth));
            double fillWidth = barWidth * ratio;
            if (fillWidth > 0.5) {
                IslandRenderer.round(infoX, barY, fillWidth, barHeight, Math.min(barRadius, fillWidth * 0.5), barTint);
            }

            int current = MathHelper.ceil(health + absorption);
            int maximum = MathHelper.ceil(maxHealth);
            String healthText = current + "/" + maximum;
            double healthScale = barFontScale;
            double healthWidth = IslandRenderer.textWidth(renderer, healthText, healthScale);

            if (healthWidth < barWidth - 4 * unit) {
                IslandRenderer.text(renderer, healthText, infoX + (barWidth - healthWidth) * 0.5,
                    barY + (barHeight - IslandRenderer.textHeight(renderer, healthScale)) * 0.5,
                    new Color(255, 255, 255, (int) (240 * alpha * vanish)), true, healthScale);
            }

            if (!withItems) return;

            boolean anyArmor = hasArmor(armor);
            double rowTop = blockTop + nameHeight + nameGap + barHeight + rowGapPixels;
            double cell = ITEM_CELL * unit;
            double drawScale = unit;
            double drawSize = 16 * drawScale;
            double blackSpan = 12 * unit;
            double barThickness = 2 * unit;
            double grayWidth = 1.5 * unit;

            int armorCount = 0;
            if (anyArmor) {
                for (ItemStack stack : armor) {
                    if (!stack.isEmpty()) armorCount++;
                }
                armorCount = Math.max(armorCount, 4);
            }

            boolean offhandPresent = !offhand.isEmpty();
            boolean mainhandPresent = !mainhand.isEmpty();
            boolean armorSide = anyArmor && (offhandPresent || mainhandPresent);

            double armorWidth = armorCount * cell;
            double offhandWidth = offhandPresent ? cell : 0;
            double mainhandWidth = mainhandPresent ? cell : 0;
            double beforeWidth = armorSide && offhandPresent ? blackSpan
                : (!anyArmor && offhandPresent && mainhandPresent ? blackSpan : 0);
            double afterWidth = armorSide && mainhandPresent ? blackSpan : 0;

            double available = infoWidth;
            double totalWidth = offhandWidth + beforeWidth + armorWidth + afterWidth + mainhandWidth;
            if (totalWidth > available && totalWidth > 0) {
                double shrink = available / totalWidth;
                cell *= shrink;
                drawScale *= shrink;
                drawSize *= shrink;
                blackSpan *= shrink;
                barThickness *= shrink;
                grayWidth *= shrink;
                armorWidth *= shrink;
                offhandWidth *= shrink;
                mainhandWidth *= shrink;
                beforeWidth *= shrink;
                afterWidth *= shrink;
                totalWidth = available;
            }

            double inset = Math.max(0, (cell - drawSize) * 0.5);
            double itemY = rowTop + (rowHeight - drawSize) * 0.5;
            double cursorX = infoX + Math.max(0, (infoWidth - totalWidth) * 0.5);
            if (armorWidth > 0) cursorX = infoX;

            Color blackBar = new Color(0, 0, 0, (int) (150 * alpha * vanish));
            Color grayBar = new Color(dividerColor.get().r, dividerColor.get().g, dividerColor.get().b,
                (int) (dividerColor.get().a * alpha * vanish));

            if (offhandPresent) {
                renderer.item(offhand, (int) Math.round(cursorX + inset), (int) Math.round(itemY),
                    (float) drawScale, true);
                cursorX += offhandWidth;
            }

            if (beforeWidth > 0) {
                IslandRenderer.round(cursorX + (blackSpan - barThickness) * 0.5, rowTop, barThickness, rowHeight, 0.5, blackBar);
                cursorX += beforeWidth;
            }

            if (anyArmor) {
                double blockStart = cursorX;

                for (int i = 0; i < armor.length; i++) {
                    double cellX = blockStart + i * cell;
                    if (!armor[i].isEmpty()) {
                        renderer.item(armor[i], (int) Math.round(cellX + inset), (int) Math.round(itemY),
                            (float) drawScale, true);
                    }

                    if (i < armor.length - 1) {
                        IslandRenderer.round(cellX + cell - grayWidth * 0.5, rowTop + 2 * unit, grayWidth,
                            rowHeight - 4 * unit, 0.5, grayBar);
                    }
                }

                cursorX += armorWidth;
            }

            if (afterWidth > 0) {
                IslandRenderer.round(cursorX + (blackSpan - barThickness) * 0.5, rowTop, barThickness, rowHeight, 0.5, blackBar);
                cursorX += afterWidth;
            }

            if (mainhandPresent) {
                renderer.item(mainhand, (int) Math.round(cursorX + inset), (int) Math.round(itemY),
                    (float) drawScale, true);
            }

            if (dying && deathAnimation.get() && ezAlpha > 0.01) {
                double typing = stageProgress(elapsed, 760, 1380);
                String full = "EZZZZZZZZ";
                int visible = Math.max(0, Math.min(full.length(), (int) Math.floor(typing * 10.0)));
                if (visible > 0) {
                    String shown = full.substring(0, visible);
                    double ezScale = 1.5 * unit;
                    double ezWidth = IslandRenderer.textWidth(renderer, full, ezScale);
                    double ezHeight = IslandRenderer.textHeight(renderer, ezScale);
                    IslandRenderer.text(renderer, shown,
                        originX + (cardWidth - ezWidth) * 0.5,
                        originY + (cardHeight - ezHeight) * 0.5,
                        new Color(255, 74, 74, (int) (255 * ezAlpha * alpha)), true, ezScale);
                }
            }
        }

        private double stageProgress(long elapsed, double from, double to) {
            if (elapsed <= from) return 0;
            if (elapsed >= to) return 1;
            return (elapsed - from) / (to - from);
        }
    }
}
