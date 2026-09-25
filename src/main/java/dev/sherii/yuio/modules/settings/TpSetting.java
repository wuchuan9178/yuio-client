package dev.sherii.yuio.modules.settings;

import com.example.addon.AddonTemplate;
import dev.sherii.yuio.AModule;
import dev.sherii.yuio.utils.TpUtils;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;

public final class TpSetting extends AModule {
   public static final TpSetting INSTANCE = new TpSetting();
   private static final SettingGroup sgGeneral = INSTANCE.settings.getDefaultGroup();
   private static final SettingGroup sgLimit = INSTANCE.settings.createGroup("Packet-Limit");
   private static final SettingGroup sgMode = INSTANCE.settings.createGroup("TP Mode");
   private static final DoubleSetting moveDistance = (DoubleSetting)sgGeneral.add(
      (Setting)((Builder)new Builder().name("MoveDistance")).min(0.0).sliderRange(1.0, 128.0).defaultValue(10.0).build()
   );
   private static final DoubleSetting velocityMoveSpeed = (DoubleSetting)sgGeneral.add(
      (Setting)((Builder)new Builder().name("VelocitySpeed")).min(0.0).sliderRange(0.1, 256.0).defaultValue(10.0).build()
   );
   private static final EnumSetting<TpSetting.TpMode> tpMode = (EnumSetting<TpSetting.TpMode>)sgMode.add(
      (Setting)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                  .name("tp-mode"))
               .description("Which TP utility to use"))
            .defaultValue(TpSetting.TpMode.TpUtils))
         .build()
   );
   private static final EnumSetting<TpSetting.Action> action = (EnumSetting<TpSetting.Action>)sgLimit.add(
      (Setting)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
               .name("action"))
            .defaultValue(TpSetting.Action.Both))
         .build()
   );
   private static final DoubleSetting interval = (DoubleSetting)sgLimit.add(
      (Setting)((Builder)((Builder)new Builder().name("interval")).min(0.0).sliderRange(0.0, 10.0).defaultValue(7.0).onChanged(TpSetting::interval$lambda$0))
         .build()
   );
   private static final IntSetting maxPacketRate = (IntSetting)sgLimit.add(
      (Setting)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
               .name("max-packets"))
            .min(0)
            .sliderRange(0, 32767)
            .defaultValue(500))
         .build()
   );
   private static final IntSetting inOneTick = (IntSetting)sgLimit.add(
      (Setting)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
               .name("tick"))
            .min(0)
            .sliderRange(0, 1000)
            .defaultValue(0))
         .build()
   );
   private static final IntSetting limitPacket = (IntSetting)sgLimit.add(
      (Setting)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
               .name("limit-packet"))
            .min(1)
            .sliderRange(1, 100)
            .defaultValue(20))
         .build()
   );

   private TpSetting() {
      super(AddonTemplate.OTHER, "TP Setting", "Tp Setting.");
   }

   public final double getMoveDistance() {
      Object var10000 = moveDistance.get();
      return ((Number)var10000).doubleValue();
   }

   public final double getVelocityMoveSpeed() {
      Object var10000 = velocityMoveSpeed.get();
      return ((Number)var10000).doubleValue();
   }

   public final TpSetting.TpMode getTpMode() {
      Object var10000 = tpMode.get();
      return (TpSetting.TpMode)var10000;
   }

   public final TpSetting.Action getAction() {
      Object var10000 = action.get();
      return (TpSetting.Action)var10000;
   }

   public final double getInterval() {
      Object var10000 = interval.get();
      return ((Number)var10000).doubleValue();
   }

   public final int getMaxPacketRate() {
      Object var10000 = maxPacketRate.get();
      return ((Number)var10000).intValue();
   }

   public final int getInOneTick() {
      Object var10000 = inOneTick.get();
      return ((Number)var10000).intValue();
   }

   public final int getLimitPacket() {
      Object var10000 = limitPacket.get();
      return ((Number)var10000).intValue();
   }

   public void onActivate() {
      this.toggle();
   }

   private static final void interval$lambda$0(Double it) {
      TpUtils var10000 = TpUtils.INSTANCE;
      var10000.setInterval(it);
   }

   
   public enum Action {
      None,
      Drop,
      Message,
      Both
   }

   
   public enum TpMode {
      TpUtils,
      TPUtilX,
      LTpUtils
   }
}
