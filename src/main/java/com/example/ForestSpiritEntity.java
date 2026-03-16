package com.example;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ForestSpiritEntity extends PathfinderMob {

    public static EntityType<ForestSpiritEntity> TYPE;

    private int lifeTicks = 0;
    private static final int MAX_LIFE = 600; // 30 seconds before despawn

    public ForestSpiritEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setGlowingTag(true);
        setInvisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.15);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new WaterAvoidingRandomStrollGoal(this, 0.5));
    }

    @Override
    public void tick() {
        super.tick();
        lifeTicks++;

        if (lifeTicks >= MAX_LIFE && !level().isClientSide()) {
            discard();
            return;
        }

        // Emit subtle nature particles every 5 ticks to hint at the spirit's location
        if (!level().isClientSide() && lifeTicks % 5 == 0) {
            ServerLevel sl = (ServerLevel) level();
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    getX(), getY() + 1.0, getZ(), 2, 0.3, 0.5, 0.3, 0.05);
        }

        // Proximity aura — the spirit's presence unsettles anyone within 6 blocks
        if (!level().isClientSide() && lifeTicks % 20 == 0) {
            ServerLevel sl = (ServerLevel) level();
            for (Player p : sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(6.0))) {
                p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 0, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.HUNGER,   30, 0, false, false));
            }
        }
    }

    @Override
    public boolean isInvisible() {
        return true;
    }

    @Override
    public void die(DamageSource cause) {
        super.die(cause);
        if (!level().isClientSide() && level() instanceof ServerLevel sl) {
            sl.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.TOTEM_USE, SoundSource.NEUTRAL, 1.0f, 0.4f);
            ItemEntity drop = new ItemEntity(sl, getX(), getY() + 0.5, getZ(),
                    new ItemStack(ModItems.NATURES_HEART));
            drop.setDefaultPickUpDelay();
            sl.addFreshEntity(drop);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }
}
