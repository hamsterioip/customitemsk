package com.example;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * The Hollow — a featureless void-creature that haunts underground.
 *
 * Design (mirrors WatcherEntity):
 *  • No pathfinding — teleports instead.
 *  • Invulnerable at stages 1–3 (watching phases).  Vanishes when spotted.
 *  • Stage 4: drops invulnerability, teleport-lunges at target, can be killed.
 *  • Void Shards only drop when killed at stage 4 (not farmable).
 *  • Unique mechanic: extinguishes nearby torches at stage 2+.
 *  • Sends HollowStarePacket (HUD vignette — fullbright-proof) at stage 3+.
 *  • 30 % split on death: a successor spawns from nearby darkness.
 *
 *  Stage 1 (0–20 s) : distant, silent, barely visible.
 *  Stage 2 (20–40 s): starts breaking torches, first whispers.
 *  Stage 3 (40–60 s): sends stare overlay, heartbeat, creeps closer.
 *  Stage 4 (60 s+)  : aggressive, vulnerable, lunge-teleport hunts player.
 */
public class HollowEntity extends PathfinderMob {

    public static EntityType<HollowEntity> TYPE;

    // Detection constants
    private static final double LOOK_DOT_THRESHOLD = Math.cos(Math.toRadians(8.0));
    private static final double VANISH_DIST_SQ     = 10.0 * 10.0;
    private static final int    MAX_LIFE_TICKS      = 72000; // 1-hour fail-safe

    /** Current encounter stage (1–4). Advances automatically. */
    public int  stage            = 1;
    /** UUID of the player this Hollow was spawned for. */
    public UUID targetPlayerUUID = null;

    private int     lifeTicks       = 0;
    private int     stageTimer      = 0;   // ticks in current stage; resets on advance
    private int     ambientTimer    = 0;
    private int     torchCooldown   = 0;
    private int     breathingTimer  = 0;
    private int     flickerTimer    = 0;
    private int     lurchTimer      = 0;   // stage 4 lunge cooldown
    private boolean spawnSoundPlayed = false;
    private boolean whispered        = false;

