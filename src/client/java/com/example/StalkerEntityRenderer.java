package com.example;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Renderer for The Stalker - A shadow entity with a dark, corrupted appearance.
 */
@Environment(EnvType.CLIENT)
public class StalkerEntityRenderer extends HumanoidMobRenderer<StalkerEntity, StalkerEntityRenderer.StalkerRenderState, HumanoidModel<StalkerEntityRenderer.StalkerRenderState>> {

    /** Dark shadow texture - pitch black humanoid */
    private static final Identifier STALKER_TEXTURE =
            Identifier.fromNamespaceAndPath("customitemsk", "textures/entity/stalker.png");
    
    /** Glowing eye texture overlay - red eyes in darkness */
    private static final Identifier STALKER_EYES_TEXTURE =
            Identifier.fromNamespaceAndPath("customitemsk", "textures/entity/stalker_eyes.png");

    public StalkerEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
    }

    @Override
    public StalkerRenderState createRenderState() {
        return new StalkerRenderState();
    }

    @Override
    public void extractRenderState(StalkerEntity entity, StalkerRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.isRevealed = !entity.isInvisible();
        state.revealProgress = state.isRevealed ? 1.0f : 0.0f;
    }

    @Override
    protected void scale(StalkerRenderState state, PoseStack poseStack) {
        super.scale(state, poseStack);
        
        // Subtle pulsing when invisible (0.95 - 1.05 scale)
        if (!state.isRevealed) {
            float pulse = 0.95f + Mth.sin(state.ageInTicks * 0.2f) * 0.05f;
            poseStack.scale(pulse, pulse, pulse);
        }
    }

    @Override
    public Identifier getTextureLocation(StalkerRenderState state) {
        return STALKER_TEXTURE;
    }
    
    /**
     * Get the eye texture for glowing effect when revealed
     */
    public Identifier getEyeTextureLocation() {
        return STALKER_EYES_TEXTURE;
    }

    @Environment(EnvType.CLIENT)
    public static class StalkerRenderState extends HumanoidRenderState {
        public boolean isRevealed = false;
        public float revealProgress = 0.0f;
    }
}
