package com.example.addon.hud;

import com.example.addon.AddonTemplate;
import com.example.addon.mixin.BarrelBlockEntityAccessor;
import com.example.addon.mixin.ChestBlockEntityAccessor;
import com.example.addon.qq.AvatarCache;
import com.example.addon.qq.NapCatConfig;
import com.example.addon.qq.QqClient;
import com.example.addon.qq.QqMessage;
import com.example.addon.qq.QqSegment;
import com.example.addon.utils.BlurRenderer;
import com.example.addon.utils.RoundRectRenderer;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.ResolutionChangedEvent;
import meteordevelopment.meteorclient.events.meteor.ActiveModulesChangedEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.settings.Vector3dSetting;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.listeners.ConsumerListener;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.Nameable;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class YuioIsland extends HudElement {
    public static final HudElementInfo<YuioIsland> INFO =
        new HudElementInfo<>(AddonTemplate.HUD_GROUP, "yuio-island", "dynamic island.", YuioIsland::new);

    private static final Identifier ICON = Identifier.of("yuio", "textures/dynamic_island/icon.png");

    private static final Color TEXT_PRIMARY = new Color(255, 255, 255, 255);
    private static final Color TEXT_SECONDARY = new Color(150, 155, 165, 255);
    private static final Color TEXT_TERTIARY = new Color(118, 122, 130, 255);
    private static final Color TRACK_BACKGROUND = new Color(200, 200, 210, 60);
    private static final Color TRACK_FILL = new Color(255, 255, 255, 240);
    private static final Color SWITCH_OFF = new Color(120, 124, 132, 255);
    private static final Color SWITCH_KNOB = new Color(255, 255, 255, 245);
    private static final Color TOGGLE_ENABLED = new Color(52, 199, 89, 255);
    private static final Color TOGGLE_DISABLED = new Color(215, 66, 56, 255);

    private static final int VANILLA_TAB_ROWS = 20;
    private static final double VANILLA_TAB_LINE = 9.0;

    public static boolean tabActive;
    public static Text tabHeader;
    public static Text tabFooter;

    private final SettingGroup sgPosition = settings.createGroup("Position");
    private final SettingGroup sgAppearance = settings.createGroup("Appearance");
    private final SettingGroup sgAnimation = settings.createGroup("Animation");
    private final SettingGroup sgIdentity = settings.createGroup("Identity");
    private final SettingGroup sgNotifs = settings.createGroup("Notifications");
    private final SettingGroup sgContainer = settings.createGroup("Container");
    private final SettingGroup sgFood = settings.createGroup("Food");
    private final SettingGroup sgTab = settings.createGroup("Tab");
    private final SettingGroup sgQq = settings.createGroup("QQ");

    private final Setting<Vector3d> positionOffset = sgPosition.add(new Vector3dSetting.Builder()
        .name("position-offset")
        .description("Offset added on top of the HUD editor position.")
        .defaultValue(new Vector3d(0, 0, 0))
        .build()
    );

    private final Setting<Double> scale = sgAppearance.add(new DoubleSetting.Builder()
        .name("scale").defaultValue(1).min(0.5).sliderRange(0.5, 2).build()
    );

    private final Setting<Double> cornerRadius = sgAppearance.add(new DoubleSetting.Builder()
        .name("corner-radius").description("0 uses a fully rounded pill.").defaultValue(999).min(0).sliderRange(0, 60).build()
    );

    private final Setting<Double> iconHeight = sgAppearance.add(new DoubleSetting.Builder()
        .name("icon-height").defaultValue(16).min(8).sliderRange(8, 32).build()
    );

    private final Setting<Integer> blurStrength = sgAppearance.add(new IntSetting.Builder()
        .name("blur-strength").description("0 disables the frosted glass.").defaultValue(12).min(0).sliderRange(0, 20).build()
    );

    private final Setting<Integer> backgroundAlpha = sgAppearance.add(new IntSetting.Builder()
        .name("background-alpha").defaultValue(165).min(0).sliderRange(0, 255).build()
    );

    private final Setting<Integer> tintStrength = sgAppearance.add(new IntSetting.Builder()
        .name("tint-strength").description("How much the glass is darkened.").defaultValue(170).min(0).sliderRange(0, 255).build()
    );

    private final Setting<FontMode> fontMode = sgAppearance.add(new EnumSetting.Builder<FontMode>()
        .name("font")
        .description("Game font or Meteor font. Meteor has no CJK glyphs, so Chinese always falls back to the game font.")
        .defaultValue(FontMode.Game)
        .build()
    );

    public enum FontMode {
        Game,
        Meteor
    }

    private final Setting<Boolean> highlight = sgAppearance.add(new BoolSetting.Builder()
        .name("highlight").defaultValue(true).build()
    );

    private final Setting<SettingColor> accentColor = sgAppearance.add(new ColorSetting.Builder()
        .name("accent-color").defaultValue(new SettingColor(52, 199, 89)).build()
    );

    private final Setting<Boolean> border = sgAppearance.add(new BoolSetting.Builder()
        .name("border").defaultValue(true).build()
    );

    private final Setting<Double> animationSpeed = sgAnimation.add(new DoubleSetting.Builder()
        .name("animation-speed").description("Higher is snappier.").defaultValue(10).min(2).sliderRange(2, 20).build()
    );

    private final Setting<Boolean> rainbowName = sgIdentity.add(new BoolSetting.Builder()
        .name("rainbow-name").description("Rainbow flowing effect on the client name.").defaultValue(false).build());

    private final Setting<Double> rainbowSpeed = sgIdentity.add(new DoubleSetting.Builder()
        .name("rainbow-speed").defaultValue(0.35).min(0.05).sliderRange(0.05, 1.5).visible(rainbowName::get).build());

    private final Setting<String> title = sgIdentity.add(new StringSetting.Builder()
        .name("client-label").description("Bold label on the first info column.").defaultValue("yuio Client").build()
    );

    private final Setting<String> subtitle = sgIdentity.add(new StringSetting.Builder()
        .name("subtitle").description("Dim label under the title.").defaultValue("v0.1.0").build()
    );

    private final Setting<Boolean> showName = sgIdentity.add(new BoolSetting.Builder()
        .name("show-name").description("Show the client name next to the icon.").defaultValue(true).build()
    );

    private final Setting<Boolean> showFps = sgIdentity.add(new BoolSetting.Builder().name("show-fps").defaultValue(true).build());
    private final Setting<Boolean> showServer = sgIdentity.add(new BoolSetting.Builder().name("show-server").defaultValue(true).build());
    private final Setting<Boolean> showCoords = sgIdentity.add(new BoolSetting.Builder().name("show-coords").defaultValue(false).build());
    private final Setting<Boolean> showTime = sgIdentity.add(new BoolSetting.Builder().name("show-time").defaultValue(false).build());
    private final Setting<Boolean> showDirection = sgIdentity.add(new BoolSetting.Builder().name("show-direction").defaultValue(false).build());
    private final Setting<Boolean> moduleNotifs = sgIdentity.add(new BoolSetting.Builder().name("module-notifs").defaultValue(true).build());

    private final Setting<Integer> notifDuration = sgNotifs.add(new IntSetting.Builder().name("duration").defaultValue(2000).min(500).sliderRange(500, 5000).build());
    private final Setting<Integer> maxNotifs = sgNotifs.add(new IntSetting.Builder().name("max-notifs").defaultValue(3).min(1).sliderRange(1, 8).build());
    private final Setting<Boolean> divider = sgNotifs.add(new BoolSetting.Builder().name("divider").defaultValue(true).build());

    private final Setting<StateStyle> stateStyle = sgNotifs.add(new EnumSetting.Builder<StateStyle>()
        .name("state-style")
        .description("How the module state is written in the toggle notification.")
        .defaultValue(StateStyle.Enabled)
        .build()
    );

    public enum StateStyle {
        Enabled,
        OnOff,
        Boolean
    }

    private final Setting<Double> previewRange = sgContainer.add(new DoubleSetting.Builder().name("preview-range").defaultValue(6).min(3).sliderRange(3, 8).build());
    private final Setting<Integer> slotsPerRow = sgContainer.add(new IntSetting.Builder().name("slots-per-row").defaultValue(9).min(4).sliderRange(4, 12).build());
    private final Setting<Integer> maxItems = sgContainer.add(new IntSetting.Builder().name("max-items").defaultValue(27).min(8).sliderRange(8, 54).build());

    private final Setting<Boolean> qqEnabled = sgQq.add(new BoolSetting.Builder()
        .name("qq-enabled")
        .description("Show QQ messages. Requires NapCat / LLOneBot attached to the official QQ.")
        .defaultValue(false)
        .build());

    private final Setting<String> qqUrl = sgQq.add(new StringSetting.Builder()
        .name("qq-ws-url").description("OneBot WebSocket URL.").defaultValue("ws://127.0.0.1:3001").build());

    private final Setting<String> qqToken = sgQq.add(new StringSetting.Builder()
        .name("qq-token").description("Access token, empty if none.").defaultValue("").build());

    private final Setting<String> qqHttpUrl = sgQq.add(new StringSetting.Builder()
        .name("qq-http-url")
        .description("OneBot HTTP API, used to look up group names. NapCat default is port 3000.")
        .defaultValue("http://127.0.0.1:3000")
        .build());

    private final Setting<String> qqGroupAvatar = sgQq.add(new StringSetting.Builder()
        .name("qq-group-avatar")
        .description("Group avatar URL template. {group} is replaced with the group id.")
        .defaultValue("https://p.qlogo.cn/gh/{group}/{group}/100")
        .build());

    private final Setting<String> qqUserAvatar = sgQq.add(new StringSetting.Builder()
        .name("qq-user-avatar")
        .description("User avatar URL template. {qq} is replaced with the sender QQ number.")
        .defaultValue("https://q1.qlogo.cn/g?b=qq&nk={qq}&s=100")
        .build());

    private final Setting<Integer> qqDuration = sgQq.add(new IntSetting.Builder()
        .name("qq-duration").defaultValue(8000).min(2000).sliderRange(2000, 30000).build());

    private final Setting<Integer> qqMax = sgQq.add(new IntSetting.Builder()
        .name("qq-max").description("How many messages to stack.").defaultValue(2).min(1).sliderRange(1, 5).build());

    private final Setting<Integer> qqMaxWidth = sgQq.add(new IntSetting.Builder()
        .name("qq-max-width").description("Width of the text area before it gets trimmed with an ellipsis.")
        .defaultValue(220).min(80).sliderRange(80, 400).build());

    private final Setting<Boolean> qqOnlyMention = sgQq.add(new BoolSetting.Builder()
        .name("qq-only-mention")
        .description("Group messages are only shown when they mention you. QQ's own do-not-disturb flag lives in QQ and never reaches the API, so this is the closest thing to it.")
        .defaultValue(false)
        .build());

    private final Setting<String> qqIgnore = sgQq.add(new StringSetting.Builder()
        .name("qq-ignore")
        .description("Groups to drop, separated by commas. A plain number is matched against the group id (the id is printed to the log), anything else against the group name.")
        .defaultValue("")
        .build());

    private final Setting<Boolean> qqStatus = sgQq.add(new BoolSetting.Builder()
        .name("qq-status").description("Show a card while QQ is connecting or disconnected, so silence is never ambiguous.")
        .defaultValue(true)
        .build());

    private final Setting<String> napcatDir = sgQq.add(new StringSetting.Builder()
        .name("napcat-dir")
        .description("Folder of NapCat.Shell. Empty or 'auto' finds it by itself, 'off' disables all of this. The addon then fixes onebot11_<qq>.json for every account, so the WebUI never has to be opened.")
        .defaultValue("auto")
        .build());

    private final Setting<Boolean> qqAutoLaunch = sgQq.add(new BoolSetting.Builder()
        .name("qq-auto-launch")
        .description("Start the injected QQ when it is not running. Windows asks for admin once.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> qqHidden = sgQq.add(new BoolSetting.Builder()
        .name("qq-hidden-launch")
        .description("Start QQ without its console window. Only applied when a quick login account is known, otherwise the QR code has to stay visible.")
        .defaultValue(true)
        .build());

    private final Setting<String> qqAccount = sgQq.add(new StringSetting.Builder()
        .name("qq-account")
        .description("QQ number passed to launcher.bat as -q, so it logs in without a QR scan. Empty means take the account from NapCat's config.")
        .defaultValue("")
        .build());

    private final Setting<Boolean> qqForceRestart = sgQq.add(new BoolSetting.Builder()
        .name("qq-force-restart")
        .description("QQ allows only one PC session per account, so a QQ started without NapCat blocks it. Close that one and start it injected instead.")
        .defaultValue(true)
        .build());

    private final Setting<SettingColor> qqNameColor = sgQq.add(new ColorSetting.Builder()
        .name("qq-name-color")
        .description("Group / contact name colour. Default is light because black is unreadable on the dark glass.")
        .defaultValue(new SettingColor(255, 255, 255))
        .build());

    private final Setting<Boolean> showFood = sgFood.add(new BoolSetting.Builder().name("show-food").defaultValue(true).build());

    private final Setting<Double> tabNameScale = sgTab.add(new DoubleSetting.Builder().name("name-scale").defaultValue(1).min(0.5).sliderRange(0.5, 2).build());
    private final Setting<Boolean> tabHeaderFooter = sgTab.add(new BoolSetting.Builder().name("header-footer").defaultValue(true).build());
    private final Setting<Double> tabOffsetX = sgTab.add(new DoubleSetting.Builder()
        .name("tab-offset-x").description("Nudge the tab panel horizontally.").defaultValue(0).sliderRange(-200, 200).build());
    private final Setting<Double> tabOffsetY = sgTab.add(new DoubleSetting.Builder()
        .name("tab-offset-y").description("Nudge the tab panel vertically.").defaultValue(0).sliderRange(-200, 200).build());

    private final Setting<Boolean> tabPingBars = sgTab.add(new BoolSetting.Builder().name("ping-bars").description("Draw vanilla style signal bars instead of a number.").defaultValue(true).build());

    private final Anim width = new Anim(0, 10);
    private final Anim height = new Anim(0, 10);
    private final Anim tab = new Anim(0, 10);
    private final Anim content = new Anim(0, 10);
    private final Anim food = new Anim(0, 10);

    private final List<TimedContent> notifications = new ArrayList<>();
    private final List<Module> lastActive = new ArrayList<>();

    private final List<QqEntry> qqMessages = new ArrayList<>();
    private final Queue<QqMessage> incomingQq = new ConcurrentLinkedQueue<>();
    private QqClient qqClient;
    private boolean qqConnected;
    private boolean napcatManaged;

    private List<ItemStack> previewStacks;
    private String previewTitle;
    private final List<ItemStack> previewItems = new ArrayList<>();
    private ItemStack foodStack;
    private double foodProgress;

    private int iconImageWidth = -1;
    private int iconImageHeight = -1;
    private boolean iconAvailable = true;

    private final ConsumerListener<ActiveModulesChangedEvent> activeModulesListener =
        new ConsumerListener<>(ActiveModulesChangedEvent.class, event -> {
            if (!moduleNotifs.get()) return;
            List<Module> active = Modules.get().getActive();
            for (Module module : active) {
                if (!lastActive.contains(module)) pushModuleNotification(module);
            }
            for (Module module : lastActive) {
                if (!active.contains(module)) pushModuleNotification(module);
            }
            lastActive.clear();
            lastActive.addAll(active);
        });

    private final ConsumerListener<Render2DEvent> captureListener =
        new ConsumerListener<>(Render2DEvent.class, event -> BlurRenderer.capture(blurStrength.get()));

    private final ConsumerListener<ResolutionChangedEvent> resizeListener =
        new ConsumerListener<>(ResolutionChangedEvent.class, event -> {
            BlurRenderer.close();
            iconImageWidth = -1;
            iconImageHeight = -1;
            iconAvailable = true;
        });

    public YuioIsland() {
        super(INFO);
        MeteorClient.EVENT_BUS.subscribe(activeModulesListener);
        MeteorClient.EVENT_BUS.subscribe(captureListener);
        MeteorClient.EVENT_BUS.subscribe(resizeListener);
    }

    @Override
    public void remove() {
        MeteorClient.EVENT_BUS.unsubscribe(activeModulesListener);
        MeteorClient.EVENT_BUS.unsubscribe(captureListener);
        MeteorClient.EVENT_BUS.unsubscribe(resizeListener);
        BlurRenderer.close();
        super.remove();
    }


    private void pushModuleNotification(Module module) {
        for (TimedContent existing : notifications) {
            if (existing.module == module) {
                existing.reborn();
                return;
            }
        }

        notifications.add(new TimedContent(module, notifDuration.get(), stateStyle.get()));
        while (notifications.size() > maxNotifs.get()) {
            notifications.remove(0);
        }
    }

    public void addNotification(String notificationTitle, String description, long duration) {
        notifications.add(new TimedContent(notificationTitle, description, duration));
        while (notifications.size() > maxNotifs.get()) {
            notifications.remove(0);
        }
    }

    private boolean wantsTab() {
        return !isInEditor() && mc.player != null && mc.currentScreen == null
            && Input.isKeyPressed(GLFW.GLFW_KEY_TAB);
    }

    private static String ellipsis(HudRenderer renderer, String text, double maxWidth, double textScale) {
        if (IslandRenderer.textWidth(renderer, text, textScale) <= maxWidth) return text;

        String trimmed = text;
        while (trimmed.length() > 1 && IslandRenderer.textWidth(renderer, trimmed + "...", textScale) > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "...";
    }


    private void ensureIconSize() {
        if (iconImageWidth > 0 || !iconAvailable) return;

        try {
            var resource = mc.getResourceManager().getResource(ICON);
            if (resource.isEmpty()) {

                iconAvailable = false;
                return;
            }

            try (InputStream in = resource.get().getInputStream()) {
                NativeImage image = NativeImage.read(in);
                iconImageWidth = image.getWidth();
                iconImageHeight = image.getHeight();
                image.close();
            }
        } catch (Exception e) {
            AddonTemplate.LOG.warn("[yuio-island] failed to read island icon", e);
            iconAvailable = false;
        }
    }


    private void updatePreview() {
        previewStacks = null;
        previewTitle = null;
        previewItems.clear();

        if (mc.player == null || mc.world == null || !(mc.crosshairTarget instanceof BlockHitResult hit)) return;

        double range = previewRange.get();
        double centerX = hit.getBlockPos().getX() + 0.5;
        double centerY = hit.getBlockPos().getY() + 0.5;
        double centerZ = hit.getBlockPos().getZ() + 0.5;
        if (mc.player.squaredDistanceTo(centerX, centerY, centerZ) > range * range) return;

        BlockEntity blockEntity = mc.world.getBlockEntity(hit.getBlockPos());
        List<ItemStack> stacks = stacksOf(blockEntity);
        if (stacks == null) return;

        previewStacks = stacks;
        previewTitle = blockEntity instanceof Nameable nameable && nameable.hasCustomName()
            ? nameable.getDisplayName().getString()
            : blockEntity.getCachedState().getBlock().getName().getString();

        for (ItemStack stack : stacks) {
            if (previewItems.size() >= maxItems.get()) break;
            if (!stack.isEmpty()) previewItems.add(stack);
        }
    }

    private static List<ItemStack> stacksOf(BlockEntity blockEntity) {
        if (blockEntity == null) return null;

        if (blockEntity instanceof Inventory inventory) {
            List<ItemStack> stacks = new ArrayList<>(inventory.size());
            for (int i = 0; i < inventory.size(); i++) {
                stacks.add(inventory.getStack(i));
            }
            return stacks;
        }

        if (blockEntity instanceof net.minecraft.block.entity.ChestBlockEntity chest) {
            return new ArrayList<>(((ChestBlockEntityAccessor) chest).yuio$getHeldStacks());
        }
        if (blockEntity instanceof net.minecraft.block.entity.BarrelBlockEntity barrel) {
            return new ArrayList<>(((BarrelBlockEntityAccessor) barrel).yuio$getHeldStacks());
        }

        return null;
    }


    private void updateFood(float delta) {
        if (showFood.get() && mc.player != null && mc.player.isUsingItem()
            && mc.player.getActiveItem().get(DataComponentTypes.FOOD) != null) {
            foodStack = mc.player.getActiveItem();
            int maxUseTime = foodStack.getMaxUseTime(mc.player);
            double raw = maxUseTime <= 0 ? 0.0
                : 1.0 - (double) mc.player.getItemUseTimeLeft() / maxUseTime;
            foodProgress = Math.max(0.0, Math.min(1.0, raw));
        } else {
            foodStack = null;
            foodProgress = 0.0;
        }

        food.set((float) foodProgress);
        food.update(delta);
    }


    private String serverName() {
        if (mc.getCurrentServerEntry() != null) return mc.getCurrentServerEntry().address;
        return mc.isIntegratedServerRunning() ? "Singleplayer" : "Offline";
    }

    private int latency() {
        if (mc.player == null || mc.getNetworkHandler() == null) return -1;
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return entry == null ? -1 : entry.getLatency();
    }

    private String latencyText() {
        int value = latency();
        return value <= 0 ? "Pinging" : value + " ms";
    }

    private static Color latencyColor(int latency) {
        if (latency < 0) return TEXT_TERTIARY;
        if (latency < 150) return TOGGLE_ENABLED;
        if (latency < 300) return new Color(231, 208, 32, 255);
        return TOGGLE_DISABLED;
    }

    private String rightTitleText() {
        String server = showServer.get() ? serverName() : "";
        String extra = extraInfo();
        if (server.isEmpty()) return extra;
        return extra.isEmpty() ? server : server + "  |  " + extra;
    }

    private String extraInfo() {
        StringBuilder builder = new StringBuilder();

        if (showFps.get()) builder.append(mc.getCurrentFps()).append(" FPS");
        if (showCoords.get() && mc.player != null) {
            appendSeparator(builder);
            builder.append((int) mc.player.getX()).append(' ')
                .append((int) mc.player.getY()).append(' ')
                .append((int) mc.player.getZ());
        }
        if (showDirection.get() && mc.player != null) {
            appendSeparator(builder);
            builder.append(switch (mc.player.getHorizontalFacing()) {
                case NORTH -> "N";
                case EAST -> "E";
                case SOUTH -> "S";
                case WEST -> "W";
                default -> "?";
            });
        }
        if (showTime.get()) {
            appendSeparator(builder);
            builder.append(String.format("%02d:%02d",
                java.time.LocalTime.now().getHour(), java.time.LocalTime.now().getMinute()));
        }

        return builder.toString();
    }

    private static void appendSeparator(StringBuilder builder) {
        if (!builder.isEmpty()) builder.append("  |  ");
    }


    @Override
    public void render(HudRenderer renderer) {
        float delta = (float) Math.min(renderer.delta, 0.05);
        long now = System.currentTimeMillis();
        double baseScale = scale.get();

        IslandRenderer.beginFrame();
        IslandRenderer.setVanillaFont(fontMode.get() == FontMode.Game);
        BlurRenderer.init();
        AvatarCache.poll();
        updateQq(delta);

        tabActive = wantsTab();
        tab.set(tabActive ? 1.0f : 0.0f);
        tab.update(delta);

        updateFood(delta);
        updatePreview();

        for (TimedContent notification : new ArrayList<>(notifications)) {
            if (notification.isExpired(now)) notifications.remove(notification);
            else notification.update(delta);
        }

        if (tabActive) {
            renderTabMode(renderer, delta, baseScale);
            renderFloatingNotifications(renderer, baseScale);
        } else {
            renderCompactMode(renderer, delta, baseScale);
        }
    }


    private void renderCompactMode(HudRenderer renderer, float delta, double baseScale) {
        List<Content> contents = new ArrayList<>();
        if (previewStacks != null) contents.add(containerContent(renderer, baseScale));
        if (foodStack != null) contents.add(foodContent(renderer, baseScale));
        for (QqEntry entry : qqMessages) contents.add(qqContent(renderer, entry, baseScale));
        if (qqStatus.get() && qqEnabled.get() && qqClient != null
            && qqClient.state() != QqClient.State.CONNECTED) {
            contents.add(qqStatusContent(renderer, baseScale));
        }
        for (TimedContent notification : notifications) {
            contents.add(notification.toContent(renderer, baseScale));
        }

        double padding = 10 * baseScale;
        double gap = 5 * baseScale;
        boolean stacked = !contents.isEmpty();

        double targetWidth;
        double targetHeight;

        if (stacked) {
            double widest = 0;
            double total = 0;
            for (Content content : contents) {
                widest = Math.max(widest, content.width + padding * 2);
                total += content.height + padding * 2 + gap;
            }
            targetWidth = widest;
            targetHeight = total - gap;
        } else {
            double[] idle = measureIdle(renderer, baseScale);
            targetWidth = idle[0];
            targetHeight = idle[1];
        }

        width.set((float) targetWidth);
        height.set((float) targetHeight);
        content.set(stacked ? 1.0f : 0.0f);
        width.update(delta);
        height.update(delta);
        content.update(delta);

        double panelWidth = width.get();
        double panelHeight = height.get();
        double radius = effectiveRadius(panelWidth, panelHeight, baseScale);

        double originX = x + positionOffset.get().x;
        double originY = y + positionOffset.get().y;
        double progress = tab.get();
        double centerX = (mc.getWindow().getScaledWidth() - panelWidth) * 0.5;
        double centerY = (mc.getWindow().getScaledHeight() - panelHeight) * 0.5;
        double panelX = originX + (centerX - originX) * progress;
        double panelY = originY + (centerY - originY) * progress;

        if (!stacked) {
            IslandRenderer.shadow(panelX, panelY, panelWidth, panelHeight, radius, 1.0);
            IslandRenderer.glass(panelX, panelY, panelWidth, panelHeight, radius,
                blurStrength.get(), backgroundAlpha.get(), tintStrength.get(),
                border.get(), highlight.get());
            drawIdle(renderer, panelX, panelY, panelWidth, panelHeight, baseScale);
        } else {
            double alpha = content.get();
            double cursorY = panelY;

            for (Content card : contents) {
                double cardWidth = card.width + padding * 2;
                double cardHeight = card.height + padding * 2;
                double cardRadius = Math.min(14 * baseScale, cardHeight * 0.5);

                IslandRenderer.shadow(panelX, cursorY, cardWidth, cardHeight, cardRadius, 1.0);
                IslandRenderer.glass(panelX, cursorY, cardWidth, cardHeight, cardRadius,
                    blurStrength.get(), backgroundAlpha.get(), tintStrength.get(),
                    border.get(), highlight.get());
                card.render().draw(renderer, panelX + padding, cursorY + padding, alpha);

                cursorY += cardHeight + gap;
            }
        }

        setSize(panelWidth, panelHeight);
    }
    private record IdleLayout(
        double width, double height, double padding,
        double nameScale, double valueScale, double labelScale,
        double iconWidth, double iconHeight, double iconGap,
        boolean hasName, double nameWidth, double nameDivider,
        double leftColumn, double rightColumn, double columnGap,
        String titleText, String subtitleText, String rightTitle, String rightSub,
        double columnHeight
    ) {
    }

    private IdleLayout layoutIdle(HudRenderer renderer, double baseScale) {
        double padding = 12 * baseScale;
        double nameScale = 1.05 * baseScale;
        double valueScale = 0.95 * baseScale;
        double labelScale = 0.70 * baseScale;
        double columnGap = 14 * baseScale;

        double iconWidth = iconDrawWidth(baseScale);
        double iconHeight = iconDrawHeight(baseScale);
        double iconGap = iconWidth > 0 ? 8 * baseScale : 0;

        boolean hasName = showName.get();
        double nameWidth = hasName ? IslandRenderer.textWidth(renderer, clientName(), nameScale) : 0;
        double nameDivider = hasName ? 17 * baseScale : 0;

        String titleText = title.get();
        String subtitleText = subtitle.get();
        double leftColumn = Math.max(
            IslandRenderer.textWidth(renderer, titleText, valueScale),
            IslandRenderer.textWidth(renderer, subtitleText, labelScale)
        );

        String rightTitle = rightTitleText();
        String rightSub = latencyText();
        double rightColumn = Math.max(
            rightTitle.isEmpty() ? 0 : IslandRenderer.textWidth(renderer, rightTitle, valueScale),
            IslandRenderer.textWidth(renderer, rightSub, labelScale)
        );

        double columnHeight = IslandRenderer.textHeight(renderer, valueScale) + 3 * baseScale
            + IslandRenderer.textHeight(renderer, labelScale);
        double rowHeight = Math.max(Math.max(iconHeight, IslandRenderer.textHeight(renderer, nameScale)), columnHeight);

        double width = padding * 2 + iconWidth + iconGap + nameWidth + nameDivider
            + leftColumn + columnGap + rightColumn;
        double height = rowHeight + padding * 2;

        return new IdleLayout(width, height, padding, nameScale, valueScale, labelScale,
            iconWidth, iconHeight, iconGap, hasName, nameWidth, nameDivider,
            leftColumn, rightColumn, columnGap, titleText, subtitleText, rightTitle, rightSub, columnHeight);
    }

    private double[] measureIdle(HudRenderer renderer, double baseScale) {
        IdleLayout layout = layoutIdle(renderer, baseScale);
        return new double[]{layout.width(), layout.height()};
    }

    private void drawIdle(HudRenderer renderer, double panelX, double panelY,
                          double panelWidth, double panelHeight, double baseScale) {
        IdleLayout layout = layoutIdle(renderer, baseScale);

        double centerY = panelY + panelHeight * 0.5;
        double cursorX = panelX + layout.padding();

        if (layout.iconWidth() > 0) {
            renderer.texture(ICON, cursorX, centerY - layout.iconHeight() * 0.5,
                layout.iconWidth(), layout.iconHeight(), Color.WHITE);
            cursorX += layout.iconWidth() + layout.iconGap();
        }

        if (layout.hasName()) {
            double nameY = centerY - IslandRenderer.textHeight(renderer, layout.nameScale()) * 0.5;
            if (rainbowName.get()) {
                drawRainbow(renderer, clientName(), cursorX, nameY, layout.nameScale());
            } else {
                IslandRenderer.text(renderer, clientName(), cursorX, nameY, accentColor.get(), true, layout.nameScale());
            }
            cursorX += layout.nameWidth() + 4 * baseScale;

            double dividerHeight = IslandRenderer.textHeight(renderer, layout.nameScale()) * 0.8;
            renderer.quad(cursorX, centerY - dividerHeight * 0.5, 1, dividerHeight, new Color(255, 255, 255, 70));
            cursorX += 4 * baseScale + 9 * baseScale;
        }

        double blockTop = centerY - layout.columnHeight() * 0.5;
        double subTop = blockTop + IslandRenderer.textHeight(renderer, layout.valueScale()) + 3 * baseScale;

        IslandRenderer.text(renderer, layout.titleText(), cursorX, blockTop, TEXT_PRIMARY, true, layout.valueScale());
        IslandRenderer.text(renderer, layout.subtitleText(), cursorX, subTop, TEXT_TERTIARY, true, layout.labelScale());

        double dividerX = cursorX + layout.leftColumn() + layout.columnGap() * 0.5;
        double dividerHeight2 = renderer.textHeight(false, layout.valueScale()) * 0.8;
        renderer.quad(dividerX, centerY - dividerHeight2 * 0.5, 1, dividerHeight2, new Color(255, 255, 255, 70));

        double rightColumnX = cursorX + layout.leftColumn() + layout.columnGap();
        if (!layout.rightTitle().isEmpty()) {
            IslandRenderer.text(renderer, layout.rightTitle(), rightColumnX, blockTop, TEXT_PRIMARY, true, layout.valueScale());
        }
        IslandRenderer.text(renderer, layout.rightSub(), rightColumnX, subTop,
            latencyColor(latency()), true, layout.labelScale());
    }
    private void drawContents(HudRenderer renderer, List<Content> contents, double panelX, double panelY,
                              double padding, double baseScale) {
        double cursorY = panelY + padding;
        double alpha = content.get();

        for (int i = 0; i < contents.size(); i++) {
            Content content = contents.get(i);
            content.render().draw(renderer, panelX + padding, cursorY, alpha);
            cursorY += content.height;

            if (divider.get() && i < contents.size() - 1) {
                IslandRenderer.round(panelX + padding, cursorY + 2 * baseScale,
                    width.get() - padding * 2, 1, 0.5, new Color(255, 255, 255, (int) (46 * alpha)));
                cursorY += 5 * baseScale;
            }
        }
    }

    private void renderFloatingNotifications(HudRenderer renderer, double baseScale) {
        if (notifications.isEmpty()) return;

        double padding = 10 * baseScale;
        double originX = x + positionOffset.get().x;
        double originY = y + positionOffset.get().y;

        for (TimedContent notification : notifications) {
            Content card = notification.toContent(renderer, baseScale);
            double cardWidth = card.width() + padding * 2;
            double cardHeight = card.height() + padding * 2;

            IslandRenderer.glass(originX, originY, cardWidth, cardHeight,
                Math.min(14 * baseScale, cardHeight * 0.5),
                blurStrength.get(), Math.max(backgroundAlpha.get(), 150), tintStrength.get(),
                border.get(), false);
            card.render().draw(renderer, originX + padding, originY + padding, notification.fade());

            originY += cardHeight + 5 * baseScale;
        }
    }

    /** 逐字上色做一个流动的彩虹; 首尾相接, 时间驱动。 */
    private void drawRainbow(HudRenderer renderer, String text, double x, double y, double textScale) {
        double cursor = x;
        double phase = (System.currentTimeMillis() % 100000L) / 1000.0 * rainbowSpeed.get();

        for (int i = 0; i < text.length(); i++) {
            float hue = (float) ((phase + i * 0.07) % 1.0);
            int rgb = java.awt.Color.HSBtoRGB(hue, 1.0F, 1.0F);
            Color color = new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255);
            cursor = IslandRenderer.text(renderer, String.valueOf(text.charAt(i)), cursor, y, color, true, textScale);
        }
    }

    /** 一条 QQ 消息 + 它自己的滑入/淡入动画。 */
    private static final class QqEntry {
        final QqMessage message;
        final Anim fade = new Anim(0, 9);
        final Anim slide = new Anim(0, 11);
        boolean leaving;

        QqEntry(QqMessage message) {
            this.message = message;
        }
    }

    /**
     * 群免打扰是 QQ 客户端的本地设置, 接口不会告诉我们,
     * 所以只能靠忽略列表和"只显示@我"来达到同样的效果。
     */
    private boolean accepts(QqMessage message) {
        if (message.groupId() < 0) return true;
        if (qqOnlyMention.get() && !message.mention()) return false;

        String id = String.valueOf(message.groupId());
        for (String part : qqIgnore.get().split("[,，;；\\s]+")) {
            if (part.isEmpty()) continue;
            // 纯数字当群号, 其它当群名的一部分
            if (part.chars().allMatch(Character::isDigit)
                ? part.equals(id)
                : message.title().contains(part)) return false;
        }

        return true;
    }

    /**
     * NapCat 是注入进 QQ 的插件, 配置按账号存, 换号就是一份默认配置。
     * 给了路径就替用户把这些配置补好 (顺便在 QQ 没跑时点起来), 一局只做一次。
     */
    private void manageNapcat() {
        if (napcatManaged) return;
        napcatManaged = true;

        String account = qqAccount.get();

        NapCatConfig.manage(napcatDir.get(), qqUrl.get(), qqHttpUrl.get(), qqToken.get(),
            account, qqAutoLaunch.get(), qqForceRestart.get(), qqHidden.get());
    }

    /** WebSocket 线程调进来的, 所以只入队, 渲染线程再取。 */
    private void onQqMessage(QqMessage message) {
        incomingQq.add(message);
        while (incomingQq.size() > 8) incomingQq.poll();
    }

    private void updateQq(float delta) {
        boolean enabled = qqEnabled.get();

        if (enabled != qqConnected) {
            qqConnected = enabled;
            if (enabled) {
                if (qqClient == null) qqClient = new QqClient(this::onQqMessage);
                qqClient.connect(qqUrl.get(), qqToken.get(), qqHttpUrl.get(), qqGroupAvatar.get(), qqUserAvatar.get());
                manageNapcat();
            } else if (qqClient != null) {
                qqClient.disconnect();
                napcatManaged = false;
            }
        }

        if (!enabled) {
            incomingQq.clear();
            qqMessages.clear();
            return;
        }

        // 掉线自己重连, 不然 QQ 一重登灵动岛就永久静默
        if (qqClient != null) {
            qqClient.tick();
            if (qqClient.state() == QqClient.State.CONNECTED) NapCatConfig.hint = "";
        }

        QqMessage fresh;
        while ((fresh = incomingQq.poll()) != null) {
            if (!accepts(fresh)) continue;
            qqMessages.add(new QqEntry(fresh));
            while (qqMessages.size() > qqMax.get()) qqMessages.remove(0);
        }

        long now = System.currentTimeMillis();
        int duration = qqDuration.get();

        for (QqEntry entry : new ArrayList<>(qqMessages)) {
            boolean expiring = now - entry.message.born() > duration - 300;
            entry.fade.set(expiring ? 0.0 : 1.0);
            entry.slide.set(1.0);
            entry.fade.update(delta);
            entry.slide.update(delta);

            if (now - entry.message.born() > duration) qqMessages.remove(entry);
        }
    }

    /**
     * 把消息分段按最大宽度裁好, 超长就用省略号收尾。
     * 裁的时候保留每段自己的颜色, 所以 @全体成员 / @你 的红字不会丢。
     */
    private List<QqSegment> fitSegments(HudRenderer renderer, List<QqSegment> segments,
                                        double maxWidth, double textScale) {
        List<QqSegment> out = new ArrayList<>();
        double used = 0;

        for (QqSegment segment : segments) {
            String text = segment.text();
            double width = IslandRenderer.textWidth(renderer, text, textScale);

            if (used + width <= maxWidth) {
                out.add(segment);
                used += width;
                continue;
            }

            double ellipsisWidth = IslandRenderer.textWidth(renderer, "...", textScale);
            StringBuilder builder = new StringBuilder();
            double current = used;

            for (int i = 0; i < text.length(); i++) {
                String next = builder.toString() + text.charAt(i);
                double nextWidth = IslandRenderer.textWidth(renderer, next, textScale);
                if (current + nextWidth - used + ellipsisWidth > maxWidth) break;
                builder.append(text.charAt(i));
            }

            if (!builder.isEmpty()) out.add(new QqSegment(builder.toString(), segment.rgb()));
            out.add(new QqSegment("...", segment.rgb()));
            return out;
        }

        return out;
    }

    private Content qqContent(HudRenderer renderer, QqEntry entry, double baseScale) {
        QqMessage message = entry.message;

        double avatar = 26 * baseScale;
        double gap = 6 * baseScale;
        double titleScale = 0.9 * baseScale;
        double textScale = 0.95 * baseScale;

        // 标题和正文都按同一个上限裁, 卡片宽度才不会撑出屏幕
        double maxWidth = qqMaxWidth.get() * baseScale;
        String titleText = ellipsis(renderer, message.title(), maxWidth, titleScale);
        List<QqSegment> fitted = fitSegments(renderer, message.segments(), Math.max(maxWidth - avatar - gap, 24 * baseScale), textScale);

        double textWidth = 0;
        for (QqSegment segment : fitted) {
            textWidth += IslandRenderer.textWidth(renderer, segment.text(), textScale);
        }

        double titleHeight = IslandRenderer.textHeight(renderer, titleScale);
        double textHeight = IslandRenderer.textHeight(renderer, textScale);
        double titleWidth = IslandRenderer.textWidth(renderer, titleText, titleScale);

        double contentWidth = Math.max(titleWidth, avatar + gap + textWidth);
        double contentHeight = titleHeight + 3 * baseScale + Math.max(avatar, textHeight);

        return new Content((r, originX, originY, alpha) -> {
            double a = alpha * entry.fade.get();
            if (a <= 0.01) return;

            // 滑入: 从下方一点点挪上来
            double baseY = originY + (1.0 - entry.slide.get()) * 8 * baseScale;

            IslandRenderer.text(r, titleText, originX, baseY,
                IslandRenderer.alpha(qqNameColor.get(), (int) (255 * a)), true, titleScale);

            double rowY = baseY + titleHeight + 3 * baseScale;

            GpuTextureView avatarView = AvatarCache.get(message.avatarUrl());
            if (avatarView != null) {
                // 圆 = 四角半径取一半; tint 的 alpha 是混合强度, 0 表示保留原色
                RoundRectRenderer.textured(originX, rowY, avatar, avatar,
                    avatar * 0.5, avatar * 0.5, avatar * 0.5, avatar * 0.5,
                    avatarView, 0f, 0f, 1f, 1f, new Color(255, 255, 255, 0), (int) (255 * a));
            } else {
                IslandRenderer.round(originX, rowY, avatar, avatar, avatar * 0.5,
                    new Color(255, 255, 255, (int) (38 * a)));
            }

            double cursor = originX + avatar + gap;
            double textY = rowY + (avatar - textHeight) * 0.5;

            for (QqSegment segment : fitted) {
                int rgb = segment.rgb();
                Color color = IslandRenderer.alpha(
                    new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255), (int) (255 * a));
                cursor = IslandRenderer.text(r, segment.text(), cursor, textY, color, true, textScale);
            }
        }, contentWidth, contentHeight);
    }

    /** QQ 没连上时给一张提示卡, 免得"没消息"和"没连上"分不清。 */
    private Content qqStatusContent(HudRenderer renderer, double baseScale) {
        double titleScale = 0.9 * baseScale;
        double textScale = 0.95 * baseScale;
        double dot = 7 * baseScale;
        double maxWidth = qqMaxWidth.get() * baseScale;

        boolean connecting = qqClient.state() == QqClient.State.CONNECTING;
        String detail = qqClient.detail();
        if (detail.isBlank()) detail = connecting ? qqUrl.get() : "qq-ws-url is empty";
        // NapCat 那边的一句结论比异常原文有用: "QQ is running without NapCat" 之类
        if (!NapCatConfig.hint.isBlank()) detail = NapCatConfig.hint;

        String titleText = ellipsis(renderer, connecting ? "QQ connecting" : "QQ offline",
            maxWidth - dot - 6 * baseScale, titleScale);
        String detailText = ellipsis(renderer, detail, maxWidth, textScale);

        double titleHeight = IslandRenderer.textHeight(renderer, titleScale);
        double textHeight = IslandRenderer.textHeight(renderer, textScale);
        double contentWidth = Math.max(
            IslandRenderer.textWidth(renderer, titleText, titleScale) + dot + 6 * baseScale,
            IslandRenderer.textWidth(renderer, detailText, textScale));
        double contentHeight = titleHeight + 3 * baseScale + textHeight;
        Color accent = connecting ? new Color(255, 190, 80, 255) : new Color(255, 95, 95, 255);

        return new Content((r, originX, originY, alpha) -> {
            if (alpha <= 0.01) return;

            IslandRenderer.round(originX, originY + (titleHeight - dot) * 0.5, dot, dot, dot * 0.5,
                IslandRenderer.alpha(accent, (int) (255 * alpha)));
            IslandRenderer.text(r, titleText, originX + dot + 6 * baseScale, originY,
                IslandRenderer.alpha(TEXT_PRIMARY, (int) (255 * alpha)), true, titleScale);
            IslandRenderer.text(r, detailText, originX, originY + titleHeight + 3 * baseScale,
                IslandRenderer.alpha(TEXT_TERTIARY, (int) (255 * alpha)), true, textScale);
        }, contentWidth, contentHeight);
    }

    private Content containerContent(HudRenderer renderer, double baseScale) {
        int perRow = Math.max(1, slotsPerRow.get());
        int visible = previewItems.size();
        int rows = Math.max(1, (visible + perRow - 1) / perRow);

        double slot = 22 * baseScale;
        double inset = 2 * baseScale;
        double headerHeight = 18 * baseScale;
        double titleScale = 1.0 * baseScale;

        int used = 0;
        for (ItemStack stack : previewStacks) {
            if (!stack.isEmpty()) used++;
        }
        String countText = used + " / " + previewStacks.size();

        double gridWidth = Math.min(Math.max(visible, 1), perRow) * slot;
        double contentWidth = Math.max(gridWidth,
            IslandRenderer.textWidth(renderer, previewTitle, titleScale) + IslandRenderer.textWidth(renderer, countText, 0.8 * baseScale) + 40 * baseScale);
        double contentHeight = headerHeight + 4 * baseScale + rows * slot;

        return new Content((r, originX, originY, alpha) -> {
            Color titleColor = IslandRenderer.alpha(TEXT_PRIMARY, (int) (255 * alpha));
            Color dimColor = IslandRenderer.alpha(TEXT_TERTIARY, (int) (255 * alpha));

            IslandRenderer.round(originX, originY + 5 * baseScale, 7 * baseScale, 7 * baseScale,
                3.5 * baseScale, IslandRenderer.alpha(accentColor.get(), (int) (255 * alpha)));
            IslandRenderer.text(r, ellipsis(r, previewTitle, contentWidth - IslandRenderer.textWidth(r, countText, 0.8 * baseScale) - 24 * baseScale, titleScale),
                originX + 13 * baseScale, originY, titleColor, true, titleScale);

            double countWidth = IslandRenderer.textWidth(r, countText, 0.8 * baseScale);
            IslandRenderer.text(r, countText, originX + contentWidth - countWidth, originY + 2 * baseScale,
                dimColor, true, 0.8 * baseScale);

            IslandRenderer.round(originX, originY + headerHeight - 2 * baseScale, contentWidth, 1, 0.5,
                new Color(255, 255, 255, (int) (30 * alpha)));

            double gridY = originY + headerHeight + 2 * baseScale;

            if (visible == 0) {
                IslandRenderer.text(r, "Empty", originX + 2 * baseScale, gridY + 3 * baseScale, dimColor, true, titleScale);
                return;
            }

            for (int i = 0; i < visible; i++) {
                double cellX = originX + (i % perRow) * slot;
                double cellY = gridY + (i / perRow) * slot;
                double cellSize = slot - inset;

                IslandRenderer.round(cellX, cellY, cellSize, cellSize, 5 * baseScale,
                    new Color(255, 255, 255, (int) (16 * alpha)));
                IslandRenderer.border(cellX, cellY, cellSize, cellSize, 5 * baseScale, 1,
                    new Color(255, 255, 255, (int) (26 * alpha)));

                ItemStack stack = previewItems.get(i);
                double itemScale = 1.0 * baseScale;
                double pad = (cellSize - 16 * itemScale) * 0.5;
                String count = stack.getCount() > 1 ? String.valueOf(stack.getCount()) : null;

                r.item(stack, (int) (cellX + pad), (int) (cellY + pad), (float) itemScale, true, count);
            }
        }, contentWidth, contentHeight);
    }
    private Content foodContent(HudRenderer renderer, double baseScale) {
        double iconBox = 30 * baseScale;
        double nameScale = 1.05 * baseScale;
        double labelScale = 0.75 * baseScale;
        String name = ellipsis(renderer, foodStack.getName().getString(), 150 * baseScale, nameScale);
        double barWidth = Math.max(130 * baseScale, IslandRenderer.textWidth(renderer, name, nameScale) + 10 * baseScale);

        double contentWidth = iconBox + 12 * baseScale + barWidth;
        double contentHeight = Math.max(iconBox, 46 * baseScale);

        return new Content((r, originX, originY, alpha) -> {
            double progress = food.get();
            double clamped = Math.max(0.0, Math.min(1.0, progress));

            IslandRenderer.round(originX, originY, iconBox, iconBox, 8 * baseScale,
                new Color(200, 200, 210, (int) (52 * alpha)));
            double itemScale = 1.5 * baseScale;
            double itemPad = (iconBox - 16 * itemScale) * 0.5;
            IslandRenderer.item(r, foodStack, originX + itemPad, originY + itemPad, itemScale);

            double textX = originX + iconBox + 12 * baseScale;
            IslandRenderer.text(r, name, textX, originY, IslandRenderer.alpha(TEXT_PRIMARY, (int) (255 * alpha)), true, nameScale);
            IslandRenderer.text(r, (int) (clamped * 100) + "%", textX,
                originY + IslandRenderer.textHeight(r, nameScale) + 3 * baseScale,
                IslandRenderer.alpha(TEXT_SECONDARY, (int) (255 * alpha)), true, labelScale);

            double barY = originY + contentHeight - 10 * baseScale;
            IslandRenderer.round(textX, barY, barWidth, 8 * baseScale, 4 * baseScale,
                IslandRenderer.alpha(TRACK_BACKGROUND, (int) (TRACK_BACKGROUND.a * alpha)));
            IslandRenderer.round(textX, barY, Math.max(2 * baseScale, barWidth * clamped), 8 * baseScale,
                4 * baseScale, IslandRenderer.alpha(TRACK_FILL, (int) (TRACK_FILL.a * alpha)));
        }, contentWidth, contentHeight);
    }


    private void renderTabMode(HudRenderer renderer, float delta, double baseScale) {
        List<PlayerListEntry> players = sortedPlayers();
        int count = players.size();

        int rows = count == 0 ? 1 : Math.min(count, VANILLA_TAB_ROWS);
        int columns = count == 0 ? 1 : (int) Math.ceil(count / (double) rows);

        double nameScale = tabNameScale.get() * baseScale;
        double statusScale = 0.8 * baseScale;
        double padding = 12 * baseScale;
        double columnGap = 14 * baseScale;
        double rowGap = 3 * baseScale;
        double rowHeight = Math.max(IslandRenderer.textHeight(renderer, nameScale) + rowGap, 11 * baseScale);
        double divider = 1 * baseScale;
        double msWidth = IslandRenderer.textWidth(renderer, "999ms", nameScale);
        double barsWidth = 13 * baseScale;
        double pingWidth = tabPingBars.get() ? barsWidth + 4 * baseScale + msWidth : msWidth;

        double[] columnWidths = new double[Math.max(1, columns)];
        for (int i = 0; i < count; i++) {
            int column = i / rows;
            if (column >= columnWidths.length) continue;
            columnWidths[column] = Math.max(columnWidths[column],
                IslandRenderer.textWidth(renderer, players.get(i).getProfile().name(), nameScale));
        }
        for (int i = 0; i < columnWidths.length; i++) columnWidths[i] += pingWidth + 10 * baseScale;

        double gridWidth = 0;
        for (int i = 0; i < columnWidths.length; i++) {
            gridWidth += columnWidths[i];
            if (i < columnWidths.length - 1) gridWidth += columnGap;
        }

        double statusHeight = IslandRenderer.textHeight(renderer, statusScale) + 6 * baseScale;
        double headerHeight = tabHeaderFooter.get() && tabHeader != null
            ? IslandRenderer.textHeight(renderer, nameScale) + 6 * baseScale : 0;
        double footerHeight = tabHeaderFooter.get() && tabFooter != null
            ? IslandRenderer.textHeight(renderer, nameScale) + 6 * baseScale : 0;

        double listHeight = rows * rowHeight + Math.max(0, rows - 1) * divider;

        double contentWidth = Math.max(gridWidth, 170 * baseScale);
        double contentHeight = statusHeight + headerHeight + listHeight + footerHeight;

        width.set((float) (contentWidth + padding * 2));
        height.set((float) (contentHeight + padding * 2));
        width.update(delta);
        height.update(delta);

        double[] idle = measureIdle(renderer, baseScale);

        double panelWidth = width.get();
        double panelHeight = height.get();
        double radius = Math.min(20 * baseScale, Math.min(panelWidth, panelHeight) * 0.5);

        double progress = tab.get();
        double originX = x + positionOffset.get().x;
        double originY = y + positionOffset.get().y;
        double centerX = (mc.getWindow().getScaledWidth() - panelWidth) * 0.5 + tabOffsetX.get();
        double centerY = (mc.getWindow().getScaledHeight() - panelHeight) * 0.5 + tabOffsetY.get();
        double panelX = originX + (centerX - originX) * progress;
        double panelY = originY + (centerY - originY) * progress;

        IslandRenderer.shadow(panelX, panelY, panelWidth, panelHeight, radius, 1.0);
        IslandRenderer.glass(panelX, panelY, panelWidth, panelHeight, radius,
            blurStrength.get(), Math.max(backgroundAlpha.get(), 170), tintStrength.get(),
            border.get(), highlight.get());

        double alpha = Math.min(1.0, progress * 1.5);
        double cursorY = panelY + padding;

        String countText = count + (count == 1 ? " player" : " players");
        IslandRenderer.text(renderer, countText, panelX + padding, cursorY,
            IslandRenderer.alpha(TEXT_SECONDARY, (int) (220 * alpha)), true, statusScale);

        if (count > 0) {
            int total = 0;
            for (PlayerListEntry entry : players) total += Math.max(0, entry.getLatency());
            String average = "avg " + (total / count) + " ms";
            double averageWidth = IslandRenderer.textWidth(renderer, average, statusScale);
            IslandRenderer.text(renderer, average, panelX + panelWidth - padding - averageWidth, cursorY,
                IslandRenderer.alpha(TEXT_SECONDARY, (int) (220 * alpha)), true, statusScale);
        }
        cursorY += statusHeight;

        if (headerHeight > 0) {
            String header = ellipsis(renderer, tabHeader.getString(), panelWidth - padding * 2, nameScale);
            IslandRenderer.text(renderer, header,
                panelX + (panelWidth - IslandRenderer.textWidth(renderer, header, nameScale)) * 0.5, cursorY,
                IslandRenderer.alpha(TEXT_PRIMARY, (int) (230 * alpha)), true, nameScale);
            cursorY += headerHeight;
        }

        String selfName = mc.player == null ? null : mc.player.getGameProfile().name();
        double textOffset = (rowHeight - IslandRenderer.textHeight(renderer, nameScale)) * 0.5;

        for (int i = 0; i < count; i++) {
            PlayerListEntry entry = players.get(i);
            int column = i / rows;
            int row = i % rows;
            if (column >= columnWidths.length) break;

            double cellX = panelX + padding;
            for (int c = 0; c < column; c++) cellX += columnWidths[c] + columnGap;
            double cellY = cursorY + row * (rowHeight + divider);

            if (row > 0) {
                IslandRenderer.round(cellX, cellY - divider - rowGap * 0.5,
                    columnWidths[column], divider, 0.5,
                    new Color(255, 255, 255, (int) (28 * alpha)));
            }

            String name = entry.getProfile().name();
            Color nameColor = entry.getScoreboardTeam() != null
                ? IslandRenderer.alpha(teamColor(entry.getScoreboardTeam()), (int) (255 * alpha))
                : IslandRenderer.alpha(TEXT_PRIMARY, (int) (255 * alpha));

            if (name.equalsIgnoreCase(selfName)) {
                nameColor = IslandRenderer.alpha(accentColor.get(), (int) (255 * alpha));
            }

            IslandRenderer.text(renderer, name, cellX, cellY + textOffset, nameColor, true, nameScale);

            int latency = entry.getLatency();
            String pingText = Math.max(0, latency) + "ms";
            Color pingColor = IslandRenderer.alpha(latencyColor(latency), (int) (255 * alpha));

            if (tabPingBars.get()) {
                double barsX = cellX + columnWidths[column] - pingWidth;
                drawPingBars(renderer, barsX, cellY + rowHeight * 0.5, baseScale, latency, alpha);
                IslandRenderer.text(renderer, pingText, barsX + barsWidth + 4 * baseScale,
                    cellY + textOffset, pingColor, true, nameScale);
            } else {
                IslandRenderer.text(renderer, pingText,
                    cellX + columnWidths[column] - IslandRenderer.textWidth(renderer, pingText, nameScale),
                    cellY + textOffset, pingColor, true, nameScale);
            }
        }

        if (columns > 1) {
            double lineX = panelX + padding;
            for (int c = 0; c < columns - 1; c++) {
                lineX += columnWidths[c] + columnGap * 0.5;
                IslandRenderer.round(lineX, cursorY, divider, listHeight, 0.5,
                    new Color(255, 255, 255, (int) (20 * alpha)));
                lineX += columnGap * 0.5;
            }
        }

        if (footerHeight > 0) {
            String footer = ellipsis(renderer, tabFooter.getString(), panelWidth - padding * 2, nameScale);
            IslandRenderer.text(renderer, footer,
                panelX + (panelWidth - IslandRenderer.textWidth(renderer, footer, nameScale)) * 0.5,
                cursorY + listHeight + 2 * baseScale,
                IslandRenderer.alpha(TEXT_PRIMARY, (int) (230 * alpha)), true, nameScale);
        }

        setSize(idle[0], idle[1]);
    }
    private void drawPingBars(HudRenderer renderer, double x, double y, double baseScale,
                              int latency, double alpha) {
        int bars = latency < 0 ? 0
            : latency < 150 ? 5
            : latency < 300 ? 4
            : latency < 600 ? 3
            : latency < 1000 ? 2
            : 1;

        Color color = IslandRenderer.alpha(latencyColor(latency), (int) (255 * alpha));
        double barWidth = 1.5 * baseScale;
        double gap = 1.5 * baseScale;

        for (int i = 0; i < 5; i++) {
            double barHeight = (i + 1) * 1.4 * baseScale;
            double barX = x + i * (barWidth + gap);
            double barY = y + (5 * 1.4 * baseScale) * 0.5 - barHeight;

            Color barColor = i < bars ? color : IslandRenderer.alpha(color, (int) (255 * alpha * 0.28));
            renderer.quad(barX, barY, barWidth, barHeight, barColor);
        }
    }

    private Color teamColor(Team team) {
        Text formatted = team.getFormattedName();
        return formatted.getStyle().getColor() != null
            ? new Color(formatted.getStyle().getColor().getRgb() | 0xFF000000)
            : TEXT_PRIMARY;
    }

    private List<PlayerListEntry> sortedPlayers() {
        if (mc.getNetworkHandler() == null) return List.of();

        List<PlayerListEntry> players = new ArrayList<>(mc.getNetworkHandler().getPlayerList());
        players.sort(Comparator
            .comparingInt((PlayerListEntry entry) -> entry.getScoreboardTeam() == null ? 1 : 0)
            .thenComparing(entry -> {
                Team team = entry.getScoreboardTeam();
                return team == null ? "" : team.getName();
            }, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(entry -> entry.getProfile().name(), String.CASE_INSENSITIVE_ORDER));
        return players;
    }


    private String clientName() {
        return mc.getSession() == null ? "yuio Client" : mc.getSession().getUsername();
    }

    private double effectiveRadius(double panelWidth, double panelHeight, double baseScale) {
        double configured = cornerRadius.get();
        double max = Math.min(panelWidth, panelHeight) * 0.5;
        return Math.min(configured >= 999 ? max : configured * baseScale, max);
    }

    private double iconDrawHeight(double baseScale) {
        ensureIconSize();
        return iconAvailable && iconImageHeight > 0 ? iconHeight.get() * baseScale : 0;
    }

    private double iconDrawWidth(double baseScale) {
        double height = iconDrawHeight(baseScale);
        return height <= 0 ? 0 : height * iconImageWidth / (double) iconImageHeight;
    }


    @FunctionalInterface
    public interface ContentDraw {
        void draw(HudRenderer renderer, double x, double y, double alpha);
    }

    private record Content(ContentDraw render, double width, double height) {
    }

    private static final class TimedContent {
        private final Module module;
        private final String title;
        private final String description;
        private final StateStyle stateStyle;
        private final Anim fade = new Anim(1, 6);
        private final Anim knob = new Anim(0, 14);

        private long born;
        private long duration;
        private boolean fadeRequested;

        TimedContent(Module module, long duration, StateStyle style) {
            this.module = module;
            this.title = "Module Toggled";

            this.stateStyle = style;
            this.description = switch (style) {
                case OnOff -> module.title + "  ";
                case Boolean -> module.title + " = ";
                default -> module.title + " has been ";
            };
            this.knob.reset(module.isActive() ? 1.0 : 0.0);
            this.born = System.currentTimeMillis();
            this.duration = duration;
        }

        TimedContent(String title, String description, long duration) {
            this.module = null;
            this.title = title;
            this.description = description;
            this.stateStyle = null;
            this.born = System.currentTimeMillis();
            this.duration = duration;
        }

        double fade() {
            return fade.get();
        }

        /** 每次都按模块当前状态算, 不在构造时冻结。 */
        private String stateWord() {
            if (module == null || stateStyle == null) return null;

            boolean on = module.isActive();
            return switch (stateStyle) {
                case OnOff -> on ? "on" : "off";
                case Boolean -> String.valueOf(on);
                default -> on ? "Enabled" : "Disabled";
            };
        }

        void reborn() {
            this.born = System.currentTimeMillis();
            this.fadeRequested = false;
            this.fade.reset(1);
        }

        boolean isExpired(long now) {
            return now - born > duration && fade.get() <= 0.01f;
        }

        void update(float delta) {
            if (!fadeRequested && System.currentTimeMillis() - born > duration - 260) {
                fadeRequested = true;
                fade.set(0.0f);
            }
            fade.update(delta);

            if (module != null) knob.set(module.isActive() ? 1.0 : 0.0);
            knob.update(delta);
        }

        Content toContent(HudRenderer renderer, double baseScale) {
            boolean enabled = module == null || module.isActive();
            Color accent = enabled ? TOGGLE_ENABLED : TOGGLE_DISABLED;
            String stateWord = stateWord();

            double switchWidth = 36 * baseScale;
            double switchHeight = 20 * baseScale;
            double titleScale = 1.0 * baseScale;
            double descriptionScale = 0.85 * baseScale;

            double descriptionWidth = IslandRenderer.textWidth(renderer, description, descriptionScale)
                + (stateWord == null ? 0 : IslandRenderer.textWidth(renderer, stateWord, descriptionScale));

            double width = switchWidth + 12 * baseScale + Math.max(
                IslandRenderer.textWidth(renderer, title, titleScale),
                descriptionWidth
            );
            double height = Math.max(switchHeight, IslandRenderer.textHeight(renderer, titleScale)
                + IslandRenderer.textHeight(renderer, descriptionScale) + 2 * baseScale);

            return new Content((r, originX, originY, alpha) -> {
                double a = alpha * fade.get();
                if (a <= 0.01) return;

                Color track = module == null
                    ? IslandRenderer.alpha(accent, (int) (255 * a))
                    : IslandRenderer.lerp(SWITCH_OFF, accent, knob.get());
                track = IslandRenderer.alpha(track, (int) (255 * a));

                double switchY = originY + (height - switchHeight) * 0.5;
                IslandRenderer.round(originX, switchY, switchWidth, switchHeight, switchHeight * 0.5, track);

                double knobSize = switchHeight - 4 * baseScale;
                double knobX = originX + 2 * baseScale + (switchWidth - knobSize - 4 * baseScale) * knob.get();
                IslandRenderer.round(knobX, switchY + 2 * baseScale, knobSize, knobSize, knobSize * 0.5,
                    IslandRenderer.alpha(SWITCH_KNOB, (int) (255 * a)));

                double textX = originX + switchWidth + 12 * baseScale;
                double textY = originY + (height - (IslandRenderer.textHeight(r, titleScale)
                    + IslandRenderer.textHeight(r, descriptionScale) + 2 * baseScale)) * 0.5;

                IslandRenderer.text(r, title, textX, textY, IslandRenderer.alpha(TEXT_PRIMARY, (int) (255 * a)), true, titleScale);

                double descriptionY = textY + IslandRenderer.textHeight(r, titleScale) + 2 * baseScale;
                IslandRenderer.text(r, description, textX, descriptionY,
                    IslandRenderer.alpha(TEXT_SECONDARY, (int) (255 * a)), true, descriptionScale);

                if (stateWord != null) {
                    IslandRenderer.text(r, stateWord, textX + IslandRenderer.textWidth(r, description, descriptionScale), descriptionY,
                        IslandRenderer.alpha(accent, (int) (255 * a)), true, descriptionScale);
                }
            }, width, height);
        }
    }
}
