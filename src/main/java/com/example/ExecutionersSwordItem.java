package com.example;

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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ExecutionersSwordItem extends Item {

    private static final int COOLDOWN_TICKS = 3000; // 2 min 30 sec

    public ExecutionersSwordItem(Properties props) {
        super(props);
    }

    /** Right-click ability: Invisibility for 1 minute. 2m30s cooldown. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel sl && !player.getCooldowns().isOnCooldown(stack)) {
            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.2f);
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 1200, 0, false, true));
            player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
            player.displayClientMessage(Component.literal("§7You fade into shadow..."), true);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /**
     * Passive buff:  +2 hearts (Health Boost I) while held in main hand.
     * Passive debuff: Hunger while the ability is on cooldown.
     */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (slot == EquipmentSlot.MAINHAND && entity instanceof Player player) {
            if (level.getGameTime() % 30 == 0) {
                // +2 hearts: Health Boost I (amplifier 0 = +4 max HP = +2 hearts)
                player.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, 40, 0, false, false));

                // Hunger debuff only while ability is on cooldown
                if (player.getCooldowns().isOnCooldown(stack)) {
                    player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 40, 0, false, false));
                }
            }
        }
    }
}
