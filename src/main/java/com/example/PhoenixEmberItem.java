package com.example;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Phoenix Ember - A defensive totem-like item that saves the player from death.
 * 
 * When the player would die while holding this in their main/off hand:
 * - Cancels the death
 * - Restores to half health
 * - Grants Resistance III, Fire Resistance, and Regeneration II for 10 seconds
 * - Consumes the ember
 * - Has a 5-minute cooldown per player
 */
public class PhoenixEmberItem extends Item {

    // Track cooldowns per player (UUID -> last used game time)
    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();
    private static final int COOLDOWN_TICKS = 6000; // 5 minutes (6000 ticks)

    public PhoenixEmberItem(Properties props) {
        super(props);
    }

    /**
     * Called every tick when the item is in the player's inventory.
     * Activates when the player would die.
     */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        // Only tick for players in valid slots (main hand or off hand)
        if (!(entity instanceof ServerPlayer player)) return;
        if (slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND) return;

        ServerLevel sl = level;
        
        // Check if player would die (health <= 0 but not already dead)
        if (player.getHealth() <= 0 && !player.isDeadOrDying()) {
            // Check cooldown
            long currentTime = sl.getGameTime();
            Long lastUsed = COOLDOWNS.get(player.getUUID());
            
            if (lastUsed != null && currentTime - lastUsed < COOLDOWN_TICKS) {
                // On cooldown - let the player die
                return;
            }

            // ACTIVATE PHOENIX EMBER!
            activatePhoenixEmber(player, sl, stack);
        }
    }

    private void activatePhoenixEmber(ServerPlayer player, ServerLevel sl, ItemStack stack) {
        // Record cooldown
        COOLDOWNS.put(player.getUUID(), sl.getGameTime());

        // Cancel death by restoring health
        player.setHealth(player.getMaxHealth() * 0.5f); // 50% health

        // Clear fire if burning
        player.clearFire();

        // Remove negative effects
        player.removeEffect(MobEffects.WITHER);
        player.removeEffect(MobEffects.POISON);
        player.removeEffect(MobEffects.WEAKNESS);
        player.removeEffect(MobEffects.SLOWNESS);
        player.removeEffect(MobEffects.BLINDNESS);

        // Apply powerful buffs for 10 seconds (200 ticks)
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 200, 2, false, true)); // Resistance III
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0, false, true)); // Fire Resistance
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1, false, true)); // Regeneration II
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1, false, true)); // Absorption II (4 bonus hearts)
        player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 100, 0, false, true)); // Strength I for 5 seconds

        // Consume the ember
        stack.shrink(1);

        // PHOENIX RISE EFFECT!
        // Ring of fire particles expanding outward
        for (int ring = 0; ring < 5; ring++) {
            double radius = 1.0 + ring * 0.8;
            for (int i = 0; i < 20; i++) {
                double angle = (2 * Math.PI / 20) * i;
                double ox = Math.cos(angle) * radius;
                double oz = Math.sin(angle) * radius;
                sl.sendParticles(ParticleTypes.FLAME,
                        player.getX() + ox, player.getY() + 0.5, player.getZ() + oz,
                        1, 0, 0.1, 0, 0.02);
            }
        }

        // Phoenix wings effect (soul fire particles)
        for (int i = 0; i < 50; i++) {
            double ox = (sl.getRandom().nextDouble() - 0.5) * 3;
            double oy = sl.getRandom().nextDouble() * 2.5;
            double oz = (sl.getRandom().nextDouble() - 0.5) * 3;
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    player.getX() + ox, player.getY() + oy, player.getZ() + oz,
                    1, 0, 0.05, 0, 0.01);
        }

        // Large smoke burst
        sl.sendParticles(ParticleTypes.LARGE_SMOKE,
                player.getX(), player.getY() + 1, player.getZ(), 30, 0.5, 0.5, 0.5, 0.1);

        // Heart particles
        sl.sendParticles(ParticleTypes.HEART,
                player.getX(), player.getY() + 1, player.getZ(), 10, 0.5, 0.5, 0.5, 0.1);

        // TERRIFYING SOUND COMBO
        // Wither spawn sound (deep bass)
        sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.6f, 0.5f);
        
        // Totem activation sound
        sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
        
        // Blaze shoot (fire sound)
        sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.8f, 0.6f);
        
        // Phoenix screech (phantom sound pitched up)
        sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PHANTOM_AMBIENT, SoundSource.PLAYERS, 1.2f, 1.5f);

        // Broadcast to all nearby players
        sl.getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(30.0))
                .forEach(nearbyPlayer -> {
                    nearbyPlayer.displayClientMessage(
                            Component.literal("§6§l✦ " + player.getName().getString() + " §ehas been reborn by the Phoenix! §6§l✦"),
                            true);
                });

        // Personal message to the saved player
        player.displayClientMessage(
                Component.literal("§6§l🔥 PHOENIX REBIRTH! 🔥\n§eYou have been saved from death!"),
                false);
    }



    /**
     * Check if a player has an active cooldown on their Phoenix Ember
     */
    public static boolean isOnCooldown(ServerPlayer player, ServerLevel level) {
        Long lastUsed = COOLDOWNS.get(player.getUUID());
        if (lastUsed == null) return false;
        return level.getGameTime() - lastUsed < COOLDOWN_TICKS;
    }

    /**
     * Get remaining cooldown in seconds for a player
     */
    public static int getCooldownSeconds(ServerPlayer player, ServerLevel level) {
        Long lastUsed = COOLDOWNS.get(player.getUUID());
        if (lastUsed == null) return 0;
        long remainingTicks = COOLDOWN_TICKS - (level.getGameTime() - lastUsed);
        return remainingTicks > 0 ? (int) (remainingTicks / 20) : 0;
    }
}
