package com.example.addon.mixin;

import com.example.addon.modules.Animations;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {

    @Unique
    private static final ThreadLocal<Boolean> yuio$active = ThreadLocal.withInitial(() -> false);

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void yuio$preTransform(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        Animations anim = Animations.get();
        if (anim == null || !anim.shouldAnimate()) return;
        if (item.isOf(Items.CROSSBOW)) return;
        if (player.isUsingItem()) return;
        if (player.isUsingRiptide()) return;
        if (player.isUsingSpyglass()) return;

        boolean mainHand = hand == Hand.MAIN_HAND;
        Arm arm = mainHand ? player.getMainArm() : player.getMainArm().getOpposite();

        yuio$active.set(true);
        matrices.push();
        applyMode(matrices, arm, swingProgress, equipProgress, anim.mode.get(), anim.flip);
    }

    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
    private void yuio$postTransform(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        if (!yuio$active.get()) return;
        yuio$active.set(false);
        matrices.pop();
    }

    //还原原版逻辑
    @Redirect(
        method = "renderFirstPersonItem",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applyEquipOffset(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/Arm;F)V")
    )
    private void yuio$skipEquip(HeldItemRenderer instance, MatrixStack matrices, Arm arm, float equipProgress) {
        if (yuio$active.get()) return;
        int i = arm == Arm.RIGHT ? 1 : -1;
        matrices.translate((float) i * 0.56F, -0.52F + equipProgress * -0.6F, -0.72F);
    }

    // 替换原版的 swingOffset，只在动画激活时跳过
    @Redirect(
        method = "swingArm",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applySwingOffset(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/Arm;F)V")
    )
    private void yuio$skipSwing(HeldItemRenderer instance, MatrixStack matrices, Arm arm, float swingProgress) {
        if (yuio$active.get()) return;
        int i = arm == Arm.RIGHT ? 1 : -1;
        float f = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * (45.0F + f * -20.0F)));
        float g = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) i * g * -20.0F));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(g * -80.0F));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * -45.0F));
    }

    private void applyMode(MatrixStack m, Arm arm, float swing, float equip, Animations.Mode mode, boolean flip) {
        int i = arm == Arm.RIGHT ? 1 : -1;

        switch (mode) {
            case Default -> {
                applyEquip(m, arm, equip);
                applySwing(m, arm, swing);
            }
            case One -> {
                float n = -0.4F * MathHelper.sin(MathHelper.sqrt(swing) * (float) Math.PI);
                applyEquip(m, arm, n);
                float f1 = MathHelper.sin(swing * swing * (float) Math.PI);
                m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * (45.0F + f1 * -20.0F)));
                float g = MathHelper.sin(MathHelper.sqrt(swing) * (float) Math.PI);
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) i * g * -20.0F));
                m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * -45.0F));
            }
            case Two -> {
                applyEquip(m, arm, equip);
                m.translate(0, 0.2F * MathHelper.sin(MathHelper.sqrt(swing) * 6.2831855F), 0);
            }
            case Three -> {
                float n = -0.4F * MathHelper.sin(MathHelper.sqrt(swing) * (float) Math.PI);
                float g = MathHelper.sin(MathHelper.sqrt(swing) * (float) Math.PI);
                applyEquip(m, arm, n);
                m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * (45.0F + swing * -20.0F)));
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) i * g * -70.0F));
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-70.0F));
                m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * -45.0F));
            }
            case Four -> {
                applyEquip(m, arm, equip);
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(swing > 0 ? -MathHelper.sin(swing * 13F) * 37F : 0));
            }
            case Five -> {
                applyEquip(m, arm, equip);
                float g = MathHelper.sin(MathHelper.sqrt(swing) * (float) Math.PI);
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) i * g * -20.0F));
            }
            case Six -> {
                applyEquip(m, arm, equip);
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(swing * (flip ? 360.0F : -360.0F)));
            }
            case Seven -> {
                applyEquip(m, arm, equip);
                float a = -MathHelper.sin(swing * 3F) / 2F + 1F;
                m.scale(a, a, a);
            }
            case Eight -> {
                applyEquip(m, arm, equip);
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(swing * -360F));
            }
            case Nine -> {
                float g = MathHelper.sin(MathHelper.sqrt(swing) * (float) Math.PI);
                applyEquip(m, arm, equip);
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(50F));
                m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-30F * (1F - g) - 30F));
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(110F));
            }
            case Ten -> {
                float g = MathHelper.sin(MathHelper.sqrt(swing) * (float) Math.PI);
                applyEquip(m, arm, equip);
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(50F));
                m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-60F * g - 50F));
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(110F));
            }
            case Eleven -> {
                float g = MathHelper.sin(MathHelper.sqrt(swing) * (float) Math.PI);
                applyEquip(m, arm, equip);
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(50F));
                m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-60F));
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(110F + 20F * g));
            }
            case Twelve -> {
                float g = MathHelper.sin(MathHelper.sqrt(swing) * (float) Math.PI);
                applyEquip(m, arm, equip);
                m.translate(0, 0, -g / 4F);
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-120F));
            }
            case Thirteen -> {
                float g = MathHelper.sin(MathHelper.sqrt(swing) * (float) Math.PI);
                applyEquip(m, arm, equip);
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-MathHelper.sin(swing * 3F) * 60F));
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-60F * g));
            }
            case Fourteen -> {
                applyEquip(m, arm, equip);
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(swing * (flip ? 720F : -720F)));
            }
        }
    }

    private void applyEquip(MatrixStack m, Arm arm, float equip) {
        int i = arm == Arm.RIGHT ? 1 : -1;
        m.translate((float) i * 0.56F, -0.52F + equip * -0.6F, -0.72F);
    }

    private void applySwing(MatrixStack m, Arm arm, float swing) {
        int i = arm == Arm.RIGHT ? 1 : -1;
        float f = MathHelper.sin(swing * swing * (float) Math.PI);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * (45.0F + f * -20.0F)));
        float g = MathHelper.sin(MathHelper.sqrt(swing) * (float) Math.PI);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) i * g * -20.0F));
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(g * -80.0F));
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * -45.0F));
    }
}
