package com.example.mixin.client;

import com.example.ConnectorMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Inject(at = @At("RETURN"), method = "render")
    private void onRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        // Bottom-center: credit
        String credit = "Made by Koon | CustomItemsK";
        int creditWidth = mc.font.width(credit);
        guiGraphics.drawString(mc.font, credit,
                (w - creditWidth) / 2, h - 20, 0xFFD700, true);

        // Top-right: version + update status badge
        String version = FabricLoader.getInstance()
                .getModContainer("customitemsk")
                .map(c -> "v" + c.getMetadata().getVersion().getFriendlyString())
                .orElse("v?");

        String statusLine;
        int barColor;
        if (!ConnectorMod.checkComplete) {
            statusLine = "Checking for updates...";
            barColor = 0xFF888888;
        } else if (ConnectorMod.updateReady) {
            statusLine = "Update " + ConnectorMod.pendingVersion + " ready!";
            barColor = 0xFFFF8800;
        } else {
            statusLine = "Up to date";
            barColor = 0xFF55FF55;
        }

        String modName  = "CustomItemsK " + version;
        int bw = Math.max(mc.font.width(modName), mc.font.width(statusLine)) + 12;
        int bh = 22;
        int bx = w - bw - 6;
        int by = 6;
        // Dark background + colored left accent bar
        guiGraphics.fill(bx, by, bx + bw, by + bh, 0xBB000000);
        guiGraphics.fill(bx, by, bx + 2, by + bh, barColor);
        guiGraphics.drawString(mc.font, modName,  bx + 5, by + 3,  0xFFFFFF, false);
        guiGraphics.drawString(mc.font, statusLine, bx + 5, by + 13, barColor, false);

        // Extra instruction line when update is waiting
        if (ConnectorMod.updateReady) {
            String inst = "Restart Minecraft to apply.";
            int iw = mc.font.width(inst) + 12;
            int iy = by + bh + 2;
            guiGraphics.fill(bx + bw - iw, iy, bx + bw, iy + 12, 0xBB000000);
            guiGraphics.drawString(mc.font, inst, bx + bw - iw + 5, iy + 2, 0xFFAA00, false);
        }
    }
}
