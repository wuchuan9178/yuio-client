package com.example.addon.hud;

import com.example.addon.AddonTemplate;
import com.example.addon.utils.Assets;
import com.example.addon.utils.RoundRectRenderer;
import com.mojang.blaze3d.textures.GpuTextureView;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IPlayerInteractEntityC2SPacket;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket.InteractType;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class TargetHud extends HudElement {
    public static final HudElementInfo<TargetHud> INFO = new HudElementInfo<>(
        AddonTemplate.HUD_GROUP, "target-hud", "Rounded target cards with skin, health, armor and held items.",
        TargetHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLayout = settings.createGroup("Layout");
    private final SettingGroup sgItems = settings.createGroup("Items");
    private final SettingGroup sgBehavior = settings.createGroup("Behavior");
    private final SettingGroup sgDeath = settings.createGroup("Death");
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

    public enum ItemAlign {
        Left,
        Center,
        Right
    }

    public enum StackDirection {
        Down,
        Up
    }

    /** How the HUD notices that a tracked target died. */
    public enum DeathMode {
        /** 1 - sample isRemoved()/isAlive() every frame (the original behaviour). */
        Normal("1 - poll entity state"),
        /** 2 - react to the server death packet and show EZ the instant it lands. */
        Instant("2 - instant on death packet");

        private final String label;

        DeathMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final Setting<Background> background = sgGeneral.add(new EnumSetting.Builder<Background>()
        .name("background")
        .description("Card background style.")
        .defaultValue(Background.ImageB).build());

    private final Setting<Integer> backgroundOpacity = sgGeneral.add(new IntSetting.Builder()
        .name("background-opacity")
        .description("Opacity of the background image.")
        .defaultValue(210).min(0).sliderRange(0, 255).build());

    private final Setting<Double> bgU1 = sgGeneral.add(new DoubleSetting.Builder()
        .name("bg-u1")
        .description("Left edge of the crop taken from the background image.")
        .defaultValue(0.0).min(0).max(1).sliderRange(0, 1)
        .visible(() -> background.get() == Background.ImageB).build());

    private final Setting<Double> bgV1 = sgGeneral.add(new DoubleSetting.Builder()
        .name("bg-v1")
        .description("Top edge of the crop taken from the background image.")
        .defaultValue(0.18).min(0).max(1).sliderRange(0, 1)
        .visible(() -> background.get() == Background.ImageB).build());

    private final Setting<Double> bgU2 = sgGeneral.add(new DoubleSetting.Builder()
        .name("bg-u2")
        .description("Right edge of the crop taken from the background image.")
        .defaultValue(1.0).min(0).max(1).sliderRange(0, 1)
        .visible(() -> background.get() == Background.ImageB).build());

    private final Setting<Double> bgV2 = sgGeneral.add(new DoubleSetting.Builder()
        .name("bg-v2")
        .description("Bottom edge of the crop taken from the background image.")
        .defaultValue(0.80).min(0).max(1).sliderRange(0, 1)
        .visible(() -> background.get() == Background.ImageB).build());

    private final Setting<SettingColor> tintColor = sgGeneral.add(new ColorSetting.Builder()
        .name("tint-color")
        .description("Colour of the darkening layer on top of the background image.")
        .defaultValue(new SettingColor(8, 10, 16)).build());

    private final Setting<Integer> tintAlpha = sgGeneral.add(new IntSetting.Builder()
        .name("tint-alpha")
        .description("Strength of the darkening layer on top of the background image.")
        .defaultValue(96).min(0).sliderRange(0, 255).build());

    private final Setting<Boolean> border = sgGeneral.add(new BoolSetting.Builder()
        .name("border")
        .description("Draw an outline around the card.")
        .defaultValue(true).build());

    private final Setting<SettingColor> borderColor = sgGeneral.add(new ColorSetting.Builder()
        .name("border-color")
        .description("Outline colour.")
        .defaultValue(new SettingColor(255, 255, 255, 46))
        .visible(border::get).build());

    private final Setting<Double> borderWidth = sgGeneral.add(new DoubleSetting.Builder()
        .name("border-width")
        .description("Outline thickness.")
        .defaultValue(1).min(0.25).sliderRange(0.25, 5)
        .visible(border::get).build());

    private final Setting<Boolean> cardShadow = sgGeneral.add(new BoolSetting.Builder()
        .name("card-shadow")
        .description("Draw a soft drop shadow behind every card.")
        .defaultValue(false).build());

    private final Setting<Double> shadowStrength = sgGeneral.add(new DoubleSetting.Builder()
        .name("shadow-strength")
        .description("How dark the drop shadow is.")
        .defaultValue(1.6).min(0.1).sliderRange(0.1, 6)
        .visible(cardShadow::get).build());

    private final Setting<Integer> glassBlur = sgGeneral.add(new IntSetting.Builder()
        .name("glass-blur")
        .description("Blur strength of the glass background.")
        .defaultValue(12).min(0).sliderRange(0, 32)
        .visible(() -> background.get() == Background.Glass).build());

    private final Setting<Integer> glassTint = sgGeneral.add(new IntSetting.Builder()
        .name("glass-tint")
        .description("Tint strength of the glass background.")
        .defaultValue(170).min(0).sliderRange(0, 255)
        .visible(() -> background.get() == Background.Glass).build());

    private final Setting<Boolean> glassHighlight = sgGeneral.add(new BoolSetting.Builder()
        .name("glass-highlight")
        .description("Draw the thin highlight line at the top of the glass card.")
        .defaultValue(true)
        .visible(() -> background.get() == Background.Glass).build());

    private final Setting<YuioIsland.FontMode> fontMode = sgGeneral.add(new EnumSetting.Builder<YuioIsland.FontMode>()
        .name("font")
        .description("Game font or Meteor font. Meteor has no CJK glyphs, so Chinese always falls back to the game font.")
        .defaultValue(YuioIsland.FontMode.Game)
        .build());

    private final Setting<Boolean> showFace = sgGeneral.add(new BoolSetting.Builder()
        .name("show-face")
        .description("Draw the skin head next to the health bar.")
        .defaultValue(true).build());

    private final Setting<SettingColor> faceTint = sgGeneral.add(new ColorSetting.Builder()
        .name("face-tint")
        .description("Tint mixed over the skin head. Alpha is the mix strength, so a fully transparent colour means no tint.")
        .defaultValue(new SettingColor(255, 255, 255, 0))
        .visible(showFace::get).build());

    private final Setting<Integer> faceAlpha = sgGeneral.add(new IntSetting.Builder()
        .name("face-alpha")
        .description("Opacity of the skin head.")
        .defaultValue(255).min(0).sliderRange(0, 255)
        .visible(showFace::get).build());

    private final Setting<Double> faceRadiusFactor = sgGeneral.add(new DoubleSetting.Builder()
        .name("face-radius")
        .description("Corner rounding of the skin head as a fraction of its size.")
        .defaultValue(0.32).min(0).max(0.5).sliderRange(0, 0.5)
        .visible(showFace::get).build());

    private final Setting<Boolean> showItems = sgGeneral.add(new BoolSetting.Builder()
        .name("show-items")
        .description("Draw the armour and held item row.")
        .defaultValue(true).build());

    private final Setting<Boolean> showItemCount = sgGeneral.add(new BoolSetting.Builder()
        .name("show-item-count")
        .description("Draw stack counts on the item icons.")
        .defaultValue(false)
        .visible(showItems::get).build());

    private final Setting<Double> scale = sgLayout.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Scales the whole card.")
        .defaultValue(1).min(0.25).sliderRange(0.5, 2).build());

    private final Setting<Double> cardWidth = sgLayout.add(new DoubleSetting.Builder()
        .name("card-width")
        .description("Card width in pixels before scale is applied.")
        .defaultValue(248).min(96).sliderRange(120, 420).build());

    private final Setting<Double> cardHeight = sgLayout.add(new DoubleSetting.Builder()
        .name("card-height")
        .description("Card height in pixels before scale is applied.")
        .defaultValue(86).min(32).sliderRange(40, 180).build());

    private final Setting<Double> radius = sgLayout.add(new DoubleSetting.Builder()
        .name("radius")
        .description("Corner rounding of the card.")
        .defaultValue(13).min(0).sliderRange(0, 40).build());

    private final Setting<Double> padding = sgLayout.add(new DoubleSetting.Builder()
        .name("padding")
        .description("Inner margin of the card.")
        .defaultValue(9).min(0).sliderRange(0, 24).build());

    private final Setting<Double> contentOffsetY = sgLayout.add(new DoubleSetting.Builder()
        .name("content-offset-y")
        .description("Moves the name, bar and item row up or down inside the card.")
        .defaultValue(0).min(-60).sliderRange(-40, 40).build());

    private final Setting<Double> rowGap = sgLayout.add(new DoubleSetting.Builder()
        .name("card-gap")
        .description("Space between stacked cards.")
        .defaultValue(6).min(0).sliderRange(0, 24).build());

    private final Setting<StackDirection> stackDirection = sgLayout.add(new EnumSetting.Builder<StackDirection>()
        .name("stack-direction")
        .description("Whether new cards stack downwards or upwards.")
        .defaultValue(StackDirection.Down).build());

    private final Setting<Integer> maxCards = sgLayout.add(new IntSetting.Builder()
        .name("max-cards")
        .description("How many cards can be on screen at once.")
        .defaultValue(6).min(1).sliderRange(1, 12).build());

    private final Setting<Double> faceSize = sgLayout.add(new DoubleSetting.Builder()
        .name("face-size")
        .description("Size of the skin head.")
        .defaultValue(46).min(8).sliderRange(16, 96)
        .visible(showFace::get).build());

    private final Setting<Double> faceGap = sgLayout.add(new DoubleSetting.Builder()
        .name("face-gap")
        .description("Space between the skin head and the text column.")
        .defaultValue(8).min(0).sliderRange(0, 32)
        .visible(showFace::get).build());

    private final Setting<Double> faceOffsetX = sgLayout.add(new DoubleSetting.Builder()
        .name("face-offset-x")
        .description("Nudges the skin head left or right.")
        .defaultValue(0).min(-60).sliderRange(-30, 30)
        .visible(showFace::get).build());

    private final Setting<Double> faceOffsetY = sgLayout.add(new DoubleSetting.Builder()
        .name("face-offset-y")
        .description("Nudges the skin head up or down.")
        .defaultValue(0).min(-60).sliderRange(-30, 30)
        .visible(showFace::get).build());

    private final Setting<Boolean> showName = sgLayout.add(new BoolSetting.Builder()
        .name("show-name")
        .description("Draw the target name above the health bar.")
        .defaultValue(true).build());

    private final Setting<Double> nameScale = sgLayout.add(new DoubleSetting.Builder()
        .name("name-scale")
        .description("Text size of the target name.")
        .defaultValue(1.15).min(0.25).sliderRange(0.5, 2.5)
        .visible(showName::get).build());

    private final Setting<Double> nameGap = sgLayout.add(new DoubleSetting.Builder()
        .name("name-gap")
        .description("Space between the name and the health bar.")
        .defaultValue(4).min(0).sliderRange(0, 16)
        .visible(showName::get).build());

    private final Setting<ItemAlign> nameAlign = sgLayout.add(new EnumSetting.Builder<ItemAlign>()
        .name("name-align")
        .description("Horizontal alignment of the target name.")
        .defaultValue(ItemAlign.Left)
        .visible(showName::get).build());

    private final Setting<Boolean> nameShadow = sgLayout.add(new BoolSetting.Builder()
        .name("name-shadow")
        .description("Draw a drop shadow behind the name.")
        .defaultValue(true)
        .visible(showName::get).build());

    private final Setting<Double> barHeight = sgLayout.add(new DoubleSetting.Builder()
        .name("bar-height")
        .description("Health bar thickness.")
        .defaultValue(13).min(2).sliderRange(6, 30).build());

    private final Setting<Double> barRadiusFactor = sgLayout.add(new DoubleSetting.Builder()
        .name("bar-radius")
        .description("Corner rounding of the health bar as a fraction of its half height.")
        .defaultValue(1.0).min(0).max(1).sliderRange(0, 1).build());

    private final Setting<Boolean> showHealthText = sgLayout.add(new BoolSetting.Builder()
        .name("show-health-text")
        .description("Draw the current/max health numbers inside the bar.")
        .defaultValue(true).build());

    private final Setting<Double> barTextScale = sgLayout.add(new DoubleSetting.Builder()
        .name("bar-text-scale")
        .description("Text size of the health numbers inside the bar.")
        .defaultValue(0.9).min(0.2).sliderRange(0.4, 2)
        .visible(showHealthText::get).build());

    private final Setting<Boolean> barTextShadow = sgLayout.add(new BoolSetting.Builder()
        .name("bar-text-shadow")
        .description("Draw a drop shadow behind the health numbers.")
        .defaultValue(true)
        .visible(showHealthText::get).build());

    private final Setting<Double> itemCell = sgItems.add(new DoubleSetting.Builder()
        .name("item-cell")
        .description("Width of one item slot.")
        .defaultValue(24).min(6).sliderRange(10, 48)
        .visible(showItems::get).build());

    private final Setting<Double> itemRowGap = sgItems.add(new DoubleSetting.Builder()
        .name("item-row-gap")
        .description("Space between the health bar and the item row.")
        .defaultValue(7).min(0).sliderRange(0, 24)
        .visible(showItems::get).build());

    private final Setting<Double> itemScale = sgItems.add(new DoubleSetting.Builder()
        .name("item-scale")
        .description("Size of the item icons.")
        .defaultValue(1.0).min(0.25).sliderRange(0.5, 2)
        .visible(showItems::get).build());

    private final Setting<ItemAlign> itemAlign = sgItems.add(new EnumSetting.Builder<ItemAlign>()
        .name("item-align")
        .description("Horizontal alignment of the item row.")
        .defaultValue(ItemAlign.Left)
        .visible(showItems::get).build());

    private final Setting<Double> itemDivider = sgItems.add(new DoubleSetting.Builder()
        .name("item-divider")
        .description("Width of the black separators around the armour group.")
        .defaultValue(12).min(0).sliderRange(0, 32)
        .visible(showItems::get).build());

    private final Setting<Double> armorDivider = sgItems.add(new DoubleSetting.Builder()
        .name("armor-divider")
        .description("Width of the grey separators between armour icons.")
        .defaultValue(1.5).min(0).sliderRange(0, 8)
        .visible(showItems::get).build());

    private final Setting<Double> dividerThickness = sgItems.add(new DoubleSetting.Builder()
        .name("divider-thickness")
        .description("Thickness of every item row separator.")
        .defaultValue(2).min(0.25).sliderRange(0.5, 8)
        .visible(showItems::get).build());

    private final Setting<Double> dividerRadiusFactor = sgItems.add(new DoubleSetting.Builder()
        .name("divider-radius")
        .description("Corner rounding of the separators. 1 makes them fully rounded pills.")
        .defaultValue(1.0).min(0).max(1).sliderRange(0, 1)
        .visible(showItems::get).build());

    private final Setting<Double> dividerInset = sgItems.add(new DoubleSetting.Builder()
        .name("divider-inset")
        .description("How far the separators are pulled in from the top and bottom of the item row.")
        .defaultValue(3).min(0).sliderRange(0, 16)
        .visible(showItems::get).build());

    private final Setting<Boolean> showOffhand = sgItems.add(new BoolSetting.Builder()
        .name("show-offhand")
        .description("Draw the offhand item.")
        .defaultValue(true)
        .visible(showItems::get).build());

    private final Setting<Boolean> showArmor = sgItems.add(new BoolSetting.Builder()
        .name("show-armor")
        .description("Draw the four armour slots.")
        .defaultValue(true)
        .visible(showItems::get).build());

    private final Setting<Boolean> showMainhand = sgItems.add(new BoolSetting.Builder()
        .name("show-mainhand")
        .description("Draw the mainhand item.")
        .defaultValue(true)
        .visible(showItems::get).build());

    private final Setting<Integer> keepTicks = sgBehavior.add(new IntSetting.Builder()
        .name("keep-ticks")
        .description("Ticks a target stays on screen after the last hit.")
        .defaultValue(60).min(5).sliderRange(5, 600).build());

    private final Setting<Boolean> rangeCheck = sgBehavior.add(new BoolSetting.Builder()
        .name("range-check")
        .description("Ignore attacks made from further away than the range. Off by default because "
            + "mace and spear modules often attack while the player is displaced.")
        .defaultValue(false).build());

    private final Setting<Double> range = sgBehavior.add(new DoubleSetting.Builder()
        .name("range")
        .description("Targets further away than this are ignored.")
        .defaultValue(48).min(1).sliderRange(1, 128)
        .visible(rangeCheck::get).build());

    private final Setting<Boolean> onlyPlayers = sgBehavior.add(new BoolSetting.Builder()
        .name("only-players")
        .description("Ignore mobs and only track players.")
        .defaultValue(false).build());

    private final Setting<Boolean> ignoreFriends = sgBehavior.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Never track Meteor friends.")
        .defaultValue(false).build());

    private final Setting<Integer> enterDuration = sgBehavior.add(new IntSetting.Builder()
        .name("enter-duration")
        .description("Milliseconds the card spends fading in before it goes live.")
        .defaultValue(450).min(0).sliderRange(0, 2000).build());

    private final Setting<Integer> exitDuration = sgBehavior.add(new IntSetting.Builder()
        .name("exit-duration")
        .description("Milliseconds the card spends fading out when a target is dropped.")
        .defaultValue(750).min(0).sliderRange(0, 3000).build());

    private final Setting<Double> fadeSpeed = sgBehavior.add(new DoubleSetting.Builder()
        .name("fade-speed")
        .description("How fast the card fade in and out animation runs.")
        .defaultValue(9).min(0.5).sliderRange(1, 30).build());

    private final Setting<Boolean> animateHealth = sgBehavior.add(new BoolSetting.Builder()
        .name("animate-health")
        .description("Smoothly slide the health bar instead of jumping.")
        .defaultValue(true).build());

    private final Setting<Double> healthAnimSpeed = sgBehavior.add(new DoubleSetting.Builder()
        .name("health-anim-speed")
        .description("How fast the health bar catches up.")
        .defaultValue(8).min(0.5).sliderRange(1, 30)
        .visible(animateHealth::get).build());

    private final Setting<Boolean> showAbsorption = sgBehavior.add(new BoolSetting.Builder()
        .name("show-absorption")
        .description("Draw absorption hearts as a golden bar segment and count them in the text.")
        .defaultValue(true).build());

    private final Setting<Boolean> deathAnimation = sgDeath.add(new BoolSetting.Builder()
        .name("death-animation")
        .description("Rotate the head and type the EZ text when the target dies.")
        .defaultValue(true).build());

    private final Setting<DeathMode> deathMode = sgDeath.add(new EnumSetting.Builder<DeathMode>()
        .name("death-mode")
        .description("1 polls the entity every frame. 2 fires the death animation the moment the server death packet arrives and always shows EZ.")
        .defaultValue(DeathMode.Normal)
        .visible(deathAnimation::get).build());

    private final Setting<String> ezText = sgDeath.add(new StringSetting.Builder()
        .name("ez-text")
        .description("Text typed out on death. Leave empty to disable.")
        .defaultValue("EZZZZZZZZ")
        .visible(deathAnimation::get).build());

    private final Setting<Boolean> ezOnlyOnKill = sgDeath.add(new BoolSetting.Builder()
        .name("ez-only-on-kill")
        .description("Only show the EZ text when the kill happened right after one of your hits.")
        .defaultValue(true)
        .visible(() -> deathAnimation.get() && deathMode.get() == DeathMode.Normal).build());

    private final Setting<Integer> ezKillWindow = sgDeath.add(new IntSetting.Builder()
        .name("ez-kill-window")
        .description("A death within this many milliseconds of your last hit counts as your kill.")
        .defaultValue(3000).min(100).sliderRange(200, 15000)
        .visible(() -> deathAnimation.get() && deathMode.get() == DeathMode.Normal && ezOnlyOnKill.get()).build());

    private final Setting<Double> ezScale = sgDeath.add(new DoubleSetting.Builder()
        .name("ez-scale")
        .description("Text size of the EZ text.")
        .defaultValue(1.5).min(0.25).sliderRange(0.5, 4)
        .visible(deathAnimation::get).build());

    private final Setting<Boolean> ezShadow = sgDeath.add(new BoolSetting.Builder()
        .name("ez-shadow")
        .description("Draw a drop shadow behind the EZ text.")
        .defaultValue(true)
        .visible(deathAnimation::get).build());

    private final Setting<Double> ezOffsetX = sgDeath.add(new DoubleSetting.Builder()
        .name("ez-offset-x")
        .description("Nudges the EZ text left or right.")
        .defaultValue(0).min(-80).sliderRange(-40, 40)
        .visible(deathAnimation::get).build());

    private final Setting<Double> ezOffsetY = sgDeath.add(new DoubleSetting.Builder()
        .name("ez-offset-y")
        .description("Nudges the EZ text up or down.")
        .defaultValue(0).min(-80).sliderRange(-40, 40)
        .visible(deathAnimation::get).build());

    private final Setting<Double> deathRotateAngle = sgDeath.add(new DoubleSetting.Builder()
        .name("rotate-angle")
        .description("Degrees the head turns clockwise while dying.")
        .defaultValue(20).min(0).sliderRange(0, 180)
        .visible(deathAnimation::get).build());

    private final Setting<Integer> deathVanishDuration = sgDeath.add(new IntSetting.Builder()
        .name("vanish-duration")
        .description("Milliseconds the bar and item row take to disappear.")
        .defaultValue(340).min(0).sliderRange(0, 3000)
        .visible(deathAnimation::get).build());

    private final Setting<Integer> deathRotateDuration = sgDeath.add(new IntSetting.Builder()
        .name("rotate-duration")
        .description("Milliseconds the head takes to rotate. The face stays opaque during the turn.")
        .defaultValue(420).min(0).sliderRange(0, 3000)
        .visible(deathAnimation::get).build());

    private final Setting<Integer> deathFaceFadeDuration = sgDeath.add(new IntSetting.Builder()
        .name("face-fade-duration")
        .description("Milliseconds the head takes to fade out after it has finished rotating.")
        .defaultValue(320).min(0).sliderRange(0, 3000)
        .visible(deathAnimation::get).build());

    private final Setting<Integer> deathTypingDuration = sgDeath.add(new IntSetting.Builder()
        .name("typing-duration")
        .description("Milliseconds the EZ text takes to type itself out.")
        .defaultValue(620).min(0).sliderRange(50, 4000)
        .visible(deathAnimation::get).build());

    private final Setting<Integer> deathEndHold = sgDeath.add(new IntSetting.Builder()
        .name("end-hold")
        .description("Milliseconds the finished EZ text is held before the card fades away.")
        .defaultValue(340).min(0).sliderRange(0, 4000)
        .visible(deathAnimation::get).build());

    private final Setting<SettingColor> barColor = sgColors.add(new ColorSetting.Builder()
        .name("bar-color").defaultValue(new SettingColor(228, 52, 62)).build());

    private final Setting<SettingColor> barBackground = sgColors.add(new ColorSetting.Builder()
        .name("bar-background").defaultValue(new SettingColor(12, 14, 20, 205)).build());

    private final Setting<SettingColor> absorptionColor = sgColors.add(new ColorSetting.Builder()
        .name("absorption-color").defaultValue(new SettingColor(250, 214, 64)).build());

    private final Setting<SettingColor> healthTextColor = sgColors.add(new ColorSetting.Builder()
        .name("health-text-color").defaultValue(new SettingColor(255, 255, 255, 240)).build());

    private final Setting<SettingColor> nameColor = sgColors.add(new ColorSetting.Builder()
        .name("name-color").defaultValue(new SettingColor(255, 255, 255)).build());

    private final Setting<SettingColor> dividerColor = sgColors.add(new ColorSetting.Builder()
        .name("divider-color")
        .description("Colour of the grey separators between the armour icons.")
        .defaultValue(new SettingColor(150, 156, 168, 190)).build());

    private final Setting<SettingColor> blackDividerColor = sgColors.add(new ColorSetting.Builder()
        .name("black-divider-color")
        .description("Colour of the separators between the held items and the armour group.")
        .defaultValue(new SettingColor(0, 0, 0, 150)).build());

    private final Setting<SettingColor> ezColor = sgColors.add(new ColorSetting.Builder()
        .name("ez-color").defaultValue(new SettingColor(255, 74, 74)).build());

    private final Map<String, List<Card>> groups = new HashMap<>();
    private final Map<Identifier, GpuTextureView> skinViews = new HashMap<>();
    private final List<Card> cards = new ArrayList<>();
    private Card previewCard;

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
        if (living.isRemoved()) return;
        if (onlyPlayers.get() && !(living instanceof PlayerEntity)) return;
        if (ignoreFriends.get() && living instanceof PlayerEntity player && !Friends.get().shouldAttack(player)) return;
        if (rangeCheck.get() && mc.player != null && living.distanceTo(mc.player) > range.get()) return;

        String name = living.getName().getString();
        String key = (living instanceof PlayerEntity ? "p:" : "e:") + name;

        List<Card> group = groups.computeIfAbsent(key, ignored -> new ArrayList<>());

        Card existing = null;
        for (int i = 0; i < group.size(); i++) {
            if (group.get(i).identity == living) {
                existing = group.remove(i);
                break;
            }
        }

        // A card that already played out its fade is dead weight: replace it so the
        // same target shows up again instead of silently staying invisible.
        Card card = existing == null || existing.finished() || existing.died
            ? new Card(living, group)
            : existing;

        card.lastAttack = System.currentTimeMillis();
        group.add(0, card);

        while (group.size() > maxCards.get()) {
            Card dropped = group.remove(group.size() - 1);
            dropped.enter(Card.STAGE_GONE);
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!(event.packet instanceof PlayerInteractEntityC2SPacket packet)) return;
        if (mc.world == null || mc.player == null) return;

        IPlayerInteractEntityC2SPacket accessor = (IPlayerInteractEntityC2SPacket) packet;
        if (accessor.meteor$getType() != InteractType.ATTACK) return;

        Entity entity = accessor.meteor$getEntity();
        if (entity == null || entity == mc.player) return;

        onAttack(AttackEntityEvent.get(entity));
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (deathMode.get() != DeathMode.Instant) return;
        if (mc.world == null) return;
        if (!(event.packet instanceof EntityStatusS2CPacket packet)) return;
        if (packet.getStatus() != EntityStatuses.PLAY_DEATH_SOUND_OR_ADD_PROJECTILE_HIT_PARTICLES) return;

        Entity entity = packet.getEntity(mc.world);
        if (entity == null) return;

        for (List<Card> group : groups.values()) {
            for (Card card : group) {
                if (card.identity != entity) continue;
                card.die(true);
                return;
            }
        }
    }

    private static boolean isAttack(PlayerInteractEntityC2SPacket packet) {
        return ((IPlayerInteractEntityC2SPacket) packet).meteor$getType() == InteractType.ATTACK;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        long now = System.currentTimeMillis();

        for (List<Card> group : groups.values()) {
            for (int i = 0; i < group.size(); i++) {
                Card card = group.get(i);
                if (!card.finished()) continue;
                if (now - card.lastAttack > 5000L) group.remove(i--);
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
        double boxWidth = cardWidth.get() * scaleValue;
        double boxHeight = cardHeight.get() * scaleValue;
        double gap = rowGap.get() * scaleValue;

        int shown = Math.min(cards.size(), maxCards.get());
        double total = shown == 0 ? 0 : shown * (boxHeight + gap) - gap;
        setSize(boxWidth, total);

        if (shown == 0) return;

        IslandRenderer.beginFrame();

        boolean down = stackDirection.get() == StackDirection.Down;
        double step = down ? boxHeight + gap : -(boxHeight + gap);
        double cursorY = down ? y : y + total - boxHeight;

        for (int i = 0; i < shown; i++) {
            cards.get(i).render(renderer, x, cursorY, boxWidth, boxHeight, scaleValue, preview);
            cursorY += step;
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

    private static double align(ItemAlign alignment, double left, double width, double contentWidth) {
        if (alignment == ItemAlign.Center) return left + (width - contentWidth) * 0.5;
        if (alignment == ItemAlign.Right) return left + Math.max(0, width - contentWidth);
        return left;
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
        final Anim healthBar = new Anim(20, 8);

        Entity entity;
        int stage = STAGE_ENTER;
        long lastAttack;
        long stageStart = System.currentTimeMillis();
        long deathStart;
        float health;
        float maxHealth = 20;
        float absorption;
        boolean died;
        boolean ezShown;
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
            healthBar.reset(health);
        }

        /** True once the card has finished fading and is only kept around for cleanup. */
        boolean finished() {
            return stage == STAGE_GONE || stage == STAGE_REMOVE;
        }

        void snapshot() {
            if (entity == null) return;
            if (!(entity instanceof LivingEntity living) || entity.isRemoved()) return;

            health = living.getHealth();
            maxHealth = Math.max(1, living.getMaxHealth());
            absorption = living.getAbsorptionAmount();
            healthBar.set(health);

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

        boolean killedByUs() {
            return System.currentTimeMillis() - lastAttack <= ezKillWindow.get();
        }

        void die() {
            die(false);
        }

        void die(boolean forceEz) {
            if (died) return;
            died = true;

            boolean animated = deathAnimation.get();
            String ez = ezText.get();

            ezShown = animated && ez != null && !ez.isBlank()
                && (forceEz || !ezOnlyOnKill.get() || killedByUs());
            deathStart = animated ? System.currentTimeMillis() : 0;

            if (stage == STAGE_ENTER) fade.reset(1);

            enter(animated ? STAGE_DEATH : STAGE_EXIT);
        }

        double deathTotal() {
            double total = deathVanishDuration.get() + deathRotateDuration.get()
                + deathFaceFadeDuration.get() + deathEndHold.get();
            if (ezShown) total += deathTypingDuration.get();
            return Math.max(1, total);
        }

        boolean update(HudRenderer renderer, double delta) {
            boolean fading = stage == STAGE_EXIT || stage == STAGE_GONE || stage == STAGE_REMOVE;
            fade.setSpeed(fadeSpeed.get());
            fade.set(fading ? 0 : 1);
            fade.update(delta);
            healthBar.setSpeed(healthAnimSpeed.get());
            healthBar.update(delta);

            long elapsed = System.currentTimeMillis() - stageStart;

            if (entity == null) {
                health = 14;
                maxHealth = 20;
                absorption = 0;
                healthBar.reset(14);
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

            if (stage == STAGE_ENTER || stage == STAGE_ACTIVE) {
                if (entity.isRemoved() || !entity.isAlive()) {
                    die();
                } else {
                    snapshot();

                    if (stage == STAGE_ENTER) {
                        if (elapsed > enterDuration.get()) enter(STAGE_ACTIVE);
                    } else if (System.currentTimeMillis() - lastAttack > keepTicks.get() * 50L) {
                        enter(STAGE_EXIT);
                    }
                }
            } else if (stage == STAGE_DEATH) {
                if (elapsed > deathTotal()) enter(STAGE_GONE);
            } else if (stage == STAGE_EXIT) {
                if (elapsed > exitDuration.get() || fade.get() < 0.02) enter(STAGE_GONE);
            }

            if (fading) return fade.get() >= 0.02;

            return true;
        }

        void render(HudRenderer renderer, double originX, double originY, double boxWidth, double boxHeight,
                    double unit, boolean preview) {
            double radiusValue = radius.get() * unit;
            double alpha = fade.get();
            if (alpha <= 0.01) return;

            long now = System.currentTimeMillis();
            double elapsed = now - stageStart;
            boolean inDeath = deathStart > 0 && (stage == STAGE_DEATH || stage == STAGE_GONE);
            long deathElapsed = inDeath ? now - deathStart : 0;

            double vanish = 1.0;
            double faceFade = 1.0;
            double faceRotation = 0;
            double ezAlpha = 0;

            if (inDeath) {
                double d1 = deathVanishDuration.get();
                double d2 = deathRotateDuration.get();
                double d3 = deathFaceFadeDuration.get();
                double d4 = ezShown ? deathTypingDuration.get() : 0;

                // Everything but the face disappears, then the face turns while still fully
                // opaque, and only after the turn finishes does it fade away.
                vanish = 1.0 - stageProgress(deathElapsed, 0, d1);
                double rotate = stageProgress(deathElapsed, d1, d1 + d2);
                faceRotation = deathRotateAngle.get() * rotate;
                faceFade = 1.0 - stageProgress(deathElapsed, d1 + d2, d1 + d2 + d3);

                if (ezShown) {
                    double typing = stageProgress(deathElapsed, d1 + d2 + d3, d1 + d2 + d3 + d4);
                    ezAlpha = Math.min(1.0, typing * 5);
                }
            }

            if (cardShadow.get()) {
                IslandRenderer.shadow(originX, originY, boxWidth, boxHeight, radiusValue, shadowStrength.get());
            }

            Background style = background.get();
            if (style == Background.Glass) {
                IslandRenderer.glass(originX, originY, boxWidth, boxHeight, radiusValue,
                    glassBlur.get(), backgroundOpacity.get(), (int) (glassTint.get() * alpha),
                    border.get(), glassHighlight.get());
            } else {
                GpuTextureView view = Assets.texture(style.path);
                if (view != null) {
                    RoundRectRenderer.textured(originX, originY, boxWidth, boxHeight,
                        radiusValue, radiusValue, radiusValue, radiusValue,
                        view, bgU1.get(), bgV1.get(), bgU2.get(), bgV2.get(), new Color(255, 255, 255, 0),
                        (int) (backgroundOpacity.get() * alpha));

                    RoundRectRenderer.fill(originX, originY, boxWidth, boxHeight,
                        radiusValue, radiusValue, radiusValue, radiusValue,
                        new Color(tintColor.get().r, tintColor.get().g, tintColor.get().b,
                            (int) (tintAlpha.get() * alpha)));
                }
            }

            if (border.get()) {
                IslandRenderer.border(originX, originY, boxWidth, boxHeight, radiusValue, borderWidth.get(),
                    new Color(borderColor.get().r, borderColor.get().g, borderColor.get().b,
                        (int) (borderColor.get().a * alpha)));
            }

            double pad = padding.get() * unit;
            boolean withFace = showFace.get();
            double facePixels = withFace ? faceSize.get() * unit : 0;
            double faceSpacing = withFace ? faceGap.get() * unit : 0;
            double infoX = originX + pad + (withFace ? facePixels + faceSpacing : 0);
            double infoWidth = Math.max(4, originX + boxWidth - pad - infoX);

            if (withFace) {
                double faceX = originX + pad + faceOffsetX.get() * unit;
                double faceY = originY + (boxHeight - facePixels) * 0.5 + faceOffsetY.get() * unit;

                if (faceFade > 0.01) {
                    GpuTextureView face = faceView(faceId);
                    double faceColor = alpha * faceFade;
                    SettingColor faceSource = faceTint.get();
                    double faceRadius = facePixels * faceRadiusFactor.get();
                    int faceOpacity = (int) (faceAlpha.get() * faceColor);

                    if (face != null) {
                        RoundRectRenderer.texturedPixel(faceX, faceY, facePixels, facePixels,
                            faceRadius, faceRadius, faceRadius, faceRadius,
                            face, 0.125, 0.125, 0.25, 0.25,
                            new Color(faceSource.r, faceSource.g, faceSource.b, faceSource.a),
                            faceOpacity, faceRotation);
                    } else {
                        IslandRenderer.round(faceX, faceY, facePixels, facePixels, faceRadius,
                            new Color(24, 27, 36, (int) (faceOpacity * 0.92)));
                    }
                }
            }

            double nameTextScale = nameScale.get() * unit;
            double barTextScaleValue = barTextScale.get() * unit;
            double barPixels = barHeight.get() * unit;
            boolean withName = showName.get();
            double nameGapPixels = withName ? nameGap.get() * unit : 0;
            double rowGapPixels = itemRowGap.get() * unit;
            double rowHeight = Math.max(barPixels, itemCell.get() * unit);
            boolean withItems = showItems.get();

            if (vanish > 0.01) {
                Color nameTint = new Color(nameColor.get().r, nameColor.get().g, nameColor.get().b,
                    (int) (nameColor.get().a * alpha * vanish));
                Color barTint = new Color(barColor.get().r, barColor.get().g, barColor.get().b,
                    (int) (barColor.get().a * alpha * vanish));
                Color trackTint = new Color(barBackground.get().r, barBackground.get().g, barBackground.get().b,
                    (int) (barBackground.get().a * alpha * vanish));

                double nameHeight = withName ? IslandRenderer.textHeight(renderer, nameTextScale) : 0;
                double blockHeight = nameHeight + nameGapPixels + barPixels
                    + (withItems ? rowGapPixels + rowHeight : 0);
                double blockTop = originY + (boxHeight - blockHeight) * 0.5 + contentOffsetY.get() * unit;

                if (withName) {
                    String nameText = fit(renderer, name, infoWidth, nameTextScale);
                    double nameWidth = IslandRenderer.textWidth(renderer, nameText, nameTextScale);

                    IslandRenderer.text(renderer, nameText,
                        align(nameAlign.get(), infoX, infoWidth, nameWidth), blockTop,
                        nameTint, nameShadow.get(), nameTextScale);
                }

                double barY = blockTop + nameHeight + nameGapPixels;
                double barRadius = barPixels * 0.5 * barRadiusFactor.get();

                IslandRenderer.round(infoX, barY, infoWidth, barPixels, barRadius, trackTint);

                double shownHealth = animateHealth.get() ? healthBar.get() : health;
                double ratio = Math.max(0, Math.min(1, shownHealth / maxHealth));
                double fillWidth = infoWidth * ratio;

                if (fillWidth > 0.5) {
                    IslandRenderer.round(infoX, barY, fillWidth, barPixels,
                        Math.min(barRadius, fillWidth * 0.5), barTint);
                }

                if (showAbsorption.get() && absorption > 0) {
                    double absorptionRatio = Math.max(0, Math.min(1 - ratio, absorption / maxHealth));
                    double absorptionWidth = infoWidth * absorptionRatio;

                    if (absorptionWidth > 0.5) {
                        SettingColor source = absorptionColor.get();
                        Color absorptionTint = new Color(source.r, source.g, source.b,
                            (int) (source.a * alpha * vanish));
                        IslandRenderer.round(infoX + fillWidth, barY, absorptionWidth, barPixels,
                            Math.min(barRadius, absorptionWidth * 0.5), absorptionTint);
                    }
                }

                if (showHealthText.get()) {
                    double healthForText = showAbsorption.get() ? health + absorption : health;
                    int current = Math.max(0, MathHelper.ceil(healthForText));
                    int maximum = MathHelper.ceil(maxHealth);
                    String healthText = current + "/" + maximum;
                    double healthWidth = IslandRenderer.textWidth(renderer, healthText, barTextScaleValue);

                    if (healthWidth < infoWidth - 4 * unit) {
                        SettingColor source = healthTextColor.get();
                        IslandRenderer.text(renderer, healthText, infoX + (infoWidth - healthWidth) * 0.5,
                            barY + (barPixels - IslandRenderer.textHeight(renderer, barTextScaleValue)) * 0.5,
                            new Color(source.r, source.g, source.b, (int) (source.a * alpha * vanish)),
                            barTextShadow.get(), barTextScaleValue);
                    }
                }

                if (withItems) {
                    renderItems(renderer, infoX, infoWidth, blockTop + nameHeight + nameGapPixels + barPixels + rowGapPixels,
                        rowHeight, unit, alpha * vanish);
                }
            }

            if (ezShown && ezAlpha > 0.01) {
                String full = ezText.get();
                double ezStart = deathVanishDuration.get() + deathRotateDuration.get() + deathFaceFadeDuration.get();
                double typing = stageProgress(deathElapsed, ezStart, ezStart + deathTypingDuration.get());
                int visible = Math.max(0, Math.min(full.length(), (int) Math.floor(typing * full.length())));

                if (visible > 0) {
                    double ezTextScale = ezScale.get() * unit;
                    double ezWidth = IslandRenderer.textWidth(renderer, full, ezTextScale);
                    double ezHeight = IslandRenderer.textHeight(renderer, ezTextScale);
                    SettingColor source = ezColor.get();

                    IslandRenderer.text(renderer, full.substring(0, visible),
                        originX + (boxWidth - ezWidth) * 0.5 + ezOffsetX.get() * unit,
                        originY + (boxHeight - ezHeight) * 0.5 + ezOffsetY.get() * unit,
                        new Color(source.r, source.g, source.b, (int) (source.a * ezAlpha * alpha)),
                        ezShadow.get(), ezTextScale);
                }
            }
        }

        private void renderItems(HudRenderer renderer, double infoX, double infoWidth, double rowTop,
                                 double rowHeight, double unit, double fadeAlpha) {
            boolean anyArmor = showArmor.get() && hasArmor(armor);
            double cell = itemCell.get() * unit;
            double drawScale = unit * itemScale.get();
            double drawSize = 16 * drawScale;
            double blackSpan = itemDivider.get() * unit;
            double barThickness = dividerThickness.get() * unit;
            double grayWidth = armorDivider.get() * unit;

            int armorCount = 0;
            if (anyArmor) {
                for (ItemStack stack : armor) {
                    if (!stack.isEmpty()) armorCount++;
                }
                armorCount = Math.max(armorCount, 4);
            }

            boolean offhandPresent = showOffhand.get() && !offhand.isEmpty();
            boolean mainhandPresent = showMainhand.get() && !mainhand.isEmpty();
            boolean armorSide = anyArmor && (offhandPresent || mainhandPresent);

            double armorWidth = armorCount * cell;
            double offhandWidth = offhandPresent ? cell : 0;
            double mainhandWidth = mainhandPresent ? cell : 0;
            double beforeWidth = armorSide && offhandPresent ? blackSpan
                : (!anyArmor && offhandPresent && mainhandPresent ? blackSpan : 0);
            double afterWidth = armorSide && mainhandPresent ? blackSpan : 0;

            double totalWidth = offhandWidth + beforeWidth + armorWidth + afterWidth + mainhandWidth;
            if (totalWidth > infoWidth && totalWidth > 0) {
                double shrink = infoWidth / totalWidth;
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
                totalWidth = infoWidth;
            }

            double inset = Math.max(0, (cell - drawSize) * 0.5);
            double itemY = rowTop + (rowHeight - drawSize) * 0.5;
            double cursorX = align(itemAlign.get(), infoX, infoWidth, totalWidth);

            double dividerRadius = barThickness * 0.5 * dividerRadiusFactor.get();
            double pull = Math.min(dividerInset.get() * unit, Math.max(0, (rowHeight - barThickness) * 0.5));
            double dividerTop = rowTop + pull;
            double dividerHeight = Math.max(barThickness, rowHeight - pull * 2);

            SettingColor blackSource = blackDividerColor.get();
            SettingColor graySource = dividerColor.get();
            Color blackBar = new Color(blackSource.r, blackSource.g, blackSource.b, (int) (blackSource.a * fadeAlpha));
            Color grayBar = new Color(graySource.r, graySource.g, graySource.b, (int) (graySource.a * fadeAlpha));

            if (offhandPresent) {
                drawItem(renderer, offhand, cursorX + inset, itemY, drawScale);
                cursorX += offhandWidth;
            }

            if (beforeWidth > 0) {
                IslandRenderer.round(cursorX + (blackSpan - barThickness) * 0.5, dividerTop, barThickness,
                    dividerHeight, dividerRadius, blackBar);
                cursorX += beforeWidth;
            }

            if (anyArmor) {
                double blockStart = cursorX;

                for (int i = 0; i < armor.length; i++) {
                    double cellX = blockStart + i * cell;

                    if (!armor[i].isEmpty()) {
                        drawItem(renderer, armor[i], cellX + inset, itemY, drawScale);
                    }

                    if (i < armor.length - 1 && grayWidth > 0) {
                        IslandRenderer.round(cellX + cell - grayWidth * 0.5, dividerTop, grayWidth,
                            dividerHeight, grayWidth * 0.5 * dividerRadiusFactor.get(), grayBar);
                    }
                }

                cursorX += armorWidth;
            }

            if (afterWidth > 0) {
                IslandRenderer.round(cursorX + (blackSpan - barThickness) * 0.5, dividerTop, barThickness,
                    dividerHeight, dividerRadius, blackBar);
                cursorX += afterWidth;
            }

            if (mainhandPresent) {
                drawItem(renderer, mainhand, cursorX + inset, itemY, drawScale);
            }
        }

        private void drawItem(HudRenderer renderer, ItemStack stack, double x, double y, double scale) {
            int pixelX = (int) Math.round(x);
            int pixelY = (int) Math.round(y);

            renderer.item(stack, pixelX, pixelY, (float) scale, true, showItemCount.get() ? null : "");
        }

        private double stageProgress(long elapsed, double from, double to) {
            if (to <= from) return elapsed >= to ? 1 : 0;
            if (elapsed <= from) return 0;
            if (elapsed >= to) return 1;
            return (elapsed - from) / (to - from);
        }
    }
}
