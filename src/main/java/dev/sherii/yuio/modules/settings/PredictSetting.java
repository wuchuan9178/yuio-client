package dev.sherii.yuio.modules.settings;

import com.example.addon.AddonTemplate;
import dev.sherii.yuio.AModule;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;

public final class PredictSetting extends AModule {
   public static final PredictSetting INSTANCE = new PredictSetting();
   private static final SettingGroup sgGeneral = INSTANCE.settings.getDefaultGroup();
   private static final SettingGroup sgAdvanced = INSTANCE.settings.createGroup("PredictV2");
   private static final IntSetting dataSize = (IntSetting)sgGeneral.add(
      (Setting)((Builder)((Builder)((Builder)new Builder().name("data-size")).description("The amount of data required for prediction."))
            .range(2, 4)
            .sliderRange(2, 4)
            .defaultValue(4))
         .build()
   );
   private static final DoubleSetting resultMul = (DoubleSetting)sgGeneral.add(
      (Setting)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
               .name("result-multiply"))
            .description("Result * Result Multiply"))
         .min(0.0)
         .sliderRange(0.5, 3.0)
         .defaultValue(3.0)
         .build()
   );
   private static final BoolSetting pingSync = (BoolSetting)sgGeneral.add(
      (Setting)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                  .name("ping-sync"))
               .description("Synchronize prediction ticks with ping."))
            .defaultValue(false))
         .build()
   );
   private static final IntSetting _predictTicks = (IntSetting)sgAdvanced.add(
      (Setting)((Builder)((Builder)((Builder)new Builder().name("predict-ticks")).description("Number of ticks to predict ahead for PredictUtil."))
            .defaultValue(4))
         .min(1)
         .max(20)
         .sliderRange(1, 20)
         .build()
   );

   private PredictSetting() {
      super(AddonTemplate.SETTINGS, "predict-setting", "Predict Setting.");
   }

   public final int getDataSize() {
      Object var10000 = dataSize.get();
      return ((Number)var10000).intValue();
   }

   public final double getResultMul() {
      Object var10000 = resultMul.get();
      return ((Number)var10000).doubleValue();
   }

   public final boolean getPingSync() {
      Object var10000 = pingSync.get();
      return (Boolean)var10000;
   }

   public final int getPredictTicks() {
      Object var10000 = _predictTicks.get();
      return ((Number)var10000).intValue();
   }

   public final void setPredictTicks(int value) {
      _predictTicks.set(value);
   }

   public void onActivate() {
      this.toggle();
   }
}
