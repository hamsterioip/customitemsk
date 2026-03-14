package com.example;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.npc.Villager;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Renderer for the Changeling.
 * - Disguised: delegates to the actual vanilla mob renderer (correct model + texture)
 * - Enraged: humanoid zombie model with the changeling's own texture
 */
@Environment(EnvType.CLIENT)
public class ChangelingEntityRenderer extends HumanoidMobRenderer<ChangelingEntity, ChangelingEntityRenderer.ChangelingRenderState, HumanoidModel<ChangelingEntityRenderer.ChangelingRenderState>> {

    private static final Identifier TRUE_FORM_TEXTURE =
            Identifier.fromNamespaceAndPath("customitemsk", "textures/entity/changeling.png");

    public ChangelingEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
    }

    @Override
    public ChangelingRenderState createRenderState() {
        return new ChangelingRenderState();
    }

    @Override
    public void extractRenderState(ChangelingEntity entity, ChangelingRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.disguiseType = entity.getDisguiseType();
        state.isEnraged = entity.isEnraged();
        // Copy rotation so the disguise faces the same direction as the changeling
        state.disguiseYRot = entity.getYRot();
        state.disguiseYBodyRot = entity.yBodyRot;
        state.disguiseXRot = entity.getXRot();
        state.disguiseTick = entity.tickCount;
    }

    @Override
    public void render(ChangelingRenderState state, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (!state.isEnraged) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                LivingEntity fake = buildFakeEntity(state, mc);
                if (fake != null) {
                    renderFakeEntity(fake, poseStack, bufferSource, packedLight);
                    return;
                }
            }
        }
        // Enraged: render true form using the humanoid model + changeling texture
        super.render(state, poseStack, bufferSource, packedLight);
    }

    private LivingEntity buildFakeEntity(ChangelingRenderState state, Minecraft mc) {
        LivingEntity fake = switch (state.disguiseType) {
            case COW      -> new Cow(EntityType.COW, mc.level);
            case PIG      -> new Pig(EntityType.PIG, mc.level);
            case SHEEP    -> new Sheep(EntityType.SHEEP, mc.level);
            case VILLAGER -> new Villager(EntityType.VILLAGER, mc.level);
            case CHICKEN  -> new Chicken(EntityType.CHICKEN, mc.level);
            case RABBIT   -> new Rabbit(EntityType.RABBIT, mc.level);
        };
        fake.setYRot(state.disguiseYRot);
        fake.yRotO = state.disguiseYRot;
        fake.yBodyRot = state.disguiseYBodyRot;
        fake.setXRot(state.disguiseXRot);
        fake.tickCount = state.disguiseTick;
        return fake;
    }

    /**
     * Renders the fake entity by extracting its render state and calling its own renderer.
     * The poseStack is already positioned at the changeling's location by the time this is called.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void renderFakeEntity(LivingEntity fake, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        net.minecraft.client.renderer.entity.EntityRenderer renderer =
                Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(fake);
        Object fakeState = renderer.createRenderState();
        renderer.extractRenderState(fake, fakeState, 0f);
        poseStack.pushPose();
        renderer.render(fakeState, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }

    @Override
    public Identifier getTextureLocation(ChangelingRenderState state) {
        // Only used for the enraged (true form) rendering path
        return TRUE_FORM_TEXTURE;
    }

    @Environment(EnvType.CLIENT)
    public static class ChangelingRenderState extends HumanoidRenderState {
        public ChangelingEntity.DisguiseType disguiseType = ChangelingEntity.DisguiseType.COW;
        public boolean isEnraged = false;
        public float disguiseYRot = 0f;
        public float disguiseYBodyRot = 0f;
        public float disguiseXRot = 0f;
        public int disguiseTick = 0;
    }
}
