package com.example;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class CataclysmItem extends Item {

    public CataclysmItem(Properties props) {
        super(props);
    }

    /** Start charging on right-click. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.getCooldowns().isOnCooldown(stack)) {
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.FAIL;
    }

    /** Max use duration — never naturally expires (player releases right-click). */
    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    /** Use the BOW animation so the player visibly charges. */
    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW;
    }

    /**
     * Called every tick while the player holds right-click.
     * Plays escalating mace sounds as the charge builds up through each stage.
     */
    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack, int remainingUseDuration) {
        if (!(level instanceof ServerLevel sl) || !(livingEntity instanceof Player player)) return;

        int ticksUsed = getUseDuration(stack, livingEntity) - remainingUseDuration;

        // Stage transition cues
        if (ticksUsed == 5) {
            // Entering stage 1 — light mace swing wind-up
            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS, 0.5f, 1.6f);
        } else if (ticksUsed == 25) {
            // Entering stage 2 — heavier build-up
            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS, 0.9f, 1.0f);
        } else if (ticksUsed == 60) {
            // Entering stage 3 — full charge warning
            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS, 1.5f, 0.6f);
        }

        // Periodic rumble every 8 ticks while charging — pitch drops and volume rises
        if (ticksUsed >= 5 && ticksUsed % 8 == 0) {
            float progress = Math.min(1.0f, ticksUsed / 60.0f);
            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS,
                    0.25f + progress * 0.6f, 1.8f - progress * 1.2f);
        }
    }

    /**
     * Called when the player releases right-click.
     * Stage 1 ( 5–24 ticks):  Normal mace smash — 15 dmg, 3-block radius, knockup
     * Stage 2 (25–59 ticks):  Heavy smash — 25 dmg, 5-block radius, 3-block sphere erosion
     * Stage 3 (60+   ticks):  Cataclysm — 40 dmg, 7-block radius, 6-block random hole
     */
    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeCharged) {
        if (!(livingEntity instanceof Player player) || !(level instanceof ServerLevel sl)) return false;
        if (player.getCooldowns().isOnCooldown(stack)) return false;

        int ticksUsed = getUseDuration(stack, livingEntity) - timeCharged;
        if (ticksUsed < 5) return false;

        if (ticksUsed >= 60) {
            applyStage3(player, sl);
            player.getCooldowns().addCooldown(stack, 200);
            player.displayClientMessage(Component.literal("§4§lCataclysm!"), true);
        } else if (ticksUsed >= 25) {
            applyStage2(player, sl);
            player.getCooldowns().addCooldown(stack, 120);
            player.displayClientMessage(Component.literal("§6§lHeavy Smash!"), true);
        } else {
            applyStage1(player, sl);
            player.getCooldowns().addCooldown(stack, 60);
            player.displayClientMessage(Component.literal("§7Smash!"), true);
        }
        return true;
    }

    /** Stage 1: mace smash — 15 dmg, 3-block radius, knockup 1.2. */
    private void applyStage1(Player player, ServerLevel sl) {
        sl.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(3.0), e -> e != player)
            .forEach(e -> {
                e.hurt(sl.damageSources().playerAttack(player), 15.0f);
                e.setDeltaMovement(e.getDeltaMovement().add(0, 1.2, 0));
                e.hurtMarked = true;
            });
        sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 1.5f, 1.0f);
    }

    /** Stage 2: heavy smash — 25 dmg, 5-block radius, knockup 1.8, 3-block sphere erosion. */
    private void applyStage2(Player player, ServerLevel sl) {
        sl.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(5.0), e -> e != player)
            .forEach(e -> {
                e.hurt(sl.damageSources().playerAttack(player), 25.0f);
                e.setDeltaMovement(e.getDeltaMovement().add(0, 1.8, 0));
                e.hurtMarked = true;
            });
        sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.5f, 0.9f);
        sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 1.1f);

        BlockPos center = player.blockPosition();
        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 1; y++) {
                for (int z = -3; z <= 3; z++) {
                    if (x * x + y * y + z * z <= 9) {
                        removeBlock(sl, center.offset(x, y, z));
                    }
                }
            }
        }
    }

    /** Stage 3: cataclysm — 40 dmg, 7-block radius, knockup 2.8, random 6-block-radius hole. */
    private void applyStage3(Player player, ServerLevel sl) {
        sl.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(7.0), e -> e != player)
            .forEach(e -> {
                e.hurt(sl.damageSources().playerAttack(player), 40.0f);
                e.setDeltaMovement(e.getDeltaMovement().add(0, 2.8, 0));
                e.hurtMarked = true;
            });
        sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 2.0f, 0.6f);
        sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.5f, 0.5f);
        sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0f, 0.8f);

        BlockPos center = player.blockPosition();
        RandomSource rng = sl.getRandom();
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                if (x * x + z * z <= 36) {
                    int depth = 2 + rng.nextInt(5); // 2–6 blocks per column
                    for (int y = 0; y >= -depth; y--) {
                        if (rng.nextFloat() < 0.85f) { // jagged edges
                            removeBlock(sl, center.offset(x, y, z));
                        }
                    }
                }
            }
        }
    }

    private void removeBlock(ServerLevel sl, BlockPos pos) {
        BlockState state = sl.getBlockState(pos);
        if (!state.isAir() && !state.is(Blocks.BEDROCK) && !state.is(Blocks.BARRIER)) {
            sl.removeBlock(pos, false);
        }
    }

    /** On melee hit: Nausea 3s to target, Darkness 3s to self. */
    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        target.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 60, 0, false, true));
        attacker.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
        super.postHurtEnemy(stack, target, attacker);
    }
}
