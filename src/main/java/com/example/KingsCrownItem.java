package com.example;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class KingsCrownItem extends Item {
    public KingsCrownItem(Properties props) {
        super(props);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (entity instanceof Player player) {
            // Strength I — works from any inventory slot
            player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 60, 0, false, false, true));
        }
    }
}
