package com.example;

import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NaturesGuardianItem extends Item {

    /** Tracks last Ancient Awakening activation tick per player. */
    public static final ConcurrentHashMap<UUID, Long> AWAKENING_COOLDOWNS = new ConcurrentHashMap<>();
    public static final int AWAKENING_COOLDOWN = 2400; // 2 minutes

    public NaturesGuardianItem(Properties props) {
        super(props);
    }

    /** Passive: +2 hearts (Health Boost I) while held in offhand. */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (slot == EquipmentSlot.OFFHAND && entity instanceof Player player) {
            if (level.getGameTime() % 30 == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, 40, 0, false, false));
            }
        }
    }

    /** Right-click → raise like a shield. */
    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BLOCK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }
}
