package com.example.addon.mixin;

import com.example.addon.modules.CustomFonts;
import com.example.addon.utils.FakeFontPack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.FontManager;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Mixin(FontManager.class)
public class FontManagerMixin {

    @ModifyVariable(
        method = "loadFontProviders",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private static List<Resource> yuio$injectFont(List<Resource> current, List<Resource> fontResources, Identifier id) {
        CustomFonts mod = CustomFonts.INSTANCE;
        if (mod == null || !mod.isActive()) return current;
        if (!id.equals(MinecraftClient.DEFAULT_FONT_ID)) return current;

        String json = "{\"providers\":["
            + "{\"type\":\"ttf\",\"file\":\"yuio:" + mod.weight.get().file + "\""
            + ",\"size\":" + mod.size.get()
            + ",\"oversample\":" + mod.oversample.get()
            + ",\"shift\":[" + mod.shiftX.get() + "," + mod.shiftY.get() + "]"
            + ",\"skip\":\"\"}"
            + "]}";

        mod.applied = true;

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        InputSupplier<InputStream> supplier = () -> new ByteArrayInputStream(bytes);

        List<Resource> newList = new ArrayList<>();
        newList.add(new Resource(new FakeFontPack(), supplier));
        return newList;
    }
}

