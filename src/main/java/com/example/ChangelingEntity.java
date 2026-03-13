package com.example;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Random;

/**
 * The Changeling - A shapeshifting mimic that disguises as peaceful mobs.
 * 
 * Behavior:
 * - Spawns disguised as a random passive mob (cow, pig, sheep, villager, etc.)
 * - Acts peaceful and wanders around like the disguised mob
 * - If player gets within 3 blocks OR hits it, it ENRAGES and reveals true form
 * - When enraged: attacks the player with high damage and speed
 * - Can periodically change disguise if not enraged
 */
public class ChangelingEntity extends PathfinderMob {

    public static EntityType<ChangelingEntity> TYPE;
    
    // Entity data for syncing disguise type to client
    private static final EntityDataAccessor<Integer> DATA_DISGUISE_TYPE = 
            SynchedEntityData.defineId(ChangelingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ENRAGED = 
            SynchedEntityData.defineId(ChangelingEntity.class, EntityDataSerializers.BOOLEAN);
    
    // Disguise types
    public enum DisguiseType {
        COW(0, "Cow"),
        PIG(1, "Pig"),
        SHEEP(2, "Sheep"),
        VILLAGER(3, "Villager"),
        CHICKEN(4, "Chicken"),
        RABBIT(5, "Rabbit");
        
        final int id;
        final String name;
        
        DisguiseType(int id, String name) {
            this.id = id;
            this.name = name;
        }
        
        static DisguiseType byId(int id) {
            for (DisguiseType type : values()) {
                if (type.id == id) return type;
            }
            return COW;
        }
    }
    
    private DisguiseType currentDisguise = DisguiseType.COW;
    private boolean enraged = false;
    private Player targetPlayer = null;
    private int disguiseChangeTimer = 0;
    private static final int DISGUISE_CHANGE_INTERVAL = 6000; // 5 minutes
    private static final double PROXIMITY_THRESHOLD = 3.0; // 3 blocks
    private static final double DETECTION_RANGE = 16.0;
    
    private int lifeTicks = 0;
    private int enragedTicks = 0;

    public ChangelingEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        pickRandomDisguise();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_DISGUISE_TYPE, 0);
        builder.define(DATA_ENRAGED, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 60.0)
                .add(Attributes.MOVEMENT_SPEED, 0.35)
                .add(Attributes.ATTACK_DAMAGE, 12.0)
                .add(Attributes.ATTACK_SPEED, 1.5)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
                .add(Attributes.ARMOR, 6.0);
    }

    @Override
    protected void registerGoals() {
        // Passive goals (when not enraged)
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        
        // Aggressive goals (only active when enraged)
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.5, true));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            // Sync data from server
            currentDisguise = DisguiseType.byId(entityData.get(DATA_DISGUISE_TYPE));
            enraged = entityData.get(DATA_ENRAGED);
            return;
        }

        ServerLevel sl = (ServerLevel) level();
        lifeTicks++;
        
        if (enraged) {
            enragedTicks++;
            
            // Periodic effects while enraged
            if (enragedTicks % 20 == 0) {
                // Smoke particles showing it's not a real mob
                sl.sendParticles(ParticleTypes.LARGE_SMOKE,
                        getX(), getY() + 1, getZ(), 3, 0.3, 0.3, 0.3, 0.02);
            }
            
            // Speed boost every few seconds
            if (enragedTicks % 100 == 0) {
                addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 1, false, false));
            }
        } else {
            // Not enraged - check for nearby players
            checkForNearbyPlayers(sl);
            
            // Periodically change disguise if not enraged
            disguiseChangeTimer++;
            if (disguiseChangeTimer >= DISGUISE_CHANGE_INTERVAL) {
                disguiseChangeTimer = 0;
                changeDisguise(sl);
            }
            
            // Passive mob behavior - wander randomly
            if (getDeltaMovement().lengthSqr() < 0.001 && random.nextInt(100) == 0) {
                // Random wander
                double wx = getX() + (random.nextDouble() - 0.5) * 10;
                double wz = getZ() + (random.nextDouble() - 0.5) * 10;
                getNavigation().moveTo(wx, getY(), wz, 0.4);
            }
        }
    }

    private void checkForNearbyPlayers(ServerLevel sl) {
        if (enraged) return;
        
        List<Player> nearbyPlayers = sl.getEntitiesOfClass(Player.class, 
                getBoundingBox().inflate(PROXIMITY_THRESHOLD));
        
        for (Player player : nearbyPlayers) {
            if (player.distanceToSqr(this) < PROXIMITY_THRESHOLD * PROXIMITY_THRESHOLD) {
                enrage(sl, player);
                return;
            }
        }
    }

    private void enrage(ServerLevel sl, Player player) {
        if (enraged) return;
        
        enraged = true;
        targetPlayer = player;
        entityData.set(DATA_ENRAGED, true);
        
        // Dramatic reveal effects
        // Particles
        for (int i = 0; i < 50; i++) {
            double ox = (random.nextDouble() - 0.5) * 2;
            double oy = random.nextDouble() * 2;
            double oz = (random.nextDouble() - 0.5) * 2;
            sl.sendParticles(ParticleTypes.LARGE_SMOKE,
                    getX() + ox, getY() + oy, getZ() + oz, 1, 0, 0, 0, 0.1);
        }
        
        // Red angry particles
        sl.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                getX(), getY() + 1.5, getZ(), 20, 0.5, 0.5, 0.5, 0.1);
        
        // Sound effects
        sl.playSound(null, getX(), getY(), getZ(),
                SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 1.0f, 0.8f);
        sl.playSound(null, getX(), getY(), getZ(),
                SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 0.8f, 1.2f);
        
        // Set target and start attacking
        setTarget(player);
        
        // Speed boost
        addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 2, false, false));
        addEffect(new MobEffectInstance(MobEffects.STRENGTH, 200, 1, false, false));
        
        // Broadcast to nearby players
        sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(20.0))
                .forEach(p -> p.displayClientMessage(
                        Component.literal("§c§l⚠ The " + currentDisguise.name + " was a CHANGELING! ⚠"), true));
        
        // Set nameplate visible
        setCustomName(Component.literal("§c§lChangeling"));
        setCustomNameVisible(true);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        
        // Enrage if hit by a player
        if (!enraged && attacker instanceof Player player) {
            enrage(level, player);
        }
        
        return super.hurtServer(level, source, amount);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        
        if (hit && target instanceof Player player) {
            // Apply weakness and nausea to confuse the player
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, false));
            
            // Blood particles
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    player.getX(), player.getY() + 1, player.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
        }
        
        return hit;
    }

    @Override
    public void die(DamageSource cause) {
        if (!level().isClientSide() && level() instanceof ServerLevel sl) {
            // Death effects - reveal true form in death
            sl.sendParticles(ParticleTypes.LARGE_SMOKE,
                    getX(), getY() + 1, getZ(), 30, 0.5, 0.8, 0.5, 0.05);
            sl.sendParticles(ParticleTypes.SCULK_SOUL,
                    getX(), getY() + 0.5, getZ(), 20, 0.3, 0.5, 0.3, 0.05);
            
            sl.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.WARDEN_DEATH, SoundSource.HOSTILE, 1.0f, 1.5f);
            
            // Drop some loot based on disguise
            if (random.nextInt(3) == 0) {
                // Drop something based on disguise type
                switch (currentDisguise) {
                    case COW -> spawnAtLocation(sl, net.minecraft.world.item.Items.LEATHER);
                    case PIG -> spawnAtLocation(sl, net.minecraft.world.item.Items.PORKCHOP);
                    case SHEEP -> spawnAtLocation(sl, net.minecraft.world.item.Items.WHITE_WOOL);
                    case VILLAGER -> spawnAtLocation(sl, net.minecraft.world.item.Items.EMERALD);
                    case CHICKEN -> spawnAtLocation(sl, net.minecraft.world.item.Items.CHICKEN);
                    case RABBIT -> spawnAtLocation(sl, net.minecraft.world.item.Items.RABBIT_HIDE);
                }
            }
        }
        super.die(cause);
    }

    private void pickRandomDisguise() {
        DisguiseType[] types = DisguiseType.values();
        currentDisguise = types[random.nextInt(types.length)];
        entityData.set(DATA_DISGUISE_TYPE, currentDisguise.id);
    }

    private void changeDisguise(ServerLevel sl) {
        DisguiseType oldDisguise = currentDisguise;
        pickRandomDisguise();
        
        // Particle effect for disguise change
        sl.sendParticles(ParticleTypes.WITCH,
                getX(), getY() + 1, getZ(), 20, 0.3, 0.5, 0.3, 0.05);
    }

    // Public getters for renderer
    public DisguiseType getDisguiseType() {
        return currentDisguise;
    }

    public boolean isEnraged() {
        return enraged;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean shouldShowName() {
        return enraged;
    }
}
