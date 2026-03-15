package com.example;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Void Shard — dropped by The Hollow.
 *
 * Right-click: shatters the shard at the player's feet, creating a 6-block
 * radius cloud of Darkness that lingers for ~8 seconds.
 */
public class VoidShardItem extends Item {

    public VoidShardItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel sl)) return InteractionResult.PASS;

        // Create a slowly-shrinking darkness area-effect cloud at the player's feet
        AreaEffectCloud cloud = new AreaEffectCloud(level, player.getX(), player.getY(), player.getZ());
        cloud.setRadius(6.0f);
        cloud.setDuration(160);
        cloud.setRadiusPerTick(-0.005f);
        cloud.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));
        cloud.setOwner(player);
        level.addFreshEntity(cloud);

        sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.6f, 1.6f);

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
