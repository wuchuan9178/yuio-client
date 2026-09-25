package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

public class AutoDick extends Module {
    private final SettingGroup sgGeneral;
    private final SettingGroup sgRender;

    private final Setting<List<Block>> blocks;
    private final Setting<Integer> placeDelay;
    private final Setting<Integer> blocksPerTick;
    private final Setting<Boolean> autoDisable;
    private final Setting<Boolean> rotate;
    private final Setting<Boolean> swingHand;
    private final Setting<Boolean> swapBack;
    private final Setting<Boolean> placeAtFeet;
    private final Setting<Boolean> followPlayer;
    private final Setting<Boolean> stopOnMove;
    private final Setting<Integer> buildDistance;
    private final Setting<Boolean> checkEntities;
    private final Setting<Boolean> render;
    private final Setting<ShapeMode> shapeMode;
    private final Setting<SettingColor> sideColor;
    private final Setting<SettingColor> lineColor;

    private int delayTimer;
    private boolean hasPlaced;
    private int consecutiveFailures;
    private final List<BlockPos> targetPositions = new ArrayList<>();
    private int cachedSlot = -1;
    private List<Block> cachedBlocks;
    private BlockPos startPosition;
    private Direction startFacing;
    private boolean hasMoved;
    private Direction lastFacing;

    public AutoDick() {
        super(AddonTemplate.OTHER, "AutoDick", "Automatically build stylish buildings");

        sgGeneral = settings.getDefaultGroup();
        sgRender = settings.createGroup("Render");

        blocks = sgGeneral.add(new BlockListSetting.Builder()
            .name("blocks")
            .description("Blocks to place")
            .defaultValue(List.of(Blocks.STONE))
            .build());

        placeDelay = sgGeneral.add(new IntSetting.Builder()
            .name("place-delay")
            .description("Delay between placement batches (ticks)")
            .defaultValue(5)
            .sliderRange(0, 20)
            .build());

        blocksPerTick = sgGeneral.add(new IntSetting.Builder()
            .name("blocks-per-tick")
            .description("Max blocks placed per tick (0 = unlimited)")
            .defaultValue(1)
            .sliderRange(0, 10)
            .build());

        autoDisable = sgGeneral.add(new BoolSetting.Builder()
            .name("auto-disable")
            .description("Auto disable after placing all blocks")
            .defaultValue(true)
            .build());

        rotate = sgGeneral.add(new BoolSetting.Builder()
            .name("rotate")
            .description("Rotate head towards block when placing")
            .defaultValue(true)
            .build());

        swingHand = sgGeneral.add(new BoolSetting.Builder()
            .name("swing-hand")
            .description("Swing hand when placing")
            .defaultValue(true)
            .build());

        swapBack = sgGeneral.add(new BoolSetting.Builder()
            .name("swap-back")
            .description("Swap back to previous slot after placing")
            .defaultValue(false)
            .build());

        placeAtFeet = sgGeneral.add(new BoolSetting.Builder()
            .name("place-at-feet")
            .description("Use feet as base position (waist if disabled)")
            .defaultValue(true)
            .build());

        followPlayer = sgGeneral.add(new BoolSetting.Builder()
            .name("follow-player")
            .description("Structure follows player movement and rotation")
            .defaultValue(false)
            .build());

        stopOnMove = sgGeneral.add(new BoolSetting.Builder()
            .name("stop-on-move")
            .description("Stop building when player moves (only works when follow-player is disabled)")
            .defaultValue(false)
            .build());

        buildDistance = sgGeneral.add(new IntSetting.Builder()
            .name("build-distance")
            .description("How far from the player to build the structure (blocks)")
            .defaultValue(2)
            .sliderRange(1, 6)
            .build());

        checkEntities = sgGeneral.add(new BoolSetting.Builder()
            .name("check-entities")
            .description("Check if target position intersects with any entity before placing")
            .defaultValue(false)
            .build());

        render = sgRender.add(new BoolSetting.Builder()
            .name("render")
            .description("Render placement positions")
            .defaultValue(true)
            .build());

        shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("Shape render mode")
            .defaultValue(ShapeMode.Both)
            .build());

        sideColor = sgRender.add(new ColorSetting.Builder()
            .name("side-color")
            .description("Side color")
            .defaultValue(new SettingColor(255, 0, 0, 50))
            .build());

