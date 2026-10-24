package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.events.KeyboardInputEvent;
import com.example.addon.events.PlayerTickEvent;
import com.example.addon.utils.eps.Easing;
import com.example.addon.utils.eps.FallingPlayer;
import com.example.addon.utils.eps.FindItemResult;
import com.example.addon.utils.eps.InvUtils;
import com.example.addon.utils.eps.MathUtils;
import com.example.addon.utils.eps.PlayerUtils;
import com.example.addon.utils.eps.RaytraceUtils;
import com.example.addon.utils.eps.Rot2f;
import com.example.addon.utils.eps.RotationManager;
import com.example.addon.utils.eps.RotationUtils;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.BushBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FlowerBlock;
import net.minecraft.block.FungusBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SignItem;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class Scaffold extends Module {

    public static final Scaffold INSTANCE = new Scaffold();

    private enum Mode {
        TellyBridge,
        GodBridge
    }

    private enum RotationMode {
        Static,
        Hypixel,
        Heypixel
    }

    private enum RaytraceMode {
        Normal,
        Strict
    }

    private enum SwapMode {
        None,
        Normal,
        Silent,
        InvSwitch
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<Block>> blacklistedBlocks = sgGeneral.add(new BlockListSetting.Builder()
            .name("blacklisted-blocks")
            .description("Blocks that will never be used for scaffolding.")
            .defaultValue(
                    Blocks.AIR,
                    Blocks.WATER,
                    Blocks.LAVA,
                    Blocks.ENCHANTING_TABLE,
                    Blocks.GLASS_PANE,
                    Blocks.IRON_BARS,
                    Blocks.SNOW,
                    Blocks.COAL_ORE,
                    Blocks.DIAMOND_ORE,
                    Blocks.EMERALD_ORE,
                    Blocks.CHEST,
                    Blocks.TRAPPED_CHEST,
                    Blocks.TORCH,
                    Blocks.ANVIL,
                    Blocks.NOTE_BLOCK,
                    Blocks.JUKEBOX,
                    Blocks.TNT,
                    Blocks.GOLD_ORE,
                    Blocks.IRON_ORE,
                    Blocks.LAPIS_ORE,
                    Blocks.STONE_PRESSURE_PLATE,
                    Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE,
                    Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE,
                    Blocks.STONE_BUTTON,
                    Blocks.LEVER,
                    Blocks.TALL_GRASS,
                    Blocks.TRIPWIRE,
                    Blocks.TRIPWIRE_HOOK,
                    Blocks.RAIL,
                    Blocks.CORNFLOWER,
                    Blocks.RED_MUSHROOM,
                    Blocks.BROWN_MUSHROOM,
                    Blocks.VINE,
                    Blocks.SUNFLOWER,
                    Blocks.LADDER,
                    Blocks.FURNACE,
                    Blocks.SAND,
                    Blocks.CACTUS,
                    Blocks.DISPENSER,
                    Blocks.DROPPER,
                    Blocks.CRAFTING_TABLE,
                    Blocks.COBWEB,
                    Blocks.PUMPKIN,
                    Blocks.COBBLESTONE_WALL,
                    Blocks.OAK_FENCE,
                    Blocks.REDSTONE_TORCH,
                    Blocks.FLOWER_POT,
                    Blocks.SCAFFOLDING
            )
            .build()
    );

    private final Setting<Boolean> toggleOnTeleport = sgGeneral.add(new BoolSetting.Builder()
            .name("toggle-on-teleport")
            .description("Disables the module after you teleport with an ender pearl.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("Scaffold mode.")
            .defaultValue(Mode.TellyBridge)
            .build()
    );

    private final Setting<SwapMode> swapMode = sgGeneral.add(new EnumSetting.Builder<SwapMode>()
            .name("swap-mode")
            .description("How to switch to a block slot.")
            .defaultValue(SwapMode.Normal)
            .build()
    );

    private final Setting<Boolean> swapBack = sgGeneral.add(new BoolSetting.Builder()
            .name("swap-back")
            .description("Restores the selected slot when the module is disabled.")
            .defaultValue(true)
            .visible(() -> swapMode.get() == SwapMode.Normal)
            .build()
    );

    private final Setting<Boolean> snap = sgGeneral.add(new BoolSetting.Builder()
            .name("snap")
            .description("Snaps rotations to the closest block face.")
            .defaultValue(false)
            .visible(() -> mode.get() == Mode.GodBridge)
            .build()
    );

    private final Setting<Boolean> degrees45 = sgGeneral.add(new BoolSetting.Builder()
            .name("45-degrees")
            .description("Only uses diagonally placed blocks.")
            .defaultValue(false)
            .build()
    );

    private final Setting<RotationMode> rotationMode = sgGeneral.add(new EnumSetting.Builder<RotationMode>()
            .name("rotation-mode")
            .description("How rotations are sped up while bridging.")
            .defaultValue(RotationMode.Static)
            .build()
    );

    private final Setting<RaytraceMode> raytrace = sgGeneral.add(new EnumSetting.Builder<RaytraceMode>()
            .name("raytrace-mode")
            .description("How strict the placement ray trace check is.")
            .defaultValue(RaytraceMode.Normal)
            .build()
    );

    private final Setting<Integer> rotationSpeed = sgGeneral.add(new IntSetting.Builder()
            .name("rotation-speed")
            .description("Rotation speed.")
            .defaultValue(127)
            .min(10)
            .max(180)
            .sliderRange(10, 180)
            .visible(() -> rotationMode.get() != RotationMode.Hypixel)
            .build()
    );

    private final Setting<Integer> rotationSpeed2 = sgGeneral.add(new IntSetting.Builder()
            .name("rotation-speed-2")
            .description("Rotation speed after the first tick.")
            .defaultValue(36)
            .min(10)
            .max(180)
            .sliderRange(10, 180)
            .visible(() -> rotationMode.get() == RotationMode.Heypixel)
            .build()
    );

    private final Setting<Integer> rotationBackSpeed = sgGeneral.add(new IntSetting.Builder()
            .name("rotation-back-speed")
            .description("Rotation speed used to look back to the input direction.")
            .defaultValue(180)
            .min(10)
            .max(180)
            .sliderRange(10, 180)
            .visible(() -> mode.get() == Mode.TellyBridge)
            .build()
    );

    private final Setting<Integer> tellyTicks = sgGeneral.add(new IntSetting.Builder()
            .name("telly-ticks")
            .description("Ticks to wait before placing while telly bridging.")
            .defaultValue(1)
            .min(0)
            .max(6)
            .sliderRange(0, 6)
            .visible(() -> mode.get() == Mode.TellyBridge)
            .build()
    );

    private final Setting<Boolean> swingHand = sgGeneral.add(new BoolSetting.Builder()
            .name("swing-hand")
            .description("Swings the hand when placing a block.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> render = sgGeneral.add(new BoolSetting.Builder()
            .name("render")
            .description("Renders placed block positions.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> fade = sgGeneral.add(new BoolSetting.Builder()
            .name("fade")
            .description("Fades the rendered boxes out.")
            .defaultValue(true)
            .visible(render::get)
            .build()
    );

    private final Setting<Integer> fadeTime = sgGeneral.add(new IntSetting.Builder()
            .name("fade-time")
            .description("How long the rendered boxes stay visible.")
            .defaultValue(500)
            .min(0)
            .max(3000)
            .sliderRange(0, 3000)
            .visible(() -> render.get() && fade.get())
            .build()
    );

    private final Setting<Boolean> shrink = sgGeneral.add(new BoolSetting.Builder()
            .name("shrink")
            .description("Shrinks the rendered boxes out.")
            .defaultValue(false)
            .visible(render::get)
            .build()
    );

    private final Setting<SettingColor> sideColor = sgGeneral.add(new ColorSetting.Builder()
            .name("side-color")
            .description("Fill color of the rendered boxes.")
            .defaultValue(new SettingColor(255, 183, 197, 100))
            .visible(render::get)
            .build()
    );

    private final Setting<SettingColor> lineColor = sgGeneral.add(new ColorSetting.Builder()
            .name("line-color")
            .description("Outline color of the rendered boxes.")
            .defaultValue(new SettingColor(255, 105, 180))
            .visible(render::get)
            .build()
    );

    private int airTicks;
    private int yLevel;
    private BlockPos blockPos;
    private Direction direction;
    private Rot2f rotation;
    private int rotateCount = 0;
    private float forwardInput, strafeInput;
    private float inputYaw;
    private float rawInputYaw;
    private double lengthSqr = 4.5 * 4.5;

    private FindItemResult blockResult;
    private boolean shouldSwapBack;
    private boolean emergencyPlacementActive;
    private boolean pearlUsePacketSent;
    private long lastWarnTime;

    private final List<RenderInfo> renderBoxes = new ArrayList<>();

    public Scaffold() {
        // 不能叫 "scaffold"：Meteor 自带同名模块会顶掉它，导致其 mixin 取到 null 崩溃
        super(AddonTemplate.SURVIVAL, "scaffold-plus", "Places blocks under your feet while bridging (Epsilon port).");
    }

    @Override
    public void onActivate() {
        airTicks = 0;
        blockPos = null;
        direction = null;
        rotation = null;
        rotateCount = 0;
        blockResult = null;
        shouldSwapBack = false;
        emergencyPlacementActive = false;
        pearlUsePacketSent = false;
        InvUtils.invSlots = null;
    }

    @Override
    public void onDeactivate() {
        yLevel = 0;
        emergencyPlacementActive = false;
        pearlUsePacketSent = false;
        RotationManager.INSTANCE.setActive(false);

        if (shouldSwapBack) {
            InvUtils.swapBack();
            shouldSwapBack = false;
        }
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!event.isCancelled()) emergencyPlacementActive = false;

        blockResult = findBlockResult();
        if (!blockResult.found()) return;

        if (mc.player.isOnGround()) {
            airTicks = 0;
            yLevel = MathHelper.floor(mc.player.getY()) - 1;
        } else {
            airTicks++;
        }

        getBlockInfo();

        boolean reachable = true;
        if (mc.player.getVelocity().y < -0.1 && blockPos != null) {
            FallingPlayer fallingPlayer = new FallingPlayer(mc.player).calculate(2);
            if (blockPos.getY() > fallingPlayer.getY()) {
                reachable = false;
            }
        }

        double strength = mc.player.getVelocity().getHorizontal().length();
        if (strength >= 1.5) {
            warnThrottled("You are moving too fast, scaffold may not work.");
        }

        if ((!reachable || strength >= 1.5) && rotateCount <= 8 && getBlockCount() >= 1 && canUseBlockResult()) {
            emergencyPlacementActive = true;
            event.setCancelled(true);

            rotateCount++;
            if (blockPos == null) return;
            Rot2f rotation = getRotation(blockPos, direction);
            RotationManager.INSTANCE.rotations = rotation;
            RotationManager.INSTANCE.setActive(true);

            sendRotation(rotation);

            swap();

            Hand hand = blockResult.getHand();

            ActionResult result = mc.interactionManager.interactBlock(
                    mc.player,
                    hand,
                    new BlockHitResult(getVec3(blockPos, direction), direction, blockPos, false)
            );
            if (result.isAccepted()) {
                if (swingHand.get()) {
                    PlayerUtils.swingHand(hand);
                }
                if (render.get()) {
                    renderBoxes.add(new RenderInfo(new Box(blockPos.offset(direction)), lineColor.get(), sideColor.get(), System.currentTimeMillis(), fade.get(), shrink.get()));
                }
            }

            swapBack();
            return;
        } else {
            rotateCount = 0;
        }

        switch (mode.get()) {
            case TellyBridge -> handleTelly();
            case GodBridge -> handleNormal();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onMoveInput(KeyboardInputEvent event) {
        forwardInput = event.getForward();
        strafeInput = event.getStrafe();
        float realYaw = RotationManager.INSTANCE.getRealYaw();
        inputYaw = MathHelper.wrapDegrees(Math.round((realYaw + (float) Math.toDegrees(Math.atan2(-strafeInput, forwardInput))) / 45.0F) * 45.0F);
        rawInputYaw = MathHelper.wrapDegrees(Math.round(realYaw + (float) Math.toDegrees(Math.atan2(-strafeInput, forwardInput))));

        if (mode.get() == Mode.TellyBridge && mc.player.isOnGround() && !mc.options.jumpKey.isPressed() && mc.player.isMovingHorizontally()) {
            event.setJump(true);
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(mc.player == null || mc.world == null) && toggleOnTeleport.get() && pearlUsePacketSent && event.packet instanceof PlayerPositionLookS2CPacket) {
            pearlUsePacketSent = false;
            error("Teleport detected, disabling scaffold.");
            disable();
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (event.packet instanceof PlayerInteractItemC2SPacket packet) {
            ItemStack usedStack = mc.player.getStackInHand(packet.getHand());
            if (usedStack.isOf(Items.ENDER_PEARL) || usedStack.isEmpty() && mc.player.getItemCooldownManager().isCoolingDown(Items.ENDER_PEARL.getDefaultStack())) {
                pearlUsePacketSent = true;
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!render.get() || renderBoxes.isEmpty()) return;

        long time = System.currentTimeMillis();
        long fadeTimeMs = fadeTime.get();

        renderBoxes.removeIf(box -> time - box.startTime() > fadeTimeMs);

        for (RenderInfo info : renderBoxes) {
            float progress = fadeTimeMs <= 0 ? 1.0f : MathHelper.clamp((float) (time - info.startTime()) / fadeTimeMs, 0.0f, 1.0f);

            double scale = 1.0;
            if (info.shrink()) {
                scale = 1.0 - Easing.easeInOutExpo(progress);
                if (scale < 0) scale = 0;
            }

            float alphaFactor = info.fade() ? MathHelper.clamp(1.0f - progress, 0.0f, 1.0f) : 1.0f;

            Color side = new Color(info.sideColor().r, info.sideColor().g, info.sideColor().b, (int) (info.sideColor().a * alphaFactor));
            Color line = new Color(info.lineColor().r, info.lineColor().g, info.lineColor().b, (int) (info.lineColor().a * alphaFactor));

            Box renderBox = info.box();
            if (info.shrink()) {
                Vec3d center = renderBox.getCenter();
                renderBox = Box.of(center, renderBox.getLengthX() * scale, renderBox.getLengthY() * scale, renderBox.getLengthZ() * scale);
            }

            event.renderer.box(renderBox, side, line, ShapeMode.Both, 0);
        }
    }

    public int getBlockCount() {
        if (mc.player == null || mc.world == null) return 91;

        int total = 0;

        if (isValidStack(mc.player.getOffHandStack())) {
            total += mc.player.getOffHandStack().getCount();
        }

        int maxSlot = swapMode.get() == SwapMode.InvSwitch ? mc.player.getInventory().size() : 9;
        for (int i = 0; i < maxSlot; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isValidStack(stack)) {
                total += stack.getCount();
            }
        }

        return total;
    }

    public ItemStack getBlockStack() {
        ItemStack offhand = mc.player.getOffHandStack();
        if (isValidStack(offhand)) return offhand;

        if (blockResult != null && blockResult.found()) {
            ItemStack stack = blockResult.isOffhand() ? mc.player.getOffHandStack() : mc.player.getInventory().getStack(blockResult.slot());
            if (isValidStack(stack)) return stack;
        }

        return ItemStack.EMPTY;
    }

    private void handleTelly() {
        if (mc.player.isOnGround() && (strafeInput != 0 || forwardInput != 0)) {
            RotationManager.INSTANCE.setRotations(new Rot2f(rawInputYaw, rotation == null ? mc.player.getPitch() : rotation.getPitch()), rotationBackSpeed.get());
            return;
        }

        rotation = getRotation(blockPos, direction);
        int speed = rotationSpeed.get();

        if (rotationMode.get() == RotationMode.Hypixel) {
            speed = airTicks <= 1 ? 127 : 35;
        } else if (rotationMode.get() == RotationMode.Heypixel) {
            speed = airTicks <= 1 ? rotationSpeed.get() : rotationSpeed2.get();
        }

        RotationManager.INSTANCE.setRotations(rotation, speed);

        if (airTicks > tellyTicks.get()) {
            place();
        }
    }

    private void handleNormal() {
        if (isOverEdge() || !snap.get() || !mc.player.isOnGround()) {
            rotation = getRotation(blockPos, direction);
            RotationManager.INSTANCE.setRotations(rotation, rotationSpeed.get());
        }
        place();
    }

    private boolean isOverEdge() {
        Vec3d predicted = predictMovement();
        Vec3d movement = mc.player.getVelocity().add(predicted.x, 0.0, predicted.z);
        return mc.world.isSpaceEmpty(mc.player, mc.player.getBoundingBox().offset(movement.x, -1.0, movement.z));
    }

    private Vec3d predictMovement() {
        float strafe = getInput(mc.options.leftKey.isPressed(), mc.options.rightKey.isPressed()) * 0.98F;
        float forward = getInput(mc.options.forwardKey.isPressed(), mc.options.backKey.isPressed()) * 0.98F;
        float magnitude = strafe * strafe + forward * forward;
        if (magnitude < 1.0E-4F) return Vec3d.ZERO;

        magnitude = Math.max(MathHelper.sqrt(magnitude), 1.0F);
        float scale = getAllowedHorizontalDistance() / magnitude;
        strafe *= scale;
        forward *= scale;

        float sinYaw = MathHelper.sin(mc.player.getYaw() * ((float) Math.PI / 180.0F));
        float cosYaw = MathHelper.cos(mc.player.getYaw() * ((float) Math.PI / 180.0F));
        return new Vec3d(strafe * cosYaw - forward * sinYaw, 0.0, forward * cosYaw + strafe * sinYaw);
    }

    private float getAllowedHorizontalDistance() {
        BlockPos posBelow = mc.player.getVelocityAffectingPos();
        float friction = mc.world.getBlockState(posBelow).getBlock().getSlipperiness();
        return mc.player.getMovementSpeed() * (0.21600002F / (friction * friction * friction));
    }

    private float getInput(boolean positive, boolean negative) {
        if (positive == negative) return 0.0F;
        return positive ? 1.0F : -1.0F;
    }

    // 同一个角度重复发会被 Grim 判 aimduplicatelook
    private void sendRotation(Rot2f rot) {
        if (RotationManager.INSTANCE.isDuplicate(rot.getYaw(), rot.getPitch())) {
            RotationManager.INSTANCE.markLookPacketSent();
            return;
        }
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                rot.getYaw(),
                rot.getPitch(),
                mc.player.isOnGround(),
                mc.player.horizontalCollision
        ));
        RotationManager.INSTANCE.markSent(rot);
    }

    private void place() {
        if (!onAir() || blockPos == null || direction == null || !canUseBlockResult()) {
            return;
        }

        if (switch (raytrace.get()) {
            case Normal -> !RaytraceUtils.overBlock(RotationManager.INSTANCE.getRotation(), blockPos);
            case Strict -> !RaytraceUtils.overBlock(RotationManager.INSTANCE.getRotation(), blockPos, direction);
        }) {
            return;
        }

        // 原版依赖 RotationManager 的发包时机，这里必须自己补，否则服务端视角还是旧的
        sendRotation(RotationManager.INSTANCE.getRotation());

        swap();

        Hand hand = blockResult.getHand();

        ActionResult result = mc.interactionManager.interactBlock(mc.player, hand, new BlockHitResult(getVec3(blockPos, direction), direction, blockPos, false));

        if (result.isAccepted()) {
            if (swingHand.get()) {
                PlayerUtils.swingHand(hand);
            }

            if (render.get()) {
                renderBoxes.add(new RenderInfo(new Box(blockPos.offset(direction)), lineColor.get(), sideColor.get(), System.currentTimeMillis(), fade.get(), shrink.get()));
            }
        }

        swapBack();
    }

    private int getYLevel() {
        if ((!mc.options.jumpKey.isPressed() && mc.player.isMovingHorizontally() && mode.get() == Mode.TellyBridge)
                && Math.abs(yLevel - (MathHelper.floor(mc.player.getY()) - 1)) <= 1.25) {
            return yLevel;
        }
        return MathHelper.floor(mc.player.getY()) - 1;
    }

    private void getBlockInfo() {
        lengthSqr = 4.5 * 4.5;
        blockPos = null;
        direction = null;

        Vec3d baseVec = mc.player.getEyePos();
        BlockPos base = BlockPos.ofFloored(baseVec.x, getYLevel(), baseVec.z);
        int baseX = base.getX();
        int baseZ = base.getZ();

        if (!onAir()) {
            return;
        }

        if (checkBlock(baseVec, base)) {
            return;
        }

        for (int d = 1; d <= 6; d++) {
            if (checkBlock(baseVec, new BlockPos(baseX, getYLevel() - d, baseZ))) {
                return;
            }

            for (int x = 0; x <= d; x++) {
                for (int z = 0; z <= d - x; z++) {
                    int y = d - x - z;
                    for (int rev1 = 0; rev1 <= 1; rev1++) {
                        for (int rev2 = 0; rev2 <= 1; rev2++) {
                            BlockPos pos = new BlockPos(
                                    baseX + (rev1 == 0 ? x : -x),
                                    getYLevel() - y,
                                    baseZ + (rev2 == 0 ? z : -z)
                            );
                            checkBlock(baseVec, pos);
                        }
                    }
                }
            }
        }
    }

    private boolean checkBlock(Vec3d baseVec, BlockPos pos) {
        if (!onAir() || pos.getY() > getYLevel()) {
            return false;
        }

        Vec3d center = Vec3d.ofBottomCenter(pos);
        for (Direction dir : Direction.values()) {
            Vec3d normal = new Vec3d(dir.getOffsetX(), dir.getOffsetY(), dir.getOffsetZ());
            Vec3d hit = center.add(normal.multiply(0.5));
            BlockPos baseBlockPos = pos.offset(dir);

            BlockState state = mc.world.getBlockState(baseBlockPos);
            if (state.getCollisionShape(mc.world, baseBlockPos).isEmpty() || state.createScreenHandlerFactory(mc.world, baseBlockPos) != null) {
                continue;
            }

            Direction face = dir.getOpposite();
            Vec3d relevant = hit.subtract(baseVec);

            if (relevant.lengthSquared() > lengthSqr || relevant.dotProduct(normal) < 0.0) {
                continue;
            }

            if (face == Direction.UP && mc.player.isMovingHorizontally() && !mc.options.jumpKey.isPressed()) {
                continue;
            }

            lengthSqr = relevant.lengthSquared();
            blockPos = baseBlockPos;
            direction = face;
            return true;
        }

        return false;
    }

    private Rot2f getRotation(BlockPos pos, Direction direction) {
        if (rotation == null) {
            return new Rot2f(MathHelper.wrapDegrees(mc.player.getYaw() - 135.0F), 82.0F);
        }

        if (!onAir() || pos == null || direction == null) {
            return rotation;
        }

        Rot2f calculated = RotationUtils.calculate(pos, direction);
        float[] yawArray = new float[]{
                -135F,
                -90F,
                -45F,
                0F,
                45F,
                90F,
                135F,
                180F
        };
        float baseYaw = MathHelper.wrapDegrees(inputYaw - 180);

        for (int i = 1; i < yawArray.length; i++) {
            float key = yawArray[i];
            int j = i - 1;
            while (j >= 0 && Math.abs(MathHelper.wrapDegrees(baseYaw - yawArray[j])) > Math.abs(MathHelper.wrapDegrees(baseYaw - key))) {
                yawArray[j + 1] = yawArray[j];
                j = j - 1;
            }
            yawArray[j + 1] = key;
        }

        float[] pitchArray = {75.0F, 82.0F, 87.0F};

        float[] finalYawArray = new float[yawArray.length + 1];
        System.arraycopy(yawArray, 0, finalYawArray, 0, yawArray.length);
        finalYawArray[yawArray.length] = calculated.getYaw();

        for (float yaw : finalYawArray) {
            if (degrees45.get() && yaw % 90 == 0) {
                continue;
            }
            for (float pitch : pitchArray) {
                Rot2f candidate = new Rot2f(yaw + MathUtils.getRandom(-0.3f, 0.3f), pitch + MathUtils.getRandom(-0.3f, 0.3f));
                boolean matches = raytrace.get() == RaytraceMode.Normal
                        ? RaytraceUtils.overBlock(candidate, pos)
                        : RaytraceUtils.overBlock(candidate, pos, direction);
                if (matches) {
                    return candidate;
                }
            }

            for (int pitch = -90; pitch < 90; pitch++) {
                Rot2f candidate = new Rot2f(yaw, pitch);
                boolean matches = raytrace.get() == RaytraceMode.Normal
                        ? RaytraceUtils.overBlock(candidate, pos)
                        : RaytraceUtils.overBlock(candidate, pos, direction);
                if (matches) {
                    return candidate;
                }
            }
        }

        return calculated;
    }

    private boolean onAir() {
        Vec3d baseVec = mc.player.getEyePos();
        BlockPos base = BlockPos.ofFloored(baseVec.x, getYLevel(), baseVec.z);
        return mc.world.getBlockState(base).isReplaceable();
    }

    private Vec3d getVec3(BlockPos pos, Direction face) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;

        if (face != Direction.UP && face != Direction.DOWN) {
            y += 0.08;
        } else {
            x += MathUtils.getRandom(-0.3, 0.3);
            z += MathUtils.getRandom(-0.3, 0.3);
        }

        if (face == Direction.WEST || face == Direction.EAST) {
            z += MathUtils.getRandom(-0.3, 0.3);
        }

        if (face == Direction.SOUTH || face == Direction.NORTH) {
            x += MathUtils.getRandom(-0.3, 0.3);
        }

        return new Vec3d(x, y, z);
    }

    private FindItemResult findBlockResult() {
        ItemStack offhandStack = mc.player.getOffHandStack();
        if (isValidStack(offhandStack)) {
            return new FindItemResult(40, offhandStack.getCount(), offhandStack.getMaxCount());
        }
        return swapMode.get() == SwapMode.InvSwitch ? InvUtils.find(this::isValidStack) : InvUtils.findInHotbar(this::isValidStack);
    }

    private boolean canUseBlockResult() {
        if (blockResult.isOffhand()) return isValidStack(mc.player.getOffHandStack());
        return swapMode.get() != SwapMode.None || isValidStack(mc.player.getInventory().getSelectedStack());
    }

    private void swap() {
        if (blockResult.isOffhand()) {
            return;
        }

        switch (swapMode.get()) {
            case Normal -> {
                int selectedSlot = mc.player.getInventory().getSelectedSlot();
                InvUtils.swap(blockResult.slot(), true);
                if (swapBack.get() && blockResult.slot() != selectedSlot) {
                    shouldSwapBack = true;
                }
            }
            case Silent -> InvUtils.swap(blockResult.slot(), true);
            case InvSwitch -> InvUtils.invSwap(blockResult.slot());
        }
    }

    private void swapBack() {
        if (blockResult.isOffhand()) {
            return;
        }

        switch (swapMode.get()) {
            case Silent -> InvUtils.swapBack();
            case InvSwitch -> InvUtils.invSwapBack();
        }
    }

    private boolean isValidStack(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) {
            return false;
        }

        String name = stack.getName().getString();
        if (name.contains("Click") || name.contains("点击")) {
            return false;
        }

        if (stack.getItem() instanceof SignItem) {
            return false;
        }

        Block block = ((BlockItem) stack.getItem()).getBlock();
        if (block instanceof FlowerBlock || block instanceof BushBlock || block instanceof FungusBlock || block instanceof CropBlock) {
            return false;
        }

        return !(block instanceof SlabBlock) && !blacklistedBlocks.get().contains(block);
    }

    public boolean isEmergencyPlacementActive() {
        return isActive() && emergencyPlacementActive;
    }

    private void warnThrottled(String message) {
        long now = System.currentTimeMillis();
        if (now - lastWarnTime < 1500L) return;
        lastWarnTime = now;
        warning(message);
    }

    private record RenderInfo(
            Box box, Color lineColor, Color sideColor, long startTime, boolean fade, boolean shrink
    ) {
    }
}
