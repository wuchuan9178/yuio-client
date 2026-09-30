package com.example.addon.hud;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.systems.friends.Friend;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import static meteordevelopment.meteorclient.MeteorClient.mc;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FriendHud extends GlassHud {
    public static final HudElementInfo<FriendHud> INFO = new HudElementInfo<>(
        AddonTemplate.HUD_GROUP, "friend-hud", "Meteor friends, highlighted when nearby.", FriendHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum Online {
        Nearby("nearby"),
        SameServer("same-server"),
        Anywhere("anywhere");

        private final String name;

        Online(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private final Setting<Online> online = sgGeneral.add(new EnumSetting.Builder<Online>()
        .name("online-mode")
        .description("What counts as online: nearby = inside render range, same-server = shows up in the tab list.")
        .defaultValue(Online.Nearby)
        .build());

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("nearby-range").defaultValue(64).min(8).sliderRange(8, 256).build());

    private final Setting<Integer> max = sgGeneral.add(new IntSetting.Builder()
        .name("max-entries").defaultValue(10).min(1).sliderRange(1, 40).build());

    private final Setting<Boolean> showCount = sgGeneral.add(new BoolSetting.Builder()
        .name("show-count").defaultValue(true).build());

    private final Setting<SettingColor> onlineColor = sgGeneral.add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
        .name("online-color").defaultValue(new SettingColor(85, 255, 120)).build());

    private record Row(String name, boolean online) {
    }

    public FriendHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        List<Row> rows = collect();
        double base = unit();
        double padding = padding();
        double rowHeight = IslandRenderer.textHeight(renderer, base) + 5 * base;

        String header = "Friends";
        if (showCount.get()) header += "  " + rows.size();

        double nameWidth = IslandRenderer.textWidth(renderer, header, 0.8 * base);
        for (Row row : rows) {
            nameWidth = Math.max(nameWidth, IslandRenderer.textWidth(renderer, row.name(), base));
        }

        double width = nameWidth + padding * 2;
        double height = padding * 2 + IslandRenderer.textHeight(renderer, 0.8 * base) + 3 * base
            + Math.max(1, rows.size()) * rowHeight;

        setSize(width, height);
        panel(x, y, width, height);

        double cursorX = x + padding;
        double cursorY = y + padding;

        IslandRenderer.text(renderer, header, cursorX, cursorY, dimColor.get(), true, 0.8 * base);
        cursorY += IslandRenderer.textHeight(renderer, 0.8 * base) + 3 * base;

        if (rows.isEmpty()) {
            IslandRenderer.text(renderer, "no friends added", cursorX, cursorY, faded(dimColor.get(), 0.7), true, base);
            return;
        }

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            Color color = row.online() ? onlineColor.get() : textColor.get();
            IslandRenderer.text(renderer, row.name(), cursorX, cursorY, color, true, base);

            cursorY += rowHeight;
            if (i < rows.size() - 1) divider(x + padding, cursorY - rowHeight * 0.5 + 1, width - padding * 2);
        }
    }

    private List<Row> collect() {
        List<Row> rows = new ArrayList<>();
        if (mc.player == null || mc.world == null) return rows;

        List<Friend> friends = new ArrayList<>();
        for (Friend friend : Friends.get()) friends.add(friend);
        friends.sort(Comparator.comparing(Friend::getName, String.CASE_INSENSITIVE_ORDER));

        for (Friend friend : friends) {
            if (rows.size() >= max.get()) break;
            rows.add(new Row(friend.getName(), isOnline(friend.getName())));
        }

        return rows;
    }

    private boolean isOnline(String name) {
        if (online.get() == Online.Anywhere) return true;

        if (online.get() == Online.SameServer) {
            if (mc.getNetworkHandler() == null) return false;

            for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                if (entry.getProfile() != null && name.equalsIgnoreCase(entry.getProfile().name())) return true;
            }

            return false;
        }

        double limit = range.get();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (!name.equalsIgnoreCase(player.getGameProfile().name())) continue;
            return mc.player.squaredDistanceTo(player) <= limit * limit;
        }

        return false;
    }
}

