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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public class TempestReaverItem extends Item {

    private static final int NOVA_COOLDOWN  = 600;   // 30 seconds
    private static final float NOVA_RADIUS  = 8.0f;
    private static final int DEBUFF_INTERVAL = 1200; // 1 minute

    public TempestReaverItem(Properties props) {
        super(props);
    }

    // ── ABILITY 1: Dark Matter Nova (right-click) ──────────────────────────────
    // Absorbs surrounding energy, then detonates a dark matter aura burst:
    //  • Pulls enemies inward
    //  • Deals 8 magic damage + Wither II (5s) + Blindness + Darkness
    //  • Blasts them outward
    //  • Grants the wielder Absorption II (angelic reaction)
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel sl)) return InteractionResult.PASS;
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.PASS;

        List<LivingEntity> targets = sl.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(NOVA_RADIUS), e -> e != player);

        // — Absorb phase: pull targets toward the player —
        for (LivingEntity target : targets) {
            double dx = player.getX() - target.getX();
            double dy = player.getY() - target.getY();
            double dz = player.getZ() - target.getZ();
            double dist = Math.max(target.distanceTo(player), 0.1);
            target.setDeltaMovement(dx / dist * 1.0, dy / dist * 0.4 + 0.2, dz / dist * 1.0);
        }
        sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.9f, 1.6f);

        // — Detonation phase: dark matter explosion —
        for (LivingEntity target : targets) {
            target.hurt(sl.damageSources().magic(), 8.0f);
            target.addEffect(new MobEffectInstance(MobEffects.WITHER,    100, 1, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,  60, 0, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS,  100, 0, false, false));
            // Blast outward
            double dx = target.getX() - player.getX();
            double dz = target.getZ() - player.getZ();
            double dist = Math.max(Math.sqrt(dx * dx + dz * dz), 0.1);
            target.setDeltaMovement(dx / dist * 1.8, 0.55, dz / dist * 1.8);
        }
        // Angelic counter-surge from absorbing dark energy
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1, false, true, true));
        sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WITHER_DEATH, SoundSource.PLAYERS, 0.7f, 1.5f);

        player.getCooldowns().addCooldown(stack, NOVA_COOLDOWN);
        player.displayClientMessage(Component.literal("§5★ Dark Matter Nova! ★"), true);
        return InteractionResult.SUCCESS;
    }

    // ── ABILITY 2: Soul Ascent (on-kill passive) ───────────────────────────────
    // Angelic feature — each kill triggers a divine surge:
    //  • Totem confetti burst on the player
    //  • Target explodes into dark matter (levelEvent 2003 + wither sounds)
    //  • Slow Falling + Levitation + Regeneration II on the player
    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (target.isDeadOrDying()
                && attacker instanceof Player player
                && attacker.level() instanceof ServerLevel sl) {

            // Particle burst at the player's feet (clean, no screen overlay)
            sl.levelEvent(2003, player.blockPosition(), 0);

            // Target explodes into dark matter — particle burst + layered wither sounds
            sl.levelEvent(2003, target.blockPosition(), 0);
            sl.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.WITHER_DEATH, SoundSource.PLAYERS, 0.7f, 1.9f);
            sl.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 1.0f, 0.4f);

            // Slow Falling + Levitation radiate outward from the death — affects all nearby entities
            sl.getEntitiesOfClass(LivingEntity.class,
                    target.getBoundingBox().inflate(6.0), e -> true)
                .forEach(e -> {
                    e.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, true, true));
                    e.addEffect(new MobEffectInstance(MobEffects.LEVITATION,    15, 1, false, false));
                });

            // Regeneration II only applies to the wielder
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 1, false, false, true));
            player.displayClientMessage(Component.literal("§eAngelic Surge!"), true);
        }
        super.postHurtEnemy(stack, target, attacker);
    }

    // ── PASSIVE + DEBUFF tick ──────────────────────────────────────────────────
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (!(entity instanceof Player player)) return;
        if (slot != EquipmentSlot.MAINHAND) return;

        long time = level.getGameTime();

        // Every 3 seconds while held:
        //   • Angelic Regeneration I aura on self
        //   • Dark matter aura: Wither I + Darkness on nearby enemies (4-block radius)
        //   • Subtle elder guardian curse sound (very quiet, ambient)
        if (time % 60 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 70, 0, false, false, true));
            level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(4.0), e -> e != player)
                .forEach(e -> {
                    e.addEffect(new MobEffectInstance(MobEffects.WITHER,   70, 0, false, true));
                    e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 70, 0, false, false));
                });
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.12f, 1.9f);
        }

        // Debuff: every 1 minute, 40% chance — Tempest disrupts the inventory
        if (time % DEBUFF_INTERVAL == 0 && level.random.nextFloat() < 0.40f) {
            shuffleInventory(player, level);
        }
    }

    // Swaps 2 random filled inventory slots (slots 0–35: hotbar + main inventory)
    private void shuffleInventory(Player player, ServerLevel level) {
        Inventory inv = player.getInventory();
        List<Integer> filled = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            if (!inv.getItem(i).isEmpty()) filled.add(i);
        }
        if (filled.size() < 2) return;

        int idxA = level.random.nextInt(filled.size());
        int idxB;
        do { idxB = level.random.nextInt(filled.size()); } while (idxB == idxA);

        int slotA = filled.get(idxA);
        int slotB = filled.get(idxB);
        ItemStack tmp = inv.getItem(slotA).copy();
        inv.setItem(slotA, inv.getItem(slotB).copy());
        inv.setItem(slotB, tmp);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.8f);
        player.displayClientMessage(
                Component.literal("§8The Tempest Reaver shifts your grasp!"), true);
    }
}
