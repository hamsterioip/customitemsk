package com.example;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;

public class MimicRenderer extends HumanoidMobRenderer<MimicEntity, MimicRenderState, HumanoidModel<MimicRenderState>> {

    private static final Identifier FALLBACK =
            Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");

    public MimicRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
    }

    @Override
    public MimicRenderState createRenderState() {
        return new MimicRenderState();
    }

    @Override
    public void extractRenderState(MimicEntity entity, MimicRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.stolenUUIDString = entity.getStolenUUIDString();
    }

    @Override
    public Identifier getTextureLocation(MimicRenderState state) {
        if (!state.stolenUUIDString.isEmpty()) {
            try {
                java.util.UUID uuid = java.util.UUID.fromString(state.stolenUUIDString);
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    var info = mc.player.connection.getPlayerInfo(uuid);
                    if (info != null) {
                        return info.getSkin().body().texturePath();
                    }
                }
            } catch (IllegalArgumentException ignored) {}
        }
        return FALLBACK;
    }
}
