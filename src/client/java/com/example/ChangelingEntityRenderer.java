package com.example;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Render state for Changeling entity
 */
@Environment(EnvType.CLIENT)
class ChangelingRenderState extends HumanoidRenderState {
    public int disguiseTypeId = 0;
    public boolean enraged    = false;
    public float ageInTicks   = 0;
}

/**
 * Switching model — holds one sub-model per disguise type.
 * renderToBuffer delegates to the active sub-model so the correct
 * quadruped / animal shape is drawn rather than the humanoid zombie shape.
 * setupAnim is intentionally left as a no-op to avoid incompatible
 * render-state casts; mobs render in their default idle pose.
 */
@SuppressWarnings("rawtypes")
@Environment(EnvType.CLIENT)
class ChangelingModel extends EntityModel<ChangelingRenderState> {

    private final EntityModel[] subModels = new EntityModel[10];
    private final HumanoidModel<ChangelingRenderState> trueFormModel;

    int currentDisguise = 0;
    boolean enraged     = false;

    ChangelingModel(EntityRendererProvider.Context ctx) {
        // Zombie root satisfies the EntityModel(ModelPart) constructor.
        // It is never drawn because we override renderToBuffer.
        super(ctx.bakeLayer(ModelLayers.ZOMBIE));

        subModels[0] = new CowModel(ctx.bakeLayer(ModelLayers.COW));           // COW
        subModels[1] = new PigModel(ctx.bakeLayer(ModelLayers.PIG));           // PIG
        subModels[2] = new SheepModel(ctx.bakeLayer(ModelLayers.SHEEP));       // SHEEP
        subModels[3] = new VillagerModel(ctx.bakeLayer(ModelLayers.VILLAGER)); // VILLAGER
        subModels[4] = new ChickenModel(ctx.bakeLayer(ModelLayers.CHICKEN));   // CHICKEN
        subModels[5] = new RabbitModel(ctx.bakeLayer(ModelLayers.RABBIT));     // RABBIT
        subModels[6] = new HorseModel(ctx.bakeLayer(ModelLayers.HORSE));       // HORSE
        subModels[7] = new CatModel(ctx.bakeLayer(ModelLayers.CAT));           // CAT
        subModels[8] = new FoxModel(ctx.bakeLayer(ModelLayers.FOX));           // FOX
        subModels[9] = new CowModel(ctx.bakeLayer(ModelLayers.COW));           // MOOSHROOM (same shape as cow)

        trueFormModel = new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE));
    }

    @Override
    public void setupAnim(ChangelingRenderState state) {
        currentDisguise = state.disguiseTypeId;
        enraged         = state.enraged;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer consumer,
                               int packedLight, int packedOverlay) {
        EntityModel active;
        if (enraged) {
            active = trueFormModel;
        } else {
            int id = (currentDisguise >= 0 && currentDisguise < subModels.length)
                     ? currentDisguise : 0;
            active = subModels[id];
        }
        active.renderToBuffer(poseStack, consumer, packedLight, packedOverlay);
    }
}

/**
 * Renderer for the Changeling.
 * Each disguise now uses the correct mob model (CowModel, PigModel, etc.)
 * so the shape and UV mapping both match the texture — fixing the all-black
 * appearance that occurred when animal textures were applied to the zombie
 * humanoid model.
 */
@Environment(EnvType.CLIENT)
public class ChangelingEntityRenderer
        extends LivingEntityRenderer<ChangelingEntity, ChangelingRenderState, ChangelingModel> {

    private static final Identifier[] DISGUISE_TEXTURES = {
        Identifier.withDefaultNamespace("textures/entity/cow/cow.png"),           // 0 COW
        Identifier.withDefaultNamespace("textures/entity/pig/pig.png"),           // 1 PIG
        Identifier.withDefaultNamespace("textures/entity/sheep/sheep.png"),       // 2 SHEEP
        Identifier.withDefaultNamespace("textures/entity/villager/villager.png"), // 3 VILLAGER
        Identifier.withDefaultNamespace("textures/entity/chicken.png"),           // 4 CHICKEN
        Identifier.withDefaultNamespace("textures/entity/rabbit/brown.png"),      // 5 RABBIT
        Identifier.withDefaultNamespace("textures/entity/horse/horse_brown.png"), // 6 HORSE
        Identifier.withDefaultNamespace("textures/entity/cat/tabby.png"),         // 7 CAT
        Identifier.withDefaultNamespace("textures/entity/fox/fox.png"),           // 8 FOX
        Identifier.withDefaultNamespace("textures/entity/cow/mooshroom.png"),     // 9 MOOSHROOM
    };

    private static final Identifier TRUE_FORM_TEXTURE =
            Identifier.fromNamespaceAndPath("customitemsk", "textures/entity/changeling.png");

    public ChangelingEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ChangelingModel(ctx), 0.5f);
    }

    @Override
    public ChangelingRenderState createRenderState() {
        return new ChangelingRenderState();
    }

    @Override
    public void extractRenderState(ChangelingEntity entity, ChangelingRenderState state,
                                   float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.disguiseTypeId = entity.getDisguiseType().ordinal();
        state.enraged        = entity.isEnraged();
        state.ageInTicks     = entity.tickCount + partialTick;
    }

    @Override
    public Identifier getTextureLocation(ChangelingRenderState state) {
        // Flicker to true form when enraged
        if (state.enraged && ((int) state.ageInTicks % 20) < 5) {
            return TRUE_FORM_TEXTURE;
        }
        int id = state.disguiseTypeId;
        if (id < 0 || id >= DISGUISE_TEXTURES.length) id = 0;
        return DISGUISE_TEXTURES[id];
    }
}
