package com.example;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * The Hollow — a featureless, silent horror that prowls underground.
 *
 * Mechanics:
 *  • Completely silent until within 8 blocks of its target.
 *  • HollowStareGoal: while in line-of-sight within 16 blocks, it stares for
 *    4 seconds then inflicts Darkness + Nausea and whispers "it sees you...".
 *  • TorchBreakGoal: seeks the nearest torch/lantern within 12 blocks and
 *    quietly extinguishes it, plunging the player's cave into darkness.
 *  • Regenerates 0.5 HP every 4 seconds while in light level ≤ 4.
 *  • Takes 50% extra damage in bright light (≥ 10).
 *  • On hit: Blindness (3 s) + Darkness (6 s) on the target.
 *  • On death: 30% chance to split — a successor spawns from a nearby dark spot.
 *  • Drops 1–2 Void Shards.
 */
public class HollowEntity extends PathfinderMob {

    public static EntityType<HollowEntity> TYPE;

    private int stareCooldown = 0;
    private int regenCooldown = 0;

    public HollowEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setSilent(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH,          65.0)
                .add(Attributes.MOVEMENT_SPEED,       0.24)
                .add(Attributes.ATTACK_DAMAGE,        11.0)
                .add(Attributes.ATTACK_SPEED,          1.2)
                .add(Attributes.FOLLOW_RANGE,         48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE,  0.5)
                .add(Attributes.ARMOR,                 4.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new HollowStareGoal());
        this.goalSelector.addGoal(2, new TorchBreakGoal());
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 24.0f));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    // ─────────────────────────────── tick ────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;

        ServerLevel sl = (ServerLevel) level();
        if (stareCooldown > 0) stareCooldown--;
        if (regenCooldown  > 0) regenCooldown--;

        // Regen HP while in the dark
        int light = sl.getMaxLocalRawBrightness(blockPosition());
        if (light <= 4 && regenCooldown <= 0 && getHealth() < getMaxHealth()) {
            heal(0.5f);
            regenCooldown = 80; // every 4 s
        }

        // Faint soul particles — just enough to hint at presence
        if (light <= 6 && tickCount % 20 == 0) {
            sl.sendParticles(ParticleTypes.SCULK_SOUL,
                    getX(), getY() + 0.5, getZ(), 1, 0.2, 0.3, 0.2, 0.005);
        }

        // Unmute when close (gives the player just enough audio warning)
        if (getTarget() instanceof Player player && distanceTo(player) < 8.0f) {
            setSilent(false);
        } else {
            setSilent(true);
        }
    }

    // ──────────────────────────── combat ─────────────────────────────────────

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // Vulnerable in bright light
        int light = level.getMaxLocalRawBrightness(blockPosition());
        if (light >= 10) amount *= 1.5f;

        // Pained shriek
        level.playSound(null, getX(), getY(), getZ(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 0.3f, 1.8f);
        return super.hurtServer(level, source, amount);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof Player player) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60,  0, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS,  120, 0, false, false));

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 0.8f, 0.3f);
            level.sendParticles(ParticleTypes.SCULK_SOUL,
                    player.getX(), player.getY() + 1, player.getZ(),
                    15, 0.3, 0.4, 0.3, 0.05);
        }
        return hit;
    }

    @Override
    public void die(DamageSource cause) {
        if (!level().isClientSide() && level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SCULK_SOUL,
                    getX(), getY() + 1, getZ(), 40, 0.6, 0.8, 0.6, 0.05);
            sl.sendParticles(ParticleTypes.SMOKE,
                    getX(), getY() + 1, getZ(), 20, 0.4, 0.4, 0.4, 0.02);
            sl.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.WARDEN_DEATH, SoundSource.HOSTILE, 1.0f, 0.5f);

            // 30% split — a successor crawls out of nearby darkness
            if (random.nextFloat() < 0.3f) {
                spawnSuccessor(sl);
            }

            spawnAtLocation(sl, new ItemStack(ModItems.VOID_SHARD, 1 + random.nextInt(2)));
        }
        super.die(cause);
    }

    private void spawnSuccessor(ServerLevel sl) {
        BlockPos origin = blockPosition();
        for (int tries = 0; tries < 20; tries++) {
            int dx = random.nextInt(17) - 8;
            int dz = random.nextInt(17) - 8;
            BlockPos base = origin.offset(dx, 0, dz);
            for (int dy = -3; dy <= 3; dy++) {
                BlockPos pos = base.offset(0, dy, 0);
                if (sl.getBlockState(pos).isAir()
                        && sl.getBlockState(pos.above()).isAir()
                        && sl.getBlockState(pos.below()).isSolid()
                        && sl.getMaxLocalRawBrightness(pos) <= 4) {

                    HollowEntity next = new HollowEntity(TYPE, sl);
                    next.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                    sl.addFreshEntity(next);

                    List<Player> near = sl.getEntitiesOfClass(
                            Player.class, getBoundingBox().inflate(20.0));
                    for (Player p : near) {
                        p.displayClientMessage(
                                Component.literal("§0§k..§8§oThe darkness splits...§0§k..§r"), true);
                    }
                    return;
                }
            }
        }
    }

    // ────────────────────────── misc overrides ────────────────────────────────

    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean canBreatheUnderwater()       { return true;  }

    // ═════════════════════════════ AI Goals ══════════════════════════════════

    /**
     * HollowStareGoal — stares at the player in silence for 4 seconds, then
     * inflicts Darkness + Nausea and mutters a warning message.
     */
    private class HollowStareGoal extends Goal {

        private int stareTimer = 0;
        private static final int TRIGGER_TICKS = 80; // 4 seconds

        HollowStareGoal() { setFlags(EnumSet.of(Flag.LOOK)); }

        @Override
        public boolean canUse() {
            return stareCooldown <= 0
                    && getTarget() instanceof Player p
                    && distanceToSqr(p) < 256.0   // 16 blocks
                    && hasLineOfSight(p);
        }

        @Override public void start() { stareTimer = 0; }

        @Override
        public void tick() {
            if (!(getTarget() instanceof Player player)) return;
            Vec3 eye = player.getEyePosition();
            getLookControl().setLookAt(eye.x, eye.y, eye.z);
            stareTimer++;

            if (!(level() instanceof ServerLevel sl)) return;

            // Subtle creep particles during build-up
            if (stareTimer % 10 == 0) {
                sl.sendParticles(ParticleTypes.SCULK_SOUL,
                        getX(), getY() + 1.5, getZ(), 2, 0.1, 0.1, 0.1, 0.01);
            }

            if (stareTimer >= TRIGGER_TICKS) {
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 140, 0, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.NAUSEA,    80, 0, false, false));
                sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 0.4f, 0.2f);
                player.displayClientMessage(
                        Component.literal("§8§oit sees you...§r"), true);
                stareCooldown = 160; // 8 s before next stare
            }
        }

        @Override
        public boolean canContinueToUse() {
            return stareCooldown <= 0
                    && stareTimer < TRIGGER_TICKS
                    && getTarget() instanceof Player p
                    && distanceToSqr(p) < 256.0
                    && hasLineOfSight(p);
        }
    }

    /**
     * TorchBreakGoal — finds the nearest torch / soul-torch / lantern within
     * 12 blocks and quietly extinguishes it.  Runs with a cooldown so it
     * doesn't eat every torch in rapid succession.
     */
    private class TorchBreakGoal extends Goal {

        private BlockPos target     = null;
        private int      searchCooldown = 0;

        TorchBreakGoal() { setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        public boolean canUse() {
            if (searchCooldown > 0) { searchCooldown--; return false; }
            if (getTarget() == null) { searchCooldown = 40; return false; }
            target = findNearbyLight();
            if (target == null) { searchCooldown = 40; return false; }
            return true;
        }

        @Override
        public void start() {
            getNavigation().moveTo(
                    target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.1);
        }

        @Override
        public void tick() {
            if (target == null) return;
            // Re-path every second in case something blocked the route
            if (tickCount % 20 == 0) {
                getNavigation().moveTo(
                        target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.1);
            }
            // Close enough to extinguish
            if (distanceToSqr(Vec3.atCenterOf(target)) < 4.0
                    && level() instanceof ServerLevel sl) {
                sl.destroyBlock(target, false);
                sl.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 0.8f);
                sl.sendParticles(ParticleTypes.SMOKE,
                        target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5,
                        5, 0.1, 0.1, 0.1, 0.02);
                target = null;
                searchCooldown = 60; // 3 s before hunting another torch
            }
        }

        @Override
        public boolean canContinueToUse() {
            if (target == null) return false;
            var s = level().getBlockState(target);
            return s.is(Blocks.TORCH)       || s.is(Blocks.WALL_TORCH)
                || s.is(Blocks.SOUL_TORCH)  || s.is(Blocks.SOUL_WALL_TORCH)
                || s.is(Blocks.LANTERN)     || s.is(Blocks.SOUL_LANTERN);
        }

        @Override
        public void stop() {
            getNavigation().stop();
            target = null;
        }

        /** Scans a 24×8×24 area (every-other-block) for light sources. */
        private BlockPos findNearbyLight() {
            BlockPos origin = blockPosition();
            for (int dx = -12; dx <= 12; dx += 2) {
                for (int dz = -12; dz <= 12; dz += 2) {
                    for (int dy = -4; dy <= 4; dy++) {
                        BlockPos p = origin.offset(dx, dy, dz);
                        var s = level().getBlockState(p);
                        if (s.is(Blocks.TORCH)       || s.is(Blocks.WALL_TORCH)
                         || s.is(Blocks.SOUL_TORCH)  || s.is(Blocks.SOUL_WALL_TORCH)
                         || s.is(Blocks.LANTERN)     || s.is(Blocks.SOUL_LANTERN)) {
                            return p;
                        }
                    }
                }
            }
            return null;
        }
    }
}