    public HollowEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setSilent(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH,          65.0)
                .add(Attributes.MOVEMENT_SPEED,        0.0)
                .add(Attributes.ATTACK_DAMAGE,         11.0)
                .add(Attributes.FOLLOW_RANGE,          48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE,   0.5)
                .add(Attributes.ARMOR,                  4.0);
    }

    @Override
    protected void registerGoals() {
        // No goals — The Hollow never pathfinds.
    }

    // ─────────────────────────────────── tick ─────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;

        lifeTicks++;
        stageTimer++;
        ambientTimer--;
        if (torchCooldown  > 0) torchCooldown--;
        if (breathingTimer > 0) breathingTimer--;
        if (flickerTimer   > 0) flickerTimer--;
        if (lurchTimer     > 0) lurchTimer--;

        ServerLevel sl = (ServerLevel) level();

        // Fail-safe despawn
        if (lifeTicks >= MAX_LIFE_TICKS) { vanish(sl, null); return; }

        // One-time spawn sound
        if (!spawnSoundPlayed && lifeTicks == 1) {
            spawnSoundPlayed = true;
            playSpawnSound(sl);
        }

        // Always face the target player
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

        // Ambient sculk-soul particles (only in dim light)
        if (lifeTicks % 6 == 0) {
            int light = sl.getMaxLocalRawBrightness(blockPosition());
            if (light <= 8) {
                sl.sendParticles(ParticleTypes.SCULK_SOUL,
                        getX(), getY() + 0.5, getZ(), 1, 0.15, 0.2, 0.15, 0.005);
            }
        }
        // Stage 3+: smoke tendrils
        if (stage >= 3 && lifeTicks % 5 == 0) {
            double ox = (sl.getRandom().nextDouble() - 0.5) * 3.0;
            double oz = (sl.getRandom().nextDouble() - 0.5) * 3.0;
            sl.sendParticles(ParticleTypes.LARGE_SMOKE,
                    getX() + ox, getY() + 0.1 + sl.getRandom().nextDouble() * 0.5, getZ() + oz,
                    1, 0.2, 0.1, 0.2, 0.002);
        }

        // Startup delay
        if (lifeTicks < 40) return;

        // First whisper (once, at 2 s)
        if (!whispered && lifeTicks == 40) {
            whispered = true;
            playWhisper(sl);
        }

        // Stage advancement — auto-escalate every 400 ticks (~20 s) while target is near
        if (target != null && distanceTo(target) < 35.0 && stage < 4 && stageTimer >= 400) {
            stageTimer = 0;
            stage++;
            onStageAdvance(sl, target);
        }

        // Timed stage speech
        playTimedSpeech(sl, target);

        // Warden heartbeat at stage 3+ when target is within 25 blocks
        if (stage >= 3 && lifeTicks % 44 == 0 && target != null && distanceTo(target) < 25.0) {
            sl.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 0.5f, 0.7f);
        }

        // Darkness pulses at stage 3+, send stare overlay at stage 4+
        if (stage >= 3 && lifeTicks % 80 == 0 && target != null) {
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
            if (stage >= 4) {
                ServerPlayNetworking.send(target, new HollowStarePacket());
            }
        }

        // Torch extinguishing at stage 2+ (passive reach, no movement)
        if (stage >= 2 && torchCooldown <= 0) {
            BlockPos torch = findNearbyTorch(sl);
            if (torch != null) {
                sl.destroyBlock(torch, false);
                sl.playSound(null, torch.getX(), torch.getY(), torch.getZ(),
                        SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 1.0f, 0.9f);
                sl.sendParticles(ParticleTypes.SMOKE,
                        torch.getX() + 0.5, torch.getY() + 0.5, torch.getZ() + 0.5,
                        6, 0.1, 0.1, 0.1, 0.02);
                torchCooldown = 80; // 4 s between breaks
            }
        }

        // Creep 3 blocks closer when player looks away (stage 3+)
        if (stage >= 3 && lifeTicks % 120 == 0 && target != null && distanceTo(target) > 12.0) {
            Vec3 look  = target.getLookAngle();
            Vec3 toMe  = getEyePosition().subtract(target.getEyePosition()).normalize();
            if (look.dot(toMe) <= Math.cos(Math.toRadians(50))) {
                creepCloser(sl, target);
            }
        }

        // Proximity / look-detection: vanish at stages 1-3
        if (stage < 4) {
            List<Player> nearby = sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(30.0));
            for (Player p : nearby) {
                if (distanceToSqr(p) < VANISH_DIST_SQ)           { vanish(sl, p); return; }
                Vec3 look  = p.getLookAngle();
                Vec3 toMe  = getEyePosition().subtract(p.getEyePosition()).normalize();
                if (look.dot(toMe) > LOOK_DOT_THRESHOLD)          { vanish(sl, p); return; }
            }
        }

        // Stage 4: lunge-teleport toward target and attack
        if (stage >= 4 && target != null) {
            double dist = distanceTo(target);
            if (dist > 3.5 && dist < 40.0 && lurchTimer <= 0) {
                lungeToward(sl, target);
                lurchTimer = 50;
            }
            if (dist <= 3.5 && lifeTicks % 20 == 0) {
                doHurtTarget(sl, target);
            }
        }

        // Breathing sounds at stage 3+ (heard at player position)
        if (stage >= 3 && breathingTimer <= 0 && target != null && distanceTo(target) < 20.0) {
            sl.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.WARDEN_NEARBY_CLOSER, SoundSource.AMBIENT, 0.3f, 0.5f);
            breathingTimer = 100 + sl.getRandom().nextInt(80);
        }

        // Flicker / glitch micro-teleport at stage 4
        if (stage >= 4 && flickerTimer <= 0) {
            if (sl.getRandom().nextInt(4) == 0) doFlickerMovement(sl);
            flickerTimer = 60 + sl.getRandom().nextInt(80);
        }

        // Ambient sounds
        if (ambientTimer <= 0) {
            playAmbientSounds(sl);
            ambientTimer = calculateAmbientInterval();
        }
    }

    // ───────────────────────────── stage advancement ──────────────────────────

    private void onStageAdvance(ServerLevel sl, ServerPlayer tp) {
        switch (stage) {
            case 2 -> {
                sl.playSound(null, getX(), getY(), getZ(),
                        SoundEvents.SCULK_BLOCK_CHARGE, SoundSource.HOSTILE, 0.8f, 0.25f);
                sl.sendParticles(ParticleTypes.SCULK_SOUL,
                        getX(), getY() + 0.5, getZ(), 15, 0.3, 0.6, 0.3, 0.05);
                tp.displayClientMessage(Component.literal("§8§o...it knows you're here."), true);
            }
            case 3 -> {
                sl.playSound(null, getX(), getY(), getZ(),
                        SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 0.6f, 0.25f);
                sl.sendParticles(ParticleTypes.SCULK_SOUL,
                        getX(), getY() + 0.5, getZ(), 30, 0.5, 0.8, 0.5, 0.06);
                tp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
                ServerPlayNetworking.send(tp, new HollowStarePacket());
                tp.displayClientMessage(Component.literal("§8§o...the lights are going out."), true);
            }
            case 4 -> {
                // Becomes vulnerable and aggressive
                setSilent(false);
                sl.playSound(null, getX(), getY(), getZ(),
                        SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.0f, 0.5f);
                sl.playSound(null, tp.getX(), tp.getY(), tp.getZ(),
                        SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 1.0f, 0.3f);
                sl.sendParticles(ParticleTypes.SCULK_SOUL,
                        getX(), getY() + 0.5, getZ(), 50, 1.0, 1.0, 1.0, 0.08);
                tp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0, false, false));
                tp.addEffect(new MobEffectInstance(MobEffects.NAUSEA,    80, 0, false, false));
                ServerPlayNetworking.send(tp, new HollowStarePacket());
                tp.displayClientMessage(Component.literal("§4§lNOWHERE LEFT TO HIDE."), true);
            }
        }
        playWhisper(sl);
    }

    // ────────────────────────── torch extinguishing ───────────────────────────

    /** Scans a 20×6×20 area for any torch or lantern. */
    private BlockPos findNearbyTorch(ServerLevel sl) {
        BlockPos origin = blockPosition();
        for (int dx = -10; dx <= 10; dx += 2) {
            for (int dz = -10; dz <= 10; dz += 2) {
                for (int dy = -3; dy <= 3; dy++) {
                    BlockPos p = origin.offset(dx, dy, dz);
                    var s = sl.getBlockState(p);
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

    // ───────────────────────────────── sounds ─────────────────────────────────

    private void playSpawnSound(ServerLevel sl) {
        double tx = getX(), ty = getY(), tz = getZ();
        switch (stage) {
            case 1 -> sl.playSound(null, tx, ty, tz,
                    SoundEvents.SCULK_BLOCK_CHARGE, SoundSource.AMBIENT, 0.3f, 0.2f);
            case 2 -> {
                sl.playSound(null, tx, ty, tz,
                        SoundEvents.SCULK_BLOCK_CHARGE, SoundSource.AMBIENT, 0.5f, 0.3f);
                sl.sendParticles(ParticleTypes.SCULK_SOUL, tx, ty + 0.5, tz, 12, 0.3, 0.5, 0.3, 0.03);
            }
            case 3 -> {
                sl.playSound(null, tx, ty, tz,
                        SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.AMBIENT, 0.6f, 0.3f);
                ServerPlayer tp = getTargetPlayer(sl);
                if (tp != null) tp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false));
                sl.sendParticles(ParticleTypes.SCULK_SOUL, tx, ty + 0.5, tz, 25, 0.5, 0.8, 0.5, 0.05);
            }
            default -> {
                setSilent(false);
                sl.playSound(null, tx, ty, tz,
                        SoundEvents.WARDEN_ROAR, SoundSource.AMBIENT, 1.2f, 0.5f);
                ServerPlayer tp = getTargetPlayer(sl);
                if (tp != null) {
                    sl.playSound(null, tp.getX(), tp.getY(), tp.getZ(),
                            SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.AMBIENT, 1.0f, 0.3f);
                    tp.addEffect(new MobEffectInstance(MobEffects.DARKNESS,  80, 0, false, false));
                    tp.addEffect(new MobEffectInstance(MobEffects.NAUSEA,    60, 0, false, false));
                }
                sl.sendParticles(ParticleTypes.SCULK_SOUL, tx, ty + 0.5, tz, 40, 1.0, 1.0, 1.0, 0.08);
            }
        }
    }

    private void playWhisper(ServerLevel sl) {
        ServerPlayer tp = getTargetPlayer(sl);
        String msg = switch (stage) {
            case 1 -> "§8§o...something stirs in the dark.";
            case 2 -> "§8§o...the dark is not empty.";
            case 3 -> "§8§o...the light cannot protect you.";
            default -> "§4§lNOWHERE LEFT TO HIDE.";
        };
        if (tp != null) {
            tp.displayClientMessage(Component.literal(msg), true);
        } else {
            sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(40.0))
              .forEach(p -> p.displayClientMessage(Component.literal(msg), true));
        }
    }

    private void playTimedSpeech(ServerLevel sl, ServerPlayer tp) {
        if (tp == null) return;
        if (stage == 2) {
            if      (stageTimer == 80)  tp.displayClientMessage(Component.literal("§8§o...it followed you down here."), true);
            else if (stageTimer == 160) tp.displayClientMessage(Component.literal("§8§o...your torch will fail."), true);
            else if (stageTimer == 260) {
                tp.displayClientMessage(Component.literal("§8§oSomething is watching your hands."), true);
                sl.playSound(null, getX(), getY(), getZ(),
                        SoundEvents.SCULK_BLOCK_CHARGE, SoundSource.AMBIENT, 0.4f, 0.2f);
            }
        } else if (stage == 3) {
            if      (stageTimer == 80) {
                tp.displayClientMessage(Component.literal("§8§o...one by one the lights go out."), true);
                sl.playSound(null, getX(), getY(), getZ(),
                        SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.AMBIENT, 0.4f, 0.25f);
            }
            else if (stageTimer == 160) {
                tp.displayClientMessage(Component.literal("§4Your map shows only darkness."), true);
                sl.playSound(null, tp.getX(), tp.getY(), tp.getZ(),
                        SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 0.5f, 0.6f);
            }
            else if (stageTimer == 260) {
                tp.displayClientMessage(Component.literal("§4§oIt has your scent."), true);
                sl.playSound(null, getX(), getY(), getZ(),
                        SoundEvents.WARDEN_AMBIENT, SoundSource.AMBIENT, 0.5f, 0.4f);
                ServerPlayNetworking.send(tp, new HollowStarePacket());
            }
        } else if (stage >= 4) {
            if      (stageTimer == 70) {
                tp.displayClientMessage(Component.literal("§4§lTHERE IS NO LIGHT LEFT."), true);
                sl.playSound(null, tp.getX(), tp.getY(), tp.getZ(),
                        SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.AMBIENT, 0.7f, 0.3f);
                sl.sendParticles(ParticleTypes.SCULK_SOUL,
                        tp.getX(), tp.getY() + 1, tp.getZ(), 15, 0.5, 0.5, 0.5, 0.06);
            }
            else if (stageTimer == 140) {
                tp.displayClientMessage(Component.literal("§4§lIT KNOWS YOUR FACE."), true);
                sl.playSound(null, tp.getX(), tp.getY(), tp.getZ(),
                        SoundEvents.WARDEN_ROAR, SoundSource.AMBIENT, 0.5f, 0.6f);
                tp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
            }
            else if (stageTimer == 220) {
                tp.displayClientMessage(Component.literal("§4§l...COME."), true);
                sl.playSound(null, tp.getX(), tp.getY(), tp.getZ(),
                        SoundEvents.WARDEN_SONIC_BOOM, SoundSource.AMBIENT, 0.5f, 0.4f);
            }
        }
    }

    private int calculateAmbientInterval() {
        return switch (stage) {
            case 1 -> 200 + getRandom().nextInt(100);
            case 2 -> 150 + getRandom().nextInt(80);
            case 3 -> 100 + getRandom().nextInt(60);
            default -> 60  + getRandom().nextInt(40);
        };
    }

    private void playAmbientSounds(ServerLevel sl) {
        double tx = getX(), ty = getY(), tz = getZ();
        switch (stage) {
            case 1 -> sl.playSound(null, tx, ty, tz,
                    SoundEvents.SCULK_BLOCK_CHARGE, SoundSource.AMBIENT, 0.2f, 0.15f);
            case 2 -> {
                sl.playSound(null, tx, ty, tz,
                        SoundEvents.SCULK_BLOCK_CHARGE, SoundSource.AMBIENT, 0.3f, 0.2f);
                if (sl.getRandom().nextInt(2) == 0)
                    sl.playSound(null, tx, ty, tz,
                            SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, 0.2f, 0.3f);
            }
            case 3 -> {
                sl.playSound(null, tx, ty, tz,
                        SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.AMBIENT, 0.25f, 0.25f);
                if (sl.getRandom().nextInt(3) == 0)
                    sl.playSound(null, tx, ty, tz,
                            SoundEvents.WARDEN_AMBIENT, SoundSource.AMBIENT, 0.3f, 0.4f);
            }
            default -> {
                sl.playSound(null, tx, ty, tz,
                        SoundEvents.WARDEN_AMBIENT, SoundSource.AMBIENT, 0.5f, 0.3f);
                if (sl.getRandom().nextInt(2) == 0)
                    sl.playSound(null, tx, ty, tz,
                            SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.AMBIENT, 0.3f, 0.3f);
            }
        }
    }

    // ──────────────────────── movement (teleport-based) ───────────────────────

    /** Silently teleports 3 blocks toward the target while they look away. */
    private void creepCloser(ServerLevel sl, ServerPlayer tp) {
        Vec3 toward = new Vec3(tp.getX() - getX(), 0, tp.getZ() - getZ()).normalize();
        double newX = getX() + toward.x * 3.0;
        double newZ = getZ() + toward.z * 3.0;
        if (positionClear(sl, newX, getY(), newZ)) {
            sl.sendParticles(ParticleTypes.SCULK_SOUL,
                    getX(), getY() + 0.8, getZ(), 3, 0.15, 0.3, 0.15, 0.01);
            sl.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.SCULK_BLOCK_CHARGE, SoundSource.AMBIENT, 0.3f, 0.15f);
            teleportTo(newX, getY(), newZ);
            sl.sendParticles(ParticleTypes.SCULK_SOUL,
                    newX, getY() + 0.8, newZ, 3, 0.1, 0.2, 0.1, 0.01);
        }
    }

    /** Aggressive lunge at stage 4: teleports to within striking range. */
    private void lungeToward(ServerLevel sl, ServerPlayer tp) {
        Vec3 toward = new Vec3(tp.getX() - getX(), 0, tp.getZ() - getZ()).normalize();
        double newX = tp.getX() - toward.x * 2.5;
        double newZ = tp.getZ() - toward.z * 2.5;
        if (positionClear(sl, newX, tp.getY(), newZ)) {
            sl.sendParticles(ParticleTypes.SCULK_SOUL,
                    getX(), getY() + 1.0, getZ(), 8, 0.3, 0.5, 0.3, 0.06);
            teleportTo(newX, tp.getY(), newZ);
            sl.sendParticles(ParticleTypes.SCULK_SOUL,
                    newX, tp.getY() + 1.0, newZ, 8, 0.3, 0.5, 0.3, 0.06);
            sl.playSound(null, newX, tp.getY(), newZ,
                    SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 0.7f, 0.3f);
        }
    }

    /** Small random glitch-teleport at stage 4. */
    private void doFlickerMovement(ServerLevel sl) {
        double ox = (sl.getRandom().nextDouble() - 0.5) * 2.0;
        double oz = (sl.getRandom().nextDouble() - 0.5) * 2.0;
        double nx = getX() + ox, nz = getZ() + oz;
        if (positionClear(sl, nx, getY(), nz)) {
            sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1.0, getZ(), 5, 0.2, 0.4, 0.2, 0.02);
            teleportTo(nx, getY(), nz);
            sl.sendParticles(ParticleTypes.END_ROD, nx, getY() + 1.0, nz, 5, 0.2, 0.4, 0.2, 0.02);
            sl.playSound(null, nx, getY(), nz,
                    SoundEvents.SCULK_BLOCK_CHARGE, SoundSource.AMBIENT, 0.2f, 0.15f);
        }
    }

    private boolean positionClear(ServerLevel sl, double x, double y, double z) {
        BlockPos feet = BlockPos.containing(x, y + 0.1, z);
        BlockPos head = feet.above();
        return (sl.getBlockState(feet).isAir() || sl.getBlockState(feet).is(net.minecraft.tags.BlockTags.REPLACEABLE))
            && (sl.getBlockState(head).isAir() || sl.getBlockState(head).is(net.minecraft.tags.BlockTags.REPLACEABLE));
    }

    // ──────────────────────────────── vanish ──────────────────────────────────

    private void vanish(ServerLevel sl, Player trigger) {
        sl.sendParticles(ParticleTypes.SCULK_SOUL,
                getX(), getY() + 0.8, getZ(), 25, 0.5, 0.8, 0.5, 0.07);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE,
                getX(), getY() + 1.0, getZ(), 15, 0.5, 0.6, 0.5, 0.02);
        sl.playSound(null, getX(), getY(), getZ(),
                SoundEvents.SCULK_BLOCK_CHARGE, SoundSource.AMBIENT, 0.7f, 0.15f);

        if (trigger != null) {
            if (stage >= 3) {
                trigger.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
                trigger.addEffect(new MobEffectInstance(MobEffects.NAUSEA,   80, 0, false, false));
                if (trigger instanceof ServerPlayer sp)
                    ServerPlayNetworking.send(sp, new HollowStarePacket());
            } else {
                trigger.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20, 0, false, false));
            }

            if (stage >= 2 && trigger instanceof ServerPlayer sp) {
                String msg = switch (stage) {
                    case 2 -> "§8§o...it will return.";
                    case 3 -> "§8§oYou saw it.  Now it knows you're afraid.";
                    default -> "§4It will not leave the dark next time.";
                };
                sp.sendSystemMessage(Component.literal(msg));
            }
        }

        discard();
    }

    // ──────────────────────────────── combat ──────────────────────────────────

    /**
     * At stages 1-3 The Hollow is invulnerable; any hit triggers a vanish.
     * At stage 4 it can be killed (vulnerable), but takes 50% extra in bright light.
     */
    @Override
    public boolean isInvulnerable() {
        return stage < 4;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (stage < 4) {
            // Invulnerable — vanish if hit by creative/bypassing damage
            Player attacker = (source.getEntity() instanceof Player p) ? p : null;
            vanish(level, attacker);
            return false;
        }
        // Stage 4 — vulnerable
        int light = level.getMaxLocalRawBrightness(blockPosition());
        if (light >= 10) amount *= 1.5f;
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

            // 30% chance to split into a new stage-1 Hollow from nearby darkness
            if (random.nextFloat() < 0.3f) spawnSuccessor(sl);

            // Void Shards only drop at stage 4 (cannot be farmed during watching)
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
                    next.targetPlayerUUID = targetPlayerUUID;
                    next.stage = 1;
                    sl.addFreshEntity(next);
                    sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(20.0))
                      .forEach(p -> p.displayClientMessage(
                              Component.literal("§0§k..§8§oThe darkness splits...§0§k..§r"), true));
                    return;
                }
            }
        }
    }

    // ─────────────────────────────────── helpers ──────────────────────────────

    private ServerPlayer getTargetPlayer(ServerLevel sl) {
        if (targetPlayerUUID == null) return null;
        return sl.getServer().getPlayerList().getPlayer(targetPlayerUUID);
    }

    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean canBreatheUnderwater()       { return true;  }
    @Override public boolean shouldShowName()              { return false; }
}
