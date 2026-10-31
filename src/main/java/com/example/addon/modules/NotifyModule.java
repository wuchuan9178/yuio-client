package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotifyModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPop = settings.createGroup("Pop");
    private final SettingGroup sgRange = settings.createGroup("Render Range");
    private final SettingGroup sgArmor = settings.createGroup("Armor");
    private final SettingGroup sgColors = settings.createGroup("Colors");

    private final Setting<Boolean> sendToChat = sgGeneral.add(new BoolSetting.Builder()
        .name("send-to-chat")
        .description("Print every notification into the chat.")
        .defaultValue(true)
        .build());

    private final Setting<String> prefix = sgGeneral.add(new StringSetting.Builder()
        .name("prefix")
        .description("Prefix prepended to every notification.")
        .defaultValue("[yuio] ")
        .build());

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug")
        .description("Log every detected event to the chat as well.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> popEnabled = sgPop.add(new BoolSetting.Builder()
        .name("pop")
        .description("Notify when a player pops a totem.")
        .defaultValue(true)
        .build());

    private final Setting<String> popFormat = sgPop.add(new StringSetting.Builder()
        .name("pop-format")
        .description("Placeholders: {player}, {count}, {causer}.")
        .defaultValue("pop：{player} Already pop {count} ，Caused by {causer}")
        .build());

    private final Setting<Boolean> popSelf = sgPop.add(new BoolSetting.Builder()
        .name("pop-self")
        .description("Also notify when you yourself pop a totem.")
        .defaultValue(false)
        .build());

    private final Setting<Double> popCauserWindow = sgPop.add(new DoubleSetting.Builder()
        .name("causer-window")
        .description("How many seconds a damage event stays associated with its attacker.")
        .defaultValue(8.0)
        .min(0.0)
        .sliderRange(1.0, 60.0)
        .build());

    private final Setting<Double> popReset = sgPop.add(new DoubleSetting.Builder()
        .name("count-reset")
        .description("Reset the pop counter of a player after this many seconds without a pop.")
        .defaultValue(15.0)
        .min(1.0)
        .sliderRange(1.0, 120.0)
        .build());

    private final Setting<String> unknownCauser = sgPop.add(new StringSetting.Builder()
        .name("unknown-causer")
        .description("Printed for {causer} when the attacker could not be resolved.")
        .defaultValue("unknown")
        .build());

    private final Setting<Boolean> rangeEnabled = sgRange.add(new BoolSetting.Builder()
        .name("range")
        .description("Notify when a player enters your render range.")
        .defaultValue(true)
        .build());

    private final Setting<String> rangeFormat = sgRange.add(new StringSetting.Builder()
        .name("range-format")
        .description("Placeholders: {player}, {distance}, {armor}.")
        .defaultValue("Player {player} has entered your area!")
        .build());

    private final Setting<Double> range = sgRange.add(new DoubleSetting.Builder()
        .name("radius")
        .description("Radius in blocks that counts as your area.")
        .defaultValue(64.0)
        .min(4.0)
        .sliderRange(4.0, 256.0)
        .build());

    private final Setting<Double> rangeCooldown = sgRange.add(new DoubleSetting.Builder()
        .name("cooldown")
        .description("Minimum seconds between two notifications for the same player.")
        .defaultValue(3.0)
        .min(0.0)
        .sliderRange(0.0, 30.0)
        .build());

    private final Setting<Boolean> ignoreFriends = sgRange.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Never notify about players on your friends list.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> armorEnabled = sgArmor.add(new BoolSetting.Builder()
        .name("armor")
        .description("Notify when an armor value drops below the threshold.")
        .defaultValue(true)
        .build());

    private final Setting<String> armorFormatOthers = sgArmor.add(new StringSetting.Builder()
        .name("armor-format")
        .description("Placeholders: {player}, {armor}, {threshold}.")
        .defaultValue("Player {player} current armor value is too low.")
        .build());

    private final Setting<String> armorFormatSelf = sgArmor.add(new StringSetting.Builder()
        .name("armor-format-self")
        .description("Placeholders: {armor}, {threshold}.")
        .defaultValue("Your current armor value is too low.")
        .build());

    private final Setting<Integer> armorThreshold = sgArmor.add(new IntSetting.Builder()
        .name("armor-threshold")
        .description("Armor value below which a notification is sent.")
        .defaultValue(5)
        .min(0)
        .sliderRange(0, 20)
        .build());

    private final Setting<Boolean> armorSelf = sgArmor.add(new BoolSetting.Builder()
        .name("armor-self")
        .description("Check your own armor.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> armorOthers = sgArmor.add(new BoolSetting.Builder()
        .name("armor-others")
        .description("Check the armor of nearby players.")
        .defaultValue(true)
        .build());

    private final Setting<Double> armorRadius = sgArmor.add(new DoubleSetting.Builder()
        .name("armor-radius")
        .description("Only players inside this radius are checked.")
        .defaultValue(32.0)
        .min(4.0)
        .sliderRange(4.0, 128.0)
        .build());

    private final Setting<Double> armorCooldown = sgArmor.add(new DoubleSetting.Builder()
        .name("armor-cooldown")
        .description("Minimum seconds between two notifications for the same player.")
        .defaultValue(5.0)
        .min(0.0)
        .sliderRange(0.0, 60.0)
        .build());

    private final Setting<SettingColor> prefixColor = sgColors.add(new ColorSetting.Builder()
        .name("prefix-color").defaultValue(new SettingColor(150, 156, 170)).build());
    private final Setting<SettingColor> textColor = sgColors.add(new ColorSetting.Builder()
        .name("text-color").defaultValue(new SettingColor(230, 230, 230)).build());
    private final Setting<SettingColor> playerColor = sgColors.add(new ColorSetting.Builder()
        .name("player-color").description("Color of {player}.")
        .defaultValue(new SettingColor(255, 216, 102)).build());
    private final Setting<SettingColor> causerColor = sgColors.add(new ColorSetting.Builder()
        .name("causer-color").description("Color of {causer}.")
        .defaultValue(new SettingColor(102, 178, 255)).build());
    private final Setting<SettingColor> countColor = sgColors.add(new ColorSetting.Builder()
        .name("count-color").description("Color of {count}.")
        .defaultValue(new SettingColor(255, 154, 60)).build());
    private final Setting<SettingColor> distanceColor = sgColors.add(new ColorSetting.Builder()
        .name("distance-color").description("Color of {distance}.")
        .defaultValue(new SettingColor(170, 170, 170)).build());
    private final Setting<SettingColor> armorColor = sgColors.add(new ColorSetting.Builder()
        .name("armor-color").description("Color of {armor}.")
        .defaultValue(new SettingColor(255, 85, 85)).build());
    private final Setting<SettingColor> thresholdColor = sgColors.add(new ColorSetting.Builder()
        .name("threshold-color").description("Color of {threshold}.")
        .defaultValue(new SettingColor(255, 170, 85)).build());

    private static final Pattern PLACEHOLDER =
        Pattern.compile("\\{(player|causer|count|distance|armor|threshold)}");

    private record Attack(String attacker, long time) {
    }

    private final Map<String, Attack> lastAttacker = new ConcurrentHashMap<>();
    private final Map<String, Integer> popCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> popSeen = new ConcurrentHashMap<>();
    private final Set<String> inRange = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> rangeSeen = new ConcurrentHashMap<>();
    private final Set<String> lowArmor = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> armorSeen = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Text> pending = new ConcurrentLinkedQueue<>();

    public NotifyModule() {
        super(AddonTemplate.OTHER, "notifications", "Chat notifications for pops, nearby players and low armor.");
    }

    @Override
    public void onActivate() {
        reset();
    }

    @Override
    public void onDeactivate() {
        reset();
    }

    private void reset() {
        lastAttacker.clear();
        popCounts.clear();
        popSeen.clear();
        inRange.clear();
        rangeSeen.clear();
        lowArmor.clear();
        armorSeen.clear();
        pending.clear();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof EntityStatusS2CPacket packet) {
            if (packet.getStatus() == EntityStatuses.USE_TOTEM_OF_UNDYING) handlePop(packet);
        } else if (event.packet instanceof EntityDamageS2CPacket packet) {
            handleDamage(packet);
        }
    }

    private void handleDamage(EntityDamageS2CPacket packet) {
        if (mc.world == null || mc.player == null) return;

        int source = packet.sourceCauseId();
        if (source < 0) return;

        Entity victim = mc.world.getEntityById(packet.entityId());
        Entity attacker = mc.world.getEntityById(source);
        if (!(victim instanceof PlayerEntity victimPlayer) || !(attacker instanceof PlayerEntity attackerPlayer)) return;
        if (victim == attacker) return;

        String victimName = victimPlayer.getName().getString();
        String attackerName = attackerPlayer.getName().getString();
        lastAttacker.put(victimName, new Attack(attackerName, System.currentTimeMillis()));

        if (debug.get()) emitLine("debug: " + victimName + " took damage from " + attackerName);
    }

    private void handlePop(EntityStatusS2CPacket packet) {
        if (!popEnabled.get()) return;
        if (mc.world == null || mc.player == null) return;

        Entity entity = packet.getEntity(mc.world);
        if (!(entity instanceof PlayerEntity player)) return;

        String name = player.getName().getString();
        boolean self = mc.player.getName().getString().equals(name);
        if (self && !popSelf.get()) return;

        long now = System.currentTimeMillis();
        long resetMs = (long) (popReset.get() * 1000.0);
        Long last = popSeen.get(name);
        int count = last == null || now - last > resetMs ? 1 : popCounts.getOrDefault(name, 0) + 1;
        popSeen.put(name, now);
        popCounts.put(name, count);

        emit(popFormat.get(), name, resolveCauser(name, now), count, 0.0, player.getArmor());
    }

    private String resolveCauser(String victim, long now) {
        Attack attack = lastAttacker.get(victim);
        if (attack == null) return unknownCauser.get();
        if (now - attack.time() > (long) (popCauserWindow.get() * 1000.0)) return unknownCauser.get();
        return attack.attacker();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        flush();

        if (mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();
        if (rangeEnabled.get()) checkRange(now);
        if (armorEnabled.get()) checkArmor(now);
        prune(now);
    }

    private void checkRange(long now) {
        double radius = range.get();
        double exitRadius = radius * 1.2;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || player.isSpectator()) continue;
            if (ignoreFriends.get() && Friends.get().isFriend(player)) continue;

            String name = player.getName().getString();
            double distance = mc.player.distanceTo(player);

            if (distance <= radius) {
                if (inRange.add(name) && allow(rangeSeen, name, rangeCooldown.get(), now)) {
                    emit(rangeFormat.get(), name, "", 0, distance, player.getArmor());
                }
            } else if (distance > exitRadius) {
                inRange.remove(name);
            }
        }
    }

    private void checkArmor(long now) {
        int threshold = armorThreshold.get();
        int recover = Math.min(20, threshold + 2);
        if (threshold <= 0) return;

        for (PlayerEntity player : mc.world.getPlayers()) {
            boolean self = player == mc.player;

            if (self) {
                if (!armorSelf.get()) continue;
            } else {
                if (!armorOthers.get() || player.isSpectator()) continue;
                if (mc.player.distanceTo(player) > armorRadius.get()) continue;
            }

            String name = self ? mc.player.getName().getString() : player.getName().getString();
            int armor = player.getArmor();

            if (armor < threshold) {
                if (lowArmor.add(name) && allow(armorSeen, name, armorCooldown.get(), now)) {
                    emit(self ? armorFormatSelf.get() : armorFormatOthers.get(), name, "", 0,
                        self ? 0.0 : mc.player.distanceTo(player), armor);
                }
            } else if (armor >= recover) {
                lowArmor.remove(name);
            }
        }
    }

    private void prune(long now) {
        long keep = 120_000L;
        lastAttacker.values().removeIf(attack -> now - attack.time() > keep);
        rangeSeen.values().removeIf(time -> now - time > keep);
        armorSeen.values().removeIf(time -> now - time > keep);
        popSeen.values().removeIf(time -> now - time > keep);
    }

    private boolean allow(Map<String, Long> map, String key, double cooldownSeconds, long now) {
        long cooldown = (long) (cooldownSeconds * 1000.0);
        Long last = map.get(key);
        if (last != null && now - last < cooldown) return false;
        map.put(key, now);
        return true;
    }

    private void emit(String template, String player, String causer, int count, double distance, int armor) {
        MutableText line = Text.empty()
            .append(Text.literal(prefix.get()).setStyle(Style.EMPTY.withColor(prefixColor.get().getPacked())));

        Matcher matcher = PLACEHOLDER.matcher(template);
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last) {
                line.append(Text.literal(template.substring(last, matcher.start()))
                    .setStyle(Style.EMPTY.withColor(textColor.get().getPacked())));
            }
            String key = matcher.group(1);
            line.append(Text.literal(placeholderValue(key, player, causer, count, distance, armor))
                .setStyle(Style.EMPTY.withColor(placeholderColor(key))));
            last = matcher.end();
        }
        if (last < template.length()) {
            line.append(Text.literal(template.substring(last))
                .setStyle(Style.EMPTY.withColor(textColor.get().getPacked())));
        }

        emitLine(line);
    }

    private String placeholderValue(String key, String player, String causer, int count, double distance, int armor) {
        return switch (key) {
            case "player" -> player;
            case "causer" -> causer;
            case "count" -> String.valueOf(count);
            case "distance" -> String.format(Locale.ROOT, "%.1f", distance);
            case "armor" -> String.valueOf(armor);
            default -> String.valueOf(armorThreshold.get());
        };
    }

    private int placeholderColor(String key) {
        return switch (key) {
            case "player" -> playerColor.get().getPacked();
            case "causer" -> causerColor.get().getPacked();
            case "count" -> countColor.get().getPacked();
            case "distance" -> distanceColor.get().getPacked();
            case "armor" -> armorColor.get().getPacked();
            default -> thresholdColor.get().getPacked();
        };
    }

    private void emitLine(String line) {
        emitLine(Text.literal(line).setStyle(Style.EMPTY.withColor(textColor.get().getPacked())));
    }

    private void emitLine(Text line) {
        if (!sendToChat.get()) return;
        pending.add(line);
    }

    private void flush() {
        if (pending.isEmpty()) return;

        Text line;
        while ((line = pending.poll()) != null) {
            if (mc.inGameHud == null) continue;
            mc.inGameHud.getChatHud().addMessage(line);
        }
    }
}
