package dev.sherii.yuio.modules.settings;

import com.example.addon.AddonTemplate;
import dev.sherii.yuio.AModule;
import dev.sherii.yuio.utils.TpUtils;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.SettingGroup;

public final class TpSetting extends AModule {
   public static final TpSetting INSTANCE = new TpSetting();
   private static final SettingGroup sgGeneral = INSTANCE.settings.getDefaultGroup();
   private static final SettingGroup sgLimit = INSTANCE.settings.createGroup("Packet-Limit");
   private static final SettingGroup sgMode = INSTANCE.settings.createGroup("TP Mode");
   private static final DoubleSetting moveDistance = sgGeneral.add(new DoubleSetting.Builder()
      .name("MoveDistance")
      .min(0.0)
      .sliderRange(1.0, 128.0)
      .defaultValue(10.0)
      .build()
   );
   private static final DoubleSetting velocityMoveSpeed = sgGeneral.add(new DoubleSetting.Builder()
      .name("VelocitySpeed")
      .min(0.0)
      .sliderRange(0.1, 256.0)
      .defaultValue(10.0)
      .build()
   );
   private static final EnumSetting<TpMode> tpMode = sgMode.add(new EnumSetting.Builder<TpMode>()
      .name("tp-mode")
      .description("Which TP utility to use")
      .defaultValue(TpMode.TpUtils)
      .build()
   );
   private static final EnumSetting<Action> action = sgLimit.add(new EnumSetting.Builder<Action>()
      .name("action")
      .defaultValue(Action.Both)
      .build()
   );
   private static final DoubleSetting interval = sgLimit.add(new DoubleSetting.Builder()
      .name("interval")
      .min(0.0)
      .sliderRange(0.0, 10.0)
      .defaultValue(7.0)
      .onChanged(TpSetting::interval$lambda$0)
      .build()
   );
   private static final IntSetting maxPacketRate = sgLimit.add(new IntSetting.Builder()
      .name("max-packets")
      .min(0)
      .sliderRange(0, 32767)
      .defaultValue(500)
      .build()
   );
   private static final IntSetting inOneTick = sgLimit.add(new IntSetting.Builder()
      .name("tick")
      .min(0)
      .sliderRange(0, 1000)
      .defaultValue(0)
      .build()
   );
   private static final IntSetting limitPacket = sgLimit.add(new IntSetting.Builder()
      .name("limit-packet")
      .min(1)
      .sliderRange(1, 100)
      .defaultValue(20)
      .build()
   );

   private TpSetting() {
      super(AddonTemplate.SETTINGS, "TP Setting", "Tp Setting.");
   }

   public final double getMoveDistance() {
      return moveDistance.get();
   }

   public final double getVelocityMoveSpeed() {
      return velocityMoveSpeed.get();
   }

   public final TpSetting.TpMode getTpMode() {
      return tpMode.get();
   }

   public final TpSetting.Action getAction() {
      return action.get();
   }

   public final double getInterval() {
      return interval.get();
   }

   public final int getMaxPacketRate() {
      return maxPacketRate.get();
   }

   public final int getInOneTick() {
      return inOneTick.get();
   }

   public final int getLimitPacket() {
      return limitPacket.get();
   }

   public void onActivate() {
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
