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
 * Adds a credit text to the Minecraft title screen
 * Displays "Made by Koon" at the bottom of the main menu
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void renderCredit(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        TitleScreen screen = (TitleScreen)(Object)this;
        var font = Minecraft.getInstance().font;
        int screenWidth = screen.width;
        int screenHeight = screen.height;
        
        // Credit text with colors
        Component creditText = Component.literal("§e§lMade by §c§lKoon");
        Component modName = Component.literal("§7| §b§lCustomItemsK");
        
        // Calculate positions (bottom center)
        int creditWidth = font.width(creditText);
        int modNameWidth = font.width(modName);
        int totalWidth = creditWidth + modNameWidth;
        
        int x = (screenWidth - totalWidth) / 2;
        int y = screenHeight - 15; // 15 pixels from bottom
        
        // Draw credit text with shadow
        guiGraphics.drawString(font, creditText, x, y, 0xFFFFFF, true);
        guiGraphics.drawString(font, modName, x + creditWidth, y, 0xFFFFFF, true);
    }
}
