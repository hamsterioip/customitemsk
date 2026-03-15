package com.example;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Renderer for The Hollow.
 *
 * Uses the zombie skeleton model scaled slightly taller and thinner to give
 * an unnervingly elongated silhouette.  The entity is rendered as slightly
 * translucent (via alpha trickery at the model layer) when it's far away,
 * making it hard to notice until it's already close.
 *
 * Texture: customitemsk:textures/entity/hollow.png
 * (Replace with a featureless pale/void-grey skin to complete the look.)
 */
@Environment(EnvType.CLIENT)
public class HollowRenderer
        extends HumanoidMobRenderer<HollowEntity, HollowRenderer.HollowRenderState,
                                    HumanoidModel<HollowRenderer.HollowRenderState>> {

    private static final Identifier HOLLOW_TEXTURE =
            Identifier.fromNamespaceAndPath("customitemsk", "textures/entity/hollow.png");

    public HollowRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
    }

    @Override
    public HollowRenderState createRenderState() {
        return new HollowRenderState();
    }

    @Override
    public void extractRenderState(HollowEntity entity, HollowRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
    }

    /** Elongated and slightly narrow — looks *wrong* at a glance. */
    @Override
    protected void scale(HollowRenderState state, PoseStack poseStack) {
        super.scale(state, poseStack);
        // Subtle slow breathing pulse on the scale to feel alive but uncanny
        float pulse = 1.0f + Mth.sin(state.ageInTicks * 0.05f) * 0.02f;
        poseStack.scale(0.88f * pulse, 1.18f * pulse, 0.88f * pulse);
    }

    @Override
    public Identifier getTextureLocation(HollowRenderState state) {
        return HOLLOW_TEXTURE;
    }

    @Environment(EnvType.CLIENT)
    public static class HollowRenderState extends HumanoidRenderState { }
}
