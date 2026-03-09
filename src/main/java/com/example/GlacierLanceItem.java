package com.example;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

public class GlacierLanceItem extends Item {

    private static final int COOLDOWN_TICKS = 2400; // 2 minutes

    public GlacierLanceItem(Properties props) {
        super(props);
    }

    /** Right-click: freeze all entities in 3-block radius for ~1 second. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel sl && !player.getCooldowns().isOnCooldown(stack)) {
            List<LivingEntity> targets = sl.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(3.0), e -> e != player);
            for (LivingEntity target : targets) {
                target.setTicksFrozen(160);
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 10, false, true));
            }
            // Debuff: user gets Weakness for 30 sec after activating
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 0, false, true));
            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.POWDER_SNOW_PLACE, SoundSource.PLAYERS, 1.5f, 0.5f);
            player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
            player.displayClientMessage(Component.literal("§bIce seizes the world!"), true);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
