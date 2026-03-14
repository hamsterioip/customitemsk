package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public class CustomItemsKClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Entity renderers
        EntityRendererRegistry.register(StormArrowEntity.TYPE, StormArrowRenderer::new);
        EntityRendererRegistry.register(ForestSpiritEntity.TYPE, ForestSpiritRenderer::new);
        EntityRendererRegistry.register(MimicEntity.TYPE, MimicRenderer::new);
        EntityRendererRegistry.register(WatcherEntity.TYPE, WatcherEntityRenderer::new);
        EntityRendererRegistry.register(StalkerEntity.TYPE, StalkerEntityRenderer::new);
        EntityRendererRegistry.register(ChangelingEntity.TYPE, ChangelingEntityRenderer::new);

        // Watcher jumpscare: receive packet → trigger overlay
        ClientPlayNetworking.registerGlobalReceiver(
                WatcherJumpscarePacket.ID,
                (payload, context) -> context.client().execute(WatcherJumpscareOverlay::trigger));

        // Tick down the overlay each client tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> WatcherJumpscareOverlay.tick());

        // Render the overlay over the HUD
        HudRenderCallback.EVENT.register(WatcherJumpscareOverlay::render);

        // Chat notification when joining a world
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            client.execute(() -> {
                if (client.player == null) return;
                String version = FabricLoader.getInstance()
                        .getModContainer("customitemsk")
                        .map(c -> c.getMetadata().getVersion().getFriendlyString())
                        .orElse("?");
                if (ConnectorMod.updateReady) {
                    client.player.displayClientMessage(Component.literal(
                        "§e[CustomItemsK] §cv" + version + " §e— Update §b" + ConnectorMod.pendingVersion +
                        " §edownloaded! Close Minecraft completely and reopen it to apply."
                    ), false);
                } else {
                    client.player.displayClientMessage(Component.literal(
                        "§e[CustomItemsK] §aRunning v" + version + " §7(up to date)"
                    ), false);
                }
            });
        });
    }
}
