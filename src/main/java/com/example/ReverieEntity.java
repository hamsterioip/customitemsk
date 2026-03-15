package com.example;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * The Reverie — a hallucination that emerges from sleep deprivation.
 *
 * Spawns when the player hasn't slept in ≥ 72,000 ticks (3 in-game days).
 * Never physically attacks — only degrades the player's mental state:
 * Hunger, Nausea, Slowness, Mining Fatigue, Weakness.
 *
 * Always invulnerable — vanishes when spotted or approached.
 * Vanishes if the player sleeps (timeSinceRest resets).
 *
 * Three stages (auto-advance every 300 ticks while target is within 40 blocks):
 *  Stage 1: Distant, barely visible, quiet whispers about sleeplessness.
 *  Stage 2: Closer, periodic mental attack (Hunger + Nausea), messages about unreality.
 *  Stage 3: Very close, full mental degradation, then force-vanishes with "...SLEEP."
 */
public class ReverieEntity extends PathfinderMob {

    public static EntityType<ReverieEntity> TYPE;

    private static final double LOOK_DOT_THRESHOLD = Math.cos(Math.toRadians(8.0));
    private static final double VANISH_DIST_SQ     = 8.0 * 8.0;
    private static final int    MAX_LIFE_TICKS      = 72000;
    private static final int    STAGE_ADVANCE_TICKS = 300;
    private static final int    SLEEP_THRESHOLD     = 1000;

    /** Current encounter stage (1–3). Auto-advances over time. */
    public int  stage            = 1;
    /** UUID of the player this Reverie was spawned for. */
    public UUID targetPlayerUUID = null;

    private int  lifeTicks          = 0;
    private int  stageTimer         = 0;
    private int  ambientTimer       = 0;
    private int  mentalAttackTimer  = 0;
    private int  hallucinationTimer = 0;
    private boolean spawnSoundPlayed = false;
    private boolean whispered        = false;

