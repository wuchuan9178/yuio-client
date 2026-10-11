package dev.sherii.yuio.modules.settings;

import com.example.addon.AddonTemplate;
import dev.sherii.yuio.AModule;
import dev.sherii.yuio.predict.PredictConfig;
import dev.sherii.yuio.predict.PredictEngine;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

public final class PredictSetting extends AModule {
    public static final PredictSetting INSTANCE = new PredictSetting();

    private static final SettingGroup sgGeneral = INSTANCE.settings.getDefaultGroup();
    private static final SettingGroup sgSampling = INSTANCE.settings.createGroup("Sampling");
    private static final SettingGroup sgClassify = INSTANCE.settings.createGroup("Classification");
    private static final SettingGroup sgOrbit = INSTANCE.settings.createGroup("Orbit");
    private static final SettingGroup sgConfidence = INSTANCE.settings.createGroup("Confidence");
    private static final SettingGroup sgLatency = INSTANCE.settings.createGroup("Latency");
    private static final SettingGroup sgLearning = INSTANCE.settings.createGroup("Learning");
    private static final SettingGroup sgEngine = INSTANCE.settings.createGroup("Engine");

    private static final IntSetting _predictTicks = sgGeneral.add(new IntSetting.Builder()
        .name("predict-ticks")
        .description("Fallback lead in ticks for modules that do not run the full latency and learning pipeline.")
        .defaultValue(4).min(1).max(20).sliderRange(1, 20)
        .build());

