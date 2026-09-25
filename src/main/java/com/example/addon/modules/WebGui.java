package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.utils.WebGuiServer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.MinecraftClient;

public class WebGui extends Module {
    public static WebGui INSTANCE;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Integer> port = sgGeneral.add(new IntSetting.Builder()
        .name("port").description("HTTP port.")
        .defaultValue(25566).min(1024).max(65535).build());

    public final Setting<Boolean> openBrowser = sgGeneral.add(new BoolSetting.Builder()
        .name("open-browser").description("Open browser on activate.")
        .defaultValue(true).build());

    private final WebGuiServer server = new WebGuiServer();

    public WebGui() {
        super(AddonTemplate.OTHER, "web-gui", "Browser-based ClickGui.");
        INSTANCE = this;
    }

    @Override
    public void onActivate() {
        try {
            server.start(port.get());
            info("Server started: http://127.0.0.1:" + port.get());
            if (openBrowser.get()) {
                MinecraftClient.getInstance().execute(() -> {
                    try {
                        net.minecraft.util.Util.getOperatingSystem().open(
                            new java.net.URI("http://127.0.0.1:" + port.get()));
                    } catch (Exception ignored) {}
                });
            }
        } catch (Exception e) {
            error("Failed to start: " + e.getMessage());
            toggle();
        }
    }

    @Override
    public void onDeactivate() {
        server.stop();
    }
}
