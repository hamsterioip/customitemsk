package com.example.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds credit text and version to the Minecraft title screen
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Inject(at = @At("RETURN"), method = "render")
    private void onRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        TitleScreen screen = (TitleScreen) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        
        int width = screen.width;
        int height = screen.height;
        
        // Bottom center - Made by Koon
        String credit = "Made by Koon | CustomItemsK";
        int creditWidth = mc.font.width(credit);
        guiGraphics.drawString(mc.font, credit, (width - creditWidth) / 2, height - 20, 0xFFD700, true);
        
        // Top right - Version
        String version = "v1.0.3";
        String versionText = "Version: " + version;
        int versionWidth = mc.font.width(versionText);
        guiGraphics.drawString(mc.font, versionText, width - versionWidth - 10, 10, 0x55FF55, true);
    }
}
