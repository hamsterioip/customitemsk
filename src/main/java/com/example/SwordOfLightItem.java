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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

public class SwordOfLightItem extends Item {

    private static final int COOLDOWN_TICKS  = 200;  // 10 second ability cooldown
    private static final int DEBUFF_INTERVAL = 300;  // 15 seconds between random effects

    public SwordOfLightItem(Properties props) {
        super(props);
    }

    /**
     * Right-click ability: light pillar — strikes targeted entity (or aimed-at position)
     * with a lightning bolt.
     */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel sl && !player.getCooldowns().isOnCooldown(stack)) {
            // Raycast to find the nearest entity in the player's line of sight
            Vec3 eyePos  = player.getEyePosition(1.0f);
            Vec3 lookVec = player.getLookAngle();
            Vec3 endPos  = eyePos.add(lookVec.scale(20.0));

            AABB searchBox = player.getBoundingBox()
                    .expandTowards(lookVec.scale(20.0))
                    .inflate(2.0);

            List<Entity> candidates = sl.getEntities(player, searchBox,
                    e -> e != player && e.isPickable());

            Entity target = null;
            double closest = Double.MAX_VALUE;
            for (Entity e : candidates) {
                Optional<Vec3> hit = e.getBoundingBox().inflate(0.3).clip(eyePos, endPos);
                if (hit.isPresent()) {
                    double dist = eyePos.distanceToSqr(hit.get());
                    if (dist < closest) { closest = dist; target = e; }
                }
            }

            Vec3 strikePos = (target != null) ? target.position() : endPos;

            // Strike with lightning at target
            LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, sl);
            bolt.setPos(strikePos.x, strikePos.y, strikePos.z);
            sl.addFreshEntity(bolt);

            sl.playSound(null, strikePos.x, strikePos.y, strikePos.z,
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.5f, 1.0f);
            player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
            player.displayClientMessage(Component.literal("§eLight descends!"), true);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /**
     * Passive debuff: every 15 seconds while held, 50% chance of Regeneration II (5s)
     * or Instant Damage I.
     */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (slot == EquipmentSlot.MAINHAND && entity instanceof Player player) {
            if (level.getGameTime() % DEBUFF_INTERVAL == 0) {
                if (level.random.nextBoolean()) {
                    player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
                    player.displayClientMessage(Component.literal("§aThe light heals you."), true);
                } else {
                    player.addEffect(new MobEffectInstance(MobEffects.INSTANT_DAMAGE, 1, 0));
                    player.displayClientMessage(Component.literal("§cThe light burns!"), true);
                }
            }
        }
    }
}
