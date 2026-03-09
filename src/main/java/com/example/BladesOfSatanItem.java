package com.example;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

public class BladesOfSatanItem extends Item {

    private static final int COOLDOWN_TICKS = 900; // 45 seconds

    public BladesOfSatanItem(Properties props) {
        super(props);
    }

    /** Right-click ability: ring of fire in 5-block radius. 45s cooldown. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel sl && !player.getCooldowns().isOnCooldown(stack)) {
            BlockPos center = player.blockPosition();
            for (int x = -5; x <= 5; x++) {
                for (int z = -5; z <= 5; z++) {
                    int d = x * x + z * z;
                    if (d >= 16 && d <= 25) {  // ring only — inner 4 blocks stay clear
                        BlockPos ground = center.offset(x, 0, z);
                        BlockPos above  = ground.above();
                        if (sl.getBlockState(ground).isAir()) {
                            sl.setBlockAndUpdate(ground, Blocks.FIRE.defaultBlockState());
                        } else if (sl.getBlockState(above).isAir()) {
                            sl.setBlockAndUpdate(above,  Blocks.FIRE.defaultBlockState());
                        }
                    }
                }
            }
            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.5f, 0.6f);
            player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
            player.displayClientMessage(Component.literal("§4Hellfire rises!"), true);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /**
     * On hit:
     *  - Soul Burn:  apply Wither II (10s) — bleeds the enemy's soul, ignores armor
     *  - Blood Pact: costs the attacker 1 HP (min 1 HP — won't kill you)
     */
    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        // Soul Burn — Wither II for 10 seconds
        target.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 1, false, true));

        // Blood Pact — pay 1 HP per hit (won't kill the player)
        if (attacker.getHealth() > 2.0f && attacker.level() instanceof ServerLevel sl) {
            attacker.hurt(sl.damageSources().magic(), 1.0f);
        }

        super.postHurtEnemy(stack, target, attacker);
    }

    /** Passives while held in main hand:
     *  - Fire Immunity (ability 5)
     *  - Slowness II (existing curse)
     */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (slot == EquipmentSlot.MAINHAND && entity instanceof Player player) {
            if (level.getGameTime() % 30 == 0) {
                // Fire Immunity
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false));
                // Slowness curse
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1, false, false));
            }
        }
    }
}
