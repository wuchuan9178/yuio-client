package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import dev.sherii.yuio.AModule;
import dev.sherii.yuio.manager.PredictManager;
import dev.sherii.yuio.modules.settings.TpSetting;
import dev.sherii.yuio.settings.DoubleListSetting;
import dev.sherii.yuio.utils.APredictUtils;
import dev.sherii.yuio.utils.FindUtils;
import dev.sherii.yuio.utils.InteractUtils;
import dev.sherii.yuio.utils.PacketUtils;
import dev.sherii.yuio.utils.PredictUtil;
import dev.sherii.yuio.utils.PredictUtils;
import dev.sherii.yuio.utils.SwapUtils;
import dev.sherii.yuio.utils.TpUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Position;
import net.minecraft.util.math.Vec3d;

public final class MaceMissAura extends AModule {
   public static final MaceMissAura INSTANCE = new MaceMissAura();
   private static final SettingGroup sgGeneral = INSTANCE.settings.getDefaultGroup();
   private static final SettingGroup sgDev = INSTANCE.settings.createGroup("dev");
   private static final SettingGroup sgBlacklist = INSTANCE.settings.createGroup("Blacklist");
   private static final SettingGroup sgHelper = INSTANCE.settings.createGroup("InvSwap");
   private static final SettingGroup sgAssist = INSTANCE.settings.createGroup("Assist");
   private static final SettingGroup sgVClipOffset = INSTANCE.settings.createGroup("VClip Offset");
   private static final SettingGroup sgMace = INSTANCE.settings.createGroup("Mace");
   private static final SettingGroup sgPrediction = INSTANCE.settings.createGroup("Predict");
   private static final SettingGroup sgSpeed = INSTANCE.settings.createGroup("Attack Speed");
   private static final SettingGroup sgTpTarget = INSTANCE.settings.createGroup("TpTarget");
   private static final SettingGroup sgTPBot = INSTANCE.settings.createGroup("TPBot");
   private static final SettingGroup sgRender = INSTANCE.settings.createGroup("Render");
   private static final EntityTypeListSetting entities;
   private static final BoolSetting ignoreFriends = (BoolSetting)sgGeneral.add(
      (Setting)((Builder)((Builder)((Builder)new Builder().name("ignore-friends")).description("Ignore friends when selecting target.")).defaultValue(true))
         .build()
   );
   private static final BoolSetting rotation = (BoolSetting)sgGeneral.add(
      (Setting)((Builder)((Builder)((Builder)new Builder().name("rotation")).description("Attacking rotation your head to target.")).defaultValue(false))
         .build()
   );
   private static final BoolSetting swing = (BoolSetting)sgGeneral.add(
      (Setting)((Builder)((Builder)((Builder)new Builder().name("swing")).description("Attacking swing your hand.")).defaultValue(true)).build()
   );
   private static final DoubleSetting range = (DoubleSetting)sgGeneral.add(
      (Setting)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
               .name("range"))
            .description("Range is 0.0, then infinity range."))
         .min(0.0)
         .sliderRange(0.0, 256.0)
         .defaultValue(200.0)
         .build()
   );
   private static final EnumSetting<MaceMissAura.TriggerType> triggerType = (EnumSetting<MaceMissAura.TriggerType>)sgGeneral.add(
      (Setting)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                  .name("trigger-type"))
               .description("When to execute the main logic."))
            .defaultValue(MaceMissAura.TriggerType.PostTick))
         .build()
   );
   private static final BoolSetting render2DUnlimited = (BoolSetting)sgGeneral.add(
      (Setting)((Builder)((Builder)((Builder)new Builder().name("Fast Render2D")).defaultValue(false)).visible(MaceMissAura::render2DUnlimited$lambda$0)).build()
   );
   private static final BoolSetting debugLog = (BoolSetting)sgDev.add((Setting)((Builder)((Builder)new Builder().name("debug")).defaultValue(false)).build());
   private static final BoolSetting blacklistMode = (BoolSetting)sgBlacklist.add(
      (Setting)((Builder)((Builder)((Builder)new Builder().name("blacklist-mode")).description("Only attack players in the blacklist.")).defaultValue(false))
         .build()
   );
   private static final StringListSetting blacklist;
   private static final BoolSetting tpTarget = (BoolSetting)sgTpTarget.add(
      (Setting)((Builder)((Builder)new Builder().name("teleport-to-target")).defaultValue(false)).build()
   );
   private static final IntSetting moveDelay;
   private static final BoolSetting autoSwap = (BoolSetting)sgHelper.add(
      (Setting)((Builder)((Builder)((Builder)new Builder().name("inv-swap")).description("Auto swap item to attack target.")).defaultValue(true)).build()
   );
   private static final ItemSetting swapItem = (ItemSetting)sgHelper.add(
      (Setting)((meteordevelopment.meteorclient.settings.ItemSetting.Builder)((meteordevelopment.meteorclient.settings.ItemSetting.Builder)((meteordevelopment.meteorclient.settings.ItemSetting.Builder)new meteordevelopment.meteorclient.settings.ItemSetting.Builder()
                  .name("items"))
               .description("Auto swap it item to attack target."))
            .defaultValue(Items.MACE))
         .build()
   );
   private static final BoolSetting usePredict = (BoolSetting)sgHelper.add(
      (Setting)((Builder)((Builder)new Builder().name("predict")).defaultValue(false)).build()
   );
   private static final DoubleSetting safeRange = (DoubleSetting)sgHelper.add(
      (Setting)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
               .name("find-range"))
            .min(0.0)
            .sliderRange(0.0, 5.0)
            .defaultValue(3.0)
            .visible(MaceMissAura::safeRange$lambda$0))
         .build()
   );
   private static final BoolSetting alwaysUseSafeRange = (BoolSetting)sgHelper.add(
      (Setting)((Builder)((Builder)((Builder)((Builder)new Builder().name("always")).description("Always use safe range distance.")).defaultValue(false))
            .visible(MaceMissAura::alwaysUseSafeRange$lambda$0))
         .build()
   );
   private static final BoolSetting avoidWater = (BoolSetting)sgHelper.add(
      (Setting)((Builder)((Builder)new Builder().name("avoid-water")).defaultValue(false)).build()
   );
   private static final BoolSetting conditionWaterCheck = (BoolSetting)sgHelper.add(
      (Setting)((Builder)((Builder)new Builder().name("smart-check")).defaultValue(false)).build()
   );
   private static final DoubleSetting waterSearchRange = (DoubleSetting)sgHelper.add(
      (Setting)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
               .name("search-range"))
            .defaultValue(6.0)
            .min(0.0)
            .max(10.0)
            .sliderRange(0.0, 10.0)
            .visible(MaceMissAura::waterSearchRange$lambda$0))
         .build()
   );
   private static final BoolSetting onlySurfacePlayers = (BoolSetting)sgHelper.add(
      (Setting)((Builder)((Builder)new Builder().name("only-surface-players")).defaultValue(false)).build()
   );
   private static final EnumSetting<MaceMissAura.AttackTpMode> distanceMode = (EnumSetting<MaceMissAura.AttackTpMode>)sgAssist.add(
      (Setting)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
               .name("distance-mode"))
            .defaultValue(MaceMissAura.AttackTpMode.Normal))
         .build()
   );
   private static final DoubleSetting attackTpMoveDistance = (DoubleSetting)sgAssist.add(
      (Setting)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
               .name("move-distance"))
            .defaultValue(10.0)
            .min(0.0)
            .max(256.0)
            .sliderRange(1.0, 128.0)
            .visible(MaceMissAura::attackTpMoveDistance$lambda$0))
         .build()
   );
   private static final EnumSetting<MaceMissAura.VClipOrigin> vclipOrigin = (EnumSetting<MaceMissAura.VClipOrigin>)sgAssist.add(
      (Setting)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                  .name("vclip-origin"))
               .defaultValue(MaceMissAura.VClipOrigin.Target))
            .visible(MaceMissAura::vclipOrigin$lambda$0))
         .build()
   );
   private static final BoolSetting useMace = (BoolSetting)sgMace.add(
      (Setting)((Builder)((Builder)((Builder)new Builder().name("use-mace")).description("Use mace")).defaultValue(true)).build()
   );
   private static final BoolSetting useFastTp = (BoolSetting)sgMace.add(
      (Setting)((Builder)((Builder)((Builder)new Builder().name("fast-tp")).defaultValue(false)).visible(MaceMissAura::useFastTp$lambda$0)).build()
   );
   private static final DoubleListSetting missVClip;
   private static final EnumSetting<MaceMissAura.Breach> armorBreakMode = (EnumSetting<MaceMissAura.Breach>)sgMace.add(
      (Setting)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("breach-mode"))
                  .description("How to handle armored targets."))
               .defaultValue(MaceMissAura.Breach.None))
            .visible(MaceMissAura::armorBreakMode$lambda$0))
         .build()
   );
   private static final DoubleListSetting breachVclip;
   private static final BoolSetting tpsSync = (BoolSetting)sgMace.add(
      (Setting)((Builder)((Builder)((Builder)new Builder().name("tps-sync")).description("Synchronize attack delay with server TPS.")).defaultValue(true))
         .build()
   );
   private static final BoolSetting enableSafeHeight = (BoolSetting)sgMace.add(
      (Setting)((Builder)((Builder)((Builder)((Builder)new Builder().name("use-safe-height")).description("Use safe height adjustment for VClip positions."))
               .defaultValue(true))
            .visible(MaceMissAura::enableSafeHeight$lambda$0))
         .build()
   );
   private static final DoubleSetting safeHeight = (DoubleSetting)sgMace.add(
      (Setting)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("safe-height"))
               .description("Maximum vertical search range for safe Mace placement."))
            .min(0.0)
            .sliderRange(0.0, 256.0)
            .defaultValue(60.0)
            .visible(MaceMissAura::safeHeight$lambda$0))
         .build()
   );
   private static final IntSetting safeClipCount = (IntSetting)sgMace.add(
      (Setting)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("safe-vclip-count"))
                  .description("Number of safe VClip positions to try per clip height."))
               .defaultValue(3))
            .min(1)
            .max(8)
            .sliderRange(1, 8)
            .visible(MaceMissAura::safeClipCount$lambda$0))
         .build()
   );
   private static final BoolSetting useSmartVClip = (BoolSetting)sgMace.add(
      (Setting)((Builder)((Builder)((Builder)((Builder)new Builder().name("smart"))
                  .description("Use smart VClip search (prioritize higher positions, full collision check)."))
               .defaultValue(false))
            .visible(MaceMissAura::useSmartVClip$lambda$0))
         .build()
   );
   private static final BoolSetting useArmorCountThreshold = (BoolSetting)sgMace.add(
      (Setting)((Builder)((Builder)((Builder)((Builder)new Builder().name("armor-count")).description("Enable custom armor count threshold."))
               .defaultValue(false))
            .visible(MaceMissAura::useArmorCountThreshold$lambda$0))
         .build()
   );
   private static final IntSetting armorCountThreshold = (IntSetting)sgMace.add(
      (Setting)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("armor-threshold"))
                  .description("Number of armor pieces (0-4)."))
               .defaultValue(2))
            .min(0)
            .max(4)
            .sliderRange(0, 4)
            .visible(MaceMissAura::armorCountThreshold$lambda$0))
         .build()
   );
   private static final EnumSetting<MaceMissAura.ThresholdComparison> thresholdComparison = (EnumSetting<MaceMissAura.ThresholdComparison>)sgMace.add(
      (Setting)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("threshold-compare"))
                  .description("Use breach when armor count <= or >= threshold."))
               .defaultValue(MaceMissAura.ThresholdComparison.LessOrEqual))
            .visible(MaceMissAura::thresholdComparison$lambda$0))
         .build()
   );
   private static final BoolSetting syncBreachArmor = (BoolSetting)sgMace.add(
      (Setting)((Builder)((Builder)((Builder)((Builder)new Builder().name("sync-breach-armor"))
                  .description("Automatically switch armor mode based on target's armor count."))
               .defaultValue(false))
            .visible(MaceMissAura::syncBreachArmor$lambda$0))
         .build()
   );
   private static final EnumSetting<MaceMissAura.Breach> targetBreachMode = (EnumSetting<MaceMissAura.Breach>)sgMace.add(
      (Setting)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("target-breach-mode"))
                  .description("Breach mode to use when target has enough armor."))
               .defaultValue(MaceMissAura.Breach.Anan))
            .visible(MaceMissAura::targetBreachMode$lambda$0))
         .build()
   );
   private static final IntSetting syncBreachThreshold = (IntSetting)sgMace.add(
      (Setting)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("sync-breach-threshold"))
                  .description("Armor count threshold for automatic switching."))
               .defaultValue(2))
            .min(0)
            .max(4)
            .sliderRange(0, 4)
            .visible(MaceMissAura::syncBreachThreshold$lambda$0))
         .build()
   );
   private static final EnumSetting<MaceMissAura.ThresholdComparison> syncBreachComparison = (EnumSetting<MaceMissAura.ThresholdComparison>)sgMace.add(
      (Setting)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("sync-breach-compare"))
                  .description("Use breach when armor count <= or >= threshold."))
               .defaultValue(MaceMissAura.ThresholdComparison.LessOrEqual))
            .visible(MaceMissAura::syncBreachComparison$lambda$0))
         .build()
   );
   private static final IntSetting attackTimes = (IntSetting)sgSpeed.add(
      (Setting)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                  .name("attack"))
               .description("Once attack target times."))
            .min(0)
            .sliderRange(1, 128)
            .defaultValue(1))
         .build()
   );
   private static final IntSetting attackDelay = (IntSetting)sgSpeed.add(
      (Setting)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                  .name("delay"))
               .description("How wait the delay(tick) to attack"))
            .min(0)
            .sliderRange(0, 128)
            .defaultValue(0))
         .build()
   );
   private static final EnumSetting<MaceMissAura.PredictionMode> predictionMode = (EnumSetting<MaceMissAura.PredictionMode>)sgPrediction.add(
      (Setting)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                  .name("predict-mode"))
               .description("Which prediction algorithm to use."))
            .defaultValue(MaceMissAura.PredictionMode.Off))
         .build()
   );
   private static final IntSetting advancedPredictionTicks = (IntSetting)sgPrediction.add(
      (Setting)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("ticks"))
                     .description("How many ticks to predict for advanced prediction."))
                  .min(1)
                  .sliderRange(1, 20)
                  .defaultValue(4))
               .visible(MaceMissAura::advancedPredictionTicks$lambda$0))
            .onChanged(MaceMissAura::advancedPredictionTicks$lambda$1))
         .build()
   );
   private static final IntSetting advancedSmooth = (IntSetting)sgPrediction.add(
      (Setting)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("smooth"))
                     .description("Smoothing factor for advanced prediction."))
                  .min(1)
                  .sliderRange(1, 10)
                  .defaultValue(1))
               .visible(MaceMissAura::advancedSmooth$lambda$0))
            .onChanged(MaceMissAura::advancedSmooth$lambda$1))
         .build()
   );
   private static final BoolSetting advancedPingSync = (BoolSetting)sgPrediction.add(
      (Setting)((Builder)((Builder)((Builder)((Builder)((Builder)new Builder().name("ping-sync")).description("Synchronize prediction with ping."))
                  .defaultValue(true))
               .visible(MaceMissAura::advancedPingSync$lambda$0))
            .onChanged(MaceMissAura::advancedPingSync$lambda$1))
         .build()
   );
   private static final BoolSetting render3d = (BoolSetting)sgRender.add(
      (Setting)((Builder)((Builder)((Builder)new Builder().name("render-3d")).description("Render 3D boxes for target and prediction.")).defaultValue(true))
         .build()
   );
   private static final EnumSetting<ShapeMode> shapeMode;
   private static final ColorSetting targetColor;
   private static final ColorSetting predictedColor;
   private static final BoolSetting renderPrediction;
   private static final BoolSetting renderVClip;
   private static final BoolSetting renderTp;
   private static final ColorSetting tpPointColor;
   private static final ColorSetting vclipPointColor = (ColorSetting)sgRender.add(
      (Setting)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("vclip-point-color"))
               .description("Color of VClip point boxes."))
            .defaultValue(new SettingColor(255, 255, 0, 80))
            .visible(MaceMissAura::vclipPointColor$lambda$0))
         .build()
   );
   private static final BoolSetting enableTPBot = (BoolSetting)sgTPBot.add(
      (Setting)((Builder)((Builder)((Builder)new Builder().name("use-tpbot")).description("Enable automatic teleportation / velocity movement to target."))
            .defaultValue(false))
         .build()
   );
   private static final EnumSetting<MaceMissAura.TpbotTriggerType> tpbotTriggerType;
   private static final BoolSetting tpbotFaster;
   private static final EnumSetting<MaceMissAura.TpbotEntitiesMode> tpbotEntitiesMode;
   private static final EntityTypeListSetting tpbotEntities;
   private static final BoolSetting useX;
   private static final BoolSetting randomX = (BoolSetting)sgTPBot.add(
      (Setting)((Builder)((Builder)((Builder)((Builder)new Builder().name("random-x")).description("Add random offset to X.")).defaultValue(false))
            .visible(MaceMissAura::randomX$lambda$0))
         .build()
   );
   private static final DoubleSetting relativeX = (DoubleSetting)sgTPBot.add(
      (Setting)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("relative-x"))
               .description("Relative X offset from target."))
            .sliderRange(-25.0, 25.0)
            .defaultValue(0.0)
            .visible(MaceMissAura::relativeX$lambda$0))
         .build()
   );
   private static final BoolSetting useY;
   private static final BoolSetting randomY = (BoolSetting)sgTPBot.add(
      (Setting)((Builder)((Builder)((Builder)((Builder)new Builder().name("random-y")).description("Add random offset to Y.")).defaultValue(false))
            .visible(MaceMissAura::randomY$lambda$0))
         .build()
   );
   private static final DoubleSetting relativeY = (DoubleSetting)sgTPBot.add(
      (Setting)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("relative-y"))
               .description("Relative Y offset from target."))
            .sliderRange(-25.0, 25.0)
            .defaultValue(0.0)
            .visible(MaceMissAura::relativeY$lambda$0))
         .build()
   );
   private static final BoolSetting useZ;
   private static final BoolSetting randomZ = (BoolSetting)sgTPBot.add(
      (Setting)((Builder)((Builder)((Builder)((Builder)new Builder().name("random-z")).description("Add random offset to Z.")).defaultValue(false))
            .visible(MaceMissAura::randomZ$lambda$0))
         .build()
   );
   private static final DoubleSetting relativeZ = (DoubleSetting)sgTPBot.add(
      (Setting)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("relative-z"))
               .description("Relative Z offset from target."))
            .sliderRange(-25.0, 25.0)
            .defaultValue(0.0)
            .visible(MaceMissAura::relativeZ$lambda$0))
         .build()
   );
   private static final DoubleSetting tpbotRange;
   private static final IntSetting tpbotDelay;
   private static final EnumSetting<MaceMissAura.TpbotPredictionMode> tpbotPredictionMode;
   private static final IntSetting tpbotLegacyDataSize = (IntSetting)sgTPBot.add(
      (Setting)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("legacy-data-size"))
                  .description("Number of positions to store for legacy prediction (1-4)."))
               .defaultValue(4))
            .min(1)
            .max(4)
            .sliderRange(1, 4)
            .visible(MaceMissAura::tpbotLegacyDataSize$lambda$0))
         .build()
   );
   private static final DoubleSetting tpbotLegacyResultMul = (DoubleSetting)sgTPBot.add(
      (Setting)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("legacy-result-mul"))
               .description("Multiplier for legacy prediction result."))
            .defaultValue(3.0)
            .min(0.1)
            .max(5.0)
            .sliderRange(0.1, 5.0)
            .visible(MaceMissAura::tpbotLegacyResultMul$lambda$0))
         .build()
   );
   private static final DoubleSetting tpbotSimpleResultMul = (DoubleSetting)sgTPBot.add(
      (Setting)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("simple-result-mul"))
               .description("Multiplier for simple prediction (velocity based)."))
            .defaultValue(3.0)
            .min(0.1)
            .max(5.0)
            .sliderRange(0.1, 5.0)
            .visible(MaceMissAura::tpbotSimpleResultMul$lambda$0))
         .build()
   );
   private static final IntSetting tpbotAdvancedTicks = (IntSetting)sgTPBot.add(
      (Setting)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("advanced-ticks"))
                  .description("How many ticks to predict for advanced prediction."))
               .defaultValue(4))
            .min(1)
            .max(20)
            .sliderRange(1, 20)
            .visible(MaceMissAura::tpbotAdvancedTicks$lambda$0))
         .build()
   );
   private static final IntSetting tpbotAdvancedSmooth = (IntSetting)sgTPBot.add(
      (Setting)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("advanced-smooth"))
                  .description("Smoothing factor for advanced prediction."))
               .defaultValue(1))
            .min(1)
            .max(10)
            .sliderRange(1, 10)
            .visible(MaceMissAura::tpbotAdvancedSmooth$lambda$0))
         .build()
   );
   private static final BoolSetting tpbotAdvancedPingSync = (BoolSetting)sgTPBot.add(
      (Setting)((Builder)((Builder)((Builder)((Builder)new Builder().name("advanced-ping-sync")).description("Synchronize advanced prediction with ping."))
               .defaultValue(true))
            .visible(MaceMissAura::tpbotAdvancedPingSync$lambda$0))
         .build()
   );
   private static final BoolSetting ignoreTPBotFriends;
   private static final BoolSetting ignoreCreative;
   private static final DoubleSetting diffLength;
   private static final BoolSetting velocityMove;
   private static final BoolSetting enableVClipOffset = (BoolSetting)sgVClipOffset.add(
      (Setting)((Builder)((Builder)new Builder().name("vclip-offset")).defaultValue(false)).build()
   );
   private static final BoolSetting randomOffsetX;
   private static final DoubleSetting offsetX;
   private static final BoolSetting randomOffsetY;
   private static final DoubleSetting offsetY;
   private static final BoolSetting randomOffsetZ;
   private static final DoubleSetting offsetZ;
   private static final BoolSetting offsetOnlyTarget;
   private static Entity target;
   private static int tickTimer;
   private static int moveTickTimer;
   private static boolean isSending;
   private static boolean wasDoTp;
   private static final PredictManager predict = new PredictManager();
   private static PlayerEntity predictedTarget;
   private static Vec3d renderPredictedPos;
   private static Entity tpbotTarget;
   private static int tpbotTickTimer;
   private static ArrayList<Vec3d> tpbotHistory = new ArrayList<>();
   private static Vec3d tpbotRealPos = Vec3d.ZERO;
   private static int savedAPredictTicks;
   private static int savedAPredictSmooth;
   private static boolean savedAPredictPingSync;
   private static Vec3d tpRenderPos;
   private static List<Vec3d> vclipRenderPositions = new ArrayList<>();
   private static int lastRender2DTick;
   private static final java.util.Random RANDOM = new java.util.Random();

   private MaceMissAura() {
      super(AddonTemplate.MAIN, "MaceMissAura", "Use Mace Try MissTotem");
   }

   public final Set<EntityType<?>> getEntities() {
      return (Set<EntityType<?>>)entities.get();
   }

   private final int getArmorCount(PlayerEntity player) {
      int count = 0;
      if (!player.getEquippedStack(EquipmentSlot.HEAD).isEmpty()) {
         count++;
      }

      if (!player.getEquippedStack(EquipmentSlot.CHEST).isEmpty()) {
         count++;
      }

      if (!player.getEquippedStack(EquipmentSlot.LEGS).isEmpty()) {
         count++;
      }

      if (!player.getEquippedStack(EquipmentSlot.FEET).isEmpty()) {
         count++;
      }

      return count;
   }

   private final List<Double> getVClipList(Entity targetEntity) {
      PlayerEntity targetPlayer = targetEntity instanceof PlayerEntity ? (PlayerEntity)targetEntity : null;
      if ((Boolean)useFastTp.get()) {
         Object var15 = missVClip.get();
         return (List<Double>)var15;
      }

      if (targetPlayer == null || armorBreakMode.get() == MaceMissAura.Breach.None) {
         Object var14 = missVClip.get();
         return (List<Double>)var14;
      }

      if ((Boolean)useArmorCountThreshold.get()) {
         int armorCnt = this.getArmorCount(targetPlayer);
         Integer threshold = (Integer)armorCountThreshold.get();
         MaceMissAura.ThresholdComparison var16 = (MaceMissAura.ThresholdComparison)thresholdComparison.get();
         boolean useBreach = switch (var16 == null ? -1 : WhenMappings.$EnumSwitchMapping$0[var16.ordinal()]) {
            case 1 -> armorCnt <= threshold;
            case 2 -> armorCnt >= threshold;
            default -> throw new IllegalStateException("unexpected enum");
         };
         List var12;
         if (useBreach) {
            Object var11 = breachVclip.get();
            var12 = (List)var11;
         } else {
            Object var13 = missVClip.get();
            var12 = (List)var13;
         }

         return var12;
      } else {
         boolean hasArmor = this.getArmorCount(targetPlayer) > 0;
         List var7;
         if (hasArmor) {
            var7 = (List)breachVclip.get();
            var7 = var7;
         } else {
            var7 = (List)missVClip.get();
            var7 = var7;
         }

         return var7;
      }
   }

   private final void tpbotLegacyPush(Vec3d pos) {
      Integer size = (Integer)tpbotLegacyDataSize.get();
      if (tpbotHistory.size() >= size) {
         ArrayList var10000 = tpbotHistory;
         int var10002 = tpbotHistory.size();
         var10000.subList(0, var10002 - size + 1).clear();
      }

      tpbotHistory.add(pos);
   }

   private final Vec3d tpbotLegacyGetDiff() {
      int size = tpbotHistory.size();
      if (size == 0) {
         return null;
      }

      Double mul = (Double)tpbotLegacyResultMul.get();

      Vec3d diff = switch (size) {
         case 1 -> (Vec3d)tpbotHistory.get(0);
         case 2 -> tpbotHistory.get(0).add(tpbotHistory.get(1).subtract(tpbotHistory.get(0)));
         case 3 -> tpbotHistory.get(2).multiply(3.0).subtract(tpbotHistory.get(1).multiply(3.0)).add(tpbotHistory.get(0));
         case 4 -> tpbotHistory.get(3)
            .multiply(4.0)
            .subtract(tpbotHistory.get(2).multiply(6.0))
            .add(tpbotHistory.get(1).multiply(4.0))
            .subtract(tpbotHistory.get(0));
         default -> null;
      };
      if (diff != null) {
         Vec3d var4 = diff.subtract(tpbotHistory.get(tpbotHistory.size() - 1));
         if (var4 != null) {
            return var4.multiply(mul);
         }
      }

      return null;
   }

   private final Vec3d tpbotSimplePredict(Entity entity) {
      Double mul = (Double)tpbotSimpleResultMul.get();
      Vec3d old = new Vec3d(entity.lastX, entity.lastY, entity.lastZ);
      Vec3d cur = entity.getEntityPos();
      Vec3d var10001 = cur.subtract(old);
      Vec3d predicted = cur.add(var10001.multiply(mul));
      Vec3d var10000 = cur.add(entity.adjustMovementForCollisions(predicted.subtract(cur)));
      return var10000;
   }

   private final boolean isPositionWaterSafe(BlockPos pos) {
      if (this.mc.world == null) {
         return true;
      }

      ClientWorld world = this.mc.world;

      for (int i = 0; i < 3; i++) {
         BlockPos checkPos = pos.add(0, i, 0);
         FluidState fluid = world.getFluidState(checkPos);
         if (!fluid.isEmpty()) {
            return false;
         }
      }

      return true;
   }

   private final boolean isPositionFullySafe(Vec3d pos, PlayerEntity player) {
      Box box = player.getBoundingBox().offset(pos.subtract(player.getEntityPos()));
      int minX = (int)box.minX;
      int maxX = (int)box.maxX;
      int minY = (int)box.minY;
      int maxY = (int)box.maxY;
      int minZ = (int)box.minZ;
      int maxZ = (int)box.maxZ;
      int x = minX;
      if (x <= maxX) {
         while (true) {
            int y = minY;
            if (y <= maxY) {
               while (true) {
                  int z = minZ;
                  if (z <= maxZ) {
                     while (true) {
                        BlockPos blockPos = new BlockPos(x, y, z);
                        if (this.mc.world != null) {
                           BlockState var10000 = this.mc.world.getBlockState(blockPos);
                           if (var10000 != null) {
                              BlockState state = var10000;
                              if (!state.isAir() && !state.isReplaceable()) {
                                 return false;
                              }
                           }
                        }

                        if (z == maxZ) {
                           break;
                        }

                        z++;
                     }
                  }

                  if (y == maxY) {
                     break;
                  }

                  y++;
               }
            }

            if (x == maxX) {
               break;
            }

            x++;
         }
      }

      return true;
   }

   private final Vec3d adjustTargetPosition(Vec3d targetPos) {
      BlockPos blockPos = BlockPos.ofFloored((Position)targetPos);
      TpUtils var10000 = TpUtils.INSTANCE;
      boolean safeCheck = var10000.isPlayerSafe(blockPos);
      boolean waterCheck = true;
      boolean playerWater = false;
      if ((Boolean)avoidWater.get() || (Boolean)conditionWaterCheck.get()) {
         waterCheck = this.isPositionWaterSafe(blockPos);
         if ((Boolean)conditionWaterCheck.get()) {
            if (this.mc.player == null) {
               return targetPos;
            }

            ClientPlayerEntity player = this.mc.player;
            Vec3d playerPos = player.getEntityPos();
            BlockPos playerBlockPos = BlockPos.ofFloored((Position)playerPos);
            playerWater = this.isPositionWaterSafe(playerBlockPos);
         }
      }

      boolean shouldFind = !safeCheck || (Boolean)avoidWater.get() && !waterCheck || (Boolean)conditionWaterCheck.get() && (!waterCheck || !playerWater);
      if (shouldFind) {
         Double range = avoidWater.get() ? (Double)waterSearchRange.get() : (Double)safeRange.get();
         FindUtils var12 = FindUtils.INSTANCE;
         Vec3d safePos = var12.radiusBlockIter(blockPos, range, MaceMissAura::adjustTargetPosition$lambda$0);
         Vec3d var13 = safePos;
         if (safePos == null) {
            var13 = targetPos;
         }

         return var13;
      } else {
         return targetPos;
      }
   }

   private final Vec3d getSafeOffsetPosition(Vec3d targetPos, Vec3d playerPos) {
      Double range = (Double)safeRange.get();
      if (range <= 0.0) {
         return null;
      }

      boolean waterCheck = true;
      boolean playerWater = false;
      if ((Boolean)avoidWater.get() || (Boolean)conditionWaterCheck.get()) {
         BlockPos blockPos = BlockPos.ofFloored((Position)targetPos);
         waterCheck = this.isPositionWaterSafe(blockPos);
         if ((Boolean)conditionWaterCheck.get()) {
            if (this.mc.player == null) {
               return null;
            }

            ClientPlayerEntity player = this.mc.player;
            BlockPos playerBlockPos = BlockPos.ofFloored((Position)playerPos);
            playerWater = this.isPositionWaterSafe(playerBlockPos);
         }
      }

      if ((Boolean)conditionWaterCheck.get() && waterCheck && playerWater) {
         return null;
      }

      List directions = new ArrayList();

      for (int i = 0; i < 8; i++) {
         double angle = Math.toRadians(i * 45.0);
         directions.add(new Vec3d(Math.cos(angle), 0.0, Math.sin(angle)).normalize());
      }

      directions.add(new Vec3d(0.0, 1.0, 0.0));
      directions.add(new Vec3d(0.0, -1.0, 0.0));
      Vec3d toPlayer = playerPos.subtract(targetPos);
      if (toPlayer.horizontalLengthSquared() > 0.01) {
         Vec3d away = toPlayer.normalize().multiply(-1.0);
         directions.add(0, away);
      }

      for (Object var10000 : directions) {
         Vec3d dir = (Vec3d)var10000;
         Vec3d offsetPos = targetPos.add(dir.multiply(range));
         BlockPos blockPos = BlockPos.ofFloored((Position)offsetPos);
         TpUtils var21 = TpUtils.INSTANCE;
         if (var21.isPlayerSafe(blockPos) && (!(Boolean)avoidWater.get() || this.isPositionWaterSafe(blockPos))) {
            double dist = offsetPos.distanceTo(targetPos);
            if (dist >= range - 0.1) {
               return offsetPos;
            }
         }
      }

      FindUtils var22 = FindUtils.INSTANCE;
      BlockPos var10001 = BlockPos.ofFloored((Position)targetPos);
      Vec3d fallback = var22.radiusBlockIter(var10001, range, MaceMissAura::getSafeOffsetPosition$lambda$0);
      Vec3d var23 = fallback;
      if (fallback == null) {
         var23 = null;
      }

      return var23;
   }

   private final void stepwiseTeleport(Vec3d from, Vec3d to, double maxDist, boolean onGround) {
      double distance = from.distanceTo(to);
      if (!(distance <= 0.001)) {
         int steps = (int)Math.ceil(distance / maxDist);

         for (int i = 0; i < steps; i++) {
            double progress = (i + 1.0) / steps;
            Vec3d stepPos = from.lerp(to, progress);
            TpUtils var10000 = TpUtils.INSTANCE;
            var10000.sendMovePacket(stepPos, onGround, null);
         }

         TpUtils.INSTANCE.sendMovePacket(to, onGround, null);
      }
   }

   private final List<Vec3d> findMultipleSafePositions(Vec3d base, double clip, double searchRange, int count, boolean waterCheck) {
      double targetY = base.y + clip;
      List result = new ArrayList();
      Set checked = new LinkedHashSet();
      int i = 0;
      int var13 = (int)searchRange * 2;
      if (i <= var13) {
         while (true) {
            int offset = i % 2 == 0 ? i / 2 : -(i / 2 + 1);
            double y = targetY + offset;
            int yInt = (int)y;
            if (!checked.contains(yInt)) {
               checked.add(yInt);
               Vec3d pos = new Vec3d(base.x, y, base.z);
               BlockPos blockPos = BlockPos.ofFloored((Position)pos);
               TpUtils var10000 = TpUtils.INSTANCE;
               if (var10000.isPlayerSafe(blockPos) && (!waterCheck || this.isPositionWaterSafe(blockPos))) {
                  result.add(pos);
                  if (result.size() >= count) {
                     break;
                  }
               }
            }

            if (i == var13) {
               break;
            }

            i++;
         }
      }

      return result;
   }

   private final List<Vec3d> findSmartVClipPositions(Vec3d base, double clip, double searchRange, int count, boolean waterCheck) {
      double targetY = base.y + clip;
      List result = new ArrayList();
      Set checked = new LinkedHashSet();
      if (this.mc.player == null) {
         return result;
      }

      ClientPlayerEntity player = this.mc.player;

      for (int upOffset = 0; result.size() < count && upOffset <= (int)searchRange; upOffset++) {
         double y = targetY + upOffset;
         int yInt = (int)y;
         if (yInt > 320) {
            break;
         }

         if (checked.add(yInt)) {
            Vec3d pos = new Vec3d(base.x, y, base.z);
            if (this.isPositionFullySafe(pos, (PlayerEntity)player)) {
               if (waterCheck) {
                  BlockPos var10001 = BlockPos.ofFloored((Position)pos);
                  if (!this.isPositionWaterSafe(var10001)) {
                     continue;
                  }
               }

               result.add(pos);
            }
         }
      }

      if (result.size() < count) {
         for (int downOffset = 1; result.size() < count && downOffset <= (int)searchRange; downOffset++) {
            double y = targetY - downOffset;
            int yInt = (int)y;
            if (yInt < -64) {
               break;
            }

            if (checked.add(yInt)) {
               Vec3d pos = new Vec3d(base.x, y, base.z);
               if (this.isPositionFullySafe(pos, (PlayerEntity)player)) {
                  if (waterCheck) {
                     BlockPos var23 = BlockPos.ofFloored((Position)pos);
                     if (!this.isPositionWaterSafe(var23)) {
                        continue;
                     }
                  }

                  result.add(pos);
               }
            }
         }
      }

      List sortByDescending$iv = result;
      int sortByDescending = 0;
      if (sortByDescending$iv.size() > 1) {
         sortByDescending$iv.sort((a, b) -> Double.compare(((Vec3d)b).y, ((Vec3d)a).y));
      }

      return result;
   }

   private final void log(String msg) {
      if ((Boolean)debugLog.get()) {
         ChatUtils.info("[MaceMissAura] " + msg, new Object[0]);
      }
   }

   private final void act() {
      UUID targetUuid = target != null ? target.getUuid() : null;
      Vec3d targetDiff = targetUuid != null && predict.isNotEmpty() ? predict.getDiff() : null;
      FindUtils var10000 = FindUtils.INSTANCE;
      Set var10001 = (Set)entities.get();
      var10001 = var10001;
      Object var10002 = ignoreFriends.get();
      Entity t = var10000.findTarget(var10001, (Boolean)var10002, true, e -> actTargetFilter(e, targetUuid, targetDiff));
      UUID tUuid = t != null ? t.getUuid() : null;
      target = t;
      if ((Boolean)syncBreachArmor.get() && t instanceof PlayerEntity && (Boolean)useMace.get() && !(Boolean)useFastTp.get()) {
         int armorCnt = this.getArmorCount((PlayerEntity)t);
         Integer threshold = (Integer)syncBreachThreshold.get();
         MaceMissAura.ThresholdComparison var40 = (MaceMissAura.ThresholdComparison)syncBreachComparison.get();
         boolean shouldUseBreach = switch (var40 == null ? -1 : WhenMappings.$EnumSwitchMapping$0[var40.ordinal()]) {
            case 1 -> armorCnt <= threshold;
            case 2 -> armorCnt >= threshold;
            default -> throw new IllegalStateException("unexpected enum");
         };
         MaceMissAura.Breach targetMode = shouldUseBreach ? (MaceMissAura.Breach)targetBreachMode.get() : MaceMissAura.Breach.None;
         if (armorBreakMode.get() != targetMode) {
            armorBreakMode.set(targetMode);
         }
      }

      MaceMissAura.PredictionMode var20 = (MaceMissAura.PredictionMode)predictionMode.get();
      switch (var20 == null ? -1 : WhenMappings.$EnumSwitchMapping$1[var20.ordinal()]) {
         case 1:
            if (t != null && t instanceof PlayerEntity && APredictUtils.enablePrediction) {
               predictedTarget = APredictUtils.createPredict((PlayerEntity)t);
               Vec3d var23;
               if (predictedTarget != null) {
                  PlayerEntity var22 = predictedTarget;
                  var23 = var22.getEntityPos();
               } else {
                  var23 = null;
               }

               renderPredictedPos = var23;
            } else {
               predictedTarget = null;
               renderPredictedPos = null;
            }
            break;
         case 2:
            if (t != null && (Boolean)usePredict.get() && java.util.Objects.equals(targetUuid, tUuid)) {
               PredictManager var21 = predict;
               Vec3d var36 = t.getEntityPos();
               var21.push(var36);
            } else {
               predict.clear();
            }
            break;
         default:
            predict.clear();
            predictedTarget = null;
            renderPredictedPos = null;
      }

      if (t != null) {
         if (this.mc.player == null) {
            return;
         }

         ClientPlayerEntity player = this.mc.player;
         Vec3d playerPos = player.getEntityPos();
         MaceMissAura.PredictionMode var41 = (MaceMissAura.PredictionMode)predictionMode.get();
         Vec3d targetPos = switch (var41 == null ? -1 : WhenMappings.$EnumSwitchMapping$1[var41.ordinal()]) {
            case 1 -> predictedTarget != null ? predictedTarget.getEntityPos() : t.getEntityPos();
            case 2 -> {
               if ((Boolean)usePredict.get() && predict.isNotEmpty()) {
                  yield t.getEntityPos().add(predict.getDiff());
               }

               yield t.getEntityPos();
            }
            case 3 -> PredictUtils.INSTANCE.predictEntity(t);
            case 4 -> { Vec3d predicted = PredictUtil.INSTANCE.predictEntity(t); yield predicted != null ? predicted : t.getEntityPos(); }
            default -> t.getEntityPos();
         };
         if (!(Boolean)useFastTp.get()) {
            BlockPos blockTargetPos = BlockPos.ofFloored((Position)targetPos);
            TpUtils var26 = TpUtils.INSTANCE;
            if (!var26.isPlayerSafe(blockTargetPos)) {
               FindUtils var27 = FindUtils.INSTANCE;
               var10002 = safeRange.get();
               Vec3d var28 = var27.radiusBlockIter(blockTargetPos, ((Number)var10002).doubleValue(), MaceMissAura::act$lambda$1);
               if (var28 == null) {
                  var28 = targetPos;
               }

               targetPos = var28;
            }
         }

         var41 = (MaceMissAura.PredictionMode)predictionMode.get();
         switch (var41 == null ? -1 : WhenMappings.$EnumSwitchMapping$1[var41.ordinal()]) {
            case 1:
               if (predictedTarget == null || predictedTarget.getBoundingBox() == null) {
                  t.getBoundingBox();
               }
               break;
            case 2:
               if ((Boolean)usePredict.get() && predict.isNotEmpty()) {
                  t.getBoundingBox().offset(predict.getDiff());
                  break;
               }

               t.getBoundingBox();
               break;
            case 3:
               Vec3d predictedPos = PredictUtils.INSTANCE.predictEntity(t);
               t.getBoundingBox().offset(predictedPos.subtract(t.getEntityPos()));
               break;
            case 4:
               if (PredictUtil.INSTANCE.predictBox(t) == null) {
                  t.getBoundingBox();
               }
               break;
            default:
               t.getBoundingBox();
         }

         if ((Boolean)tpTarget.get() && moveTickTimer >= ((Number)moveDelay.get()).intValue()) {
            TpUtils var30 = TpUtils.INSTANCE;
            Vec3d delay = targetPos;
            var30.tp3rd(playerPos, delay, player.isOnGround());
            player.updatePosition(targetPos.x, targetPos.y, targetPos.z);
            tpRenderPos = targetPos;
         }

         if (moveTickTimer < ((Number)moveDelay.get()).intValue()) {
            moveTickTimer++;
         } else {
            moveTickTimer = 0;
         }

         Integer delay = tpsSync.get()
            ? (int)(((Number)attackDelay.get()).doubleValue() + (20.0 - TickRate.INSTANCE.getTickRate()))
            : (Integer)attackDelay.get();
         if (tickTimer < delay) {
            tickTimer++;
            return;
         }

         tickTimer = 0;
         if (!(Boolean)tpTarget.get()) {
            wasDoTp = true;
         }

         int i = 0;
         Object var31 = attackTimes.get();

         for (int var17 = ((Number)var31).intValue(); i < var17; i++) {
            InteractUtils var32 = InteractUtils.INSTANCE;
            var10002 = rotation.get();
            boolean var39 = (Boolean)var10002;
            Object var10003 = swing.get();
            var32.attack(t, var39, (Boolean)var10003);
         }
      } else {
         Object var33 = attackDelay.get();
         tickTimer = ((Number)var33).intValue();
         var33 = moveDelay.get();
         moveTickTimer = ((Number)var33).intValue();
         tpRenderPos = null;
      }
   }

   private final void tpbotAct() {
      Entity t = null;
      FindUtils var10000 = FindUtils.INSTANCE;
      Set var10001 = (Set)tpbotEntities.get();
      var10001 = var10001;
      Object var10002 = ignoreTPBotFriends.get();
      boolean var61 = (Boolean)var10002;
      Object var10003 = ignoreCreative.get();
      t = var10000.findTarget(var10001, var61, (Boolean)var10003, e -> true);
      if (((Number)tpbotRange.get()).doubleValue() > 0.0 && t != null && this.mc.player != null) {
         ClientPlayerEntity var36 = this.mc.player;
         if (var36.distanceTo((Entity)t) > ((Number)tpbotRange.get()).doubleValue()) {
            t = null;
         }
      }

      if ((Boolean)blacklistMode.get() && t instanceof PlayerEntity) {
         String name = ((PlayerEntity)t).getName().getString();
         Object var37 = blacklist.get();
         Iterable any$iv = (Iterable)var37;
         int any = 0;
         boolean var38;
         if (any$iv instanceof Collection && ((Collection)any$iv).isEmpty()) {
            var38 = false;
         } else {
            Iterator predictedPos = any$iv.iterator();

            while (true) {
               if (!predictedPos.hasNext()) {
                  var38 = false;
                  break;
               }

               Object element$iv = predictedPos.next();
               String it = (String)element$iv;
               int var8 = 0;
               if (it.equalsIgnoreCase(name)) {
                  var38 = true;
                  break;
               }
            }
         }

         if (!var38) {
            t = null;
         }
      }

      UUID tUuid = t != null ? t.getUuid() : null;
      UUID targetUuid = tpbotTarget != null ? tpbotTarget.getUuid() : null;
      if (tpbotTarget == null) {
         tpbotTarget = (Entity)t;
      } else if (tpbotTarget != null) {
         Entity it = tpbotTarget;
         int var23 = 0;
         if (t == null || !it.isAlive() || it instanceof LivingEntity && ((LivingEntity)it).isDead()) {
            tpbotTarget = (Entity)t;
         }
      }

      t = tpbotTarget;
      if (t == null) {
         Object var53 = tpbotDelay.get();
         tpbotTickTimer = ((Number)var53).intValue();
         tpbotHistory.clear();
      } else {
         MaceMissAura.TpbotPredictionMode mode = (MaceMissAura.TpbotPredictionMode)tpbotPredictionMode.get();

         Vec3d predictedPos = switch (mode == null ? -1 : WhenMappings.$EnumSwitchMapping$2[mode.ordinal()]) {
            case 1 -> {
               Vec3d var55 = t.getEntityPos();
               this.tpbotLegacyPush(var55);
               Vec3d diff = this.tpbotLegacyGetDiff();
               yield diff != null ? t.getEntityPos().add(diff) : t.getEntityPos();
            }
            case 2 -> this.tpbotSimplePredict((Entity)t);
            case 3 -> {
               Vec3d var43;
               vclipResolve: {
                  savedAPredictTicks = APredictUtils.predictionTicks;
                  savedAPredictSmooth = APredictUtils.smooth;
                  savedAPredictPingSync = APredictUtils.pingSync;
                  APredictUtils.enablePrediction = true;
                  Object var40 = tpbotAdvancedTicks.get();
                  APredictUtils.predictionTicks = ((Number)var40).intValue();
                  var40 = tpbotAdvancedSmooth.get();
                  APredictUtils.smooth = ((Number)var40).intValue();
                  var40 = tpbotAdvancedPingSync.get();
                  APredictUtils.pingSync = (Boolean)var40;
                  PlayerEntity pred = t instanceof PlayerEntity ? APredictUtils.createPredict((PlayerEntity)t) : null;
                  if (pred != null) {
                     var43 = pred.getEntityPos();
                     if (var43 != null) {
                        break vclipResolve;
                     }
                  }

                  var43 = t.getEntityPos();
               }

               Vec3d result = var43;
               APredictUtils.predictionTicks = savedAPredictTicks;
               APredictUtils.smooth = savedAPredictSmooth;
               APredictUtils.pingSync = savedAPredictPingSync;
               yield result;
            }
            default -> t.getEntityPos();
         };
         if (this.mc.player != null) {
            ClientPlayerEntity player = this.mc.player;
            Vec3d playerPos = player.getEntityPos();
            Vec3d targetPos = predictedPos;
            Double x = (Double)relativeX.get();
            Double y = (Double)relativeY.get();
            Double z = (Double)relativeZ.get();
            if ((Boolean)randomX.get()) {
               double var44 = x;
               double var56 = RANDOM.nextDouble();
               Double pos = x;
               x = var44 - var56 * pos.doubleValue() * 2.0;
            }

            if ((Boolean)randomY.get()) {
               double var45 = y;
               double var57 = RANDOM.nextDouble();
               Double var29 = y;
               y = var45 - var57 * var29.doubleValue() * 2.0;
            }

            if ((Boolean)randomZ.get()) {
               double var46 = z;
               double var58 = RANDOM.nextDouble();
               Double var30 = z;
               z = var46 - var58 * var30.doubleValue() * 2.0;
            }

                        double var63;
            if ((Boolean)useX.get()) {
               double var62 = targetPos.x;
               Double distance = x;
               var63 = var62 + distance.doubleValue();
            } else {
               var63 = playerPos.x;
            }

            double var67;
            if ((Boolean)useY.get()) {
               var67 = targetPos.y;
               Double var32 = y;
               var67 += var32.doubleValue();
            } else {
               var67 = playerPos.y;
            }

            double var68;
            if ((Boolean)useZ.get()) {
               var68 = targetPos.z;
               Double var33 = z;
               var68 += var33.doubleValue();
            } else {
               var68 = playerPos.z;
            }

            Vec3d pos = new Vec3d(var63, var67, var68);
            if (tpbotTickTimer < ((Number)tpbotDelay.get()).intValue()) {
               tpbotTickTimer++;
            } else {
               tpbotTickTimer = 0;
               tpbotRealPos = pos;
            }

            if ((Boolean)useY.get()) {
               TpUtils var48 = TpUtils.INSTANCE;
               BlockPos var59 = BlockPos.ofFloored((Position)tpbotRealPos);
               if (!var48.isPlayerSafe(var59)) {
                  var10000 = FindUtils.INSTANCE;
                  Vec3d var60 = tpbotRealPos;
                  Object var64 = diffLength.get();
                  Double var50 = var10000.findVClip(var60, ((Number)var64).doubleValue());
                  double vclip = var50 != null ? var50 : tpbotRealPos.y;
                  Vec3d var51 = tpbotRealPos;
                  ((IVec3d)var51).meteor$setY(vclip);
               }
            }

            if ((Boolean)velocityMove.get()) {
               double distance = TpSetting.INSTANCE.getVelocityMoveSpeed();
               if (!(distance <= 0.0) && !(playerPos.distanceTo(tpbotRealPos) < distance)) {
                  player.setVelocity(tpbotRealPos.subtract(playerPos).normalize().multiply(distance));
               } else {
                  player.setVelocity(tpbotRealPos.subtract(playerPos));
               }
            } else if (!java.util.Objects.equals(playerPos, tpbotRealPos)) {
               TpUtils var52 = TpUtils.INSTANCE;
               Vec3d var65 = tpbotRealPos;
               var52.tp3rd(playerPos, var65, player.isOnGround());
               player.updatePosition(tpbotRealPos.x, tpbotRealPos.y, tpbotRealPos.z);
            }
         }
      }
   }

   @EventHandler(priority = -200)
   private final void onTickPost(Post event) {
      if (triggerType.get() == MaceMissAura.TriggerType.PostTick) {
         this.act();
      }

      if ((Boolean)enableTPBot.get()) {
         MaceMissAura.TpbotTriggerType tType = (MaceMissAura.TpbotTriggerType)tpbotTriggerType.get();
         if (tType == MaceMissAura.TpbotTriggerType.PostTick || tType == MaceMissAura.TpbotTriggerType.Both && (Boolean)tpbotFaster.get()) {
            this.tpbotAct();
         }
      }
   }

   @EventHandler(priority = -200)
   private final void onTickPre(Pre event) {
      if (triggerType.get() == MaceMissAura.TriggerType.PreTick) {
         this.act();
      }

      if ((Boolean)enableTPBot.get()) {
         MaceMissAura.TpbotTriggerType tType = (MaceMissAura.TpbotTriggerType)tpbotTriggerType.get();
         if (tType == MaceMissAura.TpbotTriggerType.PreTick || tType == MaceMissAura.TpbotTriggerType.Both) {
            this.tpbotAct();
         }
      }
   }

   @EventHandler(priority = -200)
   private final void onRender2D(Render2DEvent event) {
      if (triggerType.get() == MaceMissAura.TriggerType.Render2D) {
         if ((Boolean)render2DUnlimited.get()) {
            this.act();
         } else {
            int currentTick = this.mc.player != null ? this.mc.player.age : 0;
            if (currentTick != lastRender2DTick) {
               this.act();
               lastRender2DTick = currentTick;
            }
         }
      }
   }

   @EventHandler
   private final void onSendPacket(Send event) {
      Packet packet = event.packet;
      if (packet instanceof PlayerInteractEntityC2SPacket) {
         if (!InteractUtils.INSTANCE.isAttack((PlayerInteractEntityC2SPacket)packet)) {
            return;
         }

         if (isSending) {
            return;
         }

         isSending = true;
         vclipRenderPositions.clear();
         if (target == null) {
            MaceMissAura onSendPacket_u24lambda_u240 = this;
            int var33 = 0;
            isSending = false;
            return;
         }

         Entity t = target;
         if (this.mc.player == null) {
            MaceMissAura onSendPacket_u24lambda_u241 = this;
            int var35 = 0;
            isSending = false;
            return;
         }

         ClientPlayerEntity player = this.mc.player;
         Vec3d playerPos = player.getEntityPos();
         MaceMissAura.PredictionMode var91 = (MaceMissAura.PredictionMode)predictionMode.get();
         Vec3d targetPos = switch (var91 == null ? -1 : WhenMappings.$EnumSwitchMapping$1[var91.ordinal()]) {
            case 1 -> predictedTarget != null ? predictedTarget.getEntityPos() : t.getEntityPos();
            case 2 -> {
               if ((Boolean)usePredict.get() && predict.isNotEmpty()) {
                  yield t.getEntityPos().add(predict.getDiff());
               }

               yield t.getEntityPos();
            }
            case 3 -> PredictUtils.INSTANCE.predictEntity(t);
            case 4 -> { Vec3d predicted = PredictUtil.INSTANCE.predictEntity(t); yield predicted != null ? predicted : t.getEntityPos(); }
            default -> t.getEntityPos();
         };
         targetPos = usePredict.get() && predict.isNotEmpty() ? targetPos.add(predict.getDiff()) : targetPos;
         Vec3d var59;
         if ((Boolean)useFastTp.get()) {
            var59 = targetPos;
         } else if ((Boolean)alwaysUseSafeRange.get()) {
            Vec3d offsetYVal = targetPos;
            var59 = this.getSafeOffsetPosition(offsetYVal, playerPos);
            if (var59 == null) {
               offsetYVal = targetPos;
               var59 = this.adjustTargetPosition(offsetYVal);
            }
         } else {
            Vec3d offsetXVal = targetPos;
            var59 = this.adjustTargetPosition(offsetXVal);
         }

         Vec3d attackPos = var59;
         Double offsetXVal = (Double)offsetX.get();
         Double offsetYVal = (Double)offsetY.get();
         Double offsetZVal = (Double)offsetZ.get();
         if ((Boolean)randomOffsetX.get()) {
            double var60 = RANDOM.nextDouble() * 2;
            Double offsetApplied = offsetXVal;
            double var61 = var60 * offsetApplied.doubleValue();
            offsetApplied = offsetXVal;
            offsetXVal = var61 - offsetApplied.doubleValue();
         }

         if ((Boolean)randomOffsetY.get()) {
            double var62 = RANDOM.nextDouble() * 2;
            Double var39 = offsetYVal;
            double var63 = var62 * var39.doubleValue();
            var39 = offsetYVal;
            offsetYVal = var63 - var39.doubleValue();
         }

         if ((Boolean)randomOffsetZ.get()) {
            double var64 = RANDOM.nextDouble() * 2;
            Double var41 = offsetZVal;
            double var65 = var64 * var41.doubleValue();
            var41 = offsetZVal;
            offsetZVal = var65 - var41.doubleValue();
         }

         boolean offsetApplied = false;
         Vec3d offsettedAttackPos = attackPos;
         if ((Boolean)enableVClipOffset.get()) {
            Double isDoTp = offsetXVal;
            double var10002 = isDoTp.doubleValue();
            isDoTp = offsetYVal;
            double var10003 = isDoTp.doubleValue();
            isDoTp = offsetZVal;
            Vec3d offset = new Vec3d(var10002, var10003, isDoTp.doubleValue());
            offsettedAttackPos = attackPos.add(offset);
            offsetApplied = true;
         }

         Vec3d actualAttackPos = offsetApplied ? offsettedAttackPos : attackPos;
         tpRenderPos = actualAttackPos;
         boolean isDoTp = wasDoTp;
         wasDoTp = false;
         boolean isSafe = distanceMode.get() == MaceMissAura.AttackTpMode.Safe;
         double distance = playerPos.distanceTo(actualAttackPos);
         String item = "%.1f";
         Object[] mace = new Object[]{distance};
         mace = mace;
         String var10001 = String.format(item, Arrays.copyOf(mace, mace.length));
         this.log("当前位置 -> 攻击点，距离 " + var10001 + " 米，是否传送 " + isDoTp + "，安全模式 " + isSafe);
         if (isDoTp) {
            if (isSafe) {
               this.log("分段tp");
               Object var80 = attackTpMoveDistance.get();
               this.stepwiseTeleport(playerPos, actualAttackPos, ((Number)var80).doubleValue(), false);
            } else {
               TpUtils var66 = TpUtils.INSTANCE;
               var66.tp3rd(playerPos, actualAttackPos, false);
            }
         } else if (isSafe && distance > 0.1) {
            this.log("强制分段tp（因为远）");
            Object var81 = attackTpMoveDistance.get();
            this.stepwiseTeleport(playerPos, actualAttackPos, ((Number)var81).doubleValue(), false);
         }

         Item[] maceItems = new Item[]{(Item)swapItem.get()};
         FindItemResult itemx = InvUtils.find(maceItems);
         Item[] isCanSwap = new Item[]{Items.MACE};
         FindItemResult macex = InvUtils.find(isCanSwap);
         boolean isCanSwapx = itemx.found() && (Boolean)autoSwap.get();
         if (isCanSwapx) {
            SwapUtils.INSTANCE.swap(itemx.slot(), true);
         }

         PacketUtils.INSTANCE.sendPacket(packet);
         if (isCanSwapx) {
            SwapUtils.INSTANCE.swapBack();
         }

         if (macex.found() && (Boolean)useMace.get()) {
            Vec3d basePos = offsetApplied && !offsetOnlyTarget.get() ? actualAttackPos : attackPos;
            Vec3d var67;
            if (isSafe) {
               var67 = basePos;
            } else {
               MaceMissAura.VClipOrigin var68 = (MaceMissAura.VClipOrigin)vclipOrigin.get();
               switch (var68 == null ? -1 : WhenMappings.$EnumSwitchMapping$3[var68.ordinal()]) {
                  case 1:
                     var67 = playerPos;
                     break;
                  case 2:
                     var67 = basePos;
                     break;
                  default:
                     throw new IllegalStateException("unexpected enum");
               }
            }

            Vec3d pos = var67;
            SwapUtils.INSTANCE.swap(macex.slot(), true);
            List clipList = this.getVClipList(t);
            this.log("VClip 高度列表: " + clipList + "，基准位置 " + pos);
            Iterator newPos = clipList.iterator();

            while (newPos.hasNext()) {
               double clip = ((Number)newPos.next()).doubleValue();
               List<Vec3d> var70;
               if (!(Boolean)enableSafeHeight.get()) {
                  var70 = List.of(pos.add(0.0, clip, 0.0));
               } else {
                  boolean waterCheck = (Boolean)avoidWater.get() || (Boolean)conditionWaterCheck.get();
                  List<Vec3d> var69;
                  if ((Boolean)useSmartVClip.get()) {
                     Object var82 = safeHeight.get();
                     double var83 = ((Number)var82).doubleValue();
                     Object var10004 = safeClipCount.get();
                     var69 = this.findSmartVClipPositions(pos, clip, var83, ((Number)var10004).intValue(), waterCheck);
                  } else {
                     Object var84 = safeHeight.get();
                     double var85 = ((Number)var84).doubleValue();
                     Object var90 = safeClipCount.get();
                     var69 = this.findMultipleSafePositions(pos, clip, var85, ((Number)var90).intValue(), waterCheck);
                  }

                  List<Vec3d> positions = var69;
                  var70 = positions;
               }

               for (Vec3d finalPos : var70) {
                  if (!isSafe && finalPos.y == pos.y) {
                     this.log("跳过 VClip 高度 " + clip + "（无变化）");
                  } else {
                     this.log("执行 VClip 高度 " + clip + "，新位置 " + finalPos);
                     if ((Boolean)renderVClip.get()) {
                        vclipRenderPositions.add(finalPos);
                     }

                     TpUtils var71 = TpUtils.INSTANCE;
                     var71.tp(pos, finalPos, false);
                     TpUtils.INSTANCE.tp(finalPos, pos, false);
                     PacketUtils var72 = PacketUtils.INSTANCE;
                     Object var78 = attackTimes.get();
                     var72.sendPacket(packet, ((Number)var78).intValue());
                  }
               }
            }

            Vec3d newPosx = pos.add(0.0, 1.0E-4, 0.0);
            Vec3d var74;
            if ((Boolean)enableSafeHeight.get()) {
               boolean waterCheck = (Boolean)avoidWater.get() || (Boolean)conditionWaterCheck.get();
               List var73;
               if ((Boolean)useSmartVClip.get()) {
                  Object var86 = safeHeight.get();
                  var73 = this.findSmartVClipPositions(pos, 1.0E-4, ((Number)var86).doubleValue(), 1, waterCheck);
               } else {
                  Object var87 = safeHeight.get();
                  var73 = this.findMultipleSafePositions(pos, 1.0E-4, ((Number)var87).doubleValue(), 1, waterCheck);
               }

               List positions = var73;
               var74 = (positions.isEmpty() ? null : (Vec3d)positions.get(0));
               if (var74 == null) {
                  var74 = newPosx;
               }
            } else {
               var74 = newPosx;
            }

            Vec3d finalPos = var74;
            TpUtils var75 = TpUtils.INSTANCE;
            var75.tp(pos, finalPos, false);
            TpUtils.INSTANCE.tp(finalPos, pos, false);
            PacketUtils var76 = PacketUtils.INSTANCE;
            Object var79 = attackTimes.get();
            var76.sendPacket(packet, ((Number)var79).intValue());
            SwapUtils.INSTANCE.swapBack();
         }

         if (isDoTp) {
            if (isSafe) {
               this.log("分段back");
               Object var88 = attackTpMoveDistance.get();
               this.stepwiseTeleport(actualAttackPos, playerPos, ((Number)var88).doubleValue(), false);
            } else {
               TpUtils var77 = TpUtils.INSTANCE;
               var77.tp3rd(actualAttackPos, playerPos, false);
            }
         } else if (isSafe && distance > 0.1) {
            this.log("强制分段back");
            Object var89 = attackTpMoveDistance.get();
            this.stepwiseTeleport(actualAttackPos, playerPos, ((Number)var89).doubleValue(), false);
         }

         event.cancel();
         isSending = false;
      }
   }

   @EventHandler
   private final void onRender3D(Render3DEvent event) {
      if ((Boolean)render3d.get()) {
         if (target != null) {
            Entity t = target;
            int var5 = 0;
            Box box = t.getBoundingBox();
            event.renderer.box(box, (Color)targetColor.get(), (Color)targetColor.get(), (ShapeMode)shapeMode.get(), 0);
         }

         if ((Boolean)renderPrediction.get()) {
            MaceMissAura.PredictionMode var10000 = (MaceMissAura.PredictionMode)predictionMode.get();
            switch (var10000 == null ? -1 : WhenMappings.$EnumSwitchMapping$1[var10000.ordinal()]) {
               case 1:
                  if (renderPredictedPos != null) {
                     Vec3d pos = renderPredictedPos;
                     int var16 = 0;
                     Box box = new Box(
                        pos.x - 0.3, pos.y, pos.z - 0.3, pos.x + 0.3, pos.y + 1.8, pos.z + 0.3
                     );
                     event.renderer.box(box, (Color)predictedColor.get(), (Color)predictedColor.get(), (ShapeMode)shapeMode.get(), 0);
                  }
               case 2:
               default:
                  break;
               case 3:
                  if (target != null) {
                     Entity tgt = target;
                     int var15 = 0;
                     Vec3d predictedPos = PredictUtils.INSTANCE.predictEntity(tgt);
                     Box box = tgt.getBoundingBox().offset(predictedPos.subtract(tgt.getEntityPos()));
                     event.renderer.box(box, (Color)predictedColor.get(), (Color)predictedColor.get(), (ShapeMode)shapeMode.get(), 0);
                  }
                  break;
               case 4:
                  if (target != null) {
                     Entity tgt = target;
                     int var7 = 0;
                     Box var20 = PredictUtil.INSTANCE.predictBox(tgt);
                     if (var20 != null) {
                        Box box = var20;
                        int var9 = 0;
                        event.renderer.box(box, (Color)predictedColor.get(), (Color)predictedColor.get(), (ShapeMode)shapeMode.get(), 0);
                     }
                  }
            }
         }

         if ((Boolean)renderTp.get() && tpRenderPos != null) {
            Vec3d var10002 = tpRenderPos;
            double var22 = var10002.x - 0.3;
            Vec3d var10003 = tpRenderPos;
            double var23 = var10003.y;
            Vec3d var10004 = tpRenderPos;
            double var24 = var10004.z - 0.3;
            Vec3d var10005 = tpRenderPos;
            double var25 = var10005.x + 0.3;
            Vec3d var10006 = tpRenderPos;
            double var26 = var10006.y + 1.8;
            Vec3d var10007 = tpRenderPos;
            Box box = new Box(var22, var23, var24, var25, var26, var10007.z + 0.3);
            event.renderer.box(box, (Color)tpPointColor.get(), (Color)tpPointColor.get(), (ShapeMode)shapeMode.get(), 0);
         }

         if ((Boolean)renderVClip.get() && !vclipRenderPositions.isEmpty()) {
            for (Object var21 : vclipRenderPositions) {
               Vec3d pos = (Vec3d)var21;
               Box box = new Box(pos.x - 0.3, pos.y, pos.z - 0.3, pos.x + 0.3, pos.y + 1.8, pos.z + 0.3);
               event.renderer.box(box, (Color)vclipPointColor.get(), (Color)vclipPointColor.get(), (ShapeMode)shapeMode.get(), 0);
            }
         }
      }
   }

   public void onActivate() {
      target = null;
      Object var10000 = attackDelay.get();
      tickTimer = ((Number)var10000).intValue();
      var10000 = moveDelay.get();
      moveTickTimer = ((Number)var10000).intValue();
      isSending = false;
      wasDoTp = false;
      predict.clear();
      predictedTarget = null;
      renderPredictedPos = null;
      tpRenderPos = null;
      vclipRenderPositions.clear();
      APredictUtils.enablePrediction = predictionMode.get() == MaceMissAura.PredictionMode.Advanced;
      var10000 = advancedPredictionTicks.get();
      APredictUtils.predictionTicks = ((Number)var10000).intValue();
      var10000 = advancedSmooth.get();
      APredictUtils.smooth = ((Number)var10000).intValue();
      var10000 = advancedPingSync.get();
      APredictUtils.pingSync = (Boolean)var10000;
      tpbotTarget = null;
      var10000 = tpbotDelay.get();
      tpbotTickTimer = ((Number)var10000).intValue();
      tpbotHistory.clear();
      tpbotRealPos = Vec3d.ZERO;
      lastRender2DTick = 0;
   }

   public void onDeactivate() {
      super.onDeactivate();
      APredictUtils.enablePrediction = false;
      renderPredictedPos = null;
      tpRenderPos = null;
      vclipRenderPositions.clear();
      tpbotTarget = null;
      tpbotHistory.clear();
   }

   public String getInfoString() {
      if (target != null) {
         Entity t = target;
         int var2 = 0;
         String var10000 = EntityUtils.getName(t);
         if (var10000 != null) {
            return var10000;
         }
      }

      String var5;
      if ((Boolean)enableTPBot.get()) {
         if (tpbotTarget != null) {
            Entity t = tpbotTarget;
            int var3 = 0;
            var5 = EntityUtils.getName(t);
         } else {
            var5 = null;
         }
      } else {
         var5 = null;
      }

      return var5;
   }

   private static final boolean render2DUnlimited$lambda$0() {
      return triggerType.get() == MaceMissAura.TriggerType.Render2D;
   }

   private static final boolean safeRange$lambda$0() {
      return !(Boolean)useFastTp.get();
   }

   private static final boolean alwaysUseSafeRange$lambda$0() {
      return !(Boolean)useFastTp.get();
   }

   private static final boolean waterSearchRange$lambda$0() {
      return (Boolean)avoidWater.get() || (Boolean)conditionWaterCheck.get();
   }

   private static final boolean attackTpMoveDistance$lambda$0() {
      return distanceMode.get() == MaceMissAura.AttackTpMode.Safe;
   }

   private static final boolean vclipOrigin$lambda$0() {
      Object var10000 = useMace.get();
      return (Boolean)var10000;
   }

   private static final boolean useFastTp$lambda$0() {
      Object var10000 = useMace.get();
      return (Boolean)var10000;
   }

   private static final boolean missVClip$lambda$0() {
      Object var10000 = useMace.get();
      return (Boolean)var10000;
   }

   private static final boolean armorBreakMode$lambda$0() {
      return (Boolean)useMace.get() && !(Boolean)useFastTp.get();
   }

   private static final boolean breachVclip$lambda$0() {
      return (Boolean)useMace.get() && armorBreakMode.get() != MaceMissAura.Breach.None && !(Boolean)useFastTp.get();
   }

   private static final boolean enableSafeHeight$lambda$0() {
      return (Boolean)useMace.get() && !(Boolean)useFastTp.get();
   }

   private static final boolean safeHeight$lambda$0() {
      return (Boolean)useMace.get() && (Boolean)enableSafeHeight.get() && !(Boolean)useFastTp.get();
   }

   private static final boolean safeClipCount$lambda$0() {
      Object var10000 = enableSafeHeight.get();
      return (Boolean)var10000;
   }

   private static final boolean useSmartVClip$lambda$0() {
      Object var10000 = enableSafeHeight.get();
      return (Boolean)var10000;
   }

   private static final boolean useArmorCountThreshold$lambda$0() {
      return (Boolean)useMace.get() && armorBreakMode.get() != MaceMissAura.Breach.None && !(Boolean)useFastTp.get();
   }

   private static final boolean armorCountThreshold$lambda$0() {
      return (Boolean)useMace.get() && (Boolean)useArmorCountThreshold.get() && armorBreakMode.get() != MaceMissAura.Breach.None && !(Boolean)useFastTp.get();
   }

   private static final boolean thresholdComparison$lambda$0() {
      return (Boolean)useMace.get() && (Boolean)useArmorCountThreshold.get() && armorBreakMode.get() != MaceMissAura.Breach.None && !(Boolean)useFastTp.get();
   }

   private static final boolean syncBreachArmor$lambda$0() {
      return (Boolean)useMace.get() && !(Boolean)useFastTp.get();
   }

   private static final boolean targetBreachMode$lambda$0() {
      return (Boolean)useMace.get() && (Boolean)syncBreachArmor.get() && !(Boolean)useFastTp.get();
   }

   private static final boolean syncBreachThreshold$lambda$0() {
      return (Boolean)useMace.get() && (Boolean)syncBreachArmor.get() && !(Boolean)useFastTp.get();
   }

   private static final boolean syncBreachComparison$lambda$0() {
      return (Boolean)useMace.get() && (Boolean)syncBreachArmor.get() && !(Boolean)useFastTp.get();
   }

   private static final boolean advancedPredictionTicks$lambda$0() {
      return predictionMode.get() == MaceMissAura.PredictionMode.Advanced;
   }

   private static final void advancedPredictionTicks$lambda$1(Integer it) {
      APredictUtils.predictionTicks = it;
   }

   private static final boolean advancedSmooth$lambda$0() {
      return predictionMode.get() == MaceMissAura.PredictionMode.Advanced;
   }

   private static final void advancedSmooth$lambda$1(Integer it) {
      APredictUtils.smooth = it;
   }

   private static final boolean advancedPingSync$lambda$0() {
      return predictionMode.get() == MaceMissAura.PredictionMode.Advanced;
   }

   private static final void advancedPingSync$lambda$1(Boolean it) {
      APredictUtils.pingSync = it;
   }

   private static final boolean vclipPointColor$lambda$0() {
      return (Boolean)render3d.get() && (Boolean)renderVClip.get();
   }

   private static final boolean tpbotEntities$lambda$0() {
      return (Boolean)enableTPBot.get() && tpbotEntitiesMode.get() != MaceMissAura.TpbotEntitiesMode.Always;
   }

   private static final boolean randomX$lambda$0() {
      return (Boolean)enableTPBot.get() && (Boolean)useX.get();
   }

   private static final boolean relativeX$lambda$0() {
      return (Boolean)enableTPBot.get() && (Boolean)useX.get();
   }

   private static final boolean randomY$lambda$0() {
      return (Boolean)enableTPBot.get() && (Boolean)useY.get();
   }

   private static final boolean relativeY$lambda$0() {
      return (Boolean)enableTPBot.get() && (Boolean)useY.get();
   }

   private static final boolean randomZ$lambda$0() {
      return (Boolean)enableTPBot.get() && (Boolean)useZ.get();
   }

   private static final boolean relativeZ$lambda$0() {
      return (Boolean)enableTPBot.get() && (Boolean)useZ.get();
   }

   private static final boolean tpbotLegacyDataSize$lambda$0() {
      return (Boolean)enableTPBot.get() && tpbotPredictionMode.get() == MaceMissAura.TpbotPredictionMode.Legacy;
   }

   private static final boolean tpbotLegacyResultMul$lambda$0() {
      return (Boolean)enableTPBot.get() && tpbotPredictionMode.get() == MaceMissAura.TpbotPredictionMode.Legacy;
   }

   private static final boolean tpbotSimpleResultMul$lambda$0() {
      return (Boolean)enableTPBot.get() && tpbotPredictionMode.get() == MaceMissAura.TpbotPredictionMode.Simple;
   }

   private static final boolean tpbotAdvancedTicks$lambda$0() {
      return (Boolean)enableTPBot.get() && tpbotPredictionMode.get() == MaceMissAura.TpbotPredictionMode.Advanced;
   }

   private static final boolean tpbotAdvancedSmooth$lambda$0() {
      return (Boolean)enableTPBot.get() && tpbotPredictionMode.get() == MaceMissAura.TpbotPredictionMode.Advanced;
   }

   private static final boolean tpbotAdvancedPingSync$lambda$0() {
      return (Boolean)enableTPBot.get() && tpbotPredictionMode.get() == MaceMissAura.TpbotPredictionMode.Advanced;
   }

   private static final Vec3d adjustTargetPosition$lambda$0(BlockPos bp, BlockState var1) {
      return !TpUtils.INSTANCE.isPlayerSafe(bp) || avoidWater.get() && !INSTANCE.isPositionWaterSafe(bp)
         ? null
         : new Vec3d(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
   }

   private static final Vec3d getSafeOffsetPosition$lambda$0(BlockPos bp, BlockState var1) {
      return !TpUtils.INSTANCE.isPlayerSafe(bp) || avoidWater.get() && !INSTANCE.isPositionWaterSafe(bp)
         ? null
         : new Vec3d(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
   }

   private static final boolean actTargetFilter(Entity entity, UUID targetUuid, Vec3d targetDiff) {
      if (INSTANCE.mc.player == null) {
         return true;
      }

      ClientPlayerEntity player = INSTANCE.mc.player;
      UUID entityUuid = entity.getUuid();
      Vec3d playerPos = player.getEntityPos();
      if ((Boolean)blacklistMode.get() && entity instanceof PlayerEntity) {
         String name = ((PlayerEntity)entity).getName().getString();
         Object var10000 = blacklist.get();
         Iterable any$iv = (Iterable)var10000;
         int any = 0;
         boolean var19;
         if (any$iv instanceof Collection && ((Collection)any$iv).isEmpty()) {
            var19 = false;
         } else {
            Iterator predictedPos = any$iv.iterator();

            while (true) {
               if (!predictedPos.hasNext()) {
                  var19 = false;
                  break;
               }

               Object element$iv = predictedPos.next();
               String it = (String)element$iv;
               int var12 = 0;
               if (it.equalsIgnoreCase(name)) {
                  var19 = true;
                  break;
               }
            }
         }

         if (!var19) {
            return false;
         }
      }

      if ((Boolean)onlySurfacePlayers.get() && entity instanceof PlayerEntity) {
         Vec3d eyePos = entity.getEyePos();
         BlockPos blockPos = BlockPos.ofFloored((Position)eyePos);
         FluidState fluidState = INSTANCE.mc.world != null ? INSTANCE.mc.world.getFluidState(blockPos) : null;
         if (fluidState != null && !fluidState.isEmpty()) {
            return false;
         }
      }
      MaceMissAura.PredictionMode var23 = (MaceMissAura.PredictionMode)predictionMode.get();
      Box entityBox = switch (var23 == null ? -1 : WhenMappings.$EnumSwitchMapping$1[var23.ordinal()]) {
         case 1 -> {
            if (entity instanceof PlayerEntity && java.util.Objects.equals(targetUuid, entityUuid) && APredictUtils.enablePrediction) {
               PlayerEntity predicted = APredictUtils.createPredict((PlayerEntity)entity);
               yield predicted != null ? predicted.getBoundingBox() : ((PlayerEntity)entity).getBoundingBox();
            } else {
               yield entity.getBoundingBox();
            }
         }
         case 2 -> java.util.Objects.equals(targetUuid, entityUuid) && predict.isNotEmpty() ? entity.getBoundingBox().offset(targetDiff) : entity.getBoundingBox();
         case 3 -> {
            if (java.util.Objects.equals(targetUuid, entityUuid)) {
               Vec3d predictedPos = PredictUtils.INSTANCE.predictEntity(entity);
               yield entity.getBoundingBox().offset(predictedPos.subtract(entity.getEntityPos()));
            } else {
               yield entity.getBoundingBox();
            }
         }
         case 4 -> {
            if (java.util.Objects.equals(targetUuid, entityUuid)) {
               Box predicted = PredictUtil.INSTANCE.predictBox(entity);
               yield predicted != null ? predicted : entity.getBoundingBox();
            } else {
               yield entity.getBoundingBox();
            }
         }
         default -> entity.getBoundingBox();
      };
      if (((Number)range.get()).doubleValue() <= 0.0) {
         return true;
      }

      InteractUtils var22 = InteractUtils.INSTANCE;
      Object var10003 = range.get();
      return var22.isInRange(playerPos, entityBox, ((Number)var10003).doubleValue());
   }

   private static final Vec3d act$lambda$1(BlockPos bp, BlockState bs) {
      return !TpUtils.INSTANCE.isPlayerSafe(bp) || avoidWater.get() && !INSTANCE.isPositionWaterSafe(bp)
         ? null
         : new Vec3d(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
   }

   static {
      SettingGroup var10000 = sgGeneral;
      meteordevelopment.meteorclient.settings.EntityTypeListSetting.Builder var10001 = ((meteordevelopment.meteorclient.settings.EntityTypeListSetting.Builder)((meteordevelopment.meteorclient.settings.EntityTypeListSetting.Builder)new meteordevelopment.meteorclient.settings.EntityTypeListSetting.Builder()
               .name("targets"))
            .description("Entities to attack."))
         .onlyAttackable();
      EntityType[] var0 = new EntityType[]{EntityType.PLAYER};
      entities = (EntityTypeListSetting)var10000.add((Setting)var10001.defaultValue(var0).build());
      var10000 = sgBlacklist;
      meteordevelopment.meteorclient.settings.StringListSetting.Builder var36 = ((meteordevelopment.meteorclient.settings.StringListSetting.Builder)((meteordevelopment.meteorclient.settings.StringListSetting.Builder)new meteordevelopment.meteorclient.settings.StringListSetting.Builder()
               .name("blacklist"))
            .description("Players to attack (case-insensitive)."))
         .defaultValue(new String[0]);
      BoolSetting var10002 = blacklistMode;
      blacklist = (StringListSetting)var10000.add(
         (Setting)((meteordevelopment.meteorclient.settings.StringListSetting.Builder)var36.visible(() -> (Boolean)blacklistMode.get())).build()
      );
      var10000 = sgTpTarget;
      meteordevelopment.meteorclient.settings.IntSetting.Builder var37 = (meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("move-delay"))
         .min(0)
         .sliderRange(0, 20)
         .defaultValue(0);
      var10002 = tpTarget;
      moveDelay = (IntSetting)var10000.add(
         (Setting)((meteordevelopment.meteorclient.settings.IntSetting.Builder)var37.visible(() -> (Boolean)tpTarget.get())).build()
      );
      var10000 = sgMace;
      DoubleListSetting.Builder var38 = ((DoubleListSetting.Builder)new DoubleListSetting.Builder().name("VClip")).min(0.0).sliderRange(0.0, 1000.0);
      Double[] var1 = new Double[]{40.0, 80.0, 100.0};
      missVClip = (DoubleListSetting)var10000.add(((DoubleListSetting.Builder)var38.defaultValue(var1).visible(MaceMissAura::missVClip$lambda$0)).build());
      var10000 = sgMace;
      DoubleListSetting.Builder var39 = ((DoubleListSetting.Builder)new DoubleListSetting.Builder().name("breachVClip")).min(0.0).sliderRange(0.0, 1000.0);
      Double[] var2 = new Double[]{20.0, 60.0, 120.0};
      breachVclip = (DoubleListSetting)var10000.add(((DoubleListSetting.Builder)var39.defaultValue(var2).visible(MaceMissAura::breachVclip$lambda$0)).build());
      var10000 = sgRender;
      meteordevelopment.meteorclient.settings.EnumSetting.Builder var40 = (meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
               .name("shape-mode"))
            .description("How to render the boxes."))
         .defaultValue(ShapeMode.Both);
      var10002 = render3d;
      shapeMode = (EnumSetting<ShapeMode>)var10000.add(
         (Setting)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)var40.visible(() -> (Boolean)render3d.get())).build()
      );
      var10000 = sgRender;
      meteordevelopment.meteorclient.settings.ColorSetting.Builder var41 = ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
               .name("target-color"))
            .description("Color of the target entity box."))
         .defaultValue(new SettingColor(255, 0, 0, 255));
      var10002 = render3d;
      targetColor = (ColorSetting)var10000.add(
         (Setting)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)var41.visible(() -> (Boolean)render3d.get())).build()
      );
      var10000 = sgRender;
      meteordevelopment.meteorclient.settings.ColorSetting.Builder var42 = ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
               .name("predicted-color"))
            .description("Color of the predicted position box."))
         .defaultValue(new SettingColor(0, 255, 0, 255));
      var10002 = render3d;
      predictedColor = (ColorSetting)var10000.add(
         (Setting)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)var42.visible(() -> (Boolean)render3d.get())).build()
      );
      var10000 = sgRender;
      Builder var43 = (Builder)((Builder)((Builder)new Builder().name("render-prediction")).description("Render predicted position boxes.")).defaultValue(true);
      var10002 = render3d;
      renderPrediction = (BoolSetting)var10000.add((Setting)((Builder)var43.visible(() -> (Boolean)render3d.get())).build());
      var10000 = sgRender;
      Builder var44 = (Builder)((Builder)((Builder)new Builder().name("render-vclip"))
            .description("Render VClip positions. If disabled, only teleport point is shown."))
         .defaultValue(true);
      var10002 = render3d;
      renderVClip = (BoolSetting)var10000.add((Setting)((Builder)var44.visible(() -> (Boolean)render3d.get())).build());
      var10000 = sgRender;
      Builder var45 = (Builder)((Builder)((Builder)new Builder().name("render-tp")).description("Render the teleport point box.")).defaultValue(true);
      var10002 = render3d;
      renderTp = (BoolSetting)var10000.add((Setting)((Builder)var45.visible(() -> (Boolean)render3d.get())).build());
      var10000 = sgRender;
      meteordevelopment.meteorclient.settings.ColorSetting.Builder var46 = ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
               .name("tp-point-color"))
            .description("Color of the teleport point box."))
         .defaultValue(new SettingColor(255, 255, 255, 80));
      var10002 = render3d;
      tpPointColor = (ColorSetting)var10000.add(
         (Setting)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)var46.visible(() -> (Boolean)render3d.get())).build()
      );
      var10000 = sgTPBot;
      meteordevelopment.meteorclient.settings.EnumSetting.Builder var47 = (meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
               .name("tp-trigger"))
            .description("When to execute TPBot logic."))
         .defaultValue(MaceMissAura.TpbotTriggerType.PreTick);
      var10002 = enableTPBot;
      tpbotTriggerType = (EnumSetting<MaceMissAura.TpbotTriggerType>)var10000.add(
         (Setting)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)var47.visible(() -> (Boolean)enableTPBot.get())).build()
      );
      var10000 = sgTPBot;
      Builder var48 = (Builder)((Builder)new Builder().name("faster")).defaultValue(false);
      var10002 = enableTPBot;
      tpbotFaster = (BoolSetting)var10000.add((Setting)((Builder)var48.visible(() -> (Boolean)enableTPBot.get())).build());
      var10000 = sgTPBot;
      meteordevelopment.meteorclient.settings.EnumSetting.Builder var49 = (meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
               .name("entities-mode"))
            .description("Which entity list to use for TPBot."))
         .defaultValue(MaceMissAura.TpbotEntitiesMode.None);
      var10002 = enableTPBot;
      tpbotEntitiesMode = (EnumSetting<MaceMissAura.TpbotEntitiesMode>)var10000.add(
         (Setting)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)var49.visible(() -> (Boolean)enableTPBot.get())).build()
      );
      var10000 = sgTPBot;
      var10001 = ((meteordevelopment.meteorclient.settings.EntityTypeListSetting.Builder)((meteordevelopment.meteorclient.settings.EntityTypeListSetting.Builder)new meteordevelopment.meteorclient.settings.EntityTypeListSetting.Builder()
               .name("entities"))
            .description("Entities to target for TPBot."))
         .onlyAttackable();
      var0 = new EntityType[]{EntityType.PLAYER};
      tpbotEntities = (EntityTypeListSetting)var10000.add(
         (Setting)((meteordevelopment.meteorclient.settings.EntityTypeListSetting.Builder)var10001.defaultValue(var0).visible(MaceMissAura::tpbotEntities$lambda$0))
            .build()
      );
      var10000 = sgTPBot;
      Builder var51 = (Builder)((Builder)((Builder)new Builder().name("use-x")).description("Teleport with X coordinate.")).defaultValue(true);
      var10002 = enableTPBot;
      useX = (BoolSetting)var10000.add((Setting)((Builder)var51.visible(() -> (Boolean)enableTPBot.get())).build());
      var10000 = sgTPBot;
      Builder var52 = (Builder)((Builder)((Builder)new Builder().name("use-y")).description("Teleport with Y coordinate.")).defaultValue(true);
      var10002 = enableTPBot;
      useY = (BoolSetting)var10000.add((Setting)((Builder)var52.visible(() -> (Boolean)enableTPBot.get())).build());
      var10000 = sgTPBot;
      Builder var53 = (Builder)((Builder)((Builder)new Builder().name("use-z")).description("Teleport with Z coordinate.")).defaultValue(true);
      var10002 = enableTPBot;
      useZ = (BoolSetting)var10000.add((Setting)((Builder)var53.visible(() -> (Boolean)enableTPBot.get())).build());
      var10000 = sgTPBot;
      meteordevelopment.meteorclient.settings.DoubleSetting.Builder var54 = ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("tpbot-range"))
         .min(0.0)
         .max(256.0)
         .sliderRange(0.0, 256.0)
         .defaultValue(0.0);
      var10002 = enableTPBot;
      tpbotRange = (DoubleSetting)var10000.add(
         (Setting)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)var54.visible(() -> (Boolean)enableTPBot.get())).build()
      );
      var10000 = sgTPBot;
      meteordevelopment.meteorclient.settings.IntSetting.Builder var55 = (meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
               .name("tpbot-delay"))
            .description("Tick delay between teleport/movement updates."))
         .min(0)
         .sliderRange(0, 20)
         .defaultValue(0);
      var10002 = enableTPBot;
      tpbotDelay = (IntSetting)var10000.add(
         (Setting)((meteordevelopment.meteorclient.settings.IntSetting.Builder)var55.visible(() -> (Boolean)enableTPBot.get())).build()
      );
      var10000 = sgTPBot;
      meteordevelopment.meteorclient.settings.EnumSetting.Builder var56 = (meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
               .name("tpbot-predict-mode"))
            .description("Prediction mode for TPBot target position."))
         .defaultValue(MaceMissAura.TpbotPredictionMode.Simple);
      var10002 = enableTPBot;
      tpbotPredictionMode = (EnumSetting<MaceMissAura.TpbotPredictionMode>)var10000.add(
         (Setting)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)var56.visible(() -> (Boolean)enableTPBot.get())).build()
      );
      var10000 = sgTPBot;
      Builder var57 = (Builder)((Builder)((Builder)new Builder().name("ignore-friends")).description("Ignore friends when finding target for TPBot."))
         .defaultValue(true);
      var10002 = enableTPBot;
      ignoreTPBotFriends = (BoolSetting)var10000.add((Setting)((Builder)var57.visible(() -> (Boolean)enableTPBot.get())).build());
      var10000 = sgTPBot;
      Builder var58 = (Builder)((Builder)((Builder)new Builder().name("ignore-creative")).description("Ignore players in creative mode.")).defaultValue(true);
      var10002 = enableTPBot;
      ignoreCreative = (BoolSetting)var10000.add((Setting)((Builder)var58.visible(() -> (Boolean)enableTPBot.get())).build());
      var10000 = sgTPBot;
      meteordevelopment.meteorclient.settings.DoubleSetting.Builder var59 = ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
               .name("diff-length"))
            .description("Find nearest safe coordinate within this length."))
         .min(0.0)
         .sliderRange(0.0, 256.0)
         .defaultValue(40.0);
      var10002 = enableTPBot;
      diffLength = (DoubleSetting)var10000.add(
         (Setting)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)var59.visible(() -> (Boolean)enableTPBot.get())).build()
      );
      var10000 = sgTPBot;
      Builder var60 = (Builder)((Builder)((Builder)new Builder().name("velocity-move")).description("Use velocity instead of direct teleportation."))
         .defaultValue(true);
      var10002 = enableTPBot;
      velocityMove = (BoolSetting)var10000.add((Setting)((Builder)var60.visible(() -> (Boolean)enableTPBot.get())).build());
      var10000 = sgVClipOffset;
      Builder var61 = (Builder)((Builder)new Builder().name("random-x")).defaultValue(false);
      var10002 = enableVClipOffset;
      randomOffsetX = (BoolSetting)var10000.add((Setting)((Builder)var61.visible(() -> (Boolean)enableVClipOffset.get())).build());
      var10000 = sgVClipOffset;
      meteordevelopment.meteorclient.settings.DoubleSetting.Builder var62 = ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("offset-x"))
         .defaultValue(0.0)
         .sliderRange(-5.0, 5.0);
      var10002 = enableVClipOffset;
      offsetX = (DoubleSetting)var10000.add(
         (Setting)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)var62.visible(() -> (Boolean)enableVClipOffset.get())).build()
      );
      var10000 = sgVClipOffset;
      Builder var63 = (Builder)((Builder)new Builder().name("random-y")).defaultValue(false);
      var10002 = enableVClipOffset;
      randomOffsetY = (BoolSetting)var10000.add((Setting)((Builder)var63.visible(() -> (Boolean)enableVClipOffset.get())).build());
      var10000 = sgVClipOffset;
      meteordevelopment.meteorclient.settings.DoubleSetting.Builder var64 = ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("offset-y"))
         .defaultValue(0.0)
         .sliderRange(-5.0, 5.0);
      var10002 = enableVClipOffset;
      offsetY = (DoubleSetting)var10000.add(
         (Setting)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)var64.visible(() -> (Boolean)enableVClipOffset.get())).build()
      );
      var10000 = sgVClipOffset;
      Builder var65 = (Builder)((Builder)new Builder().name("random-z")).defaultValue(false);
      var10002 = enableVClipOffset;
      randomOffsetZ = (BoolSetting)var10000.add((Setting)((Builder)var65.visible(() -> (Boolean)enableVClipOffset.get())).build());
      var10000 = sgVClipOffset;
      meteordevelopment.meteorclient.settings.DoubleSetting.Builder var66 = ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("offset-z"))
         .defaultValue(0.0)
         .sliderRange(-5.0, 5.0);
      var10002 = enableVClipOffset;
      offsetZ = (DoubleSetting)var10000.add(
         (Setting)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)var66.visible(() -> (Boolean)enableVClipOffset.get())).build()
      );
      var10000 = sgVClipOffset;
      Builder var67 = (Builder)((Builder)new Builder().name("only-attack-pos")).defaultValue(false);
      var10002 = enableVClipOffset;
      offsetOnlyTarget = (BoolSetting)var10000.add((Setting)((Builder)var67.visible(() -> (Boolean)enableVClipOffset.get())).build());
   }


   public enum AttackTpMode {
      Normal,
      Safe
   }


   public enum Breach {
      None,
      Acid,
      Anan
   }


   public enum PredictionMode {
      Off,
      Legacy,
      Advanced,
      Simple,
      V2
   }


   public enum ThresholdComparison {
      LessOrEqual,
      GreaterOrEqual
   }


   public enum TpbotEntitiesMode {
      None,
      OnlyActive,
      Always
   }


   public enum TpbotPredictionMode {
      Off,
      Legacy,
      Simple,
      Advanced
   }


   public enum TpbotTriggerType {
      PreTick,
      PostTick,
      Both
   }


   public enum TriggerType {
      PreTick,
      PostTick,
      Render2D
   }


   public enum VClipMode {
      New,
      Fast
   }


   public enum VClipOrigin {
      Self,
      Target
   }
   private static final class WhenMappings {
      static final int[] $EnumSwitchMapping$0 = new int[ThresholdComparison.values().length];
      static final int[] $EnumSwitchMapping$1 = new int[PredictionMode.values().length];
      static final int[] $EnumSwitchMapping$2 = new int[TpbotPredictionMode.values().length];
      static final int[] $EnumSwitchMapping$3 = new int[VClipOrigin.values().length];

      static {
         $EnumSwitchMapping$0[ThresholdComparison.LessOrEqual.ordinal()] = 1;
         $EnumSwitchMapping$0[ThresholdComparison.GreaterOrEqual.ordinal()] = 2;
         $EnumSwitchMapping$1[PredictionMode.Advanced.ordinal()] = 1;
         $EnumSwitchMapping$1[PredictionMode.Legacy.ordinal()] = 2;
         $EnumSwitchMapping$1[PredictionMode.Simple.ordinal()] = 3;
         $EnumSwitchMapping$1[PredictionMode.V2.ordinal()] = 4;
         $EnumSwitchMapping$2[TpbotPredictionMode.Legacy.ordinal()] = 1;
         $EnumSwitchMapping$2[TpbotPredictionMode.Simple.ordinal()] = 2;
         $EnumSwitchMapping$2[TpbotPredictionMode.Advanced.ordinal()] = 3;
         $EnumSwitchMapping$3[VClipOrigin.Self.ordinal()] = 1;
         $EnumSwitchMapping$3[VClipOrigin.Target.ordinal()] = 2;
      }
   }
}
