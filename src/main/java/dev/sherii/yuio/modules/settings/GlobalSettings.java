package dev.sherii.yuio.modules.settings;

import com.example.addon.AddonTemplate;
import dev.sherii.yuio.AModule;
import dev.sherii.yuio.utils.TpUtils;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.world.TickRate;

public final class GlobalSettings extends AModule {
    public static final GlobalSettings INSTANCE = new GlobalSettings();

    public enum ToolTpMode {
        TpUtils,
        TPUtilX,
        LTpUtils
    }

    public enum LimitAction {
        None,
        Drop,
        Message,
        Both
    }

    private static final SettingGroup sgTp = INSTANCE.settings.getDefaultGroup();
    private static final SettingGroup sgLimit = INSTANCE.settings.createGroup("Packet-Limit");
    private static final SettingGroup sgTools = INSTANCE.settings.createGroup("Tools");
    private static final SettingGroup sgInfo = INSTANCE.settings.createGroup("Info");

    private static final DoubleSetting moveDistance = sgTp.add(new DoubleSetting.Builder()
        .name("move-distance")
        .description("Distance of a single teleport step used by TpUtils.")
        .min(0.0)
        .sliderRange(1.0, 128.0)
        .defaultValue(10.0)
        .build());

    private static final DoubleSetting velocityMoveSpeed = sgTp.add(new DoubleSetting.Builder()
        .name("velocity-speed")
        .description("Velocity used by the velocity move mode of TpUtils.")
        .min(0.0)
        .sliderRange(0.1, 256.0)
        .defaultValue(10.0)
        .build());

    private static final EnumSetting<ToolTpMode> tpMode = sgTp.add(new EnumSetting.Builder<ToolTpMode>()
        .name("tp-mode")
        .description("Which TP utility the modules should use.")
        .defaultValue(ToolTpMode.TpUtils)
        .build());

    private static final EnumSetting<LimitAction> action = sgLimit.add(new EnumSetting.Builder<LimitAction>()
        .name("limit-action")
        .description("What TpUtils does when the outgoing packet rate is over the limit.")
        .defaultValue(LimitAction.Both)
        .build());

    private static final DoubleSetting interval = sgLimit.add(new DoubleSetting.Builder()
        .name("interval")
        .description("Sampling window in seconds used for the teleport packet rate.")
        .min(0.0)
        .sliderRange(0.0, 10.0)
        .defaultValue(7.0)
        .onChanged(value -> TpUtils.INSTANCE.setInterval(value))
        .build());

    private static final IntSetting maxPacketRate = sgLimit.add(new IntSetting.Builder()
        .name("max-packets")
        .description("Maximum teleport packets inside the sampling window.")
        .min(0)
        .sliderRange(0, 32767)
        .defaultValue(500)
        .build());

    private static final IntSetting inOneTick = sgLimit.add(new IntSetting.Builder()
        .name("packets-per-tick")
        .description("Maximum teleport packets in a single tick, 0 for unlimited.")
        .min(0)
        .sliderRange(0, 1000)
        .defaultValue(0)
        .build());

    private static final IntSetting limitPacket = sgLimit.add(new IntSetting.Builder()
        .name("limit-packet")
        .description("Hard packet budget used by the limiter helpers.")
        .min(1)
        .sliderRange(1, 100)
        .defaultValue(20)
        .build());

    private static final BoolSetting tpOnGround = sgTools.add(new BoolSetting.Builder()
        .name("tp-on-ground")
        .description("Mark teleport packets as on ground.")
        .defaultValue(false)
        .build());

    private static final BoolSetting swapSilent = sgTools.add(new BoolSetting.Builder()
        .name("silent-swap")
        .description("SwapUtils restores the previous slot after a swap with the back flag.")
        .defaultValue(true)
        .build());

    private static final BoolSetting swingOnAttack = sgTools.add(new BoolSetting.Builder()
        .name("swing-on-attack")
        .description("InteractUtils swings the hand after sending an attack packet.")
        .defaultValue(true)
        .build());

    private static final BoolSetting countPackets = sgTools.add(new BoolSetting.Builder()
        .name("count-packets")
        .description("Track how many packets PacketUtils sent since the module was enabled.")
        .defaultValue(true)
        .build());

    private static final DoubleSetting blocksPerSecond = sgInfo.add(new DoubleSetting.Builder()
        .name("blocks-per-second")
        .description("Average horizontal speed assumed when a module estimates travel time.")
        .min(0.1)
        .sliderRange(0.1, 100.0)
        .defaultValue(10.0)
        .build());

    private GlobalSettings() {
        super(AddonTemplate.SETTINGS, "GlobalSettings", "Shared options for the TpUtils, SwapUtils, InteractUtils and PacketUtils helpers.");
    }

    @Override
    public void onActivate() {
        TpUtils.INSTANCE.setInterval(interval.get());
    }

    public static double getMoveDistance() {
        return moveDistance.get();
    }

    public static double getVelocityMoveSpeed() {
        return velocityMoveSpeed.get();
    }

    public static ToolTpMode getToolTpMode() {
        return tpMode.get();
    }

    public static LimitAction getLimitAction() {
        return action.get();
    }

    public static double getInterval() {
        return interval.get();
    }

    public static int getMaxPacketRate() {
        return maxPacketRate.get();
    }

    public static int getInOneTick() {
        return inOneTick.get();
    }

    public static int getLimitPacket() {
        return limitPacket.get();
    }

    public static boolean isTpOnGround() {
        return tpOnGround.get();
    }

    public static boolean isSilentSwap() {
        return swapSilent.get();
    }

    public static boolean isSwingOnAttack() {
        return swingOnAttack.get();
    }

    public static boolean isCountPackets() {
        return countPackets.get();
    }

    public static double getBlocksPerSecond() {
        return blocksPerSecond.get();
    }

    @Override
    public String getInfoString() {
        return String.format("%.2f tps", TickRate.INSTANCE.getTickRate());
    }

}