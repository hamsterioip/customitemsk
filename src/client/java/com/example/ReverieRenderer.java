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
 * Renderer for The Reverie.
 *
 * Uses the humanoid zombie model.  The custom scale() applies two incommensurable
 * sin waves on XZ (fast micro-jitter) combined with a slow Y pulse — the silhouette
 * never repeats, never settles, never looks quite right.
 *
 * Texture: customitemsk:textures/entity/reverie.png
 */
@Environment(EnvType.CLIENT)
public class ReverieRenderer
        extends HumanoidMobRenderer<ReverieEntity, ReverieRenderer.ReverieRenderState,
                                    HumanoidModel<ReverieRenderer.ReverieRenderState>> {

    private static final Identifier REVERIE_TEXTURE =
            Identifier.fromNamespaceAndPath("customitemsk", "textures/entity/reverie.png");

    public ReverieRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
    }

    @Override
    public ReverieRenderState createRenderState() {
        return new ReverieRenderState();
    }

    @Override
    public void extractRenderState(ReverieEntity entity, ReverieRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
    }

    /**
     * Compound flickering scale — the hallucination refuses to hold a stable form.
     *
     * XZ: two incommensurable sin waves (0.35 and 0.61 rad/tick).  Because their
     *     ratio is irrational the combined wave never repeats, producing organic
     *     micro-jitter rather than a clean pulse.
     * Y:  a slow breathing-like pulse (0.05 rad/tick, ~2.5 s period) that is
     *     completely out of phase with XZ, making the body look physiologically wrong.
     */
    @Override
    protected void scale(ReverieRenderState state, PoseStack poseStack) {
        super.scale(state, poseStack);
        float t = state.ageInTicks;
        float xzFlicker = 1.0f + Mth.sin(t * 0.35f) * 0.035f + Mth.sin(t * 0.61f) * 0.015f;
        float yPulse    = 1.0f + Mth.sin(t * 0.05f) * 0.025f;
        poseStack.scale(xzFlicker, yPulse, xzFlicker);
    }

    @Override
    public Identifier getTextureLocation(ReverieRenderState state) {
        return REVERIE_TEXTURE;
    }

    @Environment(EnvType.CLIENT)
    public static class ReverieRenderState extends HumanoidRenderState { }
}
