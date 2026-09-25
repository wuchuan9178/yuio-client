package com.example.addon.utils;

import net.minecraft.resource.*;
import net.minecraft.resource.metadata.ResourceMetadataSerializer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.Optional;
import java.util.Set;

public class FakeFontPack implements ResourcePack {

    @Override
    public InputSupplier<InputStream> openRoot(String... segments) { return null; }

    @Override
    public InputSupplier<InputStream> open(ResourceType type, Identifier id) { return null; }

    @Override
    public void findResources(ResourceType type, String namespace, String prefix, ResultConsumer consumer) {}

    @Override
    public Set<String> getNamespaces(ResourceType type) { return Set.of("yuio"); }

    @Override
    public <T> T parseMetadata(ResourceMetadataSerializer<T> serializer) { return null; }

    @Override
    public ResourcePackInfo getInfo() {
        return new ResourcePackInfo("yuio-font", Text.literal("yuio-font"), ResourcePackSource.BUILTIN, Optional.empty());
    }

    @Override
    public void close() {}
}