    private static final BoolSetting debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug")
        .description("Write one line per prediction to the game log: motion mode, lead, confidence, variance and learned bias.")
        .defaultValue(false)
        .onChanged(value -> PredictConfig.debug = value)
        .build());

    private static final IntSetting sampleCapacity = sgSampling.add(new IntSetting.Builder()
        .name("sample-capacity")
        .description("Position samples kept per entity for the least squares regression. Larger is smoother but slower to react.")
        .defaultValue(24).min(PredictConfig.MIN_CAPACITY).max(PredictConfig.MAX_CAPACITY)
        .sliderRange(PredictConfig.MIN_CAPACITY, PredictConfig.MAX_CAPACITY)
        .onChanged(value -> PredictConfig.sampleCapacity = value)
        .build());

    private static final BoolSetting packetSampling = sgSampling.add(new BoolSetting.Builder()
        .name("packet-sampling")
        .description("Feed raw entity position packets into the tracker. Exactly one tick fresher than the tick sampler.")
        .defaultValue(true)
        .onChanged(value -> PredictConfig.packetSampling = value)
        .build());

    private static final BoolSetting tickSampling = sgSampling.add(new BoolSetting.Builder()
        .name("tick-sampling")
        .description("Also feed every tracked player position at the end of each client tick. Keeps history alive when packets are sparse.")
        .defaultValue(true)
        .onChanged(value -> PredictConfig.tickSampling = value)
        .build());

    private static final DoubleSetting tickMillis = sgSampling.add(new DoubleSetting.Builder()
        .name("tick-length")
        .description("Expected server tick length in milliseconds. This is the time unit the regression is solved against.")
        .defaultValue(50.0).min(10.0).max(200.0).sliderRange(10.0, 200.0)
        .onChanged(value -> PredictConfig.tickMillis = value)
        .build());

    private static final BoolSetting tickCompensation = sgSampling.add(new BoolSetting.Builder()
        .name("tick-compensation")
        .description("Rescale sampled velocity by the measured tick length, so a low TPS server does not read as slow movement.")
        .defaultValue(true)
        .onChanged(value -> PredictConfig.tickCompensation = value)
        .build());

    private static final DoubleSetting minTickScale = sgSampling.add(new DoubleSetting.Builder()
        .name("min-tick-scale")
        .description("Lower bound of the tick length compensation factor.")
        .defaultValue(0.5).min(0.05).max(1.0).sliderRange(0.05, 1.0)
        .visible(tickCompensation::get)
        .onChanged(value -> PredictConfig.minTickScale = value)
        .build());

    private static final DoubleSetting maxTickScale = sgSampling.add(new DoubleSetting.Builder()
        .name("max-tick-scale")
        .description("Upper bound of the tick length compensation factor.")
        .defaultValue(3.0).min(1.0).max(8.0).sliderRange(1.0, 8.0)
        .visible(tickCompensation::get)
        .onChanged(value -> PredictConfig.maxTickScale = value)
        .build());

    private static final IntSetting stillSamples = sgSampling.add(new IntSetting.Builder()
        .name("still-samples")
        .description("Identical positions in a row before an entity is promoted from Unknown to Static.")
        .defaultValue(3).min(1).max(10).sliderRange(1, 10)
        .onChanged(value -> PredictConfig.stillSamples = value)
        .build());

    private static final DoubleSetting staticSpeed = sgClassify.add(new DoubleSetting.Builder()
        .name("static-speed")
        .description("Speed below which a target counts as standing still, in blocks per tick. Applies to both horizontal and vertical.")
        .defaultValue(0.008).min(0.0).max(0.5).sliderRange(0.0, 0.1)
        .onChanged(value -> PredictConfig.staticSpeed = value)
        .build());

    private static final DoubleSetting teleportDistance = sgClassify.add(new DoubleSetting.Builder()
        .name("teleport-distance")
        .description("Single sample jump that counts as a teleport or VClip, in blocks.")
        .defaultValue(3.0).min(0.5).max(16.0).sliderRange(0.5, 16.0)
        .onChanged(value -> PredictConfig.teleportDistance = value)
        .build());

    private static final IntSetting teleportCooldown = sgClassify.add(new IntSetting.Builder()
        .name("teleport-cooldown")
        .description("Ticks a teleport keeps the entity classified as Teleport, during which extrapolation stays frozen.")
        .defaultValue(10).min(1).max(40).sliderRange(1, 40)
        .onChanged(value -> PredictConfig.teleportCooldown = value)
        .build());

    private static final BoolSetting clearOnTeleport = sgClassify.add(new BoolSetting.Builder()
        .name("clear-on-teleport")
        .description("Wipe the sample history on a teleport. Required for random VClip targets, otherwise the stale velocity keeps being extrapolated.")
        .defaultValue(true)
        .onChanged(value -> PredictConfig.clearOnTeleport = value)
        .build());

    private static final DoubleSetting smoothing = sgClassify.add(new DoubleSetting.Builder()
        .name("smoothing")
        .description("Weight of the instantaneous velocity in the final blend. High reacts fast but is noisy, low leans on the regression fit.")
        .defaultValue(0.35).min(0.0).max(1.0).sliderRange(0.0, 1.0)
        .onChanged(value -> PredictConfig.smoothing = value)
        .build());

    private static final IntSetting minSamples = sgClassify.add(new IntSetting.Builder()
        .name("min-samples")
        .description("Samples required before an entity is promoted from Unknown to Linear.")
        .defaultValue(3).min(2).max(16).sliderRange(2, 16)
        .onChanged(value -> PredictConfig.minSamples = value)
        .build());

    private static final IntSetting residualWindow = sgClassify.add(new IntSetting.Builder()
        .name("residual-window")
        .description("Newest samples used for the residual RMS that is reported as prediction variance.")
        .defaultValue(6).min(2).max(24).sliderRange(2, 24)
        .onChanged(value -> PredictConfig.residualWindow = value)
        .build());

    private static final BoolSetting orbitEnabled = sgOrbit.add(new BoolSetting.Builder()
        .name("orbit-enabled")
        .description("Detect circular movement and lock onto its phase instead of extrapolating a straight line.")
        .defaultValue(true)
        .onChanged(value -> PredictConfig.orbitEnabled = value)
        .build());

    private static final DoubleSetting orbitMinSpeed = sgOrbit.add(new DoubleSetting.Builder()
        .name("orbit-min-speed")
        .description("Minimum horizontal speed before circular motion is considered, in blocks per tick.")
        .defaultValue(0.04).min(0.0).max(1.0).sliderRange(0.0, 0.5)
        .visible(orbitEnabled::get)
        .onChanged(value -> PredictConfig.orbitMinSpeed = value)
        .build());

    private static final DoubleSetting orbitMinOmega = sgOrbit.add(new DoubleSetting.Builder()
        .name("orbit-min-omega")
        .description("Minimum absolute angular velocity, in radians per tick. Below this the target is treated as straight moving.")
        .defaultValue(0.01).min(0.0).max(0.5).sliderRange(0.0, 0.3)
        .visible(orbitEnabled::get)
        .onChanged(value -> PredictConfig.orbitMinOmega = value)
        .build());

    private static final IntSetting orbitMinStreak = sgOrbit.add(new IntSetting.Builder()
        .name("orbit-min-streak")
        .description("Consecutive same direction heading changes required before the circle is trusted.")
        .defaultValue(4).min(2).max(16).sliderRange(2, 16)
        .visible(orbitEnabled::get)
        .onChanged(value -> PredictConfig.orbitMinStreak = value)
        .build());

    private static final DoubleSetting orbitMaxRadius = sgOrbit.add(new DoubleSetting.Builder()
        .name("orbit-max-radius")
        .description("Largest circle radius accepted, in blocks. Wider fits are rejected as noise.")
        .defaultValue(60.0).min(2.0).max(200.0).sliderRange(2.0, 200.0)
        .visible(orbitEnabled::get)
        .onChanged(value -> PredictConfig.orbitMaxRadius = value)
        .build());

    private static final DoubleSetting orbitScore = sgOrbit.add(new DoubleSetting.Builder()
        .name("orbit-score")
        .description("Orbit confidence needed to override the linear classification.")
        .defaultValue(0.6).min(0.0).max(1.0).sliderRange(0.0, 1.0)
        .visible(orbitEnabled::get)
        .onChanged(value -> PredictConfig.orbitScore = value)
        .build());

    private static final DoubleSetting stalenessDecay = sgConfidence.add(new DoubleSetting.Builder()
        .name("staleness-decay")
        .description("How fast confidence falls per idle tick after the second one without a fresh sample.")
        .defaultValue(0.35).min(0.0).max(2.0).sliderRange(0.0, 2.0)
        .onChanged(value -> PredictConfig.stalenessDecay = value)
        .build());

    private static final DoubleSetting minConfidence = sgConfidence.add(new DoubleSetting.Builder()
        .name("min-confidence")
        .description("Floor applied to the reported confidence. A teleporting target always reports zero.")
        .defaultValue(0.0).min(0.0).max(1.0).sliderRange(0.0, 1.0)
        .onChanged(value -> PredictConfig.minConfidence = value)
        .build());

    private static final BoolSetting latencyCompensation = sgLatency.add(new BoolSetting.Builder()
        .name("latency-compensation")
        .description("Add both round trip times to the lead, so the prediction targets where the enemy will be when the hit lands.")
        .defaultValue(true)
        .onChanged(value -> PredictConfig.latencyCompensation = value)
        .build());

    private static final DoubleSetting pingScale = sgLatency.add(new DoubleSetting.Builder()
        .name("ping-scale")
        .description("Multiplier on the ping part of the lead. Raise it if you consistently hit behind a moving target.")
        .defaultValue(1.0).min(0.0).max(4.0).sliderRange(0.0, 4.0)
        .visible(latencyCompensation::get)
        .onChanged(value -> PredictConfig.pingScale = value)
        .build());

    private static final DoubleSetting pipelineTicks = sgLatency.add(new DoubleSetting.Builder()
        .name("pipeline-ticks")
        .description("Ticks consumed by our own movement and attack pipeline between deciding and landing.")
        .defaultValue(1.0).min(0.0).max(5.0).sliderRange(0.0, 5.0)
        .onChanged(value -> PredictConfig.pipelineTicks = value)
        .build());

    private static final DoubleSetting actionTicks = sgLatency.add(new DoubleSetting.Builder()
        .name("action-ticks")
        .description("Default number of ticks the action itself needs to take effect, used when a module does not override it.")
        .defaultValue(1.0).min(0.0).max(5.0).sliderRange(0.0, 5.0)
        .onChanged(value -> PredictConfig.actionTicks = value)
        .build());

    private static final DoubleSetting minLead = sgLatency.add(new DoubleSetting.Builder()
        .name("min-lead")
        .description("Smallest lead the engine may return, in ticks.")
        .defaultValue(1.0).min(0.0).max(10.0).sliderRange(0.0, 10.0)
        .onChanged(value -> PredictConfig.minLead = value)
        .build());

    private static final DoubleSetting maxLead = sgLatency.add(new DoubleSetting.Builder()
        .name("max-lead")
        .description("Largest lead the engine may return, in ticks. Caps runaway correction on very high ping.")
        .defaultValue(12.0).min(1.0).max(40.0).sliderRange(1.0, 40.0)
        .onChanged(value -> PredictConfig.maxLead = value)
        .build());

    private static final BoolSetting learning = sgLearning.add(new BoolSetting.Builder()
        .name("learning")
        .description("Learn a per player lead correction from every landed prediction that could be measured.")
        .defaultValue(true)
        .onChanged(value -> PredictConfig.learning = value)
        .build());

    private static final DoubleSetting learningRate = sgLearning.add(new DoubleSetting.Builder()
        .name("learning-rate")
        .description("Fraction of a measured error folded into the stored bias each time. Higher adapts faster and overshoots more.")
        .defaultValue(0.08).min(0.0).max(0.5).sliderRange(0.0, 0.5)
        .visible(learning::get)
        .onChanged(value -> PredictConfig.learningRate = value)
        .build());

    private static final DoubleSetting maxBias = sgLearning.add(new DoubleSetting.Builder()
        .name("max-bias")
        .description("Largest lead correction the learner may apply, in ticks.")
        .defaultValue(8.0).min(0.0).max(20.0).sliderRange(0.0, 20.0)
        .visible(learning::get)
        .onChanged(value -> PredictConfig.maxBias = value)
        .build());

    private static final DoubleSetting maxError = sgLearning.add(new DoubleSetting.Builder()
        .name("max-error")
        .description("A single measurement is clamped here before it reaches the learner, so one bad sample cannot swing the bias.")
        .defaultValue(8.0).min(0.0).max(40.0).sliderRange(0.0, 40.0)
        .visible(learning::get)
        .onChanged(value -> PredictConfig.maxError = value)
        .build());

    private static final DoubleSetting hitRadius = sgLearning.add(new DoubleSetting.Builder()
        .name("hit-radius")
        .description("Distance within which a stored prediction counts as a hit when it is scored.")
        .defaultValue(1.2).min(0.1).max(10.0).sliderRange(0.1, 10.0)
        .onChanged(value -> PredictConfig.hitRadius = value)
        .build());

    private static final BoolSetting resetData = sgEngine.add(new BoolSetting.Builder()
        .name("reset-on-enable")
        .description("Clear every tracked motion history and pending measurement when this module is enabled.")
        .defaultValue(true)
        .build());

    private static final IntSetting maxIdleTicks = sgEngine.add(new IntSetting.Builder()
        .name("max-idle-ticks")
        .description("Ticks without a sample before an entity is dropped from the tracker.")
        .defaultValue(100).min(5).max(600).sliderRange(5, 600)
        .onChanged(value -> PredictConfig.maxIdleTicks = value)
        .build());

    private static final IntSetting maxTrackedEntities = sgEngine.add(new IntSetting.Builder()
        .name("max-tracked-entities")
        .description("Upper bound on simultaneously tracked entities. The least recently seen one is evicted first.")
        .defaultValue(128).min(8).max(512).sliderRange(8, 512)
        .onChanged(value -> PredictConfig.maxTrackedEntities = value)
        .build());

    private PredictSetting() {
        super(AddonTemplate.SETTINGS, "predict-setting",
            "Fused prediction engine: packet level sampling, least squares velocity, orbit phase lock, teleport reset and online lead learning.");
    }

    public int getPredictTicks() {
        return _predictTicks.get();
    }

    public void setPredictTicks(int value) {
        _predictTicks.set(value);
    }

    private static void apply() {
        PredictConfig.debug = debug.get();
        PredictConfig.sampleCapacity = sampleCapacity.get();
        PredictConfig.packetSampling = packetSampling.get();
        PredictConfig.tickSampling = tickSampling.get();
        PredictConfig.tickMillis = tickMillis.get();
        PredictConfig.tickCompensation = tickCompensation.get();
        PredictConfig.minTickScale = minTickScale.get();
        PredictConfig.maxTickScale = maxTickScale.get();
        PredictConfig.stillSamples = stillSamples.get();

        PredictConfig.staticSpeed = staticSpeed.get();
        PredictConfig.teleportDistance = teleportDistance.get();
        PredictConfig.teleportCooldown = teleportCooldown.get();
        PredictConfig.clearOnTeleport = clearOnTeleport.get();
        PredictConfig.smoothing = smoothing.get();
        PredictConfig.minSamples = minSamples.get();
        PredictConfig.residualWindow = residualWindow.get();

        PredictConfig.orbitEnabled = orbitEnabled.get();
        PredictConfig.orbitMinSpeed = orbitMinSpeed.get();
        PredictConfig.orbitMinOmega = orbitMinOmega.get();
        PredictConfig.orbitMinStreak = orbitMinStreak.get();
        PredictConfig.orbitMaxRadius = orbitMaxRadius.get();
        PredictConfig.orbitScore = orbitScore.get();

        PredictConfig.stalenessDecay = stalenessDecay.get();
        PredictConfig.minConfidence = minConfidence.get();

        PredictConfig.latencyCompensation = latencyCompensation.get();
        PredictConfig.pingScale = pingScale.get();
        PredictConfig.pipelineTicks = pipelineTicks.get();
        PredictConfig.actionTicks = actionTicks.get();
        PredictConfig.minLead = minLead.get();
        PredictConfig.maxLead = maxLead.get();

        PredictConfig.learning = learning.get();
        PredictConfig.learningRate = learningRate.get();
        PredictConfig.maxBias = maxBias.get();
        PredictConfig.maxError = maxError.get();
        PredictConfig.hitRadius = hitRadius.get();

        PredictConfig.maxIdleTicks = maxIdleTicks.get();
        PredictConfig.maxTrackedEntities = maxTrackedEntities.get();
    }

    @Override
    public void onActivate() {
        apply();
        if (resetData.get()) PredictEngine.INSTANCE.reset();
    }
}
