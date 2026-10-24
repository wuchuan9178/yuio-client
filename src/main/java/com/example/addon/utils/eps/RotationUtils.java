package com.example.addon.utils.eps;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

public class RotationUtils {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static Rot2f calculate(Vec3d from, Vec3d to) {
        Vec3d diff = to.subtract(from);
        double distance = Math.hypot(diff.x, diff.z);
        float yaw = (float) Math.toDegrees(MathHelper.atan2(diff.z, diff.x)) - 90.0F;
        float pitch = (float) -Math.toDegrees(MathHelper.atan2(diff.y, distance));
        return new Rot2f(yaw, pitch);
    }

    public static Rot2f getRotationsToEntity(Entity entity) {
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d targetPos = new Vec3d(entity.getX(), entity.getY() + entity.getHeight() / 2.0, entity.getZ());
        double dx = targetPos.x - eyePos.x;
        double dy = targetPos.y - eyePos.y;
        double dz = targetPos.z - eyePos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(-Math.atan2(dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, dist));
        return new Rot2f(yaw, MathHelper.clamp(pitch, -90, 90));
    }

    public static double getEyeDistanceToEntity(LivingEntity entity) {
        Vec3d eyePos = mc.player.getEyePos();
        Box box = entity.getBoundingBox();
        double dx = Math.max(box.minX - eyePos.x, Math.max(0, eyePos.x - box.maxX));
        double dy = Math.max(box.minY - eyePos.y, Math.max(0, eyePos.y - box.maxY));
        double dz = Math.max(box.minZ - eyePos.z, Math.max(0, eyePos.z - box.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static Rot2f calculate(BlockPos to) {
        return calculate(mc.player.getEyePos(), Vec3d.ofCenter(to));
    }

    public static Rot2f calculate(Vec3d to) {
        return calculate(mc.player.getEyePos(), to);
    }

    public static Rot2f calculate(Vec3d position, Direction direction) {
        double x = position.x + 0.5;
        double y = position.y + 0.5;
        double z = position.z + 0.5;
        x += (double) direction.getOffsetX() * 0.5;
        y += (double) direction.getOffsetY() * 0.5;
        z += (double) direction.getOffsetZ() * 0.5;
        return calculate(new Vec3d(x, y, z));
    }

    public static Rot2f calculate(BlockPos position, Direction direction) {
        double x = position.getX() + 0.5;
        double y = position.getY() + 0.5;
        double z = position.getZ() + 0.5;
        x += (double) direction.getOffsetX() * 0.5;
        y += (double) direction.getOffsetY() * 0.5;
        z += (double) direction.getOffsetZ() * 0.5;
        return calculate(new Vec3d(x, y, z));
    }

    public static Rot2f calculate(Vec3d vec, boolean predict) {
        Vec3d eyesPos = mc.player.getEyePos();
        if (predict) eyesPos = eyesPos.add(mc.player.getVelocity());

        double diffX = vec.x - eyesPos.x;
        double diffY = vec.y - eyesPos.y;
        double diffZ = vec.z - eyesPos.z;

        float yaw = MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90f);
        float pitch = MathHelper.wrapDegrees(-(float) Math.toDegrees(Math.atan2(diffY, Math.sqrt(diffX * diffX + diffZ * diffZ))));

        return new Rot2f(yaw, pitch);
    }

    public static Direction getDirection(BlockPos pos) {
        Vec3d eyePos = mc.player.getEyePos();
        Direction best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Direction direction : Direction.values()) {
            VoxelShape shape = mc.world.getBlockState(pos).getCollisionShape(mc.world, pos);
            if (shape.isEmpty()) continue;
            double distance = eyePos.squaredDistanceTo(Vec3d.ofCenter(pos.offset(direction)));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = direction;
            }
        }

        return best == null ? Direction.UP : best;
    }

    public static Rot2f applySensitivityPatch(Rot2f rotation) {
        Rot2f previousRotation = new Rot2f(RotationManager.INSTANCE.getLastRotation().getYaw(), RotationManager.INSTANCE.getLastRotation().getPitch());
        return applySensitivityPatch(rotation, previousRotation);
    }

    public static Rot2f applySensitivityPatch(Rot2f rotation, Rot2f previousRotation) {
        float mouseSensitivity = (float) (mc.options.getMouseSensitivity().getValue() * (1 + Math.random() / 10000000) * 0.6F + 0.2F);
        double multiplier = mouseSensitivity * mouseSensitivity * mouseSensitivity * 8.0F * 0.15D;
        float yaw = previousRotation.getYaw() + (float) (Math.round((rotation.getYaw() - previousRotation.getYaw()) / multiplier) * multiplier);
        float pitch = previousRotation.getPitch() + (float) (Math.round((rotation.getPitch() - previousRotation.getPitch()) / multiplier) * multiplier);
        return new Rot2f(yaw, MathHelper.clamp(pitch, -90, 90));
    }

    public static Rot2f move(Rot2f targetRotation, double speed) {
        return move(RotationManager.INSTANCE.lastRotations, targetRotation, speed);
    }

    public static Rot2f move(Rot2f lastRotation, Rot2f targetRotation, double speed) {
        if (speed != 0) {
            double deltaYaw = MathHelper.wrapDegrees(targetRotation.getYaw() - lastRotation.getYaw());
            double deltaPitch = (targetRotation.getPitch() - lastRotation.getPitch());

            double distance = Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
            if (distance < 1.0E-4) return new Rot2f(0, 0);

            double distributionYaw = Math.abs(deltaYaw / distance);
            double distributionPitch = Math.abs(deltaPitch / distance);

            double maxYaw = speed * distributionYaw;
            double maxPitch = speed * distributionPitch;

            float moveYaw = (float) MathHelper.clamp(deltaYaw, -maxYaw, maxYaw);
            float movePitch = (float) MathHelper.clamp(deltaPitch, -maxPitch, maxPitch);

            return new Rot2f(moveYaw, movePitch);
        }

        return new Rot2f(0, 0);
    }

    public static Rot2f smooth(Rot2f targetRotation, double speed) {
        return smooth(RotationManager.INSTANCE.lastRotations, targetRotation, speed);
    }

    public static Rot2f smooth(Rot2f lastRotation, Rot2f targetRotation, double speed) {
        float yaw = targetRotation.getYaw();
        float pitch = targetRotation.getPitch();
        float lastYaw = lastRotation.getYaw();
        float lastPitch = lastRotation.getPitch();

        if (speed != 0) {
            Rot2f move = move(lastRotation, targetRotation, speed);

            yaw = lastYaw + move.getYaw();
            pitch = lastPitch + move.getPitch();

            for (int i = 1; i <= (int) (mc.getCurrentFps() / 20f + Math.random() * 10); ++i) {
                if (Math.abs(move.getYaw()) + Math.abs(move.getPitch()) > 0.0001) {
                    yaw += (float) ((Math.random() - 0.5) / 1000);
                    pitch -= (float) (Math.random() / 200);
                }

                Rot2f fixedRotations = applySensitivityPatch(new Rot2f(yaw, pitch));

                yaw = fixedRotations.getYaw();
                pitch = fixedRotations.getPitch();
            }
        }

        return new Rot2f(yaw, pitch);
    }
}
