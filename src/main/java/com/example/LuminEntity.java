package com.example;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * The Lumin — a shy, luminous spirit that escorts players and keeps them safe.
 *
 * Behaviour:
 *  • Floats 8–14 blocks behind the nearest player, following at a gentle pace.
 *  • Aura (12-block radius, every 30 ticks):
 *      – Night Vision I (3 s — just enough to feel the glow)
 *      – Regeneration I normally; Regeneration II when the player is below 50 % HP.
 *  • Shy: if a player steps within 4 blocks it teleports 6 blocks away with a
 *    soft chime.  It never tries to interact — only to be near.
 *  • Cannot be killed — it absorbs hits and teleports away each time.
 *    After 3 hits it dissolves with a shower of glow particles and drops
 *    2–4 Glowstone Dust.
 *  • Emits END_ROD + GLOW particles continuously so it's visible at night.
 */
public class LuminEntity extends PathfinderMob {

    public static EntityType<LuminEntity> TYPE;

    private static final double FOLLOW_MIN  = 7.0;   // won't close past this
    private static final double FOLLOW_MAX  = 14.0;  // starts following beyond this
    private static final double AURA_RADIUS = 12.0;
    private static final double SHY_DIST_SQ = 4.0 * 4.0;

    private int lifeTicks    = 0;
    private int hitCount     = 0;   // absorbs 3 hits then dissolves
    private int fleeCooldown = 0;

