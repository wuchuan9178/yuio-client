package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class IQBooster extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> iq = sgGeneral.add(new IntSetting.Builder()
        .name("IQ")
        .description("Your desired IQ level.")
        .defaultValue(100)
        .min(1)
        .max(250)
        .sliderMin(1)
        .sliderMax(250)
        .build());

    public IQBooster() {
        super(AddonTemplate.OTHER, "IQ+++", "IQ++++");
    }

    @Override
    public void onActivate() {
        ChatUtils.sendMsg(Text.literal("[✓] IQ加成成功，你已成为高IQ人事").formatted(Formatting.GREEN));
        toggle();
    }
}
