package com.example;

import net.minecraft.core.BlockPos;
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
 * The Reverie — a hallucination born from sleep deprivation.
 *
 * Spawns when the player hasn't slept in >= 72,000 ticks (3 in-game days).
 * Never physically attacks. Instead it dismantles the player's perception:
 *
 *  • Footsteps that play from BEHIND the player with no source
 *  • Fake mob sounds (zombie, enderman, creeper) at the player's own ears
 *  • Chat-level paranoid messages that look like server/player messages
 *  • Periodic Darkness pulses and full-screen Nausea
 *  • Hunger drain, Slowness, Mining Fatigue, Weakness at stage 3
 *
 * Stage 1: Distant, occasional whispers, faint footsteps behind you.
 * Stage 2: Mental attacks begin, paranoid chat messages, hallucination sounds,
 *           creeper hiss with no creeper, breathing on your neck.
 * Stage 3: Full psychological assault. Ends with "...SLEEP." then vanishes.
 */
public class ReverieEntity extends PathfinderMob {

    public static EntityType<ReverieEntity> TYPE;

    private static final double LOOK_DOT_THRESHOLD    = Math.cos(Math.toRadians(8.0));
    private static final double FLEE_DIST_SQ          = 8.0 * 8.0;
    private static final int    STAGE_ADVANCE_TICKS   = 300;
    private static final int    SLEEP_THRESHOLD       = 1000;
    private static final int    TELEPORT_COOLDOWN     = 60;
    private static final double CREEP_DIST_START      = 18.0; // initial behind-distance
    private static final double CREEP_DIST_MIN        = 5.5;  // closest it ever gets
    private static final double CREEP_RATE            = 0.003;// blocks/tick it inches closer

    /** Current encounter stage (1–3). Auto-advances over time. */
    public int  stage            = 1;
    /** UUID of the player this Reverie was spawned for. */
    public UUID targetPlayerUUID = null;

    private int  lifeTicks          = 0;
    private int  stageTimer         = 0;
    private int  ambientTimer       = 0;
    private int  mentalAttackTimer  = 0;
    private int  hallucinationTimer = 0;
    private int  paranoidTimer      = 0;   // footsteps + behind-sounds
    private int  chatParanoidTimer  = 0;   // fake chat messages
    private int  darknessTimer      = 0;   // random darkness pulses
    private int    tpCooldown         = 0;   // prevent teleporting every tick when spotted
    private int    sleepDisplayTimer  = 2400;// countdown for sleep-deprivation reminder
    private int    flickerTimer       = 300; // stage 1: controls when to briefly go invisible
    private double creepDist          = CREEP_DIST_START; // behind-teleport distance
    private int    spotCount          = 0;   // how many times player has caught a glimpse
    private boolean spawnSoundPlayed  = false;
    private boolean whispered         = false;

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
        // No goals — The Reverie is not real.
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
        if (paranoidTimer      > 0) paranoidTimer--;
        if (chatParanoidTimer  > 0) chatParanoidTimer--;
        if (darknessTimer      > 0) darknessTimer--;
        if (tpCooldown         > 0) tpCooldown--;
        if (sleepDisplayTimer  > 0) sleepDisplayTimer--;
        if (flickerTimer       > 0) flickerTimer--;

        ServerLevel sl = (ServerLevel) level();

        if (!spawnSoundPlayed && lifeTicks == 1) {
            spawnSoundPlayed = true;
            playSpawnSound(sl);
        }

        // Always face the target
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

