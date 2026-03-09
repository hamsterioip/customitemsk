package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class CustomItemsKClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(StormArrowEntity.TYPE, StormArrowRenderer::new);
        EntityRendererRegistry.register(ForestSpiritEntity.TYPE, ForestSpiritRenderer::new);
        EntityRendererRegistry.register(MimicEntity.TYPE, MimicRenderer::new);
    }
}
