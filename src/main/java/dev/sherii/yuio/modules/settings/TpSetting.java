package dev.sherii.yuio.modules.settings;

import com.example.addon.AddonTemplate;
import dev.sherii.yuio.AModule;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.SettingGroup;

public final class TpSetting extends AModule {
    public static final TpSetting INSTANCE = new TpSetting();

    public enum PacketMode {
        Position,
        PositionRotation,
        Rotation,
        Ground
    }

    public enum GroundMode {
        Keep,
        ForceTrue,
        ForceFalse
    }

    private static final SettingGroup sgGeneral = INSTANCE.settings.getDefaultGroup();
    private static final SettingGroup sgPacket = INSTANCE.settings.createGroup("TP Packet");

    private static final DoubleSetting moveDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("MoveDistance")
        .description("Distance between two interpolation steps when smooth teleport is enabled.")
        .min(0.0)
        .sliderRange(1.0, 128.0)
        .defaultValue(10.0)
        .build()
    );

    private static final DoubleSetting velocityMoveSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("VelocitySpeed")
        .description("Velocity used by the velocity move mode.")
        .min(0.0)
        .sliderRange(0.1, 256.0)
        .defaultValue(10.0)
        .build()
    );

    private static final EnumSetting<PacketMode> packetMode = sgPacket.add(new EnumSetting.Builder<PacketMode>()
        .name("packet-mode")
        .description("Which PlayerMoveC2SPacket flavour a teleport step sends.")
        .defaultValue(PacketMode.Position)
        .build()
    );

    private static final BoolSetting smooth = sgPacket.add(new BoolSetting.Builder()
        .name("smooth")
        .description("Interpolate along the path instead of re-sending the start position. Off = a single teleport packet.")
        .defaultValue(false)
        .build()
    );

    private static final IntSetting repeat = sgPacket.add(new IntSetting.Builder()
        .name("repeat")
        .description("How many times the destination packet is sent.")
        .min(1)
        .sliderRange(1, 20)
        .defaultValue(1)
        .build()
    );

    private static final EnumSetting<GroundMode> onGround = sgPacket.add(new EnumSetting.Builder<GroundMode>()
        .name("on-ground")
        .description("Override the onGround flag carried by the teleport packets.")
        .defaultValue(GroundMode.Keep)
        .build()
    );

    private static final BoolSetting applyClientPos = sgPacket.add(new BoolSetting.Builder()
        .name("apply-client-position")
        .description("Also move the client player to the destination after the teleport.")
        .defaultValue(false)
        .build()
    );

    private TpSetting() {
        super(AddonTemplate.SETTINGS, "tp-setting", "Tp Setting.");
    }

    public double getMoveDistance() {
        return moveDistance.get();
    }

    public double getVelocityMoveSpeed() {
        return velocityMoveSpeed.get();
    }

    public PacketMode getPacketMode() {
        return packetMode.get();
    }

    public boolean isSmooth() {
        return smooth.get();
    }

    public int getRepeat() {
        return repeat.get();
    }

    public GroundMode getGroundMode() {
        return onGround.get();
    }

    public boolean isApplyClientPos() {
        return applyClientPos.get();
    }
}
