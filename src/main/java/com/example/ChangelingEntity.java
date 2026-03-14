package com.example;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Changeling - An advanced shapeshifting mimic with multiple abilities.
 * 
 * Enhanced Features:
 * - 10 different disguises including rare ones
 * - Mimics sounds of disguised mob
 * - Clones itself when health is low
 * - Shadow step teleport when damaged
 * - Can be temporarily tamed with golden apples
 * - Stronger at night, weaker in daylight
 * - True form reveal with tentacle particles
 */
public class ChangelingEntity extends PathfinderMob {

    public static EntityType<ChangelingEntity> TYPE;
    
    private static final EntityDataAccessor<Integer> DATA_DISGUISE_TYPE = 
            SynchedEntityData.defineId(ChangelingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ENRAGED = 
            SynchedEntityData.defineId(ChangelingEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_TAMED = 
            SynchedEntityData.defineId(ChangelingEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_TAME_TIMER = 
            SynchedEntityData.defineId(ChangelingEntity.class, EntityDataSerializers.INT);
    
    // Enhanced disguise types - 10 total
    public enum DisguiseType {
        COW(0, "Cow", SoundEvents.COW_AMBIENT),
        PIG(1, "Pig", SoundEvents.PIG_AMBIENT),
        SHEEP(2, "Sheep", SoundEvents.SHEEP_AMBIENT),
        VILLAGER(3, "Villager", SoundEvents.VILLAGER_AMBIENT),
        CHICKEN(4, "Chicken", SoundEvents.CHICKEN_AMBIENT),
        RABBIT(5, "Rabbit", SoundEvents.RABBIT_AMBIENT),
        HORSE(6, "Horse", SoundEvents.HORSE_AMBIENT),
        CAT(7, "Cat", SoundEvents.CAT_AMBIENT),
        FOX(8, "Fox", SoundEvents.FOX_AMBIENT),
        MOOSHROOM(9, "Mooshroom", SoundEvents.COW_AMBIENT); // Rare disguise
        
        final int id;
        final String name;
        final SoundEvent ambientSound;
        
        DisguiseType(int id, String name, SoundEvent ambientSound) {
            this.id = id;
            this.name = name;
            this.ambientSound = ambientSound;
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
    private boolean tamed = false;
    private int tameTimer = 0;
    private Player targetPlayer = null;
    private int disguiseChangeTimer = 0;
    private static final int DISGUISE_CHANGE_INTERVAL = 4000; // 3.3 minutes
    private static final double PROXIMITY_THRESHOLD = 3.5; // 3.5 blocks
    
    private int lifeTicks = 0;
    private int enragedTicks = 0;
    private int ambientSoundTimer = 0;
    private int cloneCooldown = 0;
    private int shadowStepCooldown = 0;
    private boolean hasCloned = false;

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
        builder.define(DATA_TAMED, false);
        builder.define(DATA_TAME_TIMER, 0);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 80.0) // Increased from 60
                .add(Attributes.MOVEMENT_SPEED, 0.38)
                .add(Attributes.ATTACK_DAMAGE, 14.0) // Increased from 12
                .add(Attributes.ATTACK_SPEED, 1.8)
                .add(Attributes.FOLLOW_RANGE, 40.0) // Increased from 32
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.ARMOR, 8.0) // Increased from 6
                .add(Attributes.ARMOR_TOUGHNESS, 4.0); // Added toughness
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 10.0f));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.6, true));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            currentDisguise = DisguiseType.byId(entityData.get(DATA_DISGUISE_TYPE));
            enraged = entityData.get(DATA_ENRAGED);
            tamed = entityData.get(DATA_TAMED);
            return;
        }

        ServerLevel sl = (ServerLevel) level();
        lifeTicks++;
        
        // Handle tamed state
        if (tamed) {
            tameTimer--;
            if (tameTimer <= 0) {
                tamed = false;
                enraged = true; // Becomes enraged when taming wears off
                entityData.set(DATA_TAMED, false);
                if (targetPlayer != null) {
                    sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(20.0))
                            .forEach(p -> p.displayClientMessage(
                                    Component.literal("§c§lThe Changeling breaks free from your control!"), true));
                }
            }
            // Tamed changeling doesn't attack
            return;
        }
        
        // Cooldown management
        if (cloneCooldown > 0) cloneCooldown--;
        if (shadowStepCooldown > 0) shadowStepCooldown--;
        
        // Night boost - stronger at night
        boolean isNight = sl.getDayTime() % 24000 > 13000 && sl.getDayTime() % 24000 < 23000;
        if (isNight && lifeTicks % 200 == 0 && enraged) {
            addEffect(new MobEffectInstance(MobEffects.STRENGTH, 100, 0, false, false));
        }
        
        if (enraged) {
            enragedTicks++;
            
            // True form particles (shadow tentacles)
            if (enragedTicks % 10 == 0) {
                spawnTrueFormParticles(sl);
            }
            
            // Periodic speed boost
            if (enragedTicks % 80 == 0) {
                addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 1, false, false));
            }
            
            // Clone ability at low health (once per fight)
            if (getHealth() < getMaxHealth() * 0.3 && !hasCloned && cloneCooldown <= 0) {
                attemptClone(sl);
            }
        } else {
            // Not enraged - mimic ambient sounds
            ambientSoundTimer++;
            if (ambientSoundTimer >= 200 + random.nextInt(300)) {
                ambientSoundTimer = 0;
                playDisguiseSound(sl);
            }
            
            // Check for nearby players
            checkForNearbyPlayers(sl);
            
            // Change disguise periodically
            disguiseChangeTimer++;
            if (disguiseChangeTimer >= DISGUISE_CHANGE_INTERVAL) {
                disguiseChangeTimer = 0;
                changeDisguise(sl);
            }
            
            // Random wandering
            if (getDeltaMovement().lengthSqr() < 0.001 && random.nextInt(100) == 0) {
                double wx = getX() + (random.nextDouble() - 0.5) * 10;
                double wz = getZ() + (random.nextDouble() - 0.5) * 10;
                getNavigation().moveTo(wx, getY(), wz, 0.4);
            }
        }
    }
    
    private void spawnTrueFormParticles(ServerLevel sl) {
        // Shadow tentacle effect
        for (int i = 0; i < 3; i++) {
            double angle = (lifeTicks * 0.3) + (i * 2.0);
            double radius = 0.8 + Math.sin(lifeTicks * 0.1) * 0.3;
            double px = getX() + Math.cos(angle) * radius;
            double pz = getZ() + Math.sin(angle) * radius;
            double py = getY() + 0.5 + Math.sin(lifeTicks * 0.2 + i) * 0.5;
            
            sl.sendParticles(ParticleTypes.SCULK_SOUL, px, py, pz, 1, 0, 0, 0, 0.01);
        }
        // Smoke aura
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 2, 0.4, 0.5, 0.4, 0.02);
    }
    
    private void playDisguiseSound(ServerLevel sl) {
        if (currentDisguise.ambientSound != null && random.nextInt(3) == 0) {
            sl.playSound(null, getX(), getY(), getZ(),
                    currentDisguise.ambientSound, SoundSource.NEUTRAL, 
                    0.8f, 0.8f + random.nextFloat() * 0.4f);
        }
    }
    
    private void attemptClone(ServerLevel sl) {
        if (cloneCooldown > 0) return;
        
        hasCloned = true;
        cloneCooldown = 1200; // 1 minute cooldown
        
        // Spawn a clone
        ChangelingEntity clone = new ChangelingEntity(TYPE, sl);
        clone.setPos(getX() + (random.nextDouble() - 0.5) * 3, getY(), getZ() + (random.nextDouble() - 0.5) * 3);
        clone.enraged = true;
        clone.entityData.set(DATA_ENRAGED, true);
        clone.setCustomName(Component.literal("§c§lChangeling Clone"));
        clone.setCustomNameVisible(true);
        clone.hasCloned = true; // Clone can't clone again
        sl.addFreshEntity(clone);
        
        // Effects
        sl.sendParticles(ParticleTypes.WITCH, getX(), getY() + 1, getZ(), 30, 1, 0.5, 1, 0.1);
        sl.playSound(null, getX(), getY(), getZ(),
                SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 1.0f, 0.5f);
        
        // Alert players
        sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(25.0))
                .forEach(p -> p.displayClientMessage(
                        Component.literal("§5§lThe Changeling splits in two!"), true));
    }
    
    private void shadowStep(ServerLevel sl) {
        if (shadowStepCooldown > 0) return;
        
        shadowStepCooldown = 200; // 10 second cooldown
        
        // Teleport behind the target
        if (getTarget() instanceof Player target) {
            Vec3 look = target.getLookAngle();
            double behindX = target.getX() - look.x * 4;
            double behindZ = target.getZ() - look.z * 4;
            
            // Particles at old location
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 15, 0.3, 0.5, 0.3, 0.05);
            sl.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8f, 0.5f);
            
            teleportTo(behindX, getY(), behindZ);
            
            // Particles at new location
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 15, 0.3, 0.5, 0.3, 0.05);
            
            // Brief invisibility
            addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, false, false));
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack item = player.getItemInHand(hand);
        
        // Can be tamed temporarily with golden apple
        if (!enraged && !tamed && item.is(Items.GOLDEN_APPLE)) {
            if (!player.getAbilities().instabuild) {
                item.shrink(1);
            }
            
            tamed = true;
            tameTimer = 1200; // 1 minute of being docile
            entityData.set(DATA_TAMED, true);
            targetPlayer = player;
            
            // Effects
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1, getZ(), 20, 0.5, 0.5, 0.5, 0.1);
                sl.playSound(null, getX(), getY(), getZ(),
                        SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.NEUTRAL, 1.0f, 1.0f);
            }
            
            player.displayClientMessage(Component.literal("§a§lThe Changeling is temporarily docile..."), true);
            return InteractionResult.SUCCESS;
        }
        
        return super.mobInteract(player, hand);
    }

    private void checkForNearbyPlayers(ServerLevel sl) {
        if (enraged || tamed) return;
        
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
        for (int i = 0; i < 50; i++) {
            double ox = (random.nextDouble() - 0.5) * 2;
            double oy = random.nextDouble() * 2;
            double oz = (random.nextDouble() - 0.5) * 2;
            sl.sendParticles(ParticleTypes.LARGE_SMOKE,
                    getX() + ox, getY() + oy, getZ() + oz, 1, 0, 0, 0, 0.1);
        }
        
        sl.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 1, getZ(), 40, 0.5, 0.8, 0.5, 0.1);
        sl.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + 1.5, getZ(), 20, 0.5, 0.5, 0.5, 0.1);
        
        sl.playSound(null, getX(), getY(), getZ(),
                SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 1.0f, 0.8f);
        sl.playSound(null, getX(), getY(), getZ(),
                SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 0.8f, 1.2f);
        
        setTarget(player);
        
        addEffect(new MobEffectInstance(MobEffects.SPEED, 300, 2, false, false));
        addEffect(new MobEffectInstance(MobEffects.STRENGTH, 300, 1, false, false));
        
        sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(20.0))
                .forEach(p -> p.displayClientMessage(
                        Component.literal("§c§l⚠ The " + currentDisguise.name + " was a CHANGELING! ⚠"), true));
        
        setCustomName(Component.literal("§c§lChangeling"));
        setCustomNameVisible(true);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        
        if (tamed) {
            // Tamed changeling takes damage but doesn't enrage
            return super.hurtServer(level, source, amount);
        }
        
        if (!enraged && attacker instanceof Player player) {
            enrage(level, player);
        }
        
        // Shadow step when damaged (if enraged and cooldown ready)
        if (enraged && shadowStepCooldown <= 0 && random.nextInt(4) == 0) {
            shadowStep(level);
        }
        
        return super.hurtServer(level, source, amount);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        
        if (hit && target instanceof Player player) {
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 120, 0, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
            
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    player.getX(), player.getY() + 1, player.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
        }
        
        return hit;
    }

    @Override
    public void die(DamageSource cause) {
        if (!level().isClientSide() && level() instanceof ServerLevel sl) {
            // Death explosion of particles
            for (int ring = 0; ring < 5; ring++) {
                double radius = ring * 0.5;
                for (int i = 0; i < 12; i++) {
                    double angle = (2 * Math.PI / 12) * i;
                    double px = getX() + Math.cos(angle) * radius;
                    double pz = getZ() + Math.sin(angle) * radius;
                    sl.sendParticles(ParticleTypes.SCULK_SOUL, px, getY() + 0.5, pz, 1, 0, 0, 0, 0.05);
                }
            }
            
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 50, 1, 0.8, 1, 0.1);
            sl.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.WARDEN_DEATH, SoundSource.HOSTILE, 1.2f, 1.5f);
            
            // Better drops based on disguise
            if (random.nextInt(2) == 0) {
                switch (currentDisguise) {
                    case COW -> spawnAtLocation(sl, Items.LEATHER);
                    case PIG -> spawnAtLocation(sl, Items.PORKCHOP);
                    case SHEEP -> spawnAtLocation(sl, Items.WHITE_WOOL);
                    case VILLAGER -> spawnAtLocation(sl, Items.EMERALD);
                    case CHICKEN -> spawnAtLocation(sl, Items.CHICKEN);
                    case RABBIT -> spawnAtLocation(sl, Items.RABBIT_HIDE);
                    case HORSE -> spawnAtLocation(sl, Items.LEATHER);
                    case CAT -> spawnAtLocation(sl, Items.STRING);
                    case FOX -> spawnAtLocation(sl, Items.SWEET_BERRIES);
                    case MOOSHROOM -> spawnAtLocation(sl, Items.RED_MUSHROOM);
                }
            }
            
            // Rare drop - Shapeshifter's Essence (could be used for crafting)
            if (random.nextInt(20) == 0) {
                spawnAtLocation(sl, Items.NETHER_STAR); // Placeholder for custom item
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
        
        sl.sendParticles(ParticleTypes.WITCH, getX(), getY() + 1, getZ(), 25, 0.4, 0.5, 0.4, 0.05);
    }

    public DisguiseType getDisguiseType() { return currentDisguise; }
    public boolean isEnraged() { return enraged; }
    public boolean isTamed() { return tamed; }
    public int getTameTimer() { return tameTimer; }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }
    @Override
    public boolean shouldShowName() { return enraged || tamed; }
}
