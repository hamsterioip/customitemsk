package com.example;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;

public class WatcherEntityRenderer extends HumanoidMobRenderer<WatcherEntity, WatcherRenderState, HumanoidModel<WatcherRenderState>> {

    /** Custom watcher skin shipped with the mod — used when nooq4oz_ is not online. */
    private static final Identifier WATCHER_SKIN =
            Identifier.fromNamespaceAndPath("customitemsk", "textures/entity/watcher.png");

    public WatcherEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
    }

    @Override
    public WatcherRenderState createRenderState() {
        return new WatcherRenderState();
    }

    @Override
    public void extractRenderState(WatcherEntity entity, WatcherRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
    }

    @Override
    public Identifier getTextureLocation(WatcherRenderState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            // If nooq4oz_ is online, mirror their live skin
            for (var info : mc.player.connection.getListedOnlinePlayers()) {
                if (WatcherEntity.SKIN_NAME.equalsIgnoreCase(info.getProfile().name())) {
                    var skin = info.getSkin();
                    if (skin != null && skin.body() != null) {
                        return skin.body().texturePath();
                    }
                }
            }
        }
        // Fallback: the custom skin bundled with the mod
        return WATCHER_SKIN;
    }
}
