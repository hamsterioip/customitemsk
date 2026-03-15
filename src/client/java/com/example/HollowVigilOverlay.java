package com.example;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Client-side HUD overlay triggered by HollowStarePacket.
 *
 * Draws directly over the screen using GuiGraphics — completely unaffected
 * by fullbright, Optifine gamma, Sodium brightness, or any light-level mod.
 *
 * Effect: darkness slowly swallows the screen from all four edges, leaving
 * only a shrinking window of visibility in the center.  At peak blackness a
 * pair of featureless void-white eyes blinks open once before everything
 * retreats.  Total duration: ~8 seconds (160 ticks).
 *
 *  Ticks 160 → 80 : darkness creeps inward (build-up)
 *  Ticks  80 → 60 : peak — almost total blackout, eyes appear
 *  Ticks  60 →  0 : slow retreat
 */
@Environment(EnvType.CLIENT)
public class HollowVigilOverlay {

    private static int vigTicks  = 0;
    private static final int TOTAL     = 160;
    private static final int PEAK_HI   = 80;   // darkness starts retreating
    private static final int PEAK_LO   = 60;   // eyes fade
    private static final RandomSource RNG = RandomSource.create();

    /** Called when HollowStarePacket is received. */
    public static void trigger() {
        vigTicks = TOTAL;
        playSounds();
    }

    public static void tick() {
        if (vigTicks > 0) vigTicks--;
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        if (vigTicks <= 0) return;

        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        // ── compute vignette progress (0 = start/end, 1 = peak) ──────────────
        float progress;
        if (vigTicks > PEAK_HI) {
            // Building up: ticks go from TOTAL down to PEAK_HI
            progress = (TOTAL - vigTicks) / (float)(TOTAL - PEAK_HI);
        } else {
            // Retreating: ticks go from PEAK_HI down to 0
            progress = vigTicks / (float) PEAK_HI;
        }
        // ease-in-out for smoother feel
        progress = Mth.clamp(progress, 0f, 1f);
        float eased = progress * progress * (3f - 2f * progress);

        // ── vignette border size (fraction of screen covered per edge) ────────
        // At peak eased=1: covers 48% of each side → 4% of screen visible
        float edgeFraction = eased * 0.48f;
        int vLeft   = (int)(w * edgeFraction);
        int vRight  = w - vLeft;
        int vTop    = (int)(h * edgeFraction);
        int vBottom = h - vTop;

        int darkAlpha = clamp((int)(eased * 230));
        int darkColor = (darkAlpha << 24) | 0x00000000;

        // Four solid black panels closing in from edges
        g.fill(0,      0,      w,      vTop,    darkColor); // top
        g.fill(0,      vBottom, w,      h,       darkColor); // bottom
        g.fill(0,      0,      vLeft,  h,       darkColor); // left
        g.fill(vRight, 0,      w,      h,       darkColor); // right

        // Soft inner edge — blend the border with a semi-transparent strip
        int softAlpha = clamp((int)(eased * 120));
        int softColor = (softAlpha << 24) | 0x00000000;
        int softW = Math.max(1, (int)(w * 0.06f));
        int softH = Math.max(1, (int)(h * 0.06f));
        g.fill(vLeft,         vTop,          vLeft  + softW, vBottom,        softColor);
        g.fill(vRight - softW, vTop,          vRight,         vBottom,        softColor);
        g.fill(vLeft,         vTop,          vRight,         vTop   + softH, softColor);
        g.fill(vLeft,         vBottom - softH, vRight,        vBottom,        softColor);

        // ── eyes: appear only at peak (ticks 80 → 60) ────────────────────────
        if (vigTicks <= PEAK_HI && vigTicks >= PEAK_LO) {
            float eyeT = (vigTicks - PEAK_LO) / (float)(PEAK_HI - PEAK_LO);
            // blink-in then blink-out:  0 → open → 1 → close
            float blink = 1f - Math.abs(eyeT * 2f - 1f);
            int eyeAlpha = clamp((int)(blink * 200));

            int eyeW   = w / 18;
            int eyeH   = h / 22;
            int eyeGap = w / 9;
            int eyeCX  = w / 2;
            int eyeY   = (int)(h * 0.42f);

            // Whites of the eyes — stark white against total darkness
            int white = (eyeAlpha << 24) | 0x00EEEEEE;
            g.fill(eyeCX - eyeGap - eyeW, eyeY,        eyeCX - eyeGap + eyeW, eyeY + eyeH, white);
            g.fill(eyeCX + eyeGap - eyeW, eyeY,        eyeCX + eyeGap + eyeW, eyeY + eyeH, white);

            // Void-black irises (empty, featureless)
            int irisAlpha = clamp((int)(blink * 255));
            int irisW = eyeW / 2;
            int irisH = (int)(eyeH * 0.75f);
            int irisY = eyeY + (eyeH - irisH) / 2;
            int black = (irisAlpha << 24) | 0x00000000;
            g.fill(eyeCX - eyeGap - irisW, irisY, eyeCX - eyeGap + irisW, irisY + irisH, black);
            g.fill(eyeCX + eyeGap - irisW, irisY, eyeCX + eyeGap + irisW, irisY + irisH, black);
        }

        // ── subtitle text at peak ─────────────────────────────────────────────
        if (vigTicks <= PEAK_HI && vigTicks > PEAK_LO + 5) {
            float textAlpha = Mth.clamp((PEAK_HI - vigTicks) / 20f, 0f, 1f);
            int ta = clamp((int)(textAlpha * 180));
            // Draw "it has found you" centered at bottom-third of screen
            // We can't draw real text easily here without font metrics, so just
            // leave a subtle subtitle — skip if too complex for now
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static void playSounds() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Deep, oppressive resonance — nothing loud, just wrong
        mc.getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.4f, 0.15f));
        mc.getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.WARDEN_AMBIENT, 0.5f, 0.3f));
        mc.getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.SCULK_BLOCK_CHARGE, 0.3f, 0.2f));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
