package com.example.mixin.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds credit text and live mod version to the Minecraft title screen.
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Inject(
        at = @At("RETURN"),
        method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"
    )
    private void onRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        TitleScreen screen = (TitleScreen) (Object) this;
        Minecraft mc = Minecraft.getInstance();

        int width  = screen.width;
        int height = screen.height;

        // Bottom-centre: credit
        String credit = "Made by Koon | CustomItemsK";
        int creditWidth = mc.font.width(credit);
        guiGraphics.drawString(mc.font, credit,
                (width - creditWidth) / 2, height - 20, 0xFFD700, true);

        // Top-right: live version from fabric.mod.json (updated by the auto-updater workflow)
        String version = FabricLoader.getInstance()
                .getModContainer("customitemsk")
                .map(c -> "v" + c.getMetadata().getVersion().getFriendlyString())
                .orElse("v?");
        int versionWidth = mc.font.width(version);
        guiGraphics.drawString(mc.font, version,
                width - versionWidth - 10, 10, 0x55FF55, true);
    }
}
