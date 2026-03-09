package com.example;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class PoisonIvyItem extends CrossbowItem {

    /** UUIDs of arrows fired from this crossbow — checked for hit/miss in inventoryTick. */
    public static final Set<UUID> POISON_ARROWS = Collections.synchronizedSet(new HashSet<>());
    /** UUIDs of arrows that DID hit an entity (tracked by the ALLOW_DAMAGE event in CustomItemsK). */
    public static final Set<UUID> HIT_ARROWS = Collections.synchronizedSet(new HashSet<>());

    public PoisonIvyItem(Properties props) {
        super(props);
    }

    /** When the loaded crossbow fires, capture the newly spawned arrow UUIDs. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level instanceof ServerLevel sl && CrossbowItem.isCharged(player.getItemInHand(hand))) {
            // Snapshot existing nearby arrows before firing
            Set<UUID> before = sl.getEntitiesOfClass(AbstractArrow.class,
                    player.getBoundingBox().inflate(3.0))
                .stream().map(Entity::getUUID)
                .collect(Collectors.toSet());

            InteractionResult result = super.use(level, player, hand);

            // Track newly spawned arrows
            sl.getEntitiesOfClass(AbstractArrow.class,
                    player.getBoundingBox().inflate(3.0),
                    a -> !before.contains(a.getUUID()))
                .forEach(a -> POISON_ARROWS.add(a.getUUID()));

            // Hunger cost: 1 hunger bar (2 food points) per shot
            player.getFoodData().setFoodLevel(Math.max(0, player.getFoodData().getFoodLevel() - 2));
            return result;
        }
        return super.use(level, player, hand);
    }

    /** Check if tracked arrows have landed — miss = Glowing on the shooter. */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if ((slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND)
                && entity instanceof Player player
                && !POISON_ARROWS.isEmpty()
                && level.getGameTime() % 5 == 0) {
            Iterator<UUID> iter = POISON_ARROWS.iterator();
            while (iter.hasNext()) {
                UUID id = iter.next();
                Entity arrow = level.getEntity(id);
                boolean landed = arrow == null || (arrow instanceof AbstractArrow a && a.getDeltaMovement().lengthSqr() < 0.0001);
                if (landed) {
                    iter.remove();
                    if (!HIT_ARROWS.remove(id)) {
                        // Arrow missed — apply Glowing to shooter
                        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, true));
                    }
                }
            }
        }
        super.inventoryTick(stack, level, entity, slot);
    }
}
