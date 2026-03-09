package com.example;

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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;

import java.util.UUID;

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

    public MimicEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setCustomNameVisible(true);
        this.setPersistenceRequired();
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
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.04)   // near-still in watching phase
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
    }

    @Override
    protected void registerGoals() {
        // Phase 1: just stare — berserk goals are added dynamically
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 30.0f));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
    }

    /**
     * Called right after spawning to assign a stolen identity.
     * The mimic takes the name and face of a random online player.
     */
    public void setStolenFrom(ServerPlayer player) {
        this.stolenPlayerName = player.getName().getString();

        // Sync the UUID to clients so the renderer can fetch the correct skin
        this.entityData.set(DATA_STOLEN_UUID, player.getUUID().toString());

        // Build head with the player's profile for dropping on death
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(player.getGameProfile()));
        this.stolenHead = head;

        // Name tag matches the stolen player — looks like a real player in the dark
        this.setCustomName(Component.literal("§f" + stolenPlayerName));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        ServerLevel sl = (ServerLevel) level();
        lifeTicks++;

        if (phase == Phase.WATCHING) {
            // Four escalating warnings sent to the nearest player
            if (lifeTicks == 60)  sendWarning(sl, "§7§o...the trees feel different tonight...");
            if (lifeTicks == 140) sendWarning(sl, "§7§oYou feel eyes on you. Something is nearby...");
            if (lifeTicks == 220) sendWarning(sl, "§c§oIt hasn't moved. Neither should you...");
            if (lifeTicks == 290) sendWarning(sl, "§c§l⚠ IT'S RIGHT THERE ⚠");

            if (lifeTicks >= BERSERK_TICKS) {
                triggerBerserk(sl);
            }
        } else {
            // Rage particles every few ticks to show its position while attacking
            if (lifeTicks % 5 == 0) {
                sl.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                        getX(), getY() + 1.0, getZ(), 3, 0.4, 0.4, 0.4, 0.05);
                sl.sendParticles(ParticleTypes.LARGE_SMOKE,
                        getX(), getY() + 1.0, getZ(), 2, 0.3, 0.3, 0.3, 0.05);
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

        // Speed surge — roughly sprinting player speed
        var speedAttr = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.setBaseValue(0.45);

        // Add attack and targeting AI
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.8, false));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));

        // Intense ambient sound
        sl.playSound(null, getX(), getY(), getZ(),
                SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 3.0f, 0.3f);

        // Particle explosion to reveal its position
        sl.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                getX(), getY() + 1.0, getZ(), 50, 1.5, 1.5, 1.5, 0.2);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE,
                getX(), getY() + 1.0, getZ(), 30, 1.0, 1.0, 1.0, 0.1);

        // Final personal warning to the target
        Player nearest = sl.getNearestPlayer(this, 80.0);
        if (nearest instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.literal("§c§lIT FOUND YOU."), true);
        }
    }

    @Override
    public void die(DamageSource cause) {
        if (!level().isClientSide() && level() instanceof ServerLevel sl) {
            // Death sound + particles
            sl.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 2.0f, 1.3f);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE,
                    getX(), getY() + 1.0, getZ(), 40, 0.8, 0.8, 0.8, 0.1);

            // Drop the stolen player head
            if (!stolenHead.isEmpty()) {
                ItemEntity headDrop = new ItemEntity(sl,
                        getX(), getY() + 0.5, getZ(), stolenHead.copy());
                headDrop.setDefaultPickUpDelay();
                sl.addFreshEntity(headDrop);
            }

            // Drop 1 netherite scrap (100% drop rate)
            ItemEntity scrapDrop = new ItemEntity(sl,
                    getX(), getY() + 0.5, getZ(),
                    new ItemStack(Items.NETHERITE_SCRAP, 1));
            scrapDrop.setDefaultPickUpDelay();
            sl.addFreshEntity(scrapDrop);

            // Drop 5 XP bottles
            ItemEntity xpDrop = new ItemEntity(sl,
                    getX(), getY() + 0.5, getZ(),
                    new ItemStack(Items.EXPERIENCE_BOTTLE, 5));
            xpDrop.setDefaultPickUpDelay();
            sl.addFreshEntity(xpDrop);

            // Personal kill message only to the killer
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
}
