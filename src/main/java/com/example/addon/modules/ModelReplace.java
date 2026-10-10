package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import dev.sherii.yuio.AModule;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ModelReplace extends AModule {
    public static ModelReplace INSTANCE;

    public enum Mode {
        NameList,
        AllPlayers
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("NameList only replaces the listed players, AllPlayers replaces everyone.")
        .defaultValue(Mode.NameList)
        .build());

    private final Setting<List<String>> names = sgGeneral.add(new StringListSetting.Builder()
        .name("names")
        .description("Player names to replace, only used by the NameList mode.")
        .defaultValue(List.of())
        .visible(() -> mode.get() == Mode.NameList)
        .build());

    private final Setting<Set<EntityType<?>>> model = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("model")
        .description("Mob model used to replace the player.")
        .defaultValue(EntityType.PIG)
        .onlyAttackable()
        .build());

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Do not replace friends.")
        .defaultValue(false)
        .build());

    private final Map<UUID, LivingEntity> fakes = new HashMap<>();

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug")
        .description("Log what the module is doing once per second.")
        .defaultValue(true)
        .build());

    private long lastLog;

    public ModelReplace() {
        super(AddonTemplate.OTHER, "model-replace", "Replaces player models with vanilla mob models.");
        INSTANCE = this;
    }

    @Override
    public void onActivate() {
        fakes.clear();
    }

    @Override
    public void onDeactivate() {
        fakes.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) {
            fakes.clear();
            return;
        }

        Iterator<Map.Entry<UUID, LivingEntity>> it = fakes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, LivingEntity> entry = it.next();
            AbstractClientPlayerEntity player = findPlayer(entry.getKey());
            if (player == null || !shouldReplace(player)) it.remove();
        }
    }

    private AbstractClientPlayerEntity findPlayer(UUID uuid) {
        if (mc.world == null) return null;

        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (player.getUuid().equals(uuid)) return player;
        }
        return null;
    }

    private boolean shouldReplace(AbstractClientPlayerEntity player) {
        if (ignoreFriends.get() && !Friends.get().shouldAttack(player)) return false;
        if (mode.get() == Mode.AllPlayers) return true;
        return matchesName(player);
    }

    private boolean matchesName(AbstractClientPlayerEntity player) {
        String name = player.getGameProfile().name();
        if (name == null || name.isEmpty()) return false;

        for (String entry : names.get()) {
            if (entry != null && entry.trim().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private EntityType<?> currentType() {
        Set<EntityType<?>> set = model.get();
        if (set == null || set.isEmpty()) return null;

        for (EntityType<?> type : set) return type;
        return null;
    }

    public void apply(EntityRenderManager manager, float tickDelta, WorldRenderState state) {
        if (mc.player == null || mc.world == null || manager == null || state == null) return;
        if (state.cameraRenderState == null || state.cameraRenderState.pos == null) return;

        EntityType<?> type = currentType();
        if (type == null) {
            fakes.clear();
            return;
        }

        Vec3d cameraPos = state.cameraRenderState.pos;
        List<EntityRenderState> added = new ArrayList<>();
        boolean replaced = false;
        int players = 0;
        int selected = 0;

        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            players++;
            if (!shouldReplace(player)) continue;
            if (player.squaredDistanceTo(cameraPos) > 65536.0) continue;

            replaced = true;
            selected++;
            LivingEntity fake = fakeFor(player, type);
            if (fake == null) continue;

            sync(fake, player);
            if (!fake.shouldRender(cameraPos.x, cameraPos.y, cameraPos.z)) continue;

            added.add(manager.getAndUpdateRenderState(fake, tickDelta));
        }

// @instr
        logDebug(state.entityRenderStates.size(), players, selected, 0, fakes.size(), added.size());
        if (!replaced) return;

        removePlayerStates(state, cameraPos);
        state.entityRenderStates.addAll(added);
    }

    private void logDebug(int states, int players, int selected, int culled, int fakes, int added) {
        if (!debug.get()) return;

        long now = System.currentTimeMillis();
        if (now - lastLog < 1000L) return;
        lastLog = now;

        AddonTemplate.LOG.info("[yuio-model] mode={} types={} players={} selected={} culled={} fakes={} added={} states={}",
            mode.get(), currentType(), players, selected, culled, fakes, added, states);
    }

    private LivingEntity fakeFor(AbstractClientPlayerEntity player, EntityType<?> type) {
        LivingEntity fake = fakes.get(player.getUuid());
        if (fake != null && fake.getType() == type) return fake;

        if (fake != null) fakes.remove(player.getUuid());

        Entity entity = type.create(mc.world, SpawnReason.COMMAND);
        if (!(entity instanceof LivingEntity)) return null;

        fake = (LivingEntity) entity;
        fake.setWorld(mc.world);
        fake.setSilent(true);
        fake.setNoGravity(true);
        fake.noClip = true;
        fakes.put(player.getUuid(), fake);
        return fake;
    }

    private void sync(LivingEntity fake, AbstractClientPlayerEntity player) {
        fake.lastX = fake.getX();
        fake.lastY = fake.getY();
        fake.lastZ = fake.getZ();
        fake.age++;

        fake.setSneaking(player.isSneaking());
        fake.setPose(safePose(player.getPose()));
        fake.setVelocity(player.getVelocity());
        fake.setOnGround(player.isOnGround());
        fake.setInvisible(player.isInvisible());

        fake.headYaw = player.headYaw;
        fake.lastHeadYaw = player.lastHeadYaw;
        fake.bodyYaw = player.bodyYaw;
        fake.lastBodyYaw = player.lastBodyYaw;

        fake.handSwinging = player.handSwinging;
        fake.handSwingTicks = player.handSwingTicks;
        fake.handSwingProgress = player.handSwingProgress;
        fake.lastHandSwingProgress = player.lastHandSwingProgress;

        fake.lastRenderX = player.lastX;
        fake.lastRenderY = player.lastY;
        fake.lastRenderZ = player.lastZ;
        fake.setPos(player.getX(), player.getY(), player.getZ());
        fake.setYaw(player.getYaw());
        fake.setPitch(player.getPitch());

        fake.age = player.age;
        fake.updateLimbs(true);
    }

    private EntityPose safePose(EntityPose pose) {
        if (pose == EntityPose.SLEEPING) return EntityPose.STANDING;
        return pose;
    }

    private void removePlayerStates(WorldRenderState state, Vec3d cameraPos) {
        Iterator<EntityRenderState> it = state.entityRenderStates.iterator();
        while (it.hasNext()) {
            EntityRenderState renderState = it.next();
            if (!(renderState instanceof PlayerEntityRenderState)) continue;

            Entity entity = mc.world.getEntityById(((PlayerEntityRenderState) renderState).id);
            if (!(entity instanceof AbstractClientPlayerEntity)) continue;
            if (!shouldReplace((AbstractClientPlayerEntity) entity)) continue;
            if (entity.squaredDistanceTo(cameraPos) > 65536.0) continue;

            it.remove();
        }
    }

    @Override
    public String getInfoString() {
        if (mode.get() == Mode.AllPlayers) return "all";
        return String.valueOf(names.get().size());
    }
}