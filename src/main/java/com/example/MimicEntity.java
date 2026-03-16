package com.example;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.EnumSet;

public class MimicEntity extends PathfinderMob {

    public static EntityType<MimicEntity> TYPE;

    private static final EntityDataAccessor<String> DATA_STOLEN_UUID =
            SynchedEntityData.defineId(MimicEntity.class, EntityDataSerializers.STRING);

    private enum Phase { WATCHING, BERSERK }

    private Phase phase = Phase.WATCHING;
    private int lifeTicks = 0;
    private static final int BERSERK_TICKS = 300; // 15 seconds

    private String stolenPlayerName = "Unknown";
    private ItemStack stolenHead = ItemStack.EMPTY;

    // Low-HP speech flag — triggers once when HP drops below 35%
    private boolean spokenLowHp = false;

    // Block-placing behaviour
    private int blockHoldTimer = 0;
    private ItemStack heldWeapon = ItemStack.EMPTY;
    private static final Block[] PLACE_BLOCKS = {
        Blocks.DIRT, Blocks.COBBLESTONE, Blocks.STONE,
        Blocks.OAK_LOG, Blocks.GRAVEL, Blocks.SAND
    };

    public MimicEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setCustomNameVisible(true);
        this.setPersistenceRequired();
        this.setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STOLEN_UUID, "");
    }

    /** Returns the stolen player's UUID as a string, or empty string if not set. */
    public String getStolenUUIDString() {
        return this.entityData.get(DATA_STOLEN_UUID);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 50.0)           // Buffed from 30
                .add(Attributes.MOVEMENT_SPEED, 0.05)       // Slightly faster
                .add(Attributes.ATTACK_DAMAGE, 12.0)        // Buffed from 9
                .add(Attributes.ATTACK_SPEED, 2.0)          // Faster attacks
                .add(Attributes.FOLLOW_RANGE, 120.0)        // Increased from 100
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.7)  // More resistant
                .add(Attributes.ARMOR, 8.0)                 // Added armor
                .add(Attributes.ARMOR_TOUGHNESS, 4.0);      // Added toughness
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new LeapAttackGoal());
        this.goalSelector.addGoal(2, new BuildClimbGoal());
        this.goalSelector.addGoal(3, new BreakBlockGoal());
        this.goalSelector.addGoal(8, new PlaceBlockGoal());
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 100.0f));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
    }

    // ── Loot pickup ──────────────────────────────────────────────────────────

    @Override
    public boolean canPickUpLoot() {
        return true;
    }

    @Override
    public boolean wantsToPickUp(ServerLevel level, ItemStack stack) {
        return true; // pick up any dropped item
    }

    // When the mob picks up an item, always equip the best weapon in main hand
    @Override
    protected void pickUpItem(ServerLevel level, ItemEntity itemEntity) {
        super.pickUpItem(level, itemEntity);
        // After vanilla equip logic, ensure best weapon is in main hand
        equipBestWeapon();
    }

    /** Scans offhand and main hand; equips whichever sword has higher damage. */
    private void equipBestWeapon() {
        ItemStack main = getItemBySlot(EquipmentSlot.MAINHAND);
        ItemStack off  = getItemBySlot(EquipmentSlot.OFFHAND);
        if (getWeaponDamage(off) > getWeaponDamage(main)) {
            setItemSlot(EquipmentSlot.MAINHAND, off.copy());
            setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }
    }

    private float getWeaponDamage(ItemStack stack) {
        if (stack.isEmpty()) return 0f;
        var modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) return 0f;
        for (var entry : modifiers.modifiers()) {
            if (entry.modifier().id().toString().contains("attack_damage")) {
                return (float) entry.modifier().amount();
            }
        }
        return 0f;
    }

    // ── Melee on-hit effects ──────────────────────────────────────────────────

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean landed = super.doHurtTarget(level, target);
        if (landed && target instanceof Player player) {
            // Blind the player for 3s (buffed from 2s)
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false));
            // Slow them down so they can't escape (stronger slow)
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 2, false, false));
            // Weakness to reduce their damage
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, false));
            // Mimic gets a speed burst and strength
            this.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 2, false, false));
            this.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 30, 0, false, false));
            
            // Scary hit effect - particles and sound
            level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    player.getX(), player.getY() + 1, player.getZ(), 15, 0.3, 0.5, 0.3, 0.1);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 1.0f, 0.6f);
        }
        return landed;
    }

    // ── Main tick ─────────────────────────────────────────────────────────────

    public void setStolenFrom(ServerPlayer player) {
        this.stolenPlayerName = player.getName().getString();
        this.entityData.set(DATA_STOLEN_UUID, player.getUUID().toString());
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(player.getGameProfile()));
        this.stolenHead = head;
        this.setCustomName(Component.literal("§f" + stolenPlayerName));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        ServerLevel sl = (ServerLevel) level();
        lifeTicks++;

        // Eject from boats / minecarts immediately — cannot be trapped
        if (isPassenger()) stopRiding();

        // Actively escape water: strong upward force + drain source blocks in BERSERK
        if (isInWater()) {
            setDeltaMovement(getDeltaMovement().x, 0.42, getDeltaMovement().z);
            if (phase == Phase.BERSERK && lifeTicks % 8 == 0) {
                BlockPos center = blockPosition();
                for (int ddx = -2; ddx <= 2; ddx++) {
                    for (int ddy = -1; ddy <= 3; ddy++) {
                        for (int ddz = -2; ddz <= 2; ddz++) {
                            BlockPos bp = center.offset(ddx, ddy, ddz);
                            if (!sl.getBlockState(bp).getFluidState().isEmpty()) {
                                sl.setBlock(bp, Blocks.AIR.defaultBlockState(), 3);
                            }
                        }
                    }
                }
            }
        }

        // Block-hold timer: revert to weapon after placing
        if (blockHoldTimer > 0) {
            blockHoldTimer--;
            if (blockHoldTimer == 0) {
                if (!heldWeapon.isEmpty()) {
                    setItemSlot(EquipmentSlot.MAINHAND, heldWeapon);
                    heldWeapon = ItemStack.EMPTY;
                } else {
                    setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                }
            }
        }

        if (phase == Phase.WATCHING) {
            if (lifeTicks == 60)  sendWarning(sl, "§7§o...the trees feel different tonight...");
            if (lifeTicks == 140) sendWarning(sl, "§7§oYou feel eyes on you. Something is nearby...");
            if (lifeTicks == 220) sendWarning(sl, "§c§oIt hasn't moved. Neither should you...");
            if (lifeTicks == 290) sendWarning(sl, "§c§l⚠ IT'S RIGHT THERE ⚠");
            if (lifeTicks >= BERSERK_TICKS) triggerBerserk(sl);
        } else {
            if (lifeTicks % 5 == 0) {
                sl.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                        getX(), getY() + 1.0, getZ(), 3, 0.4, 0.4, 0.4, 0.05);
                sl.sendParticles(ParticleTypes.LARGE_SMOKE,
                        getX(), getY() + 1.0, getZ(), 2, 0.3, 0.3, 0.3, 0.05);
            }

            // Enhanced darkness aura — blind, slow, and wither players within 16 blocks (buffed from 12)
            if (lifeTicks % 30 == 0) {
                sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(16.0))
                    .forEach(p -> {
                        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, false, false));
                        p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1, false, false));
                        p.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 0, false, false));
                    });
            }
            
            // Fear aura - mining fatigue and nausea when very close (within 8 blocks)
            if (lifeTicks % 40 == 0) {
                sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(8.0))
                    .forEach(p -> {
                        p.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 60, 2, false, false));
                        p.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 80, 0, false, false));
                    });
            }
            
            // Mimic can now "mimic" player movement - if player runs, mimic gets faster
            if (lifeTicks % 20 == 0 && getTarget() instanceof Player targetPlayer) {
                if (targetPlayer.isSprinting()) {
                    this.addEffect(new MobEffectInstance(MobEffects.SPEED, 25, 1, false, false));
                }
            }

            // Better regeneration: 1 HP every second (buffed from 0.5 every 2s)
            if (lifeTicks % 20 == 0) heal(1.0f);

            // Teleport to target if they flee beyond 24 blocks
            if (lifeTicks % 60 == 0 && getTarget() instanceof Player fleePlayer
                    && distanceTo(fleePlayer) > 24.0) {
                double tx = fleePlayer.getX() + (random.nextDouble() - 0.5) * 4;
                double tz = fleePlayer.getZ() + (random.nextDouble() - 0.5) * 4;
                sl.playSound(null, getX(), getY(), getZ(),
                        SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0f, 0.5f);
                sl.sendParticles(ParticleTypes.PORTAL,
                        getX(), getY() + 1.0, getZ(), 20, 0.5, 1.0, 0.5, 0.1);
                setPos(tx, fleePlayer.getY(), tz);
                sl.playSound(null, getX(), getY(), getZ(),
                        SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0f, 0.5f);
                sl.sendParticles(ParticleTypes.PORTAL,
                        getX(), getY() + 1.0, getZ(), 20, 0.5, 1.0, 0.5, 0.1);
            }

            // Scary speech at < 35% HP — triggers once
            if (!spokenLowHp && getHealth() < getMaxHealth() * 0.35f) {
                spokenLowHp = true;
                Player nearestSpeech = sl.getNearestPlayer(this, 80.0);
                if (nearestSpeech instanceof ServerPlayer sp) {
                    sp.sendSystemMessage(Component.literal("§4§l\"You cannot kill what was never alive.\""));
                    sl.playSound(null, getX(), getY(), getZ(),
                            SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 3.0f, 0.3f);
                    sl.sendParticles(ParticleTypes.LARGE_SMOKE,
                            getX(), getY() + 1.0, getZ(), 40, 1.0, 1.0, 1.0, 0.05);
                }
            }
        }
    }

    private void sendWarning(ServerLevel sl, String message) {
        Player nearest = sl.getNearestPlayer(this, 80.0);
        if (nearest instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.literal(message), true);
        }
    }

    private void triggerBerserk(ServerLevel sl) {
        phase = Phase.BERSERK;
        this.setInvulnerable(false);
        
        // Enhanced BERSERK stats
        var speedAttr = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.setBaseValue(0.5); // Buffed from 0.45
        
        var damageAttr = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damageAttr != null) damageAttr.setBaseValue(16.0); // Boost damage in BERSERK
        
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 2.5, true)); // Faster attack
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, false));
        
        // Terrifying transformation sound combo
        sl.playSound(null, getX(), getY(), getZ(),
                SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 4.0f, 0.3f);
        sl.playSound(null, getX(), getY(), getZ(),
                SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 2.0f, 0.5f);
        sl.playSound(null, getX(), getY(), getZ(),
                SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.5f, 0.4f);
        
        // Enhanced particle burst
        sl.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                getX(), getY() + 1.0, getZ(), 80, 2.0, 2.0, 2.0, 0.3);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE,
                getX(), getY() + 1.0, getZ(), 50, 1.5, 1.5, 1.5, 0.15);
        sl.sendParticles(ParticleTypes.SCULK_SOUL,
                getX(), getY() + 0.5, getZ(), 40, 1.0, 1.0, 1.0, 0.1);
        
        // Apply darkness to all nearby players
        sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(25.0))
            .forEach(p -> {
                p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
            });
        
        Player nearest = sl.getNearestPlayer(this, 80.0);
        if (nearest instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.literal("§c§lIT FOUND YOU."), true);
            // Personal horror sound to the nearest player
            sl.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.0f, 0.5f);
        }
    }

    @Override
    public void die(DamageSource cause) {
        if (!level().isClientSide() && level() instanceof ServerLevel sl) {
            sl.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 2.0f, 1.3f);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE,
                    getX(), getY() + 1.0, getZ(), 40, 0.8, 0.8, 0.8, 0.1);
            if (!stolenHead.isEmpty()) {
                ItemEntity headDrop = new ItemEntity(sl,
                        getX(), getY() + 0.5, getZ(), stolenHead.copy());
                headDrop.setDefaultPickUpDelay();
                sl.addFreshEntity(headDrop);
            }
            ItemEntity scrapDrop = new ItemEntity(sl,
                    getX(), getY() + 0.5, getZ(),
                    new ItemStack(Items.NETHERITE_SCRAP, 1));
            scrapDrop.setDefaultPickUpDelay();
            sl.addFreshEntity(scrapDrop);
            ItemEntity xpDrop = new ItemEntity(sl,
                    getX(), getY() + 0.5, getZ(),
                    new ItemStack(Items.EXPERIENCE_BOTTLE, 5));
            xpDrop.setDefaultPickUpDelay();
            sl.addFreshEntity(xpDrop);
            if (cause.getEntity() instanceof ServerPlayer killer) {
                killer.displayClientMessage(
                        Component.literal("§6✦ §eYou unmasked The Mimic. §7It was wearing §f" +
                                stolenPlayerName + "§7's face."),
                        false);
            }
        }
        super.die(cause);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    // ── Leap Attack AI goal (BERSERK) ─────────────────────────────────────────

    private class LeapAttackGoal extends Goal {

        private int cooldown = 0;

        public LeapAttackGoal() {
            this.setFlags(EnumSet.noneOf(Goal.Flag.class));
        }

        @Override
        public boolean canUse() {
            if (phase != Phase.BERSERK) return false;
            if (level().isClientSide()) return false;
            if (--cooldown > 0) return false;
            if (!(getTarget() instanceof Player player)) return false;
            double distSq = distanceToSqr(player);
            return distSq >= 9.0 && distSq <= 144.0; // 3–12 blocks
        }

        @Override
        public boolean canContinueToUse() { return false; }

        @Override
        public void start() {
            cooldown = 60; // 3-second cooldown between leaps
            if (!(getTarget() instanceof Player player)) return;
            double dx = player.getX() - getX();
            double dz = player.getZ() - getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < 0.001) return;
            dx /= dist;
            dz /= dist;
            setDeltaMovement(dx * 0.9, 0.55, dz * 0.9);
            if (level() instanceof ServerLevel sl) {
                sl.playSound(null, getX(), getY(), getZ(),
                        SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 1.5f, 0.5f);
                sl.sendParticles(ParticleTypes.LARGE_SMOKE,
                        getX(), getY() + 0.5, getZ(), 15, 0.3, 0.2, 0.3, 0.05);
            }
        }
    }

    // ── Build-climb AI goal (BERSERK) ─────────────────────────────────────────

    private class BuildClimbGoal extends Goal {

        private int cooldown = 0;

        public BuildClimbGoal() {
            this.setFlags(EnumSet.noneOf(Goal.Flag.class));
        }

        @Override
        public boolean canUse() {
            if (phase != Phase.BERSERK) return false;
            if (level().isClientSide()) return false;
            if (blockHoldTimer > 0) return false;
            if (--cooldown > 0) return false;
            if (!(getTarget() instanceof Player player)) return false;
            double yDiff = player.getY() - getY();
            double dx = player.getX() - getX();
            double dz = player.getZ() - getZ();
            double hDist = Math.sqrt(dx * dx + dz * dz);
            return yDiff > 1.5 && hDist < 18.0;
        }

        @Override
        public boolean canContinueToUse() { return false; }

        @Override
        public void start() {
            cooldown = 20; // one build step per second
            if (!(level() instanceof ServerLevel sl)) return;
            if (!(getTarget() instanceof Player player)) return;

            double dx = player.getX() - getX();
            double dz = player.getZ() - getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 0.001) return;
            dx /= len;
            dz /= len;

            // Only use solid (non-gravity) climb blocks
            Block[] climbBlocks = { Blocks.STONE, Blocks.COBBLESTONE, Blocks.DIRT, Blocks.OAK_LOG };
            Block stepBlock = climbBlocks[random.nextInt(climbBlocks.length)];

            // Try to place a step block 1 block ahead of the mimic, scanning upward
            BlockPos placed = null;
            for (int rise = 0; rise <= 3 && placed == null; rise++) {
                BlockPos pos = BlockPos.containing(
                        getX() + dx * 1.1, getY() + rise, getZ() + dz * 1.1);
                if (sl.getBlockState(pos).isAir()) {
                    sl.setBlock(pos, stepBlock.defaultBlockState(), 3);
                    placed = pos;
                }
            }

            if (placed == null) return;

            // Visually hold the block
            heldWeapon = getItemBySlot(EquipmentSlot.MAINHAND).copy();
            setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(stepBlock));
            blockHoldTimer = 25;

            // Jump upward to start climbing, preserving horizontal movement
            setDeltaMovement(getDeltaMovement().x, 0.5, getDeltaMovement().z);
        }
    }

    // ── Block-break AI goal ───────────────────────────────────────────────────

    private class BreakBlockGoal extends Goal {

        private double prevX = 0, prevZ = 0;
        private int stuckTicks = 0;
        private int breakCooldown = 0;

        public BreakBlockGoal() {
            this.setFlags(EnumSet.noneOf(Goal.Flag.class));
        }

        @Override
        public boolean canUse() {
            return phase == Phase.BERSERK && !level().isClientSide() && getTarget() instanceof Player;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            if (--breakCooldown > 0) return;
            breakCooldown = 5; // one break attempt every 5 ticks (0.25 s)

            if (!(level() instanceof ServerLevel sl)) return;
            if (!(getTarget() instanceof Player player)) return;

            // Stuck detection: measure horizontal movement since last check
            double movedSq = (getX() - prevX) * (getX() - prevX) + (getZ() - prevZ) * (getZ() - prevZ);
            prevX = getX();
            prevZ = getZ();
            if (movedSq < 0.04) {
                stuckTicks++;
            } else {
                stuckTicks = 0;
            }

            if (stuckTicks >= 5) {
                // Cage escape: demolish all surrounding blocks
                stuckTicks = 0;
                BlockPos center = blockPosition();
                for (BlockPos pos : new BlockPos[]{
                    center.north(), center.south(), center.east(), center.west(),
                    center.north().above(), center.south().above(),
                    center.east().above(), center.west().above(),
                    center.above(), center.above().above()
                }) {
                    var state = sl.getBlockState(pos);
                    if (!state.isAir() && !state.is(Blocks.BEDROCK) && !state.is(Blocks.BARRIER)) {
                        sl.destroyBlock(pos, false);
                    }
                }
                return;
            }

            // Normal path-clearing: break foot + head blocks toward target
            double dx = player.getX() - getX();
            double dz = player.getZ() - getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1.0) return;
            dx /= len;
            dz /= len;

            BlockPos foot = BlockPos.containing(getX() + dx, getY(), getZ() + dz);
            BlockPos head = foot.above();

            for (BlockPos pos : new BlockPos[]{foot, head}) {
                var state = sl.getBlockState(pos);
                if (!state.isAir() && !state.is(Blocks.BEDROCK) && !state.is(Blocks.BARRIER)) {
                    sl.destroyBlock(pos, false);
                }
            }
        }
    }

    // ── Block-placing AI goal ─────────────────────────────────────────────────

    private class PlaceBlockGoal extends Goal {

        private int cooldown = 80;

        public PlaceBlockGoal() {
            this.setFlags(EnumSet.noneOf(Goal.Flag.class));
        }

        @Override
        public boolean canUse() {
            if (phase != Phase.WATCHING) return false;
            if (level().isClientSide()) return false;
            if (blockHoldTimer > 0) return false;
            return --cooldown <= 0;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            cooldown = 60 + random.nextInt(120); // 3–9 s between placements

            if (!(level() instanceof ServerLevel sl)) return;

            for (int attempt = 0; attempt < 15; attempt++) {
                BlockPos pos = blockPosition().offset(
                        random.nextIntBetweenInclusive(-4, 4),
                        random.nextIntBetweenInclusive(-1, 2),
                        random.nextIntBetweenInclusive(-4, 4));

                if (!sl.getBlockState(pos).isAir()) continue;
                if (!sl.getBlockState(pos.below()).isCollisionShapeFullBlock(sl, pos.below())) continue;

                Block toPlace = PLACE_BLOCKS[random.nextInt(PLACE_BLOCKS.length)];
                sl.setBlock(pos, toPlace.defaultBlockState(), 3);

                // Save current weapon and visually hold the block
                heldWeapon = getItemBySlot(EquipmentSlot.MAINHAND).copy();
                setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(toPlace));
                blockHoldTimer = 40; // hold for 2 seconds then revert
                return;
            }
        }
    }
}
