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

    @Inject(method = "render", at = @At("TAIL"))
    private void renderCustomText(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        try {
            TitleScreen screen = (TitleScreen)(Object)this;
            var font = Minecraft.getInstance().font;
            int screenWidth = screen.width;
            int screenHeight = screen.height;
            
            // === BOTTOM CENTER - Made by Koon ===
            Component creditText = Component.literal("§e§lMade by §c§lKoon");
            Component modName = Component.literal("§7| §b§lCustomItemsK");
            
            int creditWidth = font.width(creditText);
            int modNameWidth = font.width(modName);
            int totalWidth = creditWidth + modNameWidth;
            
            int bottomX = (screenWidth - totalWidth) / 2;
            int bottomY = screenHeight - 15;
            
            guiGraphics.drawString(font, creditText, bottomX, bottomY, 0xFFFFFF, true);
            guiGraphics.drawString(font, modName, bottomX + creditWidth, bottomY, 0xFFFFFF, true);
            
            // === TOP RIGHT - Version ===
            // Get version from mod metadata
            String version = "Unknown";
            try {
                version = Minecraft.getInstance().getModList().getMod("customitemsk")
                    .getVersion().toString();
            } catch (Exception e) {
                version = "v1.0.2"; // Fallback version
            }
            
            Component versionText = Component.literal("§7Version: §a§l" + version);
            int versionWidth = font.width(versionText);
            int versionX = screenWidth - versionWidth - 5; // 5 pixels from right edge
            int versionY = 5; // 5 pixels from top
            
            guiGraphics.drawString(font, versionText, versionX, versionY, 0xFFFFFF, true);
            
        } catch (Exception e) {
            // Silently fail if something goes wrong
        }
    }
}
