package com.example.mixin.client;

import com.example.ConnectorMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects into TitleScreen.init() to add a Renderable that draws
 * the credit and version text. Uses the native widget system so it
 * is guaranteed to render every frame regardless of ScreenEvents.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {

    @Shadow protected abstract <T extends Renderable> T addRenderableOnly(T renderable);

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        addRenderableOnly((guiGraphics, mouseX, mouseY, partialTick) -> {
            Minecraft mc = Minecraft.getInstance();
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();

            // Bottom-center: credit
            String credit = "Made by Koon | CustomItemsK";
            int creditWidth = mc.font.width(credit);
            guiGraphics.drawString(mc.font, credit,
                    (w - creditWidth) / 2, h - 20, 0xFFD700, true);

            // Top-right: version (orange when update is waiting)
            String version = FabricLoader.getInstance()
                    .getModContainer("customitemsk")
                    .map(c -> "v" + c.getMetadata().getVersion().getFriendlyString())
                    .orElse("v?");
            int versionColor = ConnectorMod.updateReady ? 0xFF8800 : 0x55FF55;
            int versionWidth = mc.font.width(version);
            guiGraphics.drawString(mc.font, version,
                    w - versionWidth - 10, 10, versionColor, true);

            // Update banner just below the version
            if (ConnectorMod.updateReady) {
                String line1 = "Update " + ConnectorMod.pendingVersion + " downloaded!";
                String line2 = "Close Minecraft completely, then reopen it to apply.";
                int l1w = mc.font.width(line1);
                int l2w = mc.font.width(line2);
                guiGraphics.drawString(mc.font, line1,
                        w - l1w - 10, 22, 0xFF8800, true);
                guiGraphics.drawString(mc.font, line2,
                        w - l2w - 10, 32, 0xFFAA00, true);
            }
        });
    }
}
