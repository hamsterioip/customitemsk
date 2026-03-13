package com.example;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Client-side HUD overlay triggered by WatcherJumpscarePacket.
 *
 * Phase 1 (first 8 ticks): violent flash with screen shake.
 * Phase 2 (next 12 ticks): inverted/negative colors with glitch lines.
 * Phase 3 (remaining 100 ticks): dark screen fade-out with terrifying eye pattern.
 */
@Environment(EnvType.CLIENT)
public class WatcherJumpscareOverlay {

    private static int overlayTicks = 0;
    private static final int TOTAL_TICKS = 120;
    private static final int FLASH_TICKS  = 8;
    private static final int GLITCH_TICKS = 20;
    private static final RandomSource RANDOM = RandomSource.create();
    
    private static float shakeX = 0;
    private static float shakeY = 0;

    /** Triggered when the jumpscare packet is received. */
    public static void trigger() {
        overlayTicks = TOTAL_TICKS;
        playJumpscareSounds();
    }
    
    /** Plays a layered terrifying sound sequence */
    private static void playJumpscareSounds() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        // Immediate piercing scream - cuts through everything
        mc.getSoundManager().play(
            SimpleSoundInstance.forUI(SoundEvents.ELDER_GUARDIAN_CURSE, 0.6f, 1.5f)
        );
        
        // Deep warden sonic boom - adds bass and dread
        mc.getSoundManager().play(
            SimpleSoundInstance.forUI(SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 0.7f)
        );
        
        // Sculk shriek - unsettling high frequency
        mc.getSoundManager().play(
            SimpleSoundInstance.forUI(SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.7f, 0.8f)
        );
        
