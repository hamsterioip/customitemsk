package com.example;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SentinelsCharmItem extends Item {

    // Tracks per-player hit count for the every-4th-hit ability
    public static final Map<UUID, Integer> HIT_COUNTER = new ConcurrentHashMap<>();

    // 20 seconds cooldown for the defensive proc
    private static final int DEFENSE_COOLDOWN_TICKS = 400;

    public SentinelsCharmItem(Properties props) {
        super(props);
    }

    /**
     * Passive: while held in main hand, check every second if the player is
     * below 50% HP. If so (and not on cooldown), grant Resistance I for 5
     * seconds and trigger a shield-flash particle effect.
     */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (!(entity instanceof Player player)) return;

        // Check once per second
        if (level.getGameTime() % 20 != 0) return;

        float hp    = player.getHealth();
        float maxHp = player.getMaxHealth();

        if (hp <= maxHp * 0.5f && !player.getCooldowns().isOnCooldown(stack)) {
            // Grant Resistance I for 5 seconds (100 ticks)
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 0, false, true));
            player.getCooldowns().addCooldown(stack, DEFENSE_COOLDOWN_TICKS);
            player.displayClientMessage(Component.literal("§b🛡 Sentinel's Guard!"), true);

            // Shield-flash: white/blue enchant particles around the player
            for (int i = 0; i < 16; i++) {
                double ox = (level.random.nextDouble() - 0.5) * 1.2;
                double oy = level.random.nextDouble() * 2.0;
                double oz = (level.random.nextDouble() - 0.5) * 1.2;
                level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                        player.getX() + ox, player.getY() + oy, player.getZ() + oz,
                        1, 0, 0, 0, 0.05);
            }

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8f, 1.2f);
        }
    }

}
