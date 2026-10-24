package com.example.addon.utils.eps;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class RaytraceUtils {

    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final double REACH = 4.5;

    public static boolean overBlock(Rot2f rot, Direction direction, BlockPos pos, boolean strict) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = Vec3d.fromPolar(rot.getPitch(), rot.getYaw());
        Vec3d end = eye.add(look.multiply(REACH));

        BlockHitResult result = mc.world.raycast(new RaycastContext(
                eye,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));

        if (result.getType() == HitResult.Type.MISS) return false;

        return result.getBlockPos().equals(pos) && (!strict || result.getSide() == direction);
    }

    public static boolean overBlock(Rot2f rot, BlockPos pos, boolean strict) {
        return overBlock(rot, Direction.UP, pos, strict);
    }

    public static boolean overBlock(Rot2f rot, BlockPos pos) {
        return overBlock(rot, Direction.UP, pos, false);
    }

    public static boolean overBlock(Rot2f rot, BlockPos pos, Direction direction) {
        return overBlock(rot, direction, pos, true);
    }
}
