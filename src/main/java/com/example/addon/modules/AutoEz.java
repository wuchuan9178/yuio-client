package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class AutoEz extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Switch between message parsing and entity detection")
        .defaultValue(Mode.message)
        .build());

    private final Setting<List<String>> messages = sgGeneral.add(new StringListSetting.Builder()
        .name("messages")
        .description("Message mode: %s=victim, %m=you, %t=time, %z=pos, %c=kill count, %p=victim pops")
        .defaultValue(getDefaultMessages())
        .visible(() -> mode.get() == Mode.message)
        .build());

    private final Setting<List<String>> entityMessages = sgGeneral.add(new StringListSetting.Builder()
        .name("entity-messages")
        .description("Entity mode: %s=dead player, %t=time, %z=pos, %d=distance")
        .defaultValue(getDefaultEntityMessages())
        .visible(() -> mode.get() == Mode.entity)
        .build());

    private final Setting<Boolean> enableDeathMessages = sgGeneral.add(new BoolSetting.Builder()
        .name("enable-death-messages")
        .description("Send message when you die (both modes)")
        .defaultValue(true)
        .build());

    private final Setting<List<String>> deathMessages = sgGeneral.add(new StringListSetting.Builder()
        .name("death-messages")
        .description("%s=you, %m=killer, %t=time, %z=pos, %c=deaths by killer, %p=your pops")
        .defaultValue(List.of(
            "喵呜... %m 把 %s 杀掉了... 在 (%z) 倒下，%t，第 %c 次被 %m 欺负，pop %p 次也没用...",
            "讨厌！%m 偷袭 %s！(%z) %t，第 %c 次了，%p 图腾都救不了我，再也不理 %m 了！",
            "%s 被 %m 打倒... 在 (%z) 哭泣，%t，第 %c 次了，%p 次图腾白费，%m 坏蛋！"))
        .visible(enableDeathMessages::get)
        .build());

    private final Setting<Double> entityRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("entity-range")
        .description("Range to detect deaths (entity mode only)")
        .defaultValue(100)
        .min(1)
        .sliderMax(200)
        .visible(() -> mode.get() == Mode.entity)
        .build());

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Skip if friend involved.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> autoDisable = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-disable")
        .description("Toggle off after sending.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> debugMode = sgGeneral.add(new BoolSetting.Builder()
        .name("debug-mode")
        .description("Show parse details.")
        .defaultValue(false)
        .build());

    private final Random random = new Random();
    private Mode lastMode = null;

    private static final Map<String, Integer> killCounts = new HashMap<>();
    private static final Map<String, Integer> deathCounts = new HashMap<>();
    private static final Map<String, PopRecord> totemPops = new HashMap<>();

    public AutoEz() {
        super(AddonTemplate.ASSIST, "auto-ez", "Catgirl kill/death messages with dual modes");
    }

    private List<String> getDefaultMessages() {
        return List.of(
            "喵哈哈~ %s 被 %m 打败了喵！在 (%z) 倒下了~ 第 %c 次了，pop 了 %p 次图腾也救不了你喵！%t",
            "哼！%s 敢惹 %m？在 (%z) 打趴你，第 %c 次，%p 图腾白费~ %t",
            "%s 酱又被 %m 欺负了喵，(%z)，%t，第 %c 次击杀，pop %p 次也没用~"
        );
    }

    private List<String> getDefaultEntityMessages() {
        return List.of(
            "检测到死亡：%s 在 (%z) 倒下了，距离 %d 格！%t",
            "哇！%s 挂了！位置 (%z)，距离 %d，%t",
            "有人死了！%s 在 (%z)，%d 格外，%t"
        );
    }

    @Override
    public void onActivate() {
        if (lastMode == null) lastMode = mode.get();
        if (lastMode != mode.get()) {
            lastMode = mode.get();
            ChatUtils.infoPrefix("AutoEz", "已切换到 " + mode.get().name() + " 模式！请根据模式设置对应的消息模板。");
        }
    }

    @Override
    public void onDeactivate() {
        killCounts.clear();
        deathCounts.clear();
        totemPops.clear();
    }

    private void sendDeathMessage(String killer) {
        if (!enableDeathMessages.get()) return;
        List<String> msgs = deathMessages.get();
        if (msgs.isEmpty()) return;

        if (killer == null || killer.isEmpty()) killer = "某人";

        String pos = formatPos(mc.player);
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        int pops = getRecentPops(mc.player.getName().getString());
        totemPops.remove(mc.player.getName().getString());
        int deathsByKiller = killer.equals("某人") ? 0 : deathCounts.getOrDefault(killer, 0);

        String msg = msgs.get(random.nextInt(msgs.size()))
            .replace("%s", mc.player.getName().getString())
            .replace("%m", killer)
            .replace("%t", time)
            .replace("%z", pos)
            .replace("%c", String.valueOf(deathsByKiller))
            .replace("%p", String.valueOf(pops));
        ChatUtils.sendPlayerMsg(msg);
        if (debugMode.get()) ChatUtils.infoPrefix("AutoEz", "发送死亡嘲讽: " + msg);
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.world == null || mc.player == null) return;

        Packet<?> packet = event.packet;

        if (packet instanceof EntityStatusS2CPacket statusPacket) {
            int status = getPacketStatus(statusPacket);
            int entityId = getPacketEntityId(statusPacket);

            if (status == 35) {
                Entity entity = mc.world.getEntityById(entityId);
                if (entity instanceof PlayerEntity p) {
                    String name = p.getName().getString();
                    totemPops.compute(name, (k, v) -> {
                        if (v == null) return new PopRecord(1, Instant.now());
                        v.count++;
                        v.lastTime = Instant.now();
                        return v;
                    });
                    if (debugMode.get()) ChatUtils.infoPrefix("AutoEz", "图腾弹出: " + name + " (" + totemPops.get(name).count + ")");
                }
                return;
            }

            if (status == 3 && mode.get() == Mode.entity) { // 实体死亡
                Entity entity = mc.world.getEntityById(entityId);
                if (entity instanceof PlayerEntity dead) {
                    if (dead.getUuid().equals(mc.player.getUuid())) {
                        if (debugMode.get()) ChatUtils.infoPrefix("AutoEz", "检测到自己死亡，发送遗言");
                        if (enableDeathMessages.get()) sendDeathMessage(null);
                        return;
                    }

                    double dist = mc.player.distanceTo(dead);
                    if (dist > entityRange.get()) return;
                    if (ignoreFriends.get() && Friends.get().isFriend(dead)) return;

                    String name = dead.getName().getString();
                    String pos = formatPos(dead);
                    String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                    String distStr = String.format("%.1f", dist);
                    List<String> msgs = entityMessages.get();
                    if (!msgs.isEmpty()) {
                        String msg = msgs.get(random.nextInt(msgs.size()))
                            .replace("%s", name).replace("%t", time).replace("%z", pos).replace("%d", distStr);
                        ChatUtils.sendPlayerMsg(msg);
                        if (debugMode.get()) ChatUtils.infoPrefix("AutoEz", "发送实体嘲讽: " + msg);
                        if (autoDisable.get()) toggle();
                    }
                }
                return;
            }
        }

        if (mode.get() == Mode.message && packet instanceof GameMessageS2CPacket msgPacket) {
            if (msgPacket.overlay()) return;
            Text text = msgPacket.content();
            if (text == null) return;

            String raw = text.getString();
            if (debugMode.get()) ChatUtils.infoPrefix("AutoEz", "系统消息: " + raw);

            String lower = raw.toLowerCase();
            if (!lower.contains("was") && !raw.contains("被")) {
                if (debugMode.get()) ChatUtils.infoPrefix("AutoEz", "非死亡消息，跳过");
                return;
            }

            String my = mc.player.getName().getString();

            if (raw.contains("You") && !raw.contains(my)) {
                if (debugMode.get()) ChatUtils.infoPrefix("AutoEz", "检测到自己死亡（You），提取杀手");
                String killer = extractKillerFromMessage(raw);
                if (killer == null) {
                    if (debugMode.get()) ChatUtils.infoPrefix("AutoEz", "未找到杀手，使用默认");
                    killer = "某人";
                }
                if (ignoreFriends.get() && killer != null && Friends.get().isFriend(findPlayer(killer))) return;
                if (enableDeathMessages.get()) sendDeathMessage(killer);
                if (autoDisable.get()) toggle();
                return;
            }

            List<String> names = new ArrayList<>();
            for (PlayerEntity p : mc.world.getPlayers()) {
                String name = p.getName().getString();
                if (raw.contains(name)) names.add(name);
            }

            if (names.size() < 2) {
                if (debugMode.get()) ChatUtils.infoPrefix("AutoEz", "玩家名不足2个，跳过（可能是环境死亡或无凶手）");
                return;
            }

            names.sort(Comparator.comparingInt(raw::indexOf));
            String victim = names.get(0);
            String killer = names.get(1);
            boolean iKill = killer.equals(my);
            boolean iDie = victim.equals(my);

            if (!iKill && !iDie) return;

            PlayerEntity killerP = findPlayer(killer);
            if (killerP == null && !iKill) {
                if (iDie) {
                    if (ignoreFriends.get() && killerP != null && Friends.get().isFriend(killerP)) return;
                    if (enableDeathMessages.get()) sendDeathMessage(killer);
                    if (autoDisable.get()) toggle();
                }
                return;
            }

            if (ignoreFriends.get()) {
                PlayerEntity other = iKill ? findPlayer(victim) : killerP;
                if (other != null && Friends.get().isFriend(other)) return;
            }

            String pos = iKill ? (findPlayer(victim) != null ? formatPos(findPlayer(victim)) : "未知") : formatPos(mc.player);
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            int count = iKill ? killCounts.merge(victim, 1, Integer::sum) : deathCounts.merge(killer, 1, Integer::sum);
            String deadName = iKill ? victim : my;
            int pops = getRecentPops(deadName);
            totemPops.remove(deadName);

            if (debugMode.get())
                ChatUtils.infoPrefix("AutoEz", String.format("死者=%s 杀手=%s 计数=%d 图腾=%d 类型=%s", victim, killer, count, pops, iKill ? "击杀" : "死亡"));

            if (iKill) {
                List<String> msgs = messages.get();
                if (!msgs.isEmpty()) {
                    String msg = msgs.get(random.nextInt(msgs.size()))
                        .replace("%s", victim).replace("%m", killer).replace("%t", time)
                        .replace("%z", pos).replace("%c", String.valueOf(count)).replace("%p", String.valueOf(pops));
                    ChatUtils.sendPlayerMsg(msg);
                    if (debugMode.get()) ChatUtils.infoPrefix("AutoEz", "发送击杀嘲讽: " + msg);
                }
            } else if (iDie && enableDeathMessages.get()) {
                sendDeathMessage(killer);
            }

            if (autoDisable.get()) toggle();
        }
    }


    private String extractKillerFromMessage(String raw) {
        // 优先尝试常见关键词
        String[] markers = {" by ", " was ", "被", "死于", "Slain by ", "killed by "};
        String killer = null;
        for (String marker : markers) {
            int idx = raw.indexOf(marker);
            if (idx != -1) {
                String after = raw.substring(idx + marker.length());
                String[] words = after.trim().split("\\s+");
                if (words.length > 0 && words[0].matches("[A-Za-z0-9_]{1,16}")) {
                    killer = words[0];
                    break;
                }
            }
        }
        if (killer == null) {
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (raw.contains(p.getName().getString())) {
                    killer = p.getName().getString();
                    break;
                }
            }
        }
        return killer;
    }

    private int getPacketStatus(EntityStatusS2CPacket packet) {
        try {
            return (int) EntityStatusS2CPacket.class.getMethod("getStatus").invoke(packet);
        } catch (Exception ignored) {}
        try {
            return (int) EntityStatusS2CPacket.class.getMethod("getStatus").invoke(packet, (Object[]) null);
        } catch (Exception ignored) {}
        return getIntField(packet);
    }

    private int getPacketEntityId(EntityStatusS2CPacket packet) {
        try {
            return (int) EntityStatusS2CPacket.class.getMethod("getEntityId").invoke(packet);
        } catch (Exception ignored) {}
        try {
            return (int) EntityStatusS2CPacket.class.getMethod("entityId").invoke(packet);
        } catch (Exception ignored) {}
        return getIntField(packet);
    }

    private int getIntField(EntityStatusS2CPacket packet) {
        for (Field f : EntityStatusS2CPacket.class.getDeclaredFields()) {
            if (f.getType() == int.class) {
                f.setAccessible(true);
                try { return f.getInt(packet); } catch (Exception ignored) {}
            }
        }
        return -1;
    }

    private int getRecentPops(String name) {
        PopRecord r = totemPops.get(name);
        if (r == null) return 0;
        if (Duration.between(r.lastTime, Instant.now()).getSeconds() > 30L) {
            totemPops.remove(name);
            return 0;
        }
        return r.count;
    }

    private PlayerEntity findPlayer(String name) {
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p.getName().getString().equals(name)) return p;
        }
        return null;
    }

    private String formatPos(PlayerEntity p) {
        return String.format("%d, %d, %d", p.getBlockX(), p.getBlockY(), p.getBlockZ());
    }

    private static class PopRecord {
        int count;
        Instant lastTime;
        PopRecord(int c, Instant t) { this.count = c; this.lastTime = t; }
    }

    public enum Mode { message, entity }
}
