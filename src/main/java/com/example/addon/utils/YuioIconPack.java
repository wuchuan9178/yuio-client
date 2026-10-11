package com.example.addon.utils;

import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackInfo;
import net.minecraft.resource.ResourcePackSource;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.metadata.ResourceMetadataSerializer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class YuioIconPack implements ResourcePack {
    public static final YuioIconPack INSTANCE = new YuioIconPack();

    private static final Map<String, String> ICONS = Map.of(
        "icons/icon_16x16.png", "/assets/yuio/icon/icon_16.png",
        "icons/icon_32x32.png", "/assets/yuio/icon/icon_32.png",
        "icons/icon_48x48.png", "/assets/yuio/icon/icon_48.png",
        "icons/icon_128x128.png", "/assets/yuio/icon/icon_128.png",
        "icons/icon_256x256.png", "/assets/yuio/icon/icon_256.png"
    );

    private final ResourcePackInfo info = new ResourcePackInfo(
        "yuio_icons",
        Text.literal("Yuio Icons"),
        ResourcePackSource.BUILTIN,
        Optional.empty()
    );

    private YuioIconPack() {
    }

    public static int sizeCount() {
        return ICONS.size();
    }

    public static boolean has(String path) {
        return ICONS.containsKey(path);
    }

    @Override
    public InputSupplier<InputStream> openRoot(String... segments) {
        String path = String.join("/", segments);
        String resource = ICONS.get(path);
        if (resource == null) return null;
        return () -> YuioIconPack.class.getResourceAsStream(resource);
    }

    @Override
    public InputSupplier<InputStream> open(ResourceType type, Identifier id) {
        return null;
    }

    @Override
    public void findResources(ResourceType type, String namespace, String prefix, ResultConsumer consumer) {
    }

    @Override
    public Set<String> getNamespaces(ResourceType type) {
        return Set.of("minecraft");
    }

    @Override
    public <T> T parseMetadata(ResourceMetadataSerializer<T> serializer) {
        return null;
    }

    @Override
    public ResourcePackInfo getInfo() {
        return info;
    }

    @Override
    public void close() {
    }
}