        lineColor = sgRender.add(new ColorSetting.Builder()
            .name("line-color")
            .description("Line color")
            .defaultValue(new SettingColor(255, 0, 0, 255))
            .build());
    }

    @Override
    public void onActivate() {
        delayTimer = 0;
        hasPlaced = false;
        consecutiveFailures = 0;
        hasMoved = false;
        targetPositions.clear();
        cachedSlot = -1;
        cachedBlocks = null;

        if (mc.player == null || mc.player.isDead()) {
            toggle();
            return;
        }
        if (blocks.get().isEmpty()) {
            error("No blocks selected!");
            toggle();
            return;
        }

        startPosition = mc.player.getBlockPos();
        lastFacing = startFacing = mc.player.getHorizontalFacing();
        recalcTargetPositions();
    }

    @Override
    public void onDeactivate() {
        targetPositions.clear();
        cachedSlot = -1;
        cachedBlocks = null;
        startPosition = null;
        startFacing = null;
        hasMoved = false;
        lastFacing = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (hasPlaced) return;
        if (mc.player == null || mc.world == null) return;

        if (!followPlayer.get() && stopOnMove.get() && startPosition != null && !hasMoved) {
            BlockPos currentPos = mc.player.getBlockPos();
            Direction currentFacing = mc.player.getHorizontalFacing();
            if (!currentPos.equals(startPosition) || currentFacing != startFacing) {
                hasMoved = true;
                info("Player moved, stopping build...");
                toggle();
                return;
            }
        }

        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        if (followPlayer.get()) {
            lastFacing = mc.player.getHorizontalFacing();
        }
        recalcTargetPositions();

        List<Block> selectedBlocks = blocks.get();
        if (selectedBlocks.isEmpty()) {
            error("No blocks selected!");
            toggle();
            return;
        }

        int blockSlot = findBlockSlotInHotbar(selectedBlocks);
        if (blockSlot == -1) {
            error("No selected block in hotbar!");
            toggle();
            return;
        }

        int maxPerTick = blocksPerTick.get();
        int placedThisTick = 0;

        for (BlockPos pos : targetPositions) {
            if (!mc.world.getBlockState(pos).isAir()) continue;
            if (maxPerTick > 0 && placedThisTick >= maxPerTick) break;

            boolean success = BlockUtils.place(pos, Hand.MAIN_HAND, blockSlot, rotate.get(), 0, swingHand.get(), checkEntities.get(), swapBack.get());
            if (!success) continue;

            placedThisTick++;
            blockSlot = findBlockSlotInHotbar(selectedBlocks);
            if (blockSlot == -1) {
                error("Out of blocks!");
                toggle();
                return;
            }
        }

        if (placedThisTick > 0) {
            delayTimer = placeDelay.get();
            consecutiveFailures = 0;
        } else {
            consecutiveFailures++;
            if (consecutiveFailures > 100) {
                error("Unable to place blocks, disabling");
                toggle();
            }
        }

        boolean allPlaced = true;
        for (BlockPos pos : targetPositions) {
            if (mc.world.getBlockState(pos).isAir()) {
                allPlaced = false;
                break;
            }
        }
        if (allPlaced) {
            hasPlaced = true;
            if (autoDisable.get()) {
                info("Structure completed, disabling...");
                toggle();
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!render.get() || targetPositions.isEmpty() || mc.world == null) return;

        for (BlockPos pos : targetPositions) {
            if (!mc.world.getBlockState(pos).isAir()) continue;
            Box box = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
            event.renderer.box(box, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }

    private void recalcTargetPositions() {
        if (mc.player == null) return;
        BlockPos base = placeAtFeet.get() ? mc.player.getBlockPos() : mc.player.getBlockPos().up();
        targetPositions.clear();
        Direction facing = followPlayer.get() ? mc.player.getHorizontalFacing() : lastFacing;
        int distance = buildDistance.get();
        for (BlockPos offset : getBaseOffsets(distance)) {
            targetPositions.add(base.add(rotateOffset(offset, facing)));
        }
    }

    private int findBlockSlotInHotbar(List<Block> blocks) {
        if (cachedSlot != -1 && cachedBlocks != null && cachedBlocks.equals(blocks) &&
            mc.player.getInventory().getStack(cachedSlot).getItem() instanceof BlockItem bi &&
            blocks.contains(bi.getBlock())) {
            return cachedSlot;
        }
        for (int i = 0; i < 9; i++) {
            Item item = mc.player.getInventory().getStack(i).getItem();
            if (item instanceof BlockItem bi && blocks.contains(bi.getBlock())) {
                cachedSlot = i;
                cachedBlocks = new ArrayList<>(blocks);
                return i;
            }
        }
        cachedSlot = -1;
        cachedBlocks = null;
        return -1;
    }

    private List<BlockPos> getBaseOffsets(int distance) {
        List<BlockPos> offsets = new ArrayList<>();
        offsets.add(new BlockPos(-1, 0, -distance));
        offsets.add(new BlockPos(1, 0, -distance));
        offsets.add(new BlockPos(0, 1, -distance));
        offsets.add(new BlockPos(0, 2, -distance));
        return offsets;
    }

    private BlockPos rotateOffset(BlockPos pos, Direction facing) {
        int x = pos.getX();
        int z = pos.getZ();
        return switch (facing) {
            case NORTH -> new BlockPos(x, pos.getY(), z);
            case SOUTH -> new BlockPos(-x, pos.getY(), -z);
            case WEST  -> new BlockPos(z, pos.getY(), -x);
            case EAST  -> new BlockPos(-z, pos.getY(), x);
            default -> pos;
        };
    }
}
