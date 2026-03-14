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
 * Render state for Changeling entity
 */
@Environment(EnvType.CLIENT)
class ChangelingRenderState extends HumanoidRenderState {
    public int disguiseTypeId = 0;
    public boolean enraged = false;
    public float ageInTicks = 0;
}

/**
 * Renderer for the Changeling - Uses different textures based on disguise type.
 * When enraged, flickers to true form.
 */
@Environment(EnvType.CLIENT)
public class ChangelingEntityRenderer extends HumanoidMobRenderer<ChangelingEntity, ChangelingRenderState, HumanoidModel<ChangelingRenderState>> {

    // Texture identifiers for different disguises
    private static final Identifier COW_TEXTURE      = Identifier.withDefaultNamespace("textures/entity/cow/cow.png");
    private static final Identifier PIG_TEXTURE      = Identifier.withDefaultNamespace("textures/entity/pig/pig.png");
    private static final Identifier SHEEP_TEXTURE    = Identifier.withDefaultNamespace("textures/entity/sheep/sheep.png");
    private static final Identifier VILLAGER_TEXTURE = Identifier.withDefaultNamespace("textures/entity/villager/villager.png");
    private static final Identifier CHICKEN_TEXTURE  = Identifier.withDefaultNamespace("textures/entity/chicken.png");
    private static final Identifier RABBIT_TEXTURE   = Identifier.withDefaultNamespace("textures/entity/rabbit/brown.png");
    private static final Identifier HORSE_TEXTURE    = Identifier.withDefaultNamespace("textures/entity/horse/horse_brown.png");
    private static final Identifier CAT_TEXTURE      = Identifier.withDefaultNamespace("textures/entity/cat/tabby.png");
    private static final Identifier FOX_TEXTURE      = Identifier.withDefaultNamespace("textures/entity/fox/fox.png");
    private static final Identifier MOOSHROOM_TEXTURE= Identifier.withDefaultNamespace("textures/entity/cow/mooshroom.png");
    
    // True form texture (for when enraged - glitch effect)
    private static final Identifier TRUE_FORM_TEXTURE = 
            Identifier.fromNamespaceAndPath("customitemsk", "textures/entity/changeling.png");

    public ChangelingEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.7f);
    }

    @Override
    public ChangelingRenderState createRenderState() {
        return new ChangelingRenderState();
    }

    @Override
    public void extractRenderState(ChangelingEntity entity, ChangelingRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.disguiseTypeId = entity.getDisguiseType().ordinal();
        state.enraged = entity.isEnraged();
        state.ageInTicks = entity.tickCount + partialTick;
    }

    @Override
    public Identifier getTextureLocation(ChangelingRenderState state) {
        if (state.enraged) {
            // When enraged, occasionally flicker to true form
            if (((int) state.ageInTicks % 20) < 5) {
                return TRUE_FORM_TEXTURE;
            }
        }
        
        return switch (state.disguiseTypeId) {
            case 1 -> PIG_TEXTURE;
            case 2 -> SHEEP_TEXTURE;
            case 3 -> VILLAGER_TEXTURE;
            case 4 -> CHICKEN_TEXTURE;
            case 5 -> RABBIT_TEXTURE;
            case 6 -> HORSE_TEXTURE;
            case 7 -> CAT_TEXTURE;
            case 8 -> FOX_TEXTURE;
            case 9 -> MOOSHROOM_TEXTURE;
            default -> COW_TEXTURE;
        };
    }
}
