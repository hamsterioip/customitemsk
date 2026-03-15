package com.example;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.animal.chicken.ChickenModel;
import net.minecraft.client.model.animal.cow.CowModel;
import net.minecraft.client.model.animal.equine.HorseModel;
import net.minecraft.client.model.animal.feline.CatModel;
import net.minecraft.client.model.animal.fox.FoxModel;
import net.minecraft.client.model.animal.pig.PigModel;
import net.minecraft.client.model.animal.rabbit.RabbitModel;
import net.minecraft.client.model.animal.sheep.SheepModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Render state for Changeling entity.
 */
@Environment(EnvType.CLIENT)
class ChangelingRenderState extends HumanoidRenderState {
    public int disguiseTypeId = 0;
    public boolean enraged    = false;
    public float ageInTicks   = 0;
}

/**
 * A thin EntityModel wrapper around any ModelPart root.
 *
 * renderToBuffer (final in Model) just calls root().render(…), so by passing
 * an animal model's root here we get the correct animal shape drawn without
 * ever needing to override the final method.
 *
 * setupAnim is a no-op — the animal renders in its default idle pose.
 */
@Environment(EnvType.CLIENT)
class ChangelingModel extends EntityModel<ChangelingRenderState> {

    ChangelingModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(ChangelingRenderState state) {
        // intentionally empty — static animal pose is fine for a disguise
    }
}

/**
 * Renderer for the Changeling.
 *
 * Holds one ChangelingModel per disguise type.  Each model wraps the
 * corresponding animal's baked ModelPart root, so the correct shape
 * (and matching UV layout) is used for every texture.
 *
 * In submit() we swap this.model for the active disguise before delegating
 * to the super implementation; the parent handles shadow, name-tag, and
 * render layers as normal.
 */
@Environment(EnvType.CLIENT)
public class ChangelingEntityRenderer
        extends LivingEntityRenderer<ChangelingEntity, ChangelingRenderState, ChangelingModel> {

    // Texture paths indexed by DisguiseType ordinal (must stay in sync with
    // the enum order in ChangelingEntity).
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

    /** Per-disguise models (index = DisguiseType ordinal). */
    private final ChangelingModel[] disguiseModels;
    /** Model used when enraged (true zombie humanoid form). */
    private final ChangelingModel trueFormModel;

    public ChangelingEntityRenderer(EntityRendererProvider.Context ctx) {
        // The zombie-based ChangelingModel is passed to super so this.model
        // is never null; we swap it per-frame inside submit().
        super(ctx, new ChangelingModel(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);

        trueFormModel = this.model; // reuse the one we just gave to super

        // Build one ChangelingModel per disguise by wrapping the real animal
        // model's baked root.  ChangelingModel.setupAnim is a no-op so none
        // of the animal-specific RenderState casts are ever reached.
        disguiseModels = new ChangelingModel[DISGUISE_TEXTURES.length];
        disguiseModels[0] = new ChangelingModel(new CowModel     (ctx.bakeLayer(ModelLayers.COW))     .root());
        disguiseModels[1] = new ChangelingModel(new PigModel     (ctx.bakeLayer(ModelLayers.PIG))     .root());
        disguiseModels[2] = new ChangelingModel(new SheepModel   (ctx.bakeLayer(ModelLayers.SHEEP))   .root());
        disguiseModels[3] = new ChangelingModel(new VillagerModel(ctx.bakeLayer(ModelLayers.VILLAGER)).root());
        disguiseModels[4] = new ChangelingModel(new ChickenModel (ctx.bakeLayer(ModelLayers.CHICKEN)) .root());
        disguiseModels[5] = new ChangelingModel(new RabbitModel  (ctx.bakeLayer(ModelLayers.RABBIT))  .root());
        disguiseModels[6] = new ChangelingModel(new HorseModel   (ctx.bakeLayer(ModelLayers.HORSE))   .root());
        disguiseModels[7] = new ChangelingModel(new CatModel     (ctx.bakeLayer(ModelLayers.CAT))     .root());
        disguiseModels[8] = new ChangelingModel(new FoxModel     (ctx.bakeLayer(ModelLayers.FOX))     .root());
        disguiseModels[9] = new ChangelingModel(new CowModel     (ctx.bakeLayer(ModelLayers.MOOSHROOM)).root());
    }

    // -------------------------------------------------------------------------
    // Per-frame model swap
    // -------------------------------------------------------------------------

    @Override
    public void submit(ChangelingRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState cameraState) {
        ChangelingModel saved = this.model;
        this.model = selectModel(state);
        super.submit(state, poseStack, collector, cameraState);
        this.model = saved;
    }

    private ChangelingModel selectModel(ChangelingRenderState state) {
        if (state.enraged) return trueFormModel;
        int id = state.disguiseTypeId;
        if (id < 0 || id >= disguiseModels.length) return trueFormModel;
        return disguiseModels[id];
    }

    // -------------------------------------------------------------------------
    // Standard LivingEntityRenderer overrides
    // -------------------------------------------------------------------------

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
