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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tempest Crown - The Ultimate Relic
 * 
 * A combination of storm, tempest, and divine power. When held, it enhances
 * the Tempest Reaver and grants god-like abilities.
 * 
 * Requires the player to wield the Tempest Reaver for full power.
 */
public class TempestCrownItem extends Item {

    // Track ultimate cooldowns per player
    private static final Map<UUID, Long> ULTIMATE_COOLDOWNS = new HashMap<>();
    private static final int ULTIMATE_COOLDOWN_TICKS = 12000; // 10 minutes
    
    // Track storm mode status
    private static final Map<UUID, Boolean> STORM_MODE = new HashMap<>();
    private static final Map<UUID, Integer> STORM_MODE_TICKS = new HashMap<>();
    private static final int STORM_MODE_DURATION = 600; // 30 seconds

    public TempestCrownItem(Properties props) {
        super(props);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (!(entity instanceof ServerPlayer player)) return;

        UUID uuid = player.getUUID();
        long currentTime = level.getGameTime();

        // Check if player has Tempest Reaver anywhere in inventory
        boolean hasTempestReaver = false;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).getItem() == ModItems.TEMPEST_REAVER) { hasTempestReaver = true; break; }
        }
        
        // === PASSIVE ABILITIES (Always Active When Held) ===
        
        // 1. Permanent buffs while held
        if (currentTime % 100 == 0) { // Refresh every 5 seconds
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 120, 1, false, false)); // Speed II
            player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 120, 1, false, false)); // Jump Boost II
            
            if (hasTempestReaver) {
                // Enhanced buffs when combined with Tempest Reaver
                player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 120, 2, false, false)); // Strength III
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 120, 1, false, false)); // Resistance II
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 1, false, false)); // Regen II
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 120, 0, false, false)); // Fire Res
                player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 120, 0, false, false)); // Water Breathing
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, false, false)); // Night Vision
            }
        }
        
        // 2. Lightning aura - strike nearby enemies every 2 seconds
        if (currentTime % 40 == 0 && hasTempestReaver) {
            List<LivingEntity> nearbyEnemies = level.getEntitiesOfClass(
                    LivingEntity.class,
                    player.getBoundingBox().inflate(8.0),
                    e -> e != player && !(e instanceof Player));
            
            if (!nearbyEnemies.isEmpty()) {
                LivingEntity target = nearbyEnemies.get(level.getRandom().nextInt(nearbyEnemies.size()));
                
                // Summon lightning
                net.minecraft.world.entity.LightningBolt lightning = new net.minecraft.world.entity.LightningBolt(
                        net.minecraft.world.entity.EntityType.LIGHTNING_BOLT, level);
                lightning.setPos(target.getX(), target.getY(), target.getZ());
                lightning.setCause(player);
                level.addFreshEntity(lightning);
                
                // Chain lightning to nearby enemies
                for (int i = 0; i < Math.min(3, nearbyEnemies.size()); i++) {
                    LivingEntity chainTarget = nearbyEnemies.get(i);
                    chainTarget.hurt(level.damageSources().lightningBolt(), 4.0f);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                            chainTarget.getX(), chainTarget.getY() + 1, chainTarget.getZ(),
                            10, 0.3, 0.5, 0.3, 0.1);
                }
            }
        }
        
        // 3. Storm Mode - Activates when health drops below 25%
        boolean isInStormMode = STORM_MODE.getOrDefault(uuid, false);
        if (!isInStormMode && player.getHealth() < player.getMaxHealth() * 0.25 && hasTempestReaver) {
            activateStormMode(player, level);
        }
        
        // Handle Storm Mode ticking
        if (isInStormMode) {
            int remainingTicks = STORM_MODE_TICKS.getOrDefault(uuid, 0);
            if (remainingTicks > 0) {
                STORM_MODE_TICKS.put(uuid, remainingTicks - 1);
                
                // Storm mode effects every tick
                if (currentTime % 5 == 0) {
                    // Random lightning strikes around player
                    double angle = level.getRandom().nextDouble() * Math.PI * 2;
                    double dist = 3 + level.getRandom().nextDouble() * 5;
                    int lx = (int) (player.getX() + Math.cos(angle) * dist);
                    int lz = (int) (player.getZ() + Math.sin(angle) * dist);
                    
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                            lx, player.getY() + 0.5, lz, 5, 0.5, 0.5, 0.5, 0.2);
                }
                
                // Healing during storm mode
                if (currentTime % 20 == 0) {
                    player.heal(1.0f);
                }
            } else {
                // Storm mode ended
                STORM_MODE.put(uuid, false);
                player.displayClientMessage(Component.literal("§b§l⚡ Storm Mode Deactivated ⚡"), true);
            }
        }
        
        // 4. Wind Step - Double jump ability (sneak + jump)
        // This is handled by the client-side mixin or keybind, but we apply effects here
        if (player.isFallFlying() || player.getDeltaMovement().y > 0.4) {
            // Apply slow falling when ascending rapidly
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false));
        }
    }

    private void activateStormMode(ServerPlayer player, ServerLevel level) {
        UUID uuid = player.getUUID();
        STORM_MODE.put(uuid, true);
        STORM_MODE_TICKS.put(uuid, STORM_MODE_DURATION);
        
        // Full heal
        player.setHealth(player.getMaxHealth());
        
        // Absorption hearts
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, STORM_MODE_DURATION, 2, false, false)); // 6 bonus hearts
        
        // Invulnerability for 3 seconds
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, false, false));
        
        // Massive lightning strike at player location
        for (int i = 0; i < 8; i++) {
            double angle = (Math.PI * 2 / 8) * i;
            double dist = 2.0;
            double lx = player.getX() + Math.cos(angle) * dist;
            double lz = player.getZ() + Math.sin(angle) * dist;
            
            net.minecraft.world.entity.LightningBolt lightning = new net.minecraft.world.entity.LightningBolt(
                    net.minecraft.world.entity.EntityType.LIGHTNING_BOLT, level);
            if (lightning != null) {
                lightning.setPos(lx, player.getY(), lz);
                lightning.setCause(player);
                level.addFreshEntity(lightning);
            }
        }
        
        // Epic particles
        for (int i = 0; i < 100; i++) {
            double ox = (level.getRandom().nextDouble() - 0.5) * 10;
            double oy = level.getRandom().nextDouble() * 5;
            double oz = (level.getRandom().nextDouble() - 0.5) * 10;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    player.getX() + ox, player.getY() + oy, player.getZ() + oz,
                    1, 0, 0, 0, 0.5);
        }
        
        // Sound effects
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.0f, 0.5f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1.0f, 1.5f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 1.0f, 0.8f);
        
        // Title message
        player.displayClientMessage(Component.literal("\n§b§l⚡⚡⚡ STORM MODE ACTIVATED ⚡⚡⚡\n§eYou are the eye of the tempest!"), false);
        
        // Broadcast to nearby players
        level.getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(50.0))
                .forEach(nearby -> {
                    if (nearby != player) {
                        nearby.displayClientMessage(
                                Component.literal("§b§l⚡ " + player.getName().getString() + " has awakened the storm! ⚡"), 
                                true);
                    }
                });
    }

    /**
     * Called when the player attacks an entity while holding the crown
     */
    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(attacker instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        
        // Check for Tempest Reaver anywhere in inventory
        boolean hasTempestReaver = false;
        var inv2 = player.getInventory();
        for (int i = 0; i < inv2.getContainerSize(); i++) {
            if (inv2.getItem(i).getItem() == ModItems.TEMPEST_REAVER) { hasTempestReaver = true; break; }
        }
        if (!hasTempestReaver) return;
        
        // Thunder Strike - every hit has a chance to call lightning
        if (level.getRandom().nextInt(3) == 0) { // 33% chance
            net.minecraft.world.entity.LightningBolt lightning = new net.minecraft.world.entity.LightningBolt(
                    net.minecraft.world.entity.EntityType.LIGHTNING_BOLT, level);
            if (lightning != null) {
                lightning.setPos(target.getX(), target.getY(), target.getZ());
                lightning.setCause(player);
                level.addFreshEntity(lightning);
            }
            
            // Apply weakness to target
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1, false, false));
        }
        
        // Knockback bonus
        Vec3 knockback = target.position().subtract(player.position()).normalize().scale(1.5);
        target.setDeltaMovement(knockback.x, 0.5, knockback.z);
        target.hurtMarked = true;
        
        // Chain lightning to nearby enemies
        List<LivingEntity> nearby = level.getEntitiesOfClass(
                LivingEntity.class,
                target.getBoundingBox().inflate(4.0),
                e -> e != player && e != target && !(e instanceof Player));
        
        for (LivingEntity chainTarget : nearby.subList(0, Math.min(2, nearby.size()))) {
            chainTarget.hurt(level.damageSources().playerAttack(player), 3.0f);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    chainTarget.getX(), chainTarget.getY() + 1, chainTarget.getZ(),
                    8, 0.2, 0.3, 0.2, 0.1);
        }
    }

    /**
     * Activates the Ultimate Ability - Tempest Requiem
     * Call this from a keybind or when specific conditions are met
     */
    public static void activateUltimate(ServerPlayer player, ServerLevel level) {
        UUID uuid = player.getUUID();
        long currentTime = level.getGameTime();
        
        // Check cooldown
        Long lastUsed = ULTIMATE_COOLDOWNS.get(uuid);
        if (lastUsed != null && currentTime - lastUsed < ULTIMATE_COOLDOWN_TICKS) {
            int remainingSeconds = (int) ((ULTIMATE_COOLDOWN_TICKS - (currentTime - lastUsed)) / 20);
            player.displayClientMessage(
                    Component.literal("§cTempest Requiem on cooldown: " + remainingSeconds + "s"), true);
            return;
        }
        
        // Set cooldown
        ULTIMATE_COOLDOWNS.put(uuid, currentTime);
        
        // === TEMPEST REQUIEM - THE ULTIMATE ===
        
        // 1. Massive AOE damage to all enemies in 20 block radius
        List<LivingEntity> allEnemies = level.getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(20.0),
                e -> e != player && !(e instanceof Player));
        
        for (LivingEntity enemy : allEnemies) {
            // Massive damage
            enemy.hurt(level.damageSources().playerAttack(player), 50.0f);
            
            // Apply all debuffs
            enemy.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, 2, false, false));
            enemy.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 400, 2, false, false));
            enemy.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200, 0, false, false));
            
            // Lightning on each enemy
            net.minecraft.world.entity.LightningBolt lightning = new net.minecraft.world.entity.LightningBolt(
                    net.minecraft.world.entity.EntityType.LIGHTNING_BOLT, level);
            if (lightning != null) {
                lightning.setPos(enemy.getX(), enemy.getY(), enemy.getZ());
                lightning.setCause(player);
                level.addFreshEntity(lightning);
            }
        }
        
        // 2. Full heal and god buffs for player
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20);
        
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 1200, 4, false, false)); // 10 hearts
        player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 600, 3, false, false)); // Strength IV
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 600, 3, false, false)); // Resistance IV
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 400, 2, false, false)); // Regen III
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, 600, 3, false, false)); // Speed IV
        
        // 3. Apocalyptic visual effects
        // Ring of lightning at increasing distances
        for (int ring = 1; ring <= 5; ring++) {
            double radius = ring * 4.0;
            for (int i = 0; i < 12; i++) {
                double angle = (Math.PI * 2 / 12) * i;
                double lx = player.getX() + Math.cos(angle) * radius;
                double lz = player.getZ() + Math.sin(angle) * radius;
                
                net.minecraft.world.entity.LightningBolt lightning = new net.minecraft.world.entity.LightningBolt(
                    net.minecraft.world.entity.EntityType.LIGHTNING_BOLT, level);
                if (lightning != null) {
                    lightning.setPos(lx, player.getY(), lz);
                    lightning.setCause(player);
                    level.addFreshEntity(lightning);
                }
            }
        }
        
        // Massive particle explosion
        for (int i = 0; i < 500; i++) {
            double ox = (level.getRandom().nextDouble() - 0.5) * 40;
            double oy = level.getRandom().nextDouble() * 20;
            double oz = (level.getRandom().nextDouble() - 0.5) * 40;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    player.getX() + ox, player.getY() + oy, player.getZ() + oz,
                    1, 0, 0, 0, 0.5);
        }
        
        // Sonic boom effect
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SONIC_BOOM,
                player.getX(), player.getY() + 1, player.getZ(),
                50, 10, 5, 10, 0.1);
        
        // 4. Epic sound combo
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3.0f, 0.4f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 2.0f, 1.8f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2.0f, 0.6f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 1.5f, 0.5f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.END_GATEWAY_SPAWN, SoundSource.PLAYERS, 2.0f, 0.8f);
        
        // 5. Server-wide broadcast
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("\n§b§l⚡⚡⚡ " + player.getName().getString() + " HAS UNLEASHED THE TEMPEST REQUIEM! ⚡⚡⚡\n" +
                        "§eThe skies themselves bow to their will!"),
                false);
        
        // Personal message
        player.displayClientMessage(
                Component.literal("\n§b§l🌪 TEMPEST REQUIEM ACTIVATED 🌪\n" +
                        "§eYou are the storm incarnate!"),
                false);
    }

    public static boolean isInStormMode(UUID playerUUID) {
        return STORM_MODE.getOrDefault(playerUUID, false);
    }
    
    public static int getStormModeTicksRemaining(UUID playerUUID) {
        return STORM_MODE_TICKS.getOrDefault(playerUUID, 0);
    }
    
    public static int getUltimateCooldownSeconds(UUID playerUUID, ServerLevel level) {
        Long lastUsed = ULTIMATE_COOLDOWNS.get(playerUUID);
        if (lastUsed == null) return 0;
        long remaining = ULTIMATE_COOLDOWN_TICKS - (level.getGameTime() - lastUsed);
        return remaining > 0 ? (int) (remaining / 20) : 0;
    }
}