        // Phantom bite for the "snap" effect
        mc.getSoundManager().play(
            SimpleSoundInstance.forUI(SoundEvents.PHANTOM_BITE, 0.9f, 0.6f)
        );
    }

    /** Counted down once per client tick. */
    public static void tick() {
        if (overlayTicks > 0) {
            overlayTicks--;
            
            // Calculate screen shake during flash phase
            if (overlayTicks > TOTAL_TICKS - FLASH_TICKS) {
                shakeX = (RANDOM.nextFloat() - 0.5f) * 12f;
                shakeY = (RANDOM.nextFloat() - 0.5f) * 12f;
            } else if (overlayTicks > TOTAL_TICKS - GLITCH_TICKS) {
                // Subtle shake during glitch phase
                shakeX = (RANDOM.nextFloat() - 0.5f) * 4f;
                shakeY = (RANDOM.nextFloat() - 0.5f) * 4f;
            } else {
                shakeX = 0;
                shakeY = 0;
            }
        }
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (overlayTicks <= 0) return;

        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        
        // Apply screen shake offset
        int offsetX = (int) shakeX;
        int offsetY = (int) shakeY;

        if (overlayTicks > TOTAL_TICKS - FLASH_TICKS) {
            // --- Phase 1: VIOLENT white flash with blood-red edges ---
            float t = (TOTAL_TICKS - overlayTicks) / (float) FLASH_TICKS;
            
            // Center is blinding white
            int whiteA = clamp((int)(t * 255));
            graphics.fill(0, 0, w, h, (whiteA << 24) | 0x00FFFFFF);
            
            // Blood-red vignette bleeding in from edges
            int bloodA = clamp((int)(t * 180));
            int vW = w / 3;
            int vH = h / 3;
            int bloodColor = (bloodA << 24) | 0x00AA0000;
            graphics.fill(0, 0, w, vH, bloodColor);
            graphics.fill(0, h - vH, w, h, bloodColor);
            graphics.fill(0, 0, vW, h, bloodColor);
            graphics.fill(w - vW, 0, w, h, bloodColor);
            
        } else if (overlayTicks > TOTAL_TICKS - GLITCH_TICKS) {
            // --- Phase 2: INVERTED colors with glitch lines ---
            float t = (overlayTicks - (TOTAL_TICKS - GLITCH_TICKS)) / (float)(GLITCH_TICKS - FLASH_TICKS);
            
            // Dark inverted background (blue-tinted for "negative" effect)
            int bgA = clamp((int)(180 + t * 50));
            graphics.fill(0, 0, w, h, (bgA << 24) | 0x00001133);
            
            // Horizontal glitch lines
            int glitchLines = 5;
            for (int i = 0; i < glitchLines; i++) {
                int lineY = (int)((h / (float)glitchLines) * i + (RANDOM.nextFloat() * 20 - 10));
                int lineA = clamp((int)(RANDOM.nextFloat() * 100 + 50));
                int lineColor = (lineA << 24) | (RANDOM.nextBoolean() ? 0x00FF0000 : 0x00FFFFFF);
                graphics.fill(0, lineY, w, lineY + 2 + RANDOM.nextInt(4), lineColor);
            }
            
            // Faint eyes appearing through the glitch
            int eyeA = clamp((int)(t * 150));
            int eyeColor = (eyeA << 24) | 0x00FF0000;
            int eyeSize = Math.min(w, h) / 8;
            int eyeGap = eyeSize;
            int eyeY = (int)(h * 0.4f);
            int eyeCx = w / 2;
            
            graphics.fill(eyeCx - eyeGap - eyeSize + offsetX, eyeY + offsetY,
                         eyeCx - eyeGap + offsetX, eyeY + eyeSize + offsetY, eyeColor);
            graphics.fill(eyeCx + eyeGap + offsetX, eyeY + offsetY,
                         eyeCx + eyeGap + eyeSize + offsetX, eyeY + eyeSize + offsetY, eyeColor);
            
        } else {
            // --- Phase 3: Dark fade-out with TERRIFYING eyes ---
            float t = overlayTicks / (float)(TOTAL_TICKS - GLITCH_TICKS);

            // Near-black background
            int bgA = clamp((int)(t * 245));
            graphics.fill(0, 0, w, h, (bgA << 24) | 0x00000000);

            // Deep crimson vignette — blood closing in
            int redA = clamp((int)(t * 160));
            int vW = w / 4;
            int vH = h / 4;
            int redColor = (redA << 24) | 0x00990000;
            graphics.fill(0, 0, w, vH, redColor);
            graphics.fill(0, h - vH, w, h, redColor);
            graphics.fill(0, 0, vW, h, redColor);
            graphics.fill(w - vW, 0, w, h, redColor);

            // TERRIFYING EYES - large, glowing, with vertical slit pupils
            int eyeSize = Math.min(w, h) / 6;
            int eyeGap = (int)(eyeSize * 1.2f);
            int eyeY = (int)(h * 0.35f);
            int eyeCx = w / 2;
            int eyeA = clamp((int)(t * 255));
            
            // Outer eye glow (larger, pulsating)
            int glowSize = eyeSize + 8;
            int glowA = clamp((int)(t * 120));
            int glowColor = (glowA << 24) | 0x00FF0000;
            graphics.fill(eyeCx - eyeGap - glowSize + offsetX, eyeY - 4 + offsetY,
                         eyeCx - eyeGap + glowSize + offsetX, eyeY + eyeSize + 4 + offsetY, glowColor);
            graphics.fill(eyeCx + eyeGap - glowSize + offsetX, eyeY - 4 + offsetY,
                         eyeCx + eyeGap + glowSize + offsetX, eyeY + eyeSize + 4 + offsetY, glowColor);
            
            // Main eye sclera (bright red)
            int scleraColor = (eyeA << 24) | 0x00CC0000;
            graphics.fill(eyeCx - eyeGap - eyeSize + offsetX, eyeY + offsetY,
                         eyeCx - eyeGap + offsetX, eyeY + eyeSize + offsetY, scleraColor);
            graphics.fill(eyeCx + eyeGap + offsetX, eyeY + offsetY,
                         eyeCx + eyeGap + eyeSize + offsetX, eyeY + eyeSize + offsetY, scleraColor);
            
            // Vertical slit pupils (black, terrifying)
            int pupilW = eyeSize / 5;
            int pupilH = (int)(eyeSize * 0.7f);
            int pupilY = eyeY + (eyeSize - pupilH) / 2;
            int pupilColor = (eyeA << 24) | 0x00000000;
            graphics.fill(eyeCx - eyeGap - pupilW/2 + offsetX, pupilY + offsetY,
                         eyeCx - eyeGap + pupilW/2 + offsetX, pupilY + pupilH + offsetY, pupilColor);
            graphics.fill(eyeCx + eyeGap - pupilW/2 + offsetX, pupilY + offsetY,
                         eyeCx + eyeGap + pupilW/2 + offsetX, pupilY + pupilH + offsetY, pupilColor);
            
            // Small white reflection dots (gives life to the eyes)
            int reflectSize = pupilW;
            int reflectY = pupilY + 2;
            int reflectColor = (eyeA << 24) | 0x00FFFFFF;
            graphics.fill(eyeCx - eyeGap - pupilW/2 - reflectSize + offsetX, reflectY + offsetY,
                         eyeCx - eyeGap - pupilW/2 + offsetX, reflectY + reflectSize + offsetY, reflectColor);
            graphics.fill(eyeCx + eyeGap - pupilW/2 - reflectSize + offsetX, reflectY + offsetY,
                         eyeCx + eyeGap - pupilW/2 + offsetX, reflectY + reflectSize + offsetY, reflectColor);
        }
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
