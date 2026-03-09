package com.example;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public class StormBowItem extends BowItem {

    /** UUIDs of arrows fired from this bow. */
    public static final Set<UUID> STORM_ARROWS = Collections.synchronizedSet(new HashSet<>());

    /**
     * Thunder Mark: tracks how many Storm Bow arrows have hit each entity.
     * At 3 marks the entity is struck by a lightning explosion.
     */
    public static final Map<UUID, Integer> THUNDER_MARKS = Collections.synchronizedMap(new HashMap<>());

    public StormBowItem(Properties props) {
        super(props);
    }

    /** Only accept Storm Arrows as ammunition — Storm Bow is useless with regular arrows. */
    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        return stack -> stack.getItem() == ModItems.STORM_ARROW;
    }

    @Override
    public Predicate<ItemStack> getSupportedHeldProjectiles() {
        return stack -> stack.getItem() == ModItems.STORM_ARROW;
    }

    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack, int remainingUseDuration) {
        super.onUseTick(level, livingEntity, stack, remainingUseDuration);
        if (!(level instanceof ServerLevel)) return;
        int ticksUsed = 72000 - remainingUseDuration;
        if (ticksUsed == 5) {
            level.playSound(null, livingEntity.getX(), livingEntity.getY(), livingEntity.getZ(),
                    SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.6f, 1.4f);
        } else if (ticksUsed == 12) {
            level.playSound(null, livingEntity.getX(), livingEntity.getY(), livingEntity.getZ(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7f, 1.2f);
        } else if (ticksUsed == 18) {
            level.playSound(null, livingEntity.getX(), livingEntity.getY(), livingEntity.getZ(),
                    SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeCharged) {
        boolean fired = super.releaseUsing(stack, level, livingEntity, timeCharged);

        if (fired && level instanceof ServerLevel sl && livingEntity instanceof Player player) {
            // Storm whoosh on every shot
            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.6f, 1.8f);

            // Tag the arrow that was just fired
            sl.getEntitiesOfClass(
                    AbstractArrow.class,
                    player.getBoundingBox().inflate(4.0),
                    arrow -> arrow.getOwner() != null
                            && arrow.getOwner().getUUID().equals(player.getUUID())
                            && arrow.tickCount < 5
            ).forEach(arrow -> STORM_ARROWS.add(arrow.getUUID()));

            // Debuff: 1/5 chance — 5 lightning bolts strike around the shooter
            if (sl.random.nextFloat() < 0.2f) {
                for (int i = 0; i < 5; i++) {
                    double angle = (2 * Math.PI / 5) * i;
                    double bx = player.getX() + Math.cos(angle) * 4;
                    double bz = player.getZ() + Math.sin(angle) * 4;
                    LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, sl);
                    bolt.setPos(bx, player.getY(), bz);
                    sl.addFreshEntity(bolt);
                }
                sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.5f, 0.8f);
                player.displayClientMessage(
                        Component.literal("§cThe storm turns against you!"), true);
            }
        }
        return fired;
    }
}
