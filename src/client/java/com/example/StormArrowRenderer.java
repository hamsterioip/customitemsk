package com.example;

import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.resources.Identifier;

public class StormArrowRenderer extends ArrowRenderer<StormArrowEntity, ArrowRenderState> {

    private static final Identifier TEXTURE =
            Identifier.withDefaultNamespace("textures/entity/projectiles/arrow.png");

    public StormArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ArrowRenderState createRenderState() {
        return new ArrowRenderState();
    }

    @Override
    public Identifier getTextureLocation(ArrowRenderState state) {
        return TEXTURE;
    }
}
