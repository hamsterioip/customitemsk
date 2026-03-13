package com.example;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * The Stalker - A shadow entity that hunts players from darkness.
 * 
 * Behavior:
 * - Spawns in dark areas (light level 0-3)
 * - Invisible until it attacks or takes damage
 * - Becomes visible when attacking with a terrifying reveal
 * - Teleports behind the player for backstab attacks
 * - Can only survive in darkness - dies in light level 8+
 * - Drops shadow essence on death
 */
public class StalkerEntity extends PathfinderMob {

    public static EntityType<StalkerEntity> TYPE;

    private static final int MAX_LIFE_TICKS = 6000; // 5 minutes
    private static final int REVEAL_DURATION = 60; // 3 seconds visible after attack
    
    private int lifeTicks = 0;
    private int revealTicks = 0;
    private int teleportCooldown = 0;
    private int huntCooldown = 0;
    private boolean hasRevealed = false;
    private boolean isHunting = false;

    public StalkerEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setSilent(true); // Silent until revealed
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.35)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.ATTACK_SPEED, 1.5)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.ARMOR, 6.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new ShadowStrikeGoal());
        this.goalSelector.addGoal(2, new BackstabTeleportGoal());
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2, true));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 32.0f));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        
        ServerLevel sl = (ServerLevel) level();
        lifeTicks++;
        
        // Die if in bright light
        int lightLevel = sl.getMaxLocalRawBrightness(blockPosition());
        if (lightLevel >= 9) {
            // Burning in light
            if (lifeTicks % 20 == 0) {
                hurt(sl.damageSources().onFire(), 5.0f);
                sl.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1, getZ(), 5, 0.2, 0.3, 0.2, 0.01);
            }
        }
        
        // Handle reveal duration
        if (revealTicks > 0) {
            revealTicks--;
            if (revealTicks == 0) {
                setInvisible(true);
                setSilent(true);
            }
        }
        
        // Teleport cooldown
        if (teleportCooldown > 0) teleportCooldown--;
        
        // Hunt cooldown
        if (huntCooldown > 0) huntCooldown--;
        
        // Shadow particles when invisible
        if (isInvisible() && lifeTicks % 10 == 0) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, 
                    getX(), getY() + 0.5, getZ(), 2, 0.2, 0.3, 0.2, 0.01);
        }
        
        // Check for despawn conditions
        if (lifeTicks >= MAX_LIFE_TICKS) {
            vanishInShadows(sl);
            return;
        }
        
        // If target is looking at us and we're revealed, teleport away
        if (!isInvisible() && getTarget() instanceof Player player) {
            Vec3 lookVec = player.getLookAngle();
            Vec3 toStalker = getEyePosition().subtract(player.getEyePosition()).normalize();
            if (lookVec.dot(toStalker) > 0.5 && teleportCooldown <= 0) {
                teleportToShadows(sl);
            }
        }
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        // Reveal when attacking
        revealForAttack(level);
        
        boolean hit = super.doHurtTarget(level, target);
        
        if (hit && target instanceof Player player) {
            // Apply terrifying effects
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false));
            
            // Scary sound at player's location
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 1.0f, 0.4f);
            
            // Blood particles
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    player.getX(), player.getY() + 1, player.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
        }
        
        return hit;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // Reveal when hit
        if (isInvisible()) {
            revealForAttack(level);
        }
        
        // Teleport away when hit (if not on cooldown)
        if (teleportCooldown <= 0 && source.getEntity() instanceof Player) {
            teleportCooldown = 40; // 2 second cooldown
            teleportToShadows(level);
        }
        
        return super.hurtServer(level, source, amount);
    }

    @Override
    public void die(DamageSource cause) {
        if (!level().isClientSide() && level() instanceof ServerLevel sl) {
            // Dramatic death - shadow implosion
            sl.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 1, getZ(), 30, 0.5, 0.8, 0.5, 0.1);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 40, 0.8, 0.8, 0.8, 0.05);
            sl.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.WARDEN_DEATH, SoundSource.HOSTILE, 1.5f, 0.3f);
        }
        super.die(cause);
    }

    private void revealForAttack(ServerLevel sl) {
        setInvisible(false);
        setSilent(false);
        revealTicks = REVEAL_DURATION;
        
        if (!hasRevealed) {
            hasRevealed = true;
            // Terrifying reveal sound combo
            sl.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.2f, 0.5f);
            sl.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 0.8f, 0.4f);
            
            // Reveal particles
            sl.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 1, getZ(), 20, 0.4, 0.6, 0.4, 0.1);
            
            // Message to nearby players
            List<Player> nearby = sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(20.0));
            for (Player p : nearby) {
                p.displayClientMessage(Component.literal("§0§o§k...§4§lTHE SHADOWS MOVE§0§o§k...§r"), true);
            }
        }
    }

    private void teleportToShadows(ServerLevel sl) {
        if (getTarget() == null) return;
        
        Player target = (Player) getTarget();
        Vec3 look = target.getLookAngle();
        
        // Try to teleport behind the player
        double behindX = target.getX() - look.x * 3;
        double behindZ = target.getZ() - look.z * 3;
        
        BlockPos behindPos = BlockPos.containing(behindX, target.getY(), behindZ);
        
        // Find valid position
        for (int y = -2; y <= 2; y++) {
            BlockPos testPos = behindPos.offset(0, y, 0);
            if (isValidTeleportPos(sl, testPos)) {
                // Smoke at old position
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 10, 0.3, 0.5, 0.3, 0.05);
                
                teleportTo(testPos.getX() + 0.5, testPos.getY(), testPos.getZ() + 0.5);
                
                // Smoke at new position
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 10, 0.3, 0.5, 0.3, 0.05);
                
                // Teleport sound
                sl.playSound(null, getX(), getY(), getZ(),
                        SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8f, 0.3f);
                
                return;
            }
        }
    }

    private boolean isValidTeleportPos(ServerLevel sl, BlockPos pos) {
        return sl.getBlockState(pos).isAir() && sl.getBlockState(pos.above()).isAir()
            && sl.getBlockState(pos.below()).isSolid();
    }

    private void vanishInShadows(ServerLevel sl) {
        sl.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 1, getZ(), 20, 0.5, 0.5, 0.5, 0.05);
        discard();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean fireImmune() {
        return false; // Dies in light/fire
    }

    // AI Goals

    /**
     * Shadow Strike - Wait for player to be vulnerable then strike
     */
    private class ShadowStrikeGoal extends Goal {
        private int chargeTicks = 0;
        private static final int CHARGE_TIME = 30;

        public ShadowStrikeGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!(getTarget() instanceof Player target)) return false;
            if (huntCooldown > 0) return false;
            
            // Check if player is vulnerable (looking away, low health, or in darkness)
            Vec3 look = target.getLookAngle();
            Vec3 toStalker = getEyePosition().subtract(target.getEyePosition()).normalize();
            boolean lookingAway = look.dot(toStalker) < 0;
            boolean lowHealth = target.getHealth() < target.getMaxHealth() * 0.4;
            int light = level().getMaxLocalRawBrightness(target.blockPosition());
            boolean inDarkness = light < 5;
            
            return lookingAway || lowHealth || inDarkness;
        }

        @Override
        public void start() {
            chargeTicks = 0;
            isHunting = true;
        }

        @Override
        public void tick() {
            chargeTicks++;
            
            if (getTarget() != null) {
                getNavigation().moveTo(getTarget(), 1.5);
                getLookControl().setLookAt(getTarget());
                
                // Warning particles during charge
                if (chargeTicks % 5 == 0 && level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(), 1, 0.2, 0.1, 0.2, 0.01);
                }
            }
        }

        @Override
        public boolean canContinueToUse() {
            return chargeTicks < CHARGE_TIME && getTarget() != null && isAlive();
        }

        @Override
        public void stop() {
            huntCooldown = 100; // 5 second cooldown
            isHunting = false;
        }
    }

    /**
     * Backstab Teleport - Teleport behind player for surprise attack
     */
    private class BackstabTeleportGoal extends Goal {
        private int cooldown = 0;

        public BackstabTeleportGoal() {
            setFlags(EnumSet.noneOf(Goal.Flag.class));
        }

        @Override
        public boolean canUse() {
            if (teleportCooldown > 0) return false;
            if (!(getTarget() instanceof Player target)) return false;
            if (distanceToSqr(target) < 16.0) return false; // Already close
            return --cooldown <= 0;
        }

        @Override
        public void start() {
            cooldown = 40 + random.nextInt(40);
            if (level() instanceof ServerLevel sl) {
                teleportToShadows(sl);
            }
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }
    }
}
