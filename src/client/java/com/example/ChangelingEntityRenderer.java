package com.example;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/**
 * Renderer for the Changeling - Uses humanoid model but switches texture based on disguise.
 * When enraged, flickers to true form.
 */
@Environment(EnvType.CLIENT)
public class ChangelingEntityRenderer extends HumanoidMobRenderer<ChangelingEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {

    // Texture identifiers for different disguises
    private static final Identifier COW_TEXTURE = Identifier.parse("minecraft:textures/entity/cow/cow.png");
    private static final Identifier PIG_TEXTURE = Identifier.parse("minecraft:textures/entity/pig/pig.png");
    private static final Identifier SHEEP_TEXTURE = Identifier.parse("minecraft:textures/entity/sheep/sheep.png");
    private static final Identifier VILLAGER_TEXTURE = Identifier.parse("minecraft:textures/entity/villager/villager.png");
    private static final Identifier CHICKEN_TEXTURE = Identifier.parse("minecraft:textures/entity/chicken.png");
    private static final Identifier RABBIT_TEXTURE = Identifier.parse("minecraft:textures/entity/rabbit/brown.png");
    
    // True form texture (for when enraged - glitch effect)
    private static final Identifier TRUE_FORM_TEXTURE = 
            Identifier.fromNamespaceAndPath("customitemsk", "textures/entity/changeling.png");

    private ChangelingEntity.DisguiseType currentDisguise = ChangelingEntity.DisguiseType.COW;
    private boolean isEnraged = false;

    public ChangelingEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.7f);
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public void extractRenderState(ChangelingEntity entity, HumanoidRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        this.currentDisguise = entity.getDisguiseType();
        this.isEnraged = entity.isEnraged();
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState state) {
        if (isEnraged) {
            // When enraged, occasionally flicker to true form
            if ((state.ageInTicks % 20) < 5) {
                return TRUE_FORM_TEXTURE;
            }
        }
        
        return switch (currentDisguise) {
            case COW -> COW_TEXTURE;
            case PIG -> PIG_TEXTURE;
            case SHEEP -> SHEEP_TEXTURE;
            case VILLAGER -> VILLAGER_TEXTURE;
            case CHICKEN -> CHICKEN_TEXTURE;
            case RABBIT -> RABBIT_TEXTURE;
        };
    }
}
