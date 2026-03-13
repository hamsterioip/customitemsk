package com.example;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class WatcherEntity extends PathfinderMob {

    public static EntityType<WatcherEntity> TYPE;

    /** The fixed player whose skin The Watcher wears. Read by the renderer. */
    public static final String SKIN_NAME = "nooq4oz_";

    // 8-degree viewing cone — if player's crosshair lands within this, Watcher vanishes
    private static final double LOOK_DOT_THRESHOLD = Math.cos(Math.toRadians(8.0));
    // Within 12 blocks → always vanishes
    private static final double VANISH_DIST_SQ = 12.0 * 12.0;
    // Fail-safe lifetime: 1 hour
    private static final int MAX_LIFE_TICKS = 72000;

    /** Encounter stage (1–5). Set by the spawner before addFreshEntity. */
    public int stage = 1;

    /** UUID of the player this Watcher was spawned for. Set by the spawner. */
    public UUID targetPlayerUUID = null;

    private int lifeTicks    = 0;
    private int ambientTimer = 0;
    private int flickerTimer = 0;
    private int breathingTimer = 0;
    private boolean spawnSoundPlayed = false;
    private boolean whispered        = false;
    private boolean hasTeleported    = false;

    public WatcherEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        // No glow - the Watcher should blend into the shadows
        setPersistenceRequired();
        setInvulnerable(true);
        setSilent(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    @Override
    protected void registerGoals() {
        // No goals — The Watcher never moves on its own.
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;

        lifeTicks++;
        ambientTimer--;

        ServerLevel sl = (ServerLevel) level();

        // Safety despawn
        if (lifeTicks >= MAX_LIFE_TICKS) { vanish(sl, null); return; }

        // Stage-specific spawn sound (once, first tick)
        if (!spawnSoundPlayed && lifeTicks == 1) {
            spawnSoundPlayed = true;
            playSpawnSound(sl);
        }

        // Collect nearby players — used both for facing and for trigger checks
        List<Player> nearby = sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(30.0));

        // ---- Always face the target player (or nearest player) ----
        Player faceTarget = getTargetPlayer(sl);
        if (faceTarget == null && !nearby.isEmpty()) {
            faceTarget = nearby.stream()
                    .min(Comparator.comparingDouble(this::distanceToSqr))
                    .orElse(null);
        }
        if (faceTarget != null) {
            // Look directly at player's head (pitch and yaw) for uncanny accuracy
            double dx = faceTarget.getX() - getX();
            double dy = faceTarget.getEyeY() - getEyeY();
            double dz = faceTarget.getZ() - getZ();
            float yaw = (float)(Math.atan2(-dx, dz) * (180.0 / Math.PI));
            float pitch = (float)(Math.atan2(-dy, Math.sqrt(dx*dx + dz*dz)) * (180.0 / Math.PI));
            setYRot(yaw);
            setYHeadRot(yaw);
            setXRot(pitch); // Actually look up/down at player
            
            // Stage 4+: Occasional "intense stare" particles
            if (stage >= 4 && lifeTicks % 40 == 0 && sl.getRandom().nextInt(3) == 0) {
                // Particles connecting watcher to player
                Vec3 watcherEye = getEyePosition();
                Vec3 playerEye = faceTarget.getEyePosition();
                Vec3 direction = playerEye.subtract(watcherEye).normalize();
                
                for (int i = 1; i <= 8; i++) {
                    Vec3 point = watcherEye.add(direction.scale(i * 0.8));
                    sl.sendParticles(ParticleTypes.SCULK_SOUL,
                            point.x, point.y, point.z, 1, 0.02, 0.02, 0.02, 0.01);
                }
            }
        }

        // ---- Localized fog effect - the Watcher brings its own darkness ----
        if (lifeTicks % 5 == 0) {
            // Dense ground fog
            for (int i = 0; i < 3; i++) {
                double offsetX = (sl.getRandom().nextDouble() - 0.5) * 4.0;
                double offsetZ = (sl.getRandom().nextDouble() - 0.5) * 4.0;
                sl.sendParticles(ParticleTypes.LARGE_SMOKE,
                        getX() + offsetX, getY() + 0.1 + sl.getRandom().nextDouble() * 0.5, getZ() + offsetZ,
                        1, 0.3, 0.1, 0.3, 0.002);
            }
            // Lingering soul particles for eerie atmosphere
            if (stage >= 3 && sl.getRandom().nextInt(2) == 0) {
                double offsetX = (sl.getRandom().nextDouble() - 0.5) * 3.0;
                double offsetZ = (sl.getRandom().nextDouble() - 0.5) * 3.0;
                sl.sendParticles(ParticleTypes.SCULK_SOUL,
                        getX() + offsetX, getY() + sl.getRandom().nextDouble() * 2.0, getZ() + offsetZ,
                        1, 0.1, 0.1, 0.1, 0.01);
            }
        }
        // Stage 4+: thicker fog and occasional ash particles
        if (stage >= 4 && lifeTicks % 10 == 0) {
            for (int i = 0; i < 5; i++) {
                double offsetX = (sl.getRandom().nextDouble() - 0.5) * 5.0;
                double offsetZ = (sl.getRandom().nextDouble() - 0.5) * 5.0;
                sl.sendParticles(ParticleTypes.ASH,
                        getX() + offsetX, getY() + 0.2 + sl.getRandom().nextDouble() * 0.8, getZ() + offsetZ,
                        1, 0.4, 0.2, 0.4, 0.005);
            }
        }

        // Brief delay before full activity
        if (lifeTicks < 40) return;

        // ---- First whisper at 2 seconds ----
        if (!whispered && lifeTicks == 40) {
            whispered = true;
            playWhisper(sl);
        }

        // ---- Stage-specific timed speech ----
        playTimedSpeech(sl);

        // ---- Stage 3+: warden heartbeat when target is within 25 blocks ----
        if (stage >= 3 && lifeTicks % 44 == 0) {
            ServerPlayer tp = getTargetPlayer(sl);
            if (tp != null && distanceToSqr(tp) < 25.0 * 25.0) {
                sl.playSound(null, tp.getX(), tp.getY(), tp.getZ(),
                        SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 0.6f, 0.8f);
            }
        }

        // ---- Stage 4+: periodic Darkness pulses on the target ----
        if (stage >= 4 && lifeTicks % 60 == 0) {
            ServerPlayer tp = getTargetPlayer(sl);
            if (tp != null) {
                tp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 50, 0, false, false));
            }
        }

        // ---- Stage 4+: creep 2 blocks closer when target looks away ----
        if (stage >= 4 && lifeTicks % 120 == 0) {
            ServerPlayer tp = getTargetPlayer(sl);
            if (tp != null && distanceToSqr(tp) > VANISH_DIST_SQ) {
                Vec3 look    = tp.getLookAngle();
                Vec3 toMe    = getEyePosition().subtract(tp.getEyePosition()).normalize();
                boolean seen = look.dot(toMe) > Math.cos(Math.toRadians(50));
                if (!seen) creepCloser(sl, tp);
            }
        }

        // ---- Trigger checks: look-at and proximity ----
        for (Player player : nearby) {
            if (distanceToSqr(player) < VANISH_DIST_SQ) { vanish(sl, player); return; }

            Vec3 lookVec   = player.getLookAngle();
            Vec3 toWatcher = getEyePosition().subtract(player.getEyePosition()).normalize();
            if (lookVec.dot(toWatcher) > LOOK_DOT_THRESHOLD) { vanish(sl, player); return; }
        }

        // ---- Ambient psychological sounds — vary by stage ----
        if (ambientTimer <= 0) {
            playAmbientPsychologicalSounds(sl);
            ambientTimer = calculateAmbientSoundInterval();
        }
        
        // ---- Breathing sounds (Stage 3+) — heard when close ----
        if (stage >= 3) {
            breathingTimer--;
            if (breathingTimer <= 0) {
                ServerPlayer tp = getTargetPlayer(sl);
                if (tp != null && distanceToSqr(tp) < 20.0 * 20.0) {
                    // Heavy breathing sound at the PLAYER's location (not the watcher)
                    // This makes it feel like the player is hearing their own fear
                    sl.playSound(null, tp.getX(), tp.getY(), tp.getZ(),
                            SoundEvents.WARDEN_NEARBY_CLOSER, SoundSource.AMBIENT, 0.4f, 0.6f);
                }
                breathingTimer = 100 + sl.getRandom().nextInt(80);
            }
        }
        
        // ---- Random flicker/twitch movement (Stage 4+) ----
        if (stage >= 4 && !hasTeleported) {
            flickerTimer--;
            if (flickerTimer <= 0) {
                // Sudden "flicker" teleport - small jump in a random direction
                if (sl.getRandom().nextInt(4) == 0) {
                    doFlickerMovement(sl);
                }
                flickerTimer = 60 + sl.getRandom().nextInt(100);
            }
        }
        
        // ---- Fake sounds — play mob sounds from the Watcher's location ----
        if (stage >= 3 && lifeTicks % 200 == 0 && sl.getRandom().nextInt(3) == 0) {
            playFakeMobSound(sl);
        }
    }

    // -------------------------------------------------------------------------
    // Enhanced ambient sound system
    // -------------------------------------------------------------------------
    
    private int calculateAmbientSoundInterval() {
        return switch (stage) {
            case 1 -> 200 + getRandom().nextInt(100);  // 10-15 seconds
            case 2 -> 160 + getRandom().nextInt(80);   // 8-12 seconds  
            case 3 -> 120 + getRandom().nextInt(60);   // 6-9 seconds
            case 4 -> 80 + getRandom().nextInt(60);    // 4-7 seconds
            default -> 40 + getRandom().nextInt(40);   // Stage 5+: 2-4 seconds
        };
    }
    
    private void playAmbientPsychologicalSounds(ServerLevel sl) {
        double tx = getX(), ty = getY(), tz = getZ();
        
        switch (stage) {
            case 1 -> {
                // Subtle, barely noticeable
                sl.playSound(null, tx, ty, tz,
                        SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, 0.25f, 0.3f);
            }
            case 2 -> {
                // Footsteps and subtle wood creaks
                sl.playSound(null, tx, ty, tz,
                        SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, 0.3f, 0.35f);
                if (sl.getRandom().nextInt(2) == 0) {
                    sl.playSound(null, tx, ty, tz,
                            SoundEvents.WOOD_STEP, SoundSource.AMBIENT, 0.15f, 0.5f);
                }
            }
            case 3 -> {
                // Scraping, metal sounds, heartbeats
                sl.playSound(null, tx, ty, tz,
                        SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, 0.35f, 0.3f);
                int r = sl.getRandom().nextInt(4);
                if (r == 0) {
                    sl.playSound(null, tx, ty, tz,
                            SoundEvents.GRAVEL_STEP, SoundSource.AMBIENT, 0.2f, 0.4f);
                } else if (r == 1) {
                    sl.playSound(null, tx, ty, tz,
                            SoundEvents.CHAIN_STEP, SoundSource.AMBIENT, 0.18f, 0.7f);
                } else if (r == 2) {
                    // Distant heartbeat
                    sl.playSound(null, tx, ty, tz,
                            SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 0.3f, 0.6f);
                }
            }
            case 4 -> {
                // Intense ambient - chains, scrapes, whispers, glitches
                sl.playSound(null, tx, ty, tz,
                        SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, 0.4f, 0.25f);
                int r = sl.getRandom().nextInt(5);
                if (r == 0) {
                    sl.playSound(null, tx, ty, tz,
                            SoundEvents.CHAIN_STEP, SoundSource.AMBIENT, 0.25f, 0.5f);
                } else if (r == 1) {
                    sl.playSound(null, tx, ty, tz,
                            SoundEvents.SCULK_BLOCK_BREAK, SoundSource.AMBIENT, 0.3f, 0.4f);
                } else if (r == 2) {
                    // "Glitch" sound
                    sl.playSound(null, tx, ty, tz,
                            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 0.2f, 0.15f);
                } else if (r == 3) {
                    // Glass breaking sound (distant)
                    sl.playSound(null, tx, ty, tz,
                            SoundEvents.GLASS_BREAK, SoundSource.AMBIENT, 0.15f, 0.3f);
                }
            }
            default -> {
                // Stage 5: Chaotic, overwhelming
                sl.playSound(null, tx, ty, tz,
                        SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, 0.5f, 0.2f);
                sl.playSound(null, tx, ty, tz,
                        SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.AMBIENT, 0.25f, 0.4f);
            }
        }
    }
    
    private void playFakeMobSound(ServerLevel sl) {
        // Play sounds of mobs that aren't there - psychological warfare
        double tx = getX(), ty = getY(), tz = getZ();
        int r = sl.getRandom().nextInt(6);
        
        switch (r) {
            case 0 -> sl.playSound(null, tx, ty, tz,
                    SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, 0.4f, 0.8f);
            case 1 -> sl.playSound(null, tx, ty, tz,
                    SoundEvents.SKELETON_AMBIENT, SoundSource.HOSTILE, 0.35f, 0.9f);
            case 2 -> sl.playSound(null, tx, ty, tz,
                    SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 0.3f, 1.0f);
            case 3 -> sl.playSound(null, tx, ty, tz,
                    SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 0.4f, 0.7f);
            case 4 -> sl.playSound(null, tx, ty, tz,
                    SoundEvents.ENDERMAN_STARE, SoundSource.HOSTILE, 0.25f, 0.6f);
            case 5 -> sl.playSound(null, tx, ty, tz,
                    SoundEvents.WARDEN_NEARBY_CLOSER, SoundSource.HOSTILE, 0.35f, 0.5f);
        }
    }
    
    private void doFlickerMovement(ServerLevel sl) {
        // Small, unsettling "glitch" movement
        double offsetX = (sl.getRandom().nextDouble() - 0.5) * 2.0;
        double offsetZ = (sl.getRandom().nextDouble() - 0.5) * 2.0;
        double newX = getX() + offsetX;
        double newZ = getZ() + offsetZ;
        
        // Verify position is safe
        net.minecraft.core.BlockPos feet = net.minecraft.core.BlockPos.containing(newX, getY() + 0.1, newZ);
        net.minecraft.core.BlockPos head = feet.above();
        if ((sl.getBlockState(feet).isAir() || sl.getBlockState(feet).is(net.minecraft.tags.BlockTags.REPLACEABLE))
         && (sl.getBlockState(head).isAir() || sl.getBlockState(head).is(net.minecraft.tags.BlockTags.REPLACEABLE))) {
            
            // Glitch particles at old position
            sl.sendParticles(ParticleTypes.END_ROD,
                    getX(), getY() + 1.0, getZ(), 8, 0.2, 0.5, 0.2, 0.02);
            
            teleportTo(newX, getY(), newZ);
            
            // Glitch particles at new position
            sl.sendParticles(ParticleTypes.END_ROD,
                    newX, getY() + 1.0, newZ, 8, 0.2, 0.5, 0.2, 0.02);
            
            // Glitch sound
            sl.playSound(null, newX, getY(), newZ,
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 0.3f, 0.2f);
        }
    }

    // -------------------------------------------------------------------------
    // Stage sounds
    // -------------------------------------------------------------------------

    private void playSpawnSound(ServerLevel sl) {
        double tx = getX(), ty = getY(), tz = getZ();
        switch (stage) {
            case 2 -> {
                sl.playSound(null, tx, ty, tz,
                        SoundEvents.ENDERMAN_AMBIENT, SoundSource.AMBIENT, 1.0f, 0.7f);
                // Subtle spawn particles
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, tx, ty + 1, tz, 15, 0.3, 0.5, 0.3, 0.02);
            }
            case 3 -> {
                sl.playSound(null, tx, ty, tz,
                        SoundEvents.ENDERMAN_STARE, SoundSource.AMBIENT, 1.0f, 0.8f);
                ServerPlayer tp = getTargetPlayer(sl);
                if (tp != null) sl.playSound(null, tp.getX(), tp.getY(), tp.getZ(),
                        SoundEvents.MUSIC_DISC_11, SoundSource.RECORDS, 0.8f, 1.0f);
                // Soul particles on spawn
                sl.sendParticles(ParticleTypes.SCULK_SOUL, tx, ty + 0.5, tz, 25, 0.5, 0.8, 0.5, 0.05);
                // Brief darkness for target player
                if (tp != null) {
                    tp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false));
                }
            }
            case 4 -> {
                // Terrifying spawn sound combo - inspired by fog mod horror
                sl.playSound(null, tx, ty, tz,
                        SoundEvents.ENDERMAN_SCREAM, SoundSource.AMBIENT, 1.5f, 0.5f);
                sl.playSound(null, tx, ty, tz,
                        SoundEvents.WARDEN_ROAR, SoundSource.AMBIENT, 0.8f, 0.7f);
                ServerPlayer tp = getTargetPlayer(sl);
                if (tp != null) {
                    // Close, personal horror sounds
                    sl.playSound(null, tp.getX(), tp.getY(), tp.getZ(),
                            SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.AMBIENT, 1.2f, 0.6f);
                    sl.playSound(null, tp.getX(), tp.getY(), tp.getZ(),
                            SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.AMBIENT, 0.9f, 0.5f);
                    // More intense darkness
                    tp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false));
                    tp.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 60, 0, false, false));
                }
                // Dramatic spawn particles
                sl.sendParticles(ParticleTypes.SCULK_SOUL, tx, ty + 0.5, tz, 40, 1.0, 1.0, 1.0, 0.08);
                sl.sendParticles(ParticleTypes.ASH, tx, ty + 0.2, tz, 30, 0.8, 0.5, 0.8, 0.05);
                // End rod glitch particles
                for (int i = 0; i < 20; i++) {
                    sl.sendParticles(ParticleTypes.END_ROD,
                            tx + (sl.getRandom().nextDouble() - 0.5) * 3,
                            ty + sl.getRandom().nextDouble() * 2,
                            tz + (sl.getRandom().nextDouble() - 0.5) * 3,
                            1, 0, 0, 0, 0.05);
                }
            }
            default -> {
                if (stage >= 5) {
                    sl.playSound(null, tx, ty, tz,
                            SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 3.0f, 0.5f);
                    ServerPlayer tp = getTargetPlayer(sl);
                    if (tp != null) sl.playSound(null, tp.getX(), tp.getY(), tp.getZ(),
                            SoundEvents.MUSIC_DISC_11, SoundSource.RECORDS, 1.5f, 0.85f);
                    // Apocalyptic spawn effects
                    sl.sendParticles(ParticleTypes.SCULK_SOUL, tx, ty + 0.5, tz, 80, 2.0, 1.5, 2.0, 0.1);
                    sl.sendParticles(ParticleTypes.LARGE_SMOKE, tx, ty + 1, tz, 60, 1.5, 1.0, 1.5, 0.08);
                    if (tp != null) {
                        tp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 1, false, false));
                        tp.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0, false, false));
                        tp.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
                    }
                }
            }
        }
    }

    private void playWhisper(ServerLevel sl) {
        ServerPlayer tp = getTargetPlayer(sl);
        List<Player> nearby = sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(40.0));
        String msg = switch (stage) {
            case 1 -> "§8§o...you are not alone.";
            case 2 -> "§8§o...I see you.";
            case 3 -> "§8§o...I have always been here.";
            case 4 -> "§4§lYOU CANNOT HIDE.";
            default -> "§4§l§kAAAA§r §4§lFOUND YOU§r §4§l§kAAAA§r";
        };
        if (tp != null) {
            tp.displayClientMessage(Component.literal(msg), true);
        } else {
            for (Player p : nearby) p.displayClientMessage(Component.literal(msg), true);
        }
    }

    private void playTimedSpeech(ServerLevel sl) {
        ServerPlayer tp = getTargetPlayer(sl);
        if (tp == null) return;
        double tx = getX(), ty = getY(), tz = getZ();
        
        if (stage == 3) {
            if (lifeTicks == 70) {
                tp.displayClientMessage(Component.literal("§8§o...I know where you sleep."), true);
                sl.playSound(null, tx, ty, tz, SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, 0.5f, 0.2f);
            }
            else if (lifeTicks == 110) {
                tp.displayClientMessage(Component.literal("§8§o...do not look back."), true);
                sl.playSound(null, tx, ty, tz, SoundEvents.GRAVEL_STEP, SoundSource.AMBIENT, 0.3f, 0.3f);
            }
            else if (lifeTicks == 160) {
                tp.displayClientMessage(Component.literal("§4You cannot run forever."), true);
                sl.playSound(null, tp.getX(), tp.getY(), tp.getZ(), 
                        SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 0.5f, 0.7f);
            }
        } else if (stage == 4) {
            if (lifeTicks == 70) {
                tp.displayClientMessage(Component.literal("§4§lIT WATCHES YOU."), true);
                sl.playSound(null, tp.getX(), tp.getY(), tp.getZ(),
                        SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.AMBIENT, 0.6f, 0.5f);
                // Flash particles at player
                sl.sendParticles(ParticleTypes.END_ROD, tp.getX(), tp.getY() + 1, tp.getZ(), 10, 0.5, 0.5, 0.5, 0.1);
            }
            else if (lifeTicks == 110) {
                tp.displayClientMessage(Component.literal("§4§lYOU ARE ALREADY LOST."), true);
                sl.playSound(null, tx, ty, tz,
                        SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.AMBIENT, 0.5f, 0.4f);
                // More darkness
                tp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false));
            }
            else if (lifeTicks == 160) {
                tp.displayClientMessage(Component.literal("§4§l...IT IS TOO LATE."), true);
                sl.playSound(null, tp.getX(), tp.getY(), tp.getZ(),
                        SoundEvents.WARDEN_ROAR, SoundSource.AMBIENT, 0.4f, 0.6f);
                // Soul particles around player
                sl.sendParticles(ParticleTypes.SCULK_SOUL, tp.getX(), tp.getY(), tp.getZ(), 30, 2, 1, 2, 0.05);
            }
        } else if (stage >= 5) {
            if (lifeTicks == 60) {
                tp.displayClientMessage(Component.literal("§4§l§kAAAAAAAAAAAAAAAAAAAA§r"), true);
                sl.playSound(null, tp.getX(), tp.getY(), tp.getZ(),
                        SoundEvents.WARDEN_SONIC_BOOM, SoundSource.AMBIENT, 0.8f, 0.5f);
                tp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 1, false, false));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Creeping (stage 4+)
    // -------------------------------------------------------------------------

    private void creepCloser(ServerLevel sl, ServerPlayer tp) {
        Vec3 towardPlayer = new Vec3(tp.getX() - getX(), 0, tp.getZ() - getZ()).normalize();
        double newX = getX() + towardPlayer.x * 2.5;
        double newZ = getZ() + towardPlayer.z * 2.5;

        // Verify the target column is clear (feet + head)
        net.minecraft.core.BlockPos feet = net.minecraft.core.BlockPos.containing(newX, getY() + 0.1, newZ);
        net.minecraft.core.BlockPos head = feet.above();
        if ((sl.getBlockState(feet).isAir() || sl.getBlockState(feet).is(net.minecraft.tags.BlockTags.REPLACEABLE))
         && (sl.getBlockState(head).isAir() || sl.getBlockState(head).is(net.minecraft.tags.BlockTags.REPLACEABLE))) {
            
            // Pre-teleport particles at old position
            sl.sendParticles(ParticleTypes.LARGE_SMOKE,
                    getX(), getY() + 1.0, getZ(), 8, 0.3, 0.5, 0.3, 0.02);
            
            // Creepy whisper sound as it moves
            sl.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, 0.4f, 0.15f);
            
            teleportTo(newX, getY(), newZ);
            hasTeleported = true;
            
            // Post-teleport particles at new position
            sl.sendParticles(ParticleTypes.LARGE_SMOKE,
                    newX, getY() + 1.0, newZ, 6, 0.2, 0.5, 0.2, 0.01);
            sl.sendParticles(ParticleTypes.SCULK_SOUL,
                    newX, getY() + 0.8, newZ, 3, 0.15, 0.3, 0.15, 0.01);
                    
            // Play a subtle footstep at the new position
            if (sl.getRandom().nextInt(2) == 0) {
                sl.playSound(null, newX, getY(), newZ,
                        SoundEvents.GRAVEL_STEP, SoundSource.AMBIENT, 0.2f, 0.4f);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Vanish
    // -------------------------------------------------------------------------

    private void vanish(ServerLevel sl, Player trigger) {
        // Dramatic fog burst on vanish - swallowed by the darkness
        sl.sendParticles(ParticleTypes.LARGE_SMOKE,
                getX(), getY() + 1.0, getZ(), 35, 1.0, 0.8, 1.0, 0.03);
        sl.sendParticles(ParticleTypes.SMOKE,
                getX(), getY() + 1.0, getZ(), 25, 0.8, 0.7, 0.8, 0.02);
        sl.sendParticles(ParticleTypes.ASH,
                getX(), getY() + 0.5, getZ(), 15, 0.6, 0.5, 0.6, 0.05);
        // Stage 4+: extra soul particles on vanish
        if (stage >= 4) {
            sl.sendParticles(ParticleTypes.SCULK_SOUL,
                    getX(), getY() + 1.0, getZ(), 20, 0.5, 0.5, 0.5, 0.08);
        }

        if (trigger != null) {
            if (stage >= 5 && trigger instanceof ServerPlayer sp) {
                sp.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80,  0, false, false));
                sp.addEffect(new MobEffectInstance(MobEffects.NAUSEA,    200, 0, false, false));
                sp.addEffect(new MobEffectInstance(MobEffects.DARKNESS,  80,  1, false, false));
                ServerPlayNetworking.send(sp, new WatcherJumpscarePacket());
            } else if (stage >= 3) {
                trigger.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
                trigger.addEffect(new MobEffectInstance(MobEffects.NAUSEA,   80, 0, false, false));
            } else {
                trigger.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20, 0, false, false));
            }

            // Post-vanish haunting message (stage 2+)
            if (stage >= 2 && trigger instanceof ServerPlayer sp) {
                String afterMsg = switch (stage) {
                    case 2 -> "§8§o...it will return.";
                    case 3 -> "§8§oYou saw it. Now it knows.";
                    case 4 -> "§4It remembered your face.";
                    default -> "§4§l...IT IS NEVER GONE.";
                };
                sp.sendSystemMessage(Component.literal(afterMsg));
            }
        }

        discard();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ServerPlayer getTargetPlayer(ServerLevel sl) {
        if (targetPlayerUUID == null) return null;
        return sl.getServer().getPlayerList().getPlayer(targetPlayerUUID);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        Player attacker = (source.getEntity() instanceof Player p) ? p : null;
        vanish(level, attacker);
        return false;
    }

    @Override public boolean isInvulnerable()              { return true; }
    @Override public boolean removeWhenFarAway(double d)   { return false; }
    @Override public boolean shouldShowName()              { return false; }
}
