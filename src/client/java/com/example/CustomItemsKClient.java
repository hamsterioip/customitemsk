package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.TitleScreen;

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

        // Draw credit + version on the title screen, with update warning if needed
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof TitleScreen ts) {
                ScreenEvents.afterRender(ts).register((s, guiGraphics, mouseX, mouseY, tickDelta) -> {
                    // Bottom-center: credit
                    String credit = "Made by Koon | CustomItemsK";
                    int creditWidth = client.font.width(credit);
                    guiGraphics.drawString(client.font, credit,
                            (s.width - creditWidth) / 2, s.height - 20, 0xFFD700, true);

                    // Top-right: version (orange when update is waiting)
                    String version = FabricLoader.getInstance()
                            .getModContainer("customitemsk")
                            .map(c -> "v" + c.getMetadata().getVersion().getFriendlyString())
                            .orElse("v?");
                    int versionColor = ConnectorMod.updateReady ? 0xFF8800 : 0x55FF55;
                    int versionWidth = client.font.width(version);
                    guiGraphics.drawString(client.font, version,
                            s.width - versionWidth - 10, 10, versionColor, true);

                    // Update banner just below the version
                    if (ConnectorMod.updateReady) {
                        String line1 = "Update " + ConnectorMod.pendingVersion + " downloaded!";
                        String line2 = "Close Minecraft completely, then reopen it to apply.";
                        int l1w = client.font.width(line1);
                        int l2w = client.font.width(line2);
                        guiGraphics.drawString(client.font, line1,
                                s.width - l1w - 10, 22, 0xFF8800, true);
                        guiGraphics.drawString(client.font, line2,
                                s.width - l2w - 10, 32, 0xFFAA00, true);
                    }
                });
            }
        });

        // Chat reminder when joining a world
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (ConnectorMod.updateReady) {
                client.execute(() -> {
                    if (client.player != null) {
                        client.player.displayClientMessage(Component.literal(
                            "§e[CustomItemsK] §aUpdate §b" + ConnectorMod.pendingVersion +
                            " §adownloaded! §eClose Minecraft completely and reopen it to apply."
                        ), false);
                    }
                });
            }
        });
    }
}