    public ReverieEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setSilent(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH,     20.0)
                .add(Attributes.MOVEMENT_SPEED,  0.0);
    }

    @Override
    protected void registerGoals() {
        // No goals — The Reverie never pathfinds. It is not real.
    }

    // ──────────────────────────────────── tick ────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;

        lifeTicks++;
        stageTimer++;
        ambientTimer--;
        if (mentalAttackTimer  > 0) mentalAttackTimer--;
        if (hallucinationTimer > 0) hallucinationTimer--;

        ServerLevel sl = (ServerLevel) level();

        if (lifeTicks >= MAX_LIFE_TICKS) { vanish(sl, null); return; }

        if (!spawnSoundPlayed && lifeTicks == 1) {
            spawnSoundPlayed = true;
            playSpawnSound(sl);
        }

        // Always face target
        ServerPlayer target = getTargetPlayer(sl);
        if (target != null) {
            double dx  = target.getX() - getX();
            double dy  = target.getEyeY() - getEyeY();
            double dz  = target.getZ() - getZ();
            float  yaw = (float)(Math.atan2(-dx, dz) * (180.0 / Math.PI));
            float  pit = (float)(Math.atan2(-dy, Math.sqrt(dx*dx + dz*dz)) * (180.0 / Math.PI));
            setYRot(yaw);
            setYHeadRot(yaw);
            setXRot(pit);
        }

        // Ambient particles
        emitAmbientParticles(sl);

        if (lifeTicks < 40) return;

        // First whisper
        if (!whispered && lifeTicks == 40) {
            whispered = true;
            whisper(sl, target, "§8§o...when did you last sleep?");
        }

        // Stage advancement — every 300 ticks while target is within 40 blocks
        if (target != null && distanceTo(target) < 40.0 && stage < 3 && stageTimer >= STAGE_ADVANCE_TICKS) {
            stageTimer = 0;
            stage++;
            onStageAdvance(sl, target);
        }

        // Vanish if player slept (timeSinceRest reset)
        if (target != null) {
            int timeSinceRest = target.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
            if (timeSinceRest < SLEEP_THRESHOLD) {
                vanish(sl, target);
                return;
            }
        }

        // Look-detection and proximity vanish (always — The Reverie never attacks)
        List<Player> nearby = sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(30.0));
        for (Player p : nearby) {
            if (distanceToSqr(p) < VANISH_DIST_SQ) { vanish(sl, p); return; }
            Vec3 look = p.getLookAngle();
            Vec3 toMe = getEyePosition().subtract(p.getEyePosition()).normalize();
            if (look.dot(toMe) > LOOK_DOT_THRESHOLD) { vanish(sl, p); return; }
        }

        // Timed stage speech
        playTimedSpeech(sl, target);

        // Mental attack at stage 2+ (periodic, no HP damage)
        if (stage >= 2 && mentalAttackTimer <= 0 && target != null && distanceTo(target) < 40.0) {
            doMentalAttack(sl, target);
            mentalAttackTimer = (stage >= 3) ? 120 : 200;
        }

        // Hallucination sounds from player's own position
        if (stage >= 2 && hallucinationTimer <= 0 && target != null) {
            if (sl.getRandom().nextInt(3) == 0) playHallucinationSounds(sl, target);
            hallucinationTimer = 160 + sl.getRandom().nextInt(80);
        }

        // Ambient sounds
        if (ambientTimer <= 0) {
            playAmbientSounds(sl, target);
            ambientTimer = calculateAmbientInterval();
        }
    }

    // ─────────────────────────── particles ───────────────────────────────────

    private void emitAmbientParticles(ServerLevel sl) {
        // Faint smoke wisps — a form that is barely holding together
        if (lifeTicks % 8 == 0) {
            double ox = (sl.getRandom().nextDouble() - 0.5) * 2.0;
            double oz = (sl.getRandom().nextDouble() - 0.5) * 2.0;
            sl.sendParticles(ParticleTypes.SMOKE,
                    getX() + ox, getY() + 0.5 + sl.getRandom().nextDouble() * 1.5, getZ() + oz,
                    1, 0.1, 0.1, 0.1, 0.002);
        }
        // Stage 2+: soul particles — the exhausted mind made manifest
        if (stage >= 2 && lifeTicks % 12 == 0 && sl.getRandom().nextInt(3) == 0) {
            sl.sendParticles(ParticleTypes.SOUL,
                    getX() + (sl.getRandom().nextDouble() - 0.5) * 1.5,
                    getY() + sl.getRandom().nextDouble() * 2.0,
                    getZ() + (sl.getRandom().nextDouble() - 0.5) * 1.5,
                    1, 0.05, 0.1, 0.05, 0.01);
        }
        // Stage 3: end rods — the hallucination is fragmenting
        if (stage >= 3 && lifeTicks % 10 == 0 && sl.getRandom().nextInt(2) == 0) {
            sl.sendParticles(ParticleTypes.END_ROD,
                    getX() + (sl.getRandom().nextDouble() - 0.5) * 0.8,
                    getY() + sl.getRandom().nextDouble() * 2.2,
                    getZ() + (sl.getRandom().nextDouble() - 0.5) * 0.8,
                    1, 0.05, 0.08, 0.05, 0.02);
        }
    }

    // ──────────────────────── stage advancement ──────────────────────────────

    private void onStageAdvance(ServerLevel sl, ServerPlayer tp) {
        switch (stage) {
            case 2 -> {
                sl.playSound(null, getX(), getY(), getZ(),
                        SoundEvents.SCULK_BLOCK_CHARGE, SoundSource.AMBIENT, 0.6f, 0.3f);
                sl.sendParticles(ParticleTypes.SOUL,
                        getX(), getY() + 1.0, getZ(), 12, 0.4, 0.6, 0.4, 0.03);
                tp.displayClientMessage(
                        Component.literal("§8§o...it's getting closer."), true);
            }
            case 3 -> {
                sl.playSound(null, getX(), getY(), getZ(),
                        SoundEvents.SCULK_BLOCK_CHARGE, SoundSource.AMBIENT, 0.8f, 0.2f);
                sl.sendParticles(ParticleTypes.SOUL,
                        getX(), getY() + 1.0, getZ(), 25, 0.6, 0.8, 0.6, 0.05);
                sl.sendParticles(ParticleTypes.END_ROD,
                        getX(), getY() + 1.0, getZ(), 10, 0.4, 0.6, 0.4, 0.04);
                tp.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 40, 0, false, false));
                tp.displayClientMessage(
                        Component.literal("§8§o...none of this is real."), true);
            }
        }
    }

    // ────────────────────────────── speech ───────────────────────────────────

    private void whisper(ServerLevel sl, ServerPlayer tp, String msg) {
        if (tp != null) {
            tp.displayClientMessage(Component.literal(msg), true);
        } else {
            sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(40.0))
              .forEach(p -> p.displayClientMessage(Component.literal(msg), true));
        }
    }

    private void playTimedSpeech(ServerLevel sl, ServerPlayer tp) {
        if (tp == null) return;
        double ex = getX(), ey = getY(), ez = getZ();

        if (stage == 1) {
            if (stageTimer == 80) {
                tp.displayClientMessage(Component.literal("§8§o...you feel observed."), true);
            } else if (stageTimer == 180) {
                tp.displayClientMessage(
                        Component.literal("§8§o...three days. Has it been three days?"), true);
                sl.playSound(null, ex, ey, ez,
                        SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, 0.2f, 0.25f);
            }
        } else if (stage == 2) {
            if (stageTimer == 60) {
                tp.displayClientMessage(
                        Component.literal("§8§o...the ground moves when you aren't watching."), true);
                sl.playSound(null, ex, ey, ez,
                        SoundEvents.SCULK_BLOCK_CHARGE, SoundSource.AMBIENT, 0.4f, 0.35f);
            } else if (stageTimer == 140) {
                tp.displayClientMessage(
                        Component.literal("§8§o...I am not really here.  Neither are you."), true);
                sl.playSound(null, ex, ey, ez,
                        SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, 0.3f, 0.2f);
            } else if (stageTimer == 240) {
                tp.displayClientMessage(
                        Component.literal("§8§oYou built this.  Every block.  Every night without rest."), true);
                sl.playSound(null, tp.getX(), tp.getY(), tp.getZ(),
                        SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 0.4f, 0.7f);
            }
        } else if (stage >= 3) {
            if (stageTimer == 40) {
                tp.displayClientMessage(
                        Component.literal("§4§lYOUR HANDS ARE NOT YOUR HANDS."), true);
                sl.playSound(null, tp.getX(), tp.getY(), tp.getZ(),
                        SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 0.6f, 0.5f);
                sl.sendParticles(ParticleTypes.SOUL,
                        tp.getX(), tp.getY() + 1, tp.getZ(), 20, 1.0, 0.5, 1.0, 0.05);
            } else if (stageTimer == 100) {
                tp.displayClientMessage(
                        Component.literal("§4§lCLOSE YOUR EYES.  THEY ARE ALREADY CLOSED."), true);
                sl.playSound(null, tp.getX(), tp.getY(), tp.getZ(),
                        SoundEvents.SCULK_BLOCK_CHARGE, SoundSource.AMBIENT, 0.7f, 0.2f);
                tp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 30, 0, false, false));
            } else if (stageTimer == 180) {
                tp.displayClientMessage(Component.literal("§4§l...SLEEP."), true);
                sl.playSound(null, tp.getX(), tp.getY(), tp.getZ(),
                        SoundEvents.WARDEN_ROAR, SoundSource.AMBIENT, 0.5f, 0.4f);
                sl.sendParticles(ParticleTypes.SOUL,
                        tp.getX(), tp.getY() + 0.5, tp.getZ(), 30, 1.5, 1.0, 1.5, 0.05);
                // Hallucination completes — vanish
                vanish(sl, tp);
            }
        }
    }

    // ─────────────────────────── mental attack ───────────────────────────────

    /** Degrades the player's mental state — no HP damage, ever. */
    private void doMentalAttack(ServerLevel sl, ServerPlayer tp) {
        // Always: hunger drains, screen warps
        tp.addEffect(new MobEffectInstance(MobEffects.HUNGER,  200, 1, false, false));
        tp.addEffect(new MobEffectInstance(MobEffects.NAUSEA,  100, 0, false, false));
        // Stage 3: full degradation
        if (stage >= 3) {
            tp.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,       100, 1, false, false));
            tp.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 100, 0, false, false));
            tp.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,       100, 0, false, false));
        }
        sl.playSound(null, tp.getX(), tp.getY(), tp.getZ(),
                SoundEvents.WARDEN_AMBIENT, SoundSource.AMBIENT, 0.5f, 0.3f);
    }

    // ──────────────────────── hallucination sounds ───────────────────────────

    /** Plays fake mob sounds at the PLAYER's position — manufactured by their exhausted mind. */
    private void playHallucinationSounds(ServerLevel sl, ServerPlayer tp) {
        double px = tp.getX(), py = tp.getY(), pz = tp.getZ();
        switch (sl.getRandom().nextInt(6)) {
            case 0 -> sl.playSound(null, px, py, pz,
                    SoundEvents.ZOMBIE_AMBIENT,   SoundSource.HOSTILE, 0.4f, 0.8f);
            case 1 -> sl.playSound(null, px, py, pz,
                    SoundEvents.SKELETON_AMBIENT, SoundSource.HOSTILE, 0.35f, 0.9f);
            case 2 -> sl.playSound(null, px, py, pz,
                    SoundEvents.ENDERMAN_STARE,   SoundSource.HOSTILE, 0.3f, 0.6f);
            case 3 -> sl.playSound(null, px, py, pz,
                    SoundEvents.SPIDER_AMBIENT,   SoundSource.HOSTILE, 0.35f, 0.7f);
            case 4 -> sl.playSound(null, px, py, pz,
                    SoundEvents.AMBIENT_CAVE,     SoundSource.AMBIENT, 0.4f, 0.2f);
            case 5 -> sl.playSound(null, px, py, pz,
                    SoundEvents.SCULK_BLOCK_CHARGE, SoundSource.AMBIENT, 0.3f, 0.15f);
        }
    }

    // ──────────────────────────── ambient sounds ─────────────────────────────

    private int calculateAmbientInterval() {
        return switch (stage) {
            case 1 -> 200 + getRandom().nextInt(100);
            case 2 -> 140 + getRandom().nextInt(80);
            default -> 80  + getRandom().nextInt(60);
        };
    }

    private void playAmbientSounds(ServerLevel sl, ServerPlayer tp) {
        double ex = getX(), ey = getY(), ez = getZ();
        switch (stage) {
            case 1 -> sl.playSound(null, ex, ey, ez,
                    SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, 0.2f, 0.25f);
            case 2 -> {
                sl.playSound(null, ex, ey, ez,
                        SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, 0.3f, 0.3f);
                if (sl.getRandom().nextInt(2) == 0)
                    sl.playSound(null, ex, ey, ez,
                            SoundEvents.SCULK_BLOCK_CHARGE, SoundSource.AMBIENT, 0.3f, 0.3f);
            }
            default -> {
                sl.playSound(null, ex, ey, ez,
                        SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, 0.5f, 0.2f);
                sl.playSound(null, ex, ey, ez,
                        SoundEvents.WARDEN_AMBIENT, SoundSource.AMBIENT, 0.4f, 0.3f);
                if (tp != null && sl.getRandom().nextInt(3) == 0)
                    sl.playSound(null, tp.getX(), tp.getY(), tp.getZ(),
                            SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 0.5f, 0.6f);
            }
        }
    }

    private void playSpawnSound(ServerLevel sl) {
        // The Reverie simply materializes — no dramatic entrance
        sl.playSound(null, getX(), getY(), getZ(),
                SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, 0.3f, 0.2f);
        sl.sendParticles(ParticleTypes.SOUL,
                getX(), getY() + 0.3, getZ(), 4, 0.3, 0.2, 0.3, 0.01);
    }

    // ─────────────────────────────── vanish ──────────────────────────────────

    private void vanish(ServerLevel sl, Player trigger) {
        sl.sendParticles(ParticleTypes.SOUL,
                getX(), getY() + 1.0, getZ(), 20, 0.5, 0.6, 0.5, 0.06);
        sl.sendParticles(ParticleTypes.SMOKE,
                getX(), getY() + 0.5, getZ(), 12, 0.4, 0.4, 0.4, 0.02);
        if (stage >= 3) {
            sl.sendParticles(ParticleTypes.END_ROD,
                    getX(), getY() + 1.0, getZ(), 15, 0.4, 0.6, 0.4, 0.04);
        }
        sl.playSound(null, getX(), getY(), getZ(),
                SoundEvents.SCULK_BLOCK_CHARGE, SoundSource.AMBIENT, 0.5f, 0.15f);

        if (trigger instanceof ServerPlayer sp) {
            if (stage >= 2)
                sp.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 60, 0, false, false));
            String msg = switch (stage) {
                case 1  -> "§8§o...it was only tiredness.";
                case 2  -> "§8§oYou looked at it.  It smiled.";
                default -> "§4It knows you won't sleep tonight either.";
            };
            sp.sendSystemMessage(Component.literal(msg));
        }

        discard();
    }

    // ───────────────────────────── combat ────────────────────────────────────

    @Override
    public boolean isInvulnerable() { return true; }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // Always vanishes when hit — it is not real
        Player attacker = (source.getEntity() instanceof Player p) ? p : null;
        vanish(level, attacker);
        return false;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        // Mental effects only — never HP damage
        if (target instanceof ServerPlayer sp) doMentalAttack(level, sp);
        return true;
    }

    // ─────────────────────────────── helpers ─────────────────────────────────

    private ServerPlayer getTargetPlayer(ServerLevel sl) {
        if (targetPlayerUUID == null) return null;
        return sl.getServer().getPlayerList().getPlayer(targetPlayerUUID);
    }

    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean shouldShowName()            { return false; }
}
