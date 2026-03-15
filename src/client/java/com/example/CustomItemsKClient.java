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
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public class CustomItemsKClient implements ClientModInitializer {

    private static boolean titleToastShown = false;

    @Override
    public void onInitializeClient() {
        // Entity renderers
        EntityRendererRegistry.register(StormArrowEntity.TYPE, StormArrowRenderer::new);
        EntityRendererRegistry.register(ForestSpiritEntity.TYPE, ForestSpiritRenderer::new);
        EntityRendererRegistry.register(MimicEntity.TYPE, MimicRenderer::new);
        EntityRendererRegistry.register(WatcherEntity.TYPE, WatcherEntityRenderer::new);
        EntityRendererRegistry.register(StalkerEntity.TYPE, StalkerEntityRenderer::new);
        EntityRendererRegistry.register(ChangelingEntity.TYPE, ChangelingEntityRenderer::new);
        EntityRendererRegistry.register(HollowEntity.TYPE, HollowRenderer::new);
        EntityRendererRegistry.register(ReverieEntity.TYPE, ReverieRenderer::new);

        // Watcher jumpscare: receive packet → trigger overlay
        ClientPlayNetworking.registerGlobalReceiver(
                WatcherJumpscarePacket.ID,
                (payload, context) -> context.client().execute(WatcherJumpscareOverlay::trigger));

        // Hollow stare: receive packet → trigger vignette overlay
        ClientPlayNetworking.registerGlobalReceiver(
                HollowStarePacket.ID,
                (payload, context) -> context.client().execute(HollowVigilOverlay::trigger));

        // Tick down both overlays each client tick; show title-screen toast once check finishes
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            WatcherJumpscareOverlay.tick();
            HollowVigilOverlay.tick();

            if (!titleToastShown && ConnectorMod.checkComplete && client.screen instanceof TitleScreen) {
                titleToastShown = true;
                if (ConnectorMod.updateReady) {
                    SystemToast.add(client.getToastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                            Component.literal("§eCustomItemsK Update Ready"),
                            Component.literal("§bv" + ConnectorMod.pendingVersion +
                                    " §7downloaded — restart Minecraft to apply."));
                } else {
                    SystemToast.add(client.getToastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                            Component.literal("§aCustomItemsK"),
                            Component.literal("§7Up to date — no update available."));
                }
            }
        });

        // Render both overlays over the HUD
        HudRenderCallback.EVENT.register(WatcherJumpscareOverlay::render);
        HudRenderCallback.EVENT.register(HollowVigilOverlay::render);

        // Title screen version badge (bottom-left corner)
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof TitleScreen)) return;
            ScreenEvents.afterRender(screen).register((scr, graphics, mouseX, mouseY, tickDelta) -> {
                String version = FabricLoader.getInstance()
                        .getModContainer("customitemsk")
                        .map(c -> c.getMetadata().getVersion().getFriendlyString())
                        .orElse("?");
                String line1, line2;
                int barColor;
                if (!ConnectorMod.checkComplete) {
                    line1 = "§7CustomItemsK §8v" + version;
                    line2 = "§8Checking for updates...";
                    barColor = 0xFF444444;
                } else if (ConnectorMod.updateReady) {
                    line1 = "§eCustomItemsK §8v" + version;
                    line2 = "§6Update ready: §b" + ConnectorMod.pendingVersion;
                    barColor = 0xFFFFAA00;
                } else {
                    line1 = "§aCustomItemsK §8v" + version;
                    line2 = "§7Up to date";
                    barColor = 0xFF55AA55;
                }
                net.minecraft.client.gui.Font font = client.font;
                int w = Math.max(font.width(line1), font.width(line2)) + 12;
                int h = 22;
                int x = 4;
                int y = scaledHeight - h - 4;
                graphics.fill(x, y, x + w, y + h, 0xBB000000);
                graphics.fill(x, y, x + 2, y + h, barColor);
                graphics.drawString(font, line1, x + 5, y + 3,  0xFFFFFF, false);
                graphics.drawString(font, line2, x + 5, y + 13, 0xAAAAAA, false);
            });
        });

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
