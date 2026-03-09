package com.example;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StarForgedPickaxeItem extends Item {

    /** UUID → remaining ticks of meteor shower for each active player. */
    public static final Map<UUID, Integer> METEOR_PLAYERS = new ConcurrentHashMap<>();

    /** Fire one fireball every N ticks during the shower. */
    public static final int FIRE_INTERVAL = 3;

    private static final int SHOWER_DURATION = 60;  // 3 seconds
    private static final int COOLDOWN_TICKS   = 200; // 10 seconds

    public StarForgedPickaxeItem(Properties props) {
        super(props);
    }

    /** Mine faster than netherite (9.0f) — returns 20.0f for any mineable block. */
    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        float base = super.getDestroySpeed(stack, state);
        return base > 1.0f ? 20.0f : base;
    }

    /**
     * Right-click: activate the Meteor Shower.
     * Fireballs are fired every tick in CustomItemsK's server tick event.
     */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel sl && !player.getCooldowns().isOnCooldown(stack)) {
            METEOR_PLAYERS.put(player.getUUID(), SHOWER_DURATION);
            // Glowing effect lasts a bit longer than the shower
            player.addEffect(new MobEffectInstance(MobEffects.GLOWING, SHOWER_DURATION + 60, 0, false, true));
            player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.5f, 0.6f);
            player.displayClientMessage(Component.literal("§c§l☄ Meteor Shower!"), true);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.FAIL;
    }
}
