package com.example;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BerserkersFangItem extends Item {

    // Per-player hit counter for the every-3rd-hit ability
    public static final Map<UUID, Integer> HIT_COUNTER = new ConcurrentHashMap<>();

    public BerserkersFangItem(Properties props) {
        super(props);
    }

    /**
     * Every 3rd hit: deal +40% bonus damage and spawn a red shockwave effect.
     */
    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(attacker instanceof Player player)) {
            super.postHurtEnemy(stack, target, attacker);
            return;
        }

        int count = HIT_COUNTER.merge(player.getUUID(), 1, Integer::sum);

        if (count >= 3) {
            HIT_COUNTER.put(player.getUUID(), 0);

            float baseDamage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            float bonus = baseDamage * 0.40f;

            if (bonus > 0) {
                target.hurt(target.damageSources().playerAttack(player), bonus);
            }

            if (target.level() instanceof ServerLevel sl) {
                // Red crit particles burst
                sl.sendParticles(ParticleTypes.CRIT,
                        target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                        20, 0.5, 0.7, 0.5, 0.5);

                // Shockwave ring — sweep attack particles expanding outward
                for (int i = 0; i < 12; i++) {
                    double angle = (2 * Math.PI / 12) * i;
                    double ox = Math.cos(angle) * 1.2;
                    double oz = Math.sin(angle) * 1.2;
                    sl.sendParticles(ParticleTypes.SWEEP_ATTACK,
                            target.getX() + ox, target.getY() + 0.5, target.getZ() + oz,
                            1, 0, 0, 0, 0.01);
                }

                sl.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.8f);
            }

            player.displayClientMessage(Component.literal("§c🔥 Berserker Strike!"), true);
        }

        super.postHurtEnemy(stack, target, attacker);
    }
}