        // Stage 1 flicker — briefly go invisible to gaslight the player
        if (stage == 1 && target != null && flickerTimer <= 0) {
            addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,
                    4 + getRandom().nextInt(6), 0, false, false));
            flickerTimer = 280 + getRandom().nextInt(240);
        }

        emitAmbientParticles(sl);

        if (lifeTicks < 40) return;

        // First whisper
        if (!whispered && lifeTicks == 40) {
            whispered = true;
            whisper(sl, target, "§8§o...when did you last sleep?");
        }

        // Stage advancement
        if (target != null && distanceTo(target) < 40.0 && stage < 3 && stageTimer >= STAGE_ADVANCE_TICKS) {
            stageTimer = 0;
            stage++;
            onStageAdvance(sl, target);
        }

        // Vanish if player slept + sleep deprivation reminder
        if (target != null) {
            int timeSinceRest = target.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
            if (timeSinceRest < SLEEP_THRESHOLD) { vanish(sl, target); return; }
            if (stage >= 2 && sleepDisplayTimer <= 0) {
                int nights = timeSinceRest / 24000;
                String sleepMsg;
                if      (nights <= 0) sleepMsg = "§8§o...you should have slept by now.";
                else if (nights == 1) sleepMsg = "§8§o...one night without rest.  It remembers.";
                else if (nights == 2) sleepMsg = "§8§o...two nights.  It has been here since the first.";
                else                  sleepMsg = "§8§o..." + nights + " nights.  Your mind is not yours anymore.";
                target.displayClientMessage(Component.literal(sleepMsg), true);
                sleepDisplayTimer = 2400 + sl.getRandom().nextInt(800);
            }
        }

        // Spotted / too close → teleport behind the player and keep haunting
        if (tpCooldown <= 0) {
            List<Player> nearby = sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(30.0));
            for (Player p : nearby) {
                boolean tooClose = distanceToSqr(p) < FLEE_DIST_SQ;
                Vec3 look = p.getLookAngle();
                Vec3 toMe = getEyePosition().subtract(p.getEyePosition()).normalize();
                boolean spotted = look.dot(toMe) > LOOK_DOT_THRESHOLD;
                if ((tooClose || spotted) && p instanceof ServerPlayer sp) {
                    teleportBehindPlayer(sl, sp, spotted);
                    break;
                }
            }
        }

        // Creep closer while player looks away — stage 2+
        if (stage >= 2 && target != null) {
            Vec3 lk = target.getLookAngle();
            Vec3 toE = getEyePosition().subtract(target.getEyePosition()).normalize();
            if (lk.dot(toE) <= LOOK_DOT_THRESHOLD && creepDist > CREEP_DIST_MIN) {
                creepDist -= CREEP_RATE;
            }
        }

        // Panic when it has crept within arm's reach at stage 3
        if (stage >= 3 && creepDist <= CREEP_DIST_MIN + 0.05 && tpCooldown <= 0 && target != null) {
            doPanicSequence(sl, target);
        }

        // ── scripted speech ───────────────────────────────────────────────────
        playTimedSpeech(sl, target);

        // ── mental attack (stage 2+) ──────────────────────────────────────────
        if (stage >= 2 && mentalAttackTimer <= 0 && target != null && distanceTo(target) < 40.0) {
            doMentalAttack(sl, target);
            mentalAttackTimer = (stage >= 3) ? 120 : 200;
        }

        // ── paranoid footstep / behind-sounds (all stages) ───────────────────
        if (paranoidTimer <= 0 && target != null) {
            playParanoidSounds(sl, target);
            paranoidTimer = 80 + sl.getRandom().nextInt(100); // every 4–9 s
        }

        // ── hallucination mob sounds (stage 2+) ───────────────────────────────
        if (stage >= 2 && hallucinationTimer <= 0 && target != null) {
            if (sl.getRandom().nextInt(3) == 0) playHallucinationSounds(sl, target);
            hallucinationTimer = 120 + sl.getRandom().nextInt(80);
        }

        // ── paranoid chat messages (stage 2+) ─────────────────────────────────
        if (stage >= 2 && chatParanoidTimer <= 0 && target != null) {
            if (sl.getRandom().nextInt(2) == 0) playParanoidChat(sl, target);
            chatParanoidTimer = 200 + sl.getRandom().nextInt(200);
        }

        // ── random darkness pulses (stage 2+) ─────────────────────────────────
        if (stage >= 2 && darknessTimer <= 0 && target != null) {
            if (sl.getRandom().nextInt(3) == 0) {
                target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false));
                sl.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.SCULK_BLOCK_CHARGE, SoundSource.AMBIENT, 0.4f, 0.1f);
            }
            darknessTimer = 80 + sl.getRandom().nextInt(80);
        }

        // ── ambient sounds ─────────────────────────────────────────────────────
        if (ambientTimer <= 0) {
            playAmbientSounds(sl, target);
            ambientTimer = calculateAmbientInterval();
        }
    }

    // ─────────────────────────────── particles ────────────────────────────────

    private void emitAmbientParticles(ServerLevel sl) {
        if (lifeTicks % 8 == 0) {
            double ox = (sl.getRandom().nextDouble() - 0.5) * 2.0;
            double oz = (sl.getRandom().nextDouble() - 0.5) * 2.0;
            sl.sendParticles(ParticleTypes.SMOKE,
                    getX() + ox, getY() + 0.5 + sl.getRandom().nextDouble() * 1.5, getZ() + oz,
                    1, 0.1, 0.1, 0.1, 0.002);
        }
        if (stage >= 2 && lifeTicks % 12 == 0 && sl.getRandom().nextInt(3) == 0) {
            sl.sendParticles(ParticleTypes.SOUL,
                    getX() + (sl.getRandom().nextDouble() - 0.5) * 1.5,
                    getY() + sl.getRandom().nextDouble() * 2.0,
                    getZ() + (sl.getRandom().nextDouble() - 0.5) * 1.5,
                    1, 0.05, 0.1, 0.05, 0.01);
        }
        if (stage >= 3 && lifeTicks % 10 == 0 && sl.getRandom().nextInt(2) == 0) {
            sl.sendParticles(ParticleTypes.END_ROD,
                    getX() + (sl.getRandom().nextDouble() - 0.5) * 0.8,
                    getY() + sl.getRandom().nextDouble() * 2.2,
                    getZ() + (sl.getRandom().nextDouble() - 0.5) * 0.8,
                    1, 0.05, 0.08, 0.05, 0.02);
        }
    }

    // ──────────────────────────── stage advance ───────────────────────────────

    private void onStageAdvance(ServerLevel sl, ServerPlayer tp) {
        creepDist = CREEP_DIST_START;
        switch (stage) {
            case 2 -> {
                removeEffect(MobEffects.INVISIBILITY);
                flickerTimer = Integer.MAX_VALUE; // no more flicker at stage 2+
                sl.playSound(null, getX(), getY(), getZ(),
                        SoundEvents.SCULK_BLOCK_CHARGE, SoundSource.AMBIENT, 0.6f, 0.3f);
                sl.sendParticles(ParticleTypes.SOUL,
                        getX(), getY() + 1.0, getZ(), 12, 0.4, 0.6, 0.4, 0.03);
                tp.displayClientMessage(Component.literal("§8§o...it's getting closer."), true);
            }
            case 3 -> {
                sl.playSound(null, getX(), getY(), getZ(),
                        SoundEvents.SCULK_BLOCK_CHARGE, SoundSource.AMBIENT, 0.8f, 0.2f);
                sl.sendParticles(ParticleTypes.SOUL,
                        getX(), getY() + 1.0, getZ(), 25, 0.6, 0.8, 0.6, 0.05);
                sl.sendParticles(ParticleTypes.END_ROD,
                        getX(), getY() + 1.0, getZ(), 10, 0.4, 0.6, 0.4, 0.04);
                tp.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 60, 0, false, false));
                tp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false));
                tp.displayClientMessage(Component.literal("§8§o...none of this is real."), true);
            }
        }
    }

    // ─────────────────────────────── speech ──────────────────────────────────

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
            } else if (stageTimer == 260) {
                // Fake footstep directly at the player — stage 1 teaser
                tp.displayClientMessage(
                        Component.literal("§8§o...something is behind you."), true);
                playBehindFootstep(sl, tp, 4.0);
            }
        } else if (stage == 2) {
            if (stageTimer == 60) {
                tp.displayClientMessage(
                        Component.literal("§8§o...the ground moves when you aren't watching."), true);
                sl.playSound(null, ex, ey, ez,
                        SoundEvents.SCULK_BLOCK_CHARGE, SoundSource.AMBIENT, 0.4f, 0.35f);
            } else if (stageTimer == 130) {
                tp.displayClientMessage(
                        Component.literal("§8§o...I am not really here.  Neither are you."), true);
                // Breathing sound directly at the player's ear — right next to them
                sl.playSound(null, tp.getX(), tp.getY(), tp.getZ(),
                        SoundEvents.WARDEN_NEARBY_CLOSER, SoundSource.AMBIENT, 0.4f, 0.5f);
            } else if (stageTimer == 170) {
                tp.displayClientMessage(Component.literal("§8§o...something opened a door."), true);
                sl.playSound(null,
                        tp.getX() + (sl.getRandom().nextDouble() - 0.5) * 6,
                        tp.getY(),
                        tp.getZ() + (sl.getRandom().nextDouble() - 0.5) * 6,
                        SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, 0.6f, 0.9f);
            } else if (stageTimer == 210) {
                tp.displayClientMessage(
                        Component.literal("§8§oYou built this.  Every block.  Every night without rest."), true);
                sl.playSound(null, tp.getX(), tp.getY(), tp.getZ(),
                        SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 0.4f, 0.7f);
            } else if (stageTimer == 250) {
                tp.displayClientMessage(Component.literal("§8§o...that was close."), true);
                sl.playSound(null,
                        tp.getX() + (sl.getRandom().nextDouble() - 0.5) * 2,
                        tp.getY() + 1.0,
                        tp.getZ() + (sl.getRandom().nextDouble() - 0.5) * 2,
                        SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.7f, 1.1f);
            } else if (stageTimer == 270) {
                // Creeper hiss right next to the player — no actual creeper
                tp.displayClientMessage(Component.literal("§8§o...did you hear that?"), true);
                sl.playSound(null, tp.getX() + (sl.getRandom().nextDouble() - 0.5) * 3,
                        tp.getY(), tp.getZ() + (sl.getRandom().nextDouble() - 0.5) * 3,
                        SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 0.8f, 1.0f);
                // Brief darkness — like blinking
                tp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20, 0, false, false));
            }
        } else if (stage >= 3) {
            if (stageTimer == 20) {
                tp.displayClientMessage(Component.literal("§4§o...breathe."), true);
                sl.playSound(null, tp.getX(), tp.getY(), tp.getZ(),
                        SoundEvents.WARDEN_NEARBY_CLOSER, SoundSource.AMBIENT, 0.3f, 0.4f);
            } else if (stageTimer == 40) {
                tp.displayClientMessage(
                        Component.literal("§4§lYOUR HANDS ARE NOT YOUR HANDS."), true);
                sl.playSound(null, tp.getX(), tp.getY(), tp.getZ(),
                        SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 0.6f, 0.5f);
                sl.sendParticles(ParticleTypes.SOUL,
                        tp.getX(), tp.getY() + 1, tp.getZ(), 20, 1.0, 0.5, 1.0, 0.05);
            } else if (stageTimer == 58) {
                tp.displayClientMessage(
                        Component.literal("§c§oWait.  Something moved in your inventory."), true);
                sl.playSound(null, tp.getX(), tp.getY(), tp.getZ(),
                        SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5f, 0.7f);
            } else if (stageTimer == 80) {
                // Multiple footsteps closing in from behind — rapid sequence
                tp.displayClientMessage(
                        Component.literal("§4§o...it's right behind you."), true);
                playApproachingFootsteps(sl, tp);
            } else if (stageTimer == 105) {
                tp.displayClientMessage(
                        Component.literal("§c§o...that sound was right next to you."), true);
                sl.playSound(null,
                        tp.getX() + (sl.getRandom().nextDouble() - 0.5) * 3,
                        tp.getY(),
                        tp.getZ() + (sl.getRandom().nextDouble() - 0.5) * 3,
                        SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 0.45f, 1.2f);
            } else if (stageTimer == 120) {
                tp.displayClientMessage(
                        Component.literal("§4§lCLOSE YOUR EYES.  THEY ARE ALREADY CLOSED."), true);
                sl.playSound(null, tp.getX(), tp.getY(), tp.getZ(),
                        SoundEvents.SCULK_BLOCK_CHARGE, SoundSource.AMBIENT, 0.7f, 0.2f);
                tp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
                tp.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 80, 0, false, false));
            } else if (stageTimer == 160) {
                // Enderman scream from behind — maximum paranoia
                tp.displayClientMessage(
                        Component.literal("§4§l§kX§r §4§lTURN AROUND§r §4§l§kX§r"), true);
                sl.playSound(null, tp.getX(), tp.getY(), tp.getZ(),
                        SoundEvents.ENDERMAN_SCREAM, SoundSource.HOSTILE, 1.0f, 0.7f);
                tp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 1, false, false));
            } else if (stageTimer == 200) {
                tp.displayClientMessage(Component.literal("§4§l...SLEEP."), true);
                sl.playSound(null, tp.getX(), tp.getY(), tp.getZ(),
                        SoundEvents.WARDEN_ROAR, SoundSource.AMBIENT, 0.5f, 0.4f);
                sl.sendParticles(ParticleTypes.SOUL,
                        tp.getX(), tp.getY() + 0.5, tp.getZ(), 30, 1.5, 1.0, 1.5, 0.05);
                // Haunting never ends — reset stage and continue
                stage = 1;
                stageTimer = 0;
                whispered = false;
                creepDist = CREEP_DIST_START;
                flickerTimer = 300 + getRandom().nextInt(200);
                teleportBehindPlayer(sl, tp, false);
            }
        }
    }

    // ───────────────────── paranoid footstep effects ──────────────────────────

    /**
     * Plays footstep sounds from BEHIND the player — the core paranoia mechanic.
     * When the player spins around, nothing is there.
     */
    private void playParanoidSounds(ServerLevel sl, ServerPlayer tp) {
        switch (sl.getRandom().nextInt(stage >= 2 ? 5 : 3)) {
            case 0 -> playBehindFootstep(sl, tp, 6.0 + sl.getRandom().nextDouble() * 6.0);
            case 1 -> {
                // Two footsteps in sequence — "crunch, crunch" from behind
                playBehindFootstep(sl, tp, 8.0);
                // Small delay isn't possible in a single tick, but two close positions simulate it
                playBehindFootstep(sl, tp, 7.5);
            }
            case 2 -> {
                // Footstep off to the side — player doesn't know which way to look
                Vec3 look = tp.getLookAngle();
                double bx = tp.getX() + look.z * 6.0 + (sl.getRandom().nextDouble() - 0.5) * 2;
                double bz = tp.getZ() - look.x * 6.0 + (sl.getRandom().nextDouble() - 0.5) * 2;
                sl.playSound(null, bx, tp.getY(), bz,
                        SoundEvents.GRAVEL_STEP, SoundSource.AMBIENT, 0.5f, 0.8f);
            }
            case 3 -> {
                // Something tripped on a block nearby
                double ox = (sl.getRandom().nextDouble() - 0.5) * 10.0;
                double oz = (sl.getRandom().nextDouble() - 0.5) * 10.0;
                sl.playSound(null, tp.getX() + ox, tp.getY(), tp.getZ() + oz,
                        SoundEvents.STONE_STEP, SoundSource.AMBIENT, 0.4f, 0.9f);
            }
            case 4 -> {
                // Distant block breaking — something is digging
                double ox = (sl.getRandom().nextDouble() - 0.5) * 12.0;
                double oz = (sl.getRandom().nextDouble() - 0.5) * 12.0;
                sl.playSound(null, tp.getX() + ox, tp.getY() - 1, tp.getZ() + oz,
                        SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.35f, 1.1f);
            }
        }
    }

    /** Places a single footstep sound at `dist` blocks behind the player. */
    private void playBehindFootstep(ServerLevel sl, ServerPlayer tp, double dist) {
        Vec3 look = tp.getLookAngle();
        double bx = tp.getX() - look.x * dist + (sl.getRandom().nextDouble() - 0.5) * 1.5;
        double by = tp.getY() + (sl.getRandom().nextDouble() - 0.5) * 0.5;
        double bz = tp.getZ() - look.z * dist + (sl.getRandom().nextDouble() - 0.5) * 1.5;
        // Alternate between gravel and wood — different surfaces make it believable
        if (sl.getRandom().nextBoolean()) {
            sl.playSound(null, bx, by, bz, SoundEvents.GRAVEL_STEP, SoundSource.AMBIENT, 0.55f, 0.85f);
        } else {
            sl.playSound(null, bx, by, bz, SoundEvents.WOOD_STEP,   SoundSource.AMBIENT, 0.5f,  0.9f);
        }
    }

    /**
     * Plays a rapid series of footsteps that seem to approach from behind.
     * Used at stage 3 for maximum paranoia.
     */
    private void playApproachingFootsteps(ServerLevel sl, ServerPlayer tp) {
        Vec3 look = tp.getLookAngle();
        // Steps at 14, 10, 7, 4 blocks behind — closing in
        for (double dist : new double[]{14.0, 10.0, 7.0, 4.0}) {
            double bx = tp.getX() - look.x * dist + (sl.getRandom().nextDouble() - 0.5) * 1.0;
            double bz = tp.getZ() - look.z * dist + (sl.getRandom().nextDouble() - 0.5) * 1.0;
            sl.playSound(null, bx, tp.getY(), bz,
                    SoundEvents.GRAVEL_STEP, SoundSource.AMBIENT, 0.6f, 0.8f);
        }
        // Sudden stop — the sound ends before reaching the player, so they never know
        sl.playSound(null, tp.getX(), tp.getY(), tp.getZ(),
                SoundEvents.WARDEN_NEARBY_CLOSER, SoundSource.AMBIENT, 0.3f, 0.4f);
    }

    // ──────────────────── hallucination mob sounds ────────────────────────────

    /** Plays fake mob sounds at or near the player's own position. */
    private void playHallucinationSounds(ServerLevel sl, ServerPlayer tp) {
        double px = tp.getX(), py = tp.getY(), pz = tp.getZ();
        // Slight offset so it doesn't sound exactly on the player
        double ox = (sl.getRandom().nextDouble() - 0.5) * 4.0;
        double oz = (sl.getRandom().nextDouble() - 0.5) * 4.0;
        switch (sl.getRandom().nextInt(12)) {
            case 0 -> sl.playSound(null, px + ox, py, pz + oz,
                    SoundEvents.ZOMBIE_AMBIENT,    SoundSource.HOSTILE, 0.45f, 0.8f);
            case 1 -> sl.playSound(null, px + ox, py, pz + oz,
                    SoundEvents.SKELETON_AMBIENT,  SoundSource.HOSTILE, 0.4f,  0.9f);
            case 2 -> sl.playSound(null, px, py, pz,
                    SoundEvents.ENDERMAN_STARE,    SoundSource.HOSTILE, 0.35f, 0.6f);
            case 3 -> sl.playSound(null, px + ox, py, pz + oz,
                    SoundEvents.SPIDER_AMBIENT,    SoundSource.HOSTILE, 0.4f,  0.7f);
            case 4 -> sl.playSound(null, px, py, pz,
                    SoundEvents.AMBIENT_CAVE,      SoundSource.AMBIENT, 0.45f, 0.2f);
            case 5 -> sl.playSound(null, px, py, pz,
                    SoundEvents.SCULK_BLOCK_CHARGE, SoundSource.AMBIENT, 0.35f, 0.15f);
            case 6 -> {
                // Phantom flap right above them — they look up, nothing is there
                sl.playSound(null, px + ox * 0.5, py + 3.0, pz + oz * 0.5,
                        SoundEvents.PHANTOM_FLAP,      SoundSource.HOSTILE, 0.5f, 0.9f);
            }
            case 7 -> sl.playSound(null, px + ox, py, pz + oz,
                    SoundEvents.CREEPER_PRIMED,   SoundSource.HOSTILE, 0.4f, 1.1f);
            case 8 -> // Arrow whiz past — as if someone fired at them
                sl.playSound(null, px + ox * 0.5, py + 0.5, pz + oz * 0.5,
                    SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.5f, 1.0f);
            case 9 -> // Item pickup at their ear — something took something
                sl.playSound(null, px, py, pz,
                    SoundEvents.ITEM_PICKUP,      SoundSource.PLAYERS, 0.6f, 0.8f);
            case 10 -> // Door creak nearby — something just entered
                sl.playSound(null, px + ox, py, pz + oz,
                    SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS,  0.5f, 0.85f);
            case 11 -> // Distant TNT click — very quiet, like a trap being set
                sl.playSound(null, px + ox * 2, py, pz + oz * 2,
                    SoundEvents.TNT_PRIMED,       SoundSource.BLOCKS,  0.2f, 1.3f);
        }
    }

    // ──────────────────── paranoid chat messages ──────────────────────────────

    /**
     * Sends messages that look like server or player chat — visible in the chat box,
     * not just the actionbar.  Designed to make the player doubt what is real.
     */
    private void playParanoidChat(ServerLevel sl, ServerPlayer tp) {
        // Stage-specific messages: stage 2 is subtle, stage 3 is overt
        String[] stage2msgs = {
            "§7§o[something moved behind you]",
            "§8§oYou haven't slept in days.",
            "§7§o[a sound nearby — you turn. nothing.]",
            "§8§oYou're not sure how long you've been here.",
            "§7§o[the walls look closer than they did]",
            "§8§oIs that your heartbeat?",
            "§7§o[you counted your torches twice.  the number changed.]",
            "§8§oThe shadows move when you look away.",
            "§7§o[something breathed on your neck just now]",
            "§8§oYou don't remember placing that block.",
        };
        String[] stage3msgs = {
            "§4§o[IT IS STANDING BEHIND YOU]",
            "§c§oDon't look. Don't look. Don't look.",
            "§4§k... §r§4§oyou are not alone§4§k ...§r",
            "§c§oThe torch counts are wrong. You placed more than that.",
            "§4§lYOU CANNOT TRUST YOUR EYES.",
            "§c§oSomething moved in your peripheral vision.",
            "§4§k##§r §4§oyour inventory has changed §4§k##§r",
            "§c§oIt stepped closer while you were reading this.",
            "§4§o[stop looking at the chat.  STOP LOOKING AT THE CHAT.]",
            "§c§oYou can hear it breathing.  You always could.",
            "§4§lIT IS NOT BEHIND YOU ANYMORE.",
        };
        String[] pool = (stage >= 3) ? stage3msgs : stage2msgs;
        String msg = pool[sl.getRandom().nextInt(pool.length)];
        tp.sendSystemMessage(Component.literal(msg));
    }

    // ─────────────────────── mental attack ────────────────────────────────────

    /** Degrades the player's mental state — no HP damage, ever. */
    private void doMentalAttack(ServerLevel sl, ServerPlayer tp) {
        tp.addEffect(new MobEffectInstance(MobEffects.HUNGER,  200, 1, false, false));
        tp.addEffect(new MobEffectInstance(MobEffects.NAUSEA,  100, 0, false, false));
        if (stage >= 3) {
            tp.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,        100, 1, false, false));
            tp.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE,  100, 0, false, false));
            tp.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,        100, 0, false, false));
        }
        sl.playSound(null, tp.getX(), tp.getY(), tp.getZ(),
                SoundEvents.WARDEN_AMBIENT, SoundSource.AMBIENT, 0.5f, 0.3f);
    }

    // ──────────────────────────── ambient sounds ──────────────────────────────

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
        sl.playSound(null, getX(), getY(), getZ(),
                SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, 0.3f, 0.2f);
        sl.sendParticles(ParticleTypes.SOUL,
                getX(), getY() + 0.3, getZ(), 4, 0.3, 0.2, 0.3, 0.01);
    }

    // ──────────────────────────── teleport ────────────────────────────────────

    /**
     * Teleports The Reverie to a position behind the player.
     * Called whenever spotted (looked at), approached too closely, attacked,
     * or when the stage-3 loop resets.  The entity never truly leaves — it
     * just slips to where the player cannot see it.
     */
    private void teleportBehindPlayer(ServerLevel sl, ServerPlayer tp, boolean spotted) {
        // Particles/sound at the old position — it "blinks out"
        sl.sendParticles(ParticleTypes.SOUL,
                getX(), getY() + 1.0, getZ(), 8, 0.3, 0.4, 0.3, 0.04);
        sl.sendParticles(ParticleTypes.SMOKE,
                getX(), getY() + 0.5, getZ(), 5, 0.2, 0.2, 0.2, 0.01);
        sl.playSound(null, getX(), getY(), getZ(),
                SoundEvents.SCULK_BLOCK_CHARGE, SoundSource.AMBIENT, 0.4f, 0.25f);

        BlockPos dest = findBehindPos(sl, tp);
        if (dest != null) {
            teleportTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5);
        }

        // Soft sound at new position — barely audible, just enough to unsettle
        sl.playSound(null, getX(), getY(), getZ(),
                SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, 0.15f, 0.35f);
        sl.sendParticles(ParticleTypes.SOUL,
                getX(), getY() + 1.0, getZ(), 3, 0.2, 0.3, 0.2, 0.02);

        if (spotted) {
            spotCount++;
            creepDist = CREEP_DIST_START; // reset approach when caught
            String msg;
            if (spotCount == 3)
                msg = "§8§o...you keep looking.  it counts.";
            else if (spotCount == 5)
                msg = "§8§oEvery time you find it, it was already gone.";
            else if (spotCount == 7)
                msg = "§8§o..." + spotCount + " times.  It is not tiring.";
            else if (spotCount >= 10 && stage >= 3) {
                msg = "§4§l" + spotCount + " TIMES.";
                tp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 1, false, false));
            } else {
                String[] fallback = {
                    "§8§o...you saw nothing.",
                    "§8§o...it was never there.",
                    "§8§oyour eyes are failing you.",
                    "§8§o...blink.  try again.",
                    "§8§o...it moved while you looked.",
                    "§8§o...was it ever there?",
                };
                msg = fallback[sl.getRandom().nextInt(fallback.length)];
            }
            tp.displayClientMessage(Component.literal(msg), true);
        }

        tpCooldown = TELEPORT_COOLDOWN;
    }

    /**
     * Finds a clear block position behind the player — 10–18 blocks away
     * in the direction opposite to where the player is looking.
     */
    private BlockPos findBehindPos(ServerLevel sl, ServerPlayer tp) {
        Vec3 look = tp.getLookAngle();
        double maxDist = creepDist;
        double minDist = Math.max(CREEP_DIST_MIN, maxDist - 4.0);
        for (int attempt = 0; attempt < 25; attempt++) {
            double dist = minDist + sl.getRandom().nextDouble() * (maxDist - minDist);
            double bx = tp.getX() - look.x * dist + (sl.getRandom().nextDouble() - 0.5) * 3.0;
            double bz = tp.getZ() - look.z * dist + (sl.getRandom().nextDouble() - 0.5) * 3.0;
            int tx = (int) Math.floor(bx);
            int tz = (int) Math.floor(bz);
            int ty = (int) tp.getY();
            for (int dy = 4; dy >= -8; dy--) {
                BlockPos ground = new BlockPos(tx, ty + dy, tz);
                BlockPos above  = ground.above();
                if (!sl.getBlockState(ground).isAir()
                        && sl.getBlockState(above).isAir()
                        && sl.getBlockState(above.above()).isAir()) {
                    return above;
                }
            }
        }
        return null; // if no clear spot found, stay put this tick
    }

    // ─────────────────────── panic sequence ───────────────────────────────────

    /** Triggered when The Reverie has crept to arm's reach of the player's back. */
    private void doPanicSequence(ServerLevel sl, ServerPlayer tp) {
        tp.sendSystemMessage(Component.literal(
                "§4§l§k!!§r §4§lIT IS RIGHT BEHIND YOU.§r §4§l§k!!§r"));
        sl.playSound(null, getX(), getY(), getZ(),
                SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.0f, 0.5f);
        sl.playSound(null, getX(), getY(), getZ(),
                SoundEvents.ENDERMAN_SCREAM, SoundSource.HOSTILE, 0.8f, 0.4f);
        tp.addEffect(new MobEffectInstance(MobEffects.DARKNESS,  80, 1, false, false));
        tp.addEffect(new MobEffectInstance(MobEffects.NAUSEA,   120, 0, false, false));
        tp.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,  60, 2, false, false));
        sl.sendParticles(ParticleTypes.SOUL,
                getX(), getY() + 1.0, getZ(), 30, 0.5, 0.8, 0.5, 0.07);
        sl.sendParticles(ParticleTypes.END_ROD,
                getX(), getY() + 1.0, getZ(), 15, 0.3, 0.5, 0.3, 0.05);
        sl.playSound(null, tp.getX(), tp.getY(), tp.getZ(),
                SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.6f, 0.8f);
        creepDist = CREEP_DIST_START;
        teleportBehindPlayer(sl, tp, false);
    }

    // ─────────────────────────────── vanish ───────────────────────────────────

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

    // ───────────────────────────── combat ─────────────────────────────────────

    @Override public boolean isInvulnerable() { return true; }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // Not real — it cannot be harmed, only relocates
        if (source.getEntity() instanceof ServerPlayer sp) {
            teleportBehindPlayer(level, sp, false);
            sp.sendSystemMessage(Component.literal("§8§o...you can't touch it."));
        }
        return false;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (target instanceof ServerPlayer sp) doMentalAttack(level, sp);
        return true;
    }

    // ─────────────────────────────── helpers ──────────────────────────────────

    private ServerPlayer getTargetPlayer(ServerLevel sl) {
        if (targetPlayerUUID == null) return null;
        return sl.getServer().getPlayerList().getPlayer(targetPlayerUUID);
    }

    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean shouldShowName()            { return false; }
}