    public LuminEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setGlowingTag(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH,           10.0)
                .add(Attributes.MOVEMENT_SPEED,         0.22)
                .add(Attributes.FOLLOW_RANGE,           24.0)
                .add(Attributes.KNOCKBACK_RESISTANCE,    1.0); // pure float — never tumbles
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new ShyFleeGoal());
        this.goalSelector.addGoal(2, new EscortGoal());
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.6));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        lifeTicks++;

        if (level().isClientSide()) return;
        ServerLevel sl = (ServerLevel) level();

        if (fleeCooldown > 0) fleeCooldown--;

        // Continuous particle glow — END_ROD for the crisp white core
        if (lifeTicks % 3 == 0) {
            sl.sendParticles(ParticleTypes.END_ROD,
                    getX(), getY() + 0.9, getZ(), 1, 0.12, 0.12, 0.12, 0.004);
        }
        // GLOW for the soft outer halo
        if (lifeTicks % 6 == 0) {
            sl.sendParticles(ParticleTypes.GLOW,
                    getX(), getY() + 0.9, getZ(), 1, 0.25, 0.25, 0.25, 0.0);
        }

        // Aura — helpful effects for any player within 12 blocks
        if (lifeTicks % 30 == 0) {
            List<Player> nearby = sl.getEntitiesOfClass(Player.class,
                    getBoundingBox().inflate(AURA_RADIUS));
            for (Player p : nearby) {
                boolean critical = p.getHealth() < p.getMaxHealth() * 0.5;
                // Regeneration I normally; II when the player is critically hurt
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, critical ? 1 : 0, false, false));
                // Night Vision — short duration so it fades quickly if the Lumin leaves
                p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,  60, 0, false, false));
            }
        }

        // Slight upward push when resting on the ground to maintain a floating look
        if (lifeTicks % 10 == 0) {
            BlockPos below = blockPosition().below();
            if (!sl.getBlockState(below).isAir()) {
                setDeltaMovement(getDeltaMovement().add(0, 0.07, 0));
            }
        }
    }

    // ───────────────────────────── combat / hits ─────────────────────────────

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        hitCount++;

        fleeFromSource(level, source);

        if (hitCount >= 3) {
            dissolve(level);
            return false;
        }

        // Brief flash of instant-effect particles to show it was hit
        level.sendParticles(ParticleTypes.INSTANT_EFFECT,
                getX(), getY() + 0.9, getZ(), 10, 0.3, 0.3, 0.3, 0.05);
        level.playSound(null, getX(), getY(), getZ(),
                SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.NEUTRAL, 0.6f, 1.3f);

        return false; // absorbs the hit — HP never changes
    }

    private void fleeFromSource(ServerLevel sl, DamageSource source) {
        if (fleeCooldown > 0) return;
        fleeCooldown = 60;

        Vec3 dir;
        if (source.getEntity() != null) {
            dir = position().subtract(source.getEntity().position()).normalize();
        } else {
            dir = new Vec3(sl.getRandom().nextGaussian(), 0, sl.getRandom().nextGaussian()).normalize();
        }

        double nx = getX() + dir.x * 8;
        double nz = getZ() + dir.z * 8;
        BlockPos base = BlockPos.containing(nx, getY() + 1, nz);

        for (int dy = 2; dy >= -3; dy--) {
            BlockPos test = base.offset(0, dy, 0);
            if (sl.getBlockState(test).isAir() && sl.getBlockState(test.above()).isAir()) {
                sl.sendParticles(ParticleTypes.END_ROD,
                        getX(), getY() + 0.9, getZ(), 14, 0.3, 0.3, 0.3, 0.04);
                sl.playSound(null, getX(), getY(), getZ(),
                        SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.5f, 1.6f);
                teleportTo(test.getX() + 0.5, test.getY(), test.getZ() + 0.5);
                sl.sendParticles(ParticleTypes.END_ROD,
                        getX(), getY() + 0.9, getZ(), 8, 0.2, 0.2, 0.2, 0.02);
                break;
            }
        }
    }

    private void dissolve(ServerLevel sl) {
        sl.sendParticles(ParticleTypes.END_ROD,
                getX(), getY() + 0.9, getZ(), 50, 0.5, 0.5, 0.5, 0.08);
        sl.sendParticles(ParticleTypes.GLOW,
                getX(), getY() + 0.9, getZ(), 30, 0.6, 0.6, 0.6, 0.0);
        sl.playSound(null, getX(), getY(), getZ(),
                SoundEvents.TOTEM_USE, SoundSource.NEUTRAL, 0.8f, 1.8f);

        int drops = 2 + sl.getRandom().nextInt(3); // 2–4
        for (int i = 0; i < drops; i++) {
            ItemEntity drop = new ItemEntity(sl,
                    getX() + sl.getRandom().nextDouble() * 0.5 - 0.25,
                    getY() + 0.5,
                    getZ() + sl.getRandom().nextDouble() * 0.5 - 0.25,
                    new ItemStack(Items.GLOWSTONE_DUST));
            drop.setDefaultPickUpDelay();
            sl.addFreshEntity(drop);
        }

        discard();
    }

    @Override
    public void die(DamageSource cause) {
        if (!level().isClientSide() && level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.END_ROD,
                    getX(), getY() + 0.9, getZ(), 30, 0.5, 0.5, 0.5, 0.06);
            sl.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.TOTEM_USE, SoundSource.NEUTRAL, 0.8f, 1.5f);
        }
        super.die(cause);
    }

    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean canBreatheUnderwater()      { return true; }
    @Override public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        // Immune to environmental damage so it's never killed by a cactus or fall
        return source.is(net.minecraft.world.damagesource.DamageTypes.FALL)
            || source.is(net.minecraft.world.damagesource.DamageTypes.IN_FIRE)
            || source.is(net.minecraft.world.damagesource.DamageTypes.ON_FIRE)
            || source.is(net.minecraft.world.damagesource.DamageTypes.DROWN)
            || source.is(net.minecraft.world.damagesource.DamageTypes.CACTUS)
            || source.is(net.minecraft.world.damagesource.DamageTypes.LAVA);
    }

    // ─────────────────────────────── AI Goals ────────────────────────────────

    /**
     * Shy bounce — if a player closes to within 4 blocks the Lumin blinks
     * 6 blocks away with a gentle chime.
     */
    private class ShyFleeGoal extends Goal {
        private Player threat = null;

        public ShyFleeGoal() {
            setFlags(EnumSet.noneOf(Goal.Flag.class));
        }

        @Override
        public boolean canUse() {
            if (fleeCooldown > 0) return false;
            for (Player p : level().getEntitiesOfClass(Player.class,
                    getBoundingBox().inflate(4.5))) {
                if (distanceToSqr(p) < SHY_DIST_SQ) {
                    threat = p;
                    return true;
                }
            }
            return false;
        }

        @Override
        public void start() {
            if (threat == null || !(level() instanceof ServerLevel sl)) return;

            Vec3 away = position().subtract(threat.position()).normalize();
            double nx = getX() + away.x * 6;
            double nz = getZ() + away.z * 6;
            BlockPos base = BlockPos.containing(nx, getY() + 1, nz);

            for (int dy = 2; dy >= -3; dy--) {
                BlockPos test = base.offset(0, dy, 0);
                if (sl.getBlockState(test).isAir() && sl.getBlockState(test.above()).isAir()) {
                    sl.sendParticles(ParticleTypes.END_ROD,
                            getX(), getY() + 0.9, getZ(), 6, 0.15, 0.15, 0.15, 0.02);
                    teleportTo(test.getX() + 0.5, test.getY(), test.getZ() + 0.5);
                    sl.playSound(null, getX(), getY(), getZ(),
                            SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.NEUTRAL, 0.4f, 1.6f);
                    fleeCooldown = 40;
                    break;
                }
            }
            threat = null;
        }

        @Override
        public boolean canContinueToUse() { return false; }
    }

    /**
     * Escort — follow the nearest player at a comfortable distance (7–14 blocks).
     * Targets a point 8 blocks behind the player so it doesn't block their view.
     */
    private class EscortGoal extends Goal {
        private Player escort = null;

        public EscortGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            escort = level().getNearestPlayer(LuminEntity.this, FOLLOW_MAX);
            return escort != null && distanceTo(escort) > FOLLOW_MIN;
        }

        @Override
        public boolean canContinueToUse() {
            return escort != null && escort.isAlive() && distanceTo(escort) > FOLLOW_MIN;
        }

        @Override
        public void tick() {
            if (escort == null) return;
            // Trail behind the player — target a spot 8 blocks opposite their look direction
            Vec3 look = escort.getLookAngle();
            double tx = escort.getX() - look.x * 8;
            double tz = escort.getZ() - look.z * 8;
            getNavigation().moveTo(tx, escort.getY() + 1.0, tz, 0.85);
        }

        @Override
        public void stop() {
            escort = null;
            getNavigation().stop();
        }
    }
}
