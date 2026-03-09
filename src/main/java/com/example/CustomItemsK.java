package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CustomItemsK implements ModInitializer {
    public static final String MOD_ID = "customitemsk";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // Register custom entity types
        ResourceKey<EntityType<?>> stormArrowKey = ResourceKey.create(
                Registries.ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(MOD_ID, "storm_arrow"));
        StormArrowEntity.TYPE = Registry.register(
                BuiltInRegistries.ENTITY_TYPE,
                stormArrowKey,
                EntityType.Builder.<StormArrowEntity>of(StormArrowEntity::new, MobCategory.MISC)
                        .sized(0.5f, 0.5f)
                        .clientTrackingRange(4)
                        .build(stormArrowKey));

        ResourceKey<EntityType<?>> spiritKey = ResourceKey.create(
                Registries.ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(MOD_ID, "forest_spirit"));
        ForestSpiritEntity.TYPE = Registry.register(
                BuiltInRegistries.ENTITY_TYPE,
                spiritKey,
                EntityType.Builder.<ForestSpiritEntity>of(ForestSpiritEntity::new, MobCategory.CREATURE)
                        .sized(0.6f, 1.8f)
                        .clientTrackingRange(8)
                        .build(spiritKey));
        FabricDefaultAttributeRegistry.register(ForestSpiritEntity.TYPE, ForestSpiritEntity.createAttributes().build());

        ResourceKey<EntityType<?>> mimicKey = ResourceKey.create(
                Registries.ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(MOD_ID, "mimic"));
        MimicEntity.TYPE = Registry.register(
                BuiltInRegistries.ENTITY_TYPE,
                mimicKey,
                EntityType.Builder.<MimicEntity>of(MimicEntity::new, MobCategory.MONSTER)
                        .sized(0.6f, 1.8f)
                        .clientTrackingRange(10)
                        .build(mimicKey));
        FabricDefaultAttributeRegistry.register(MimicEntity.TYPE, MimicEntity.createAttributes().build());

        ModItems.registerItems();
        registerStormBowEvents();
        registerPoisonIvyEvents();
        registerStarForgedPickaxeEvents();
        registerNaturesGuardianEvents();
        registerForestSpiritEvents();
        registerMimicEvents();
        registerPlayerHeadEvents();
        LOGGER.info("CustomItemsK loaded!");
    }

    private void registerStormBowEvents() {
        // Thunder Mark: every Storm Bow arrow hit adds 1 mark; at 3 marks → lightning explosion + stun
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, killed) -> {
            if (source.getDirectEntity() instanceof AbstractArrow arrow
                    && StormBowItem.STORM_ARROWS.contains(arrow.getUUID())
                    && entity.level() instanceof ServerLevel sl) {

                int marks = StormBowItem.THUNDER_MARKS.merge(entity.getUUID(), 1, Integer::sum);

                if (marks >= 3) {
                    StormBowItem.THUNDER_MARKS.remove(entity.getUUID());

                    // Strike the target
                    LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, sl);
                    bolt.setPos(entity.getX(), entity.getY(), entity.getZ());
                    sl.addFreshEntity(bolt);

                    // Area damage + stun nearby enemies
                    sl.getEntitiesOfClass(LivingEntity.class,
                                    entity.getBoundingBox().inflate(4.0),
                                    e -> e != entity)
                            .forEach(nearby -> {
                                nearby.hurt(sl.damageSources().lightningBolt(), 6.0f);
                                nearby.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 3, false, true));
                                nearby.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1, false, true));
                            });

                    // Stun the marked target too
                    entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 3, false, true));
                    entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1, false, true));

                    sl.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                            SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.5f, 0.8f);
                }
            }

            // Clear mark when the entity dies
            if (killed) {
                StormBowItem.THUNDER_MARKS.remove(entity.getUUID());
            }
        });

        // Clean up arrows that land in the ground without hitting anything
        ServerTickEvents.END_WORLD_TICK.register(level -> {
            if (StormBowItem.STORM_ARROWS.isEmpty()) return;
            Set<UUID> done = new HashSet<>();
            for (UUID id : StormBowItem.STORM_ARROWS) {
                var e = level.getEntity(id);
                if (e == null || (e instanceof AbstractArrow arrow
                        && arrow.getDeltaMovement().lengthSqr() < 0.0001)) {
                    done.add(id);
                }
            }
            StormBowItem.STORM_ARROWS.removeAll(done);
        });
    }

    // Venom mark counter per victim UUID (tracks stacked Poison III hits)
    private static final Map<UUID, Integer> VENOM_MARKS = new ConcurrentHashMap<>();

    private void registerPoisonIvyEvents() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(source.getDirectEntity() instanceof AbstractArrow arrow)) return true;
            if (!PoisonIvyItem.POISON_ARROWS.contains(arrow.getUUID())) return true;

            PoisonIvyItem.HIT_ARROWS.add(arrow.getUUID());

            Player shooter = source.getEntity() instanceof Player p ? p : null;
            boolean hasSynergy = shooter != null
                    && (shooter.getMainHandItem().getItem() == ModItems.NATURES_GUARDIAN
                    || shooter.getOffhandItem().getItem() == ModItems.NATURES_GUARDIAN);

            // Hunger cost per shot
            if (shooter != null) {
                shooter.getFoodData().setFoodLevel(
                        Math.max(0, shooter.getFoodData().getFoodLevel() - 2));
            }

            if (hasSynergy && entity.level() instanceof ServerLevel sl) {
                // --- Synergy mode: Poison III ---
                boolean alreadyPoisoned = entity.hasEffect(MobEffects.POISON);
                entity.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 2, false, true));

                // Stack marks only while target is already poisoned (consecutive hits)
                int marks = VENOM_MARKS.compute(entity.getUUID(),
                        (k, v) -> alreadyPoisoned ? (v == null ? 1 : v + 1) : 1);

                // Ground erosion: grass → dirt, dirt → coarse dirt under victim
                BlockPos center = entity.blockPosition();
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        BlockPos bp = center.offset(dx, -1, dz);
                        var state = sl.getBlockState(bp);
                        if (state.is(Blocks.GRASS_BLOCK)) {
                            sl.setBlock(bp, Blocks.DIRT.defaultBlockState(), 3);
                        } else if (state.is(Blocks.DIRT)) {
                            sl.setBlock(bp, Blocks.COARSE_DIRT.defaultBlockState(), 3);
                        }
                    }
                }

                // Poison drip particles at impact
                sl.sendParticles(ParticleTypes.SNEEZE,
                        entity.getX(), entity.getY() + 0.5, entity.getZ(),
                        20, 0.4, 0.5, 0.4, 0.05);

                // Ultimate at 3 marks
                if (marks >= 3) {
                    VENOM_MARKS.remove(entity.getUUID());
                    triggerToxicBloom(sl, entity, shooter);
                }
            } else {
                // Normal: Poison I
                entity.addEffect(new MobEffectInstance(MobEffects.POISON, 120, 0));
            }
            return true;
        });

        // Clean up marks on death
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) ->
                VENOM_MARKS.remove(entity.getUUID()));
    }

    private void triggerToxicBloom(ServerLevel sl, LivingEntity victim, Player shooter) {
        // 1. Burst instant magic damage to victim
        victim.hurt(sl.damageSources().magic(), 12.0f);

        // 2. Overwhelm all entities within 7 blocks
        sl.getEntitiesOfClass(LivingEntity.class,
                victim.getBoundingBox().inflate(7.0),
                e -> e != shooter)
            .forEach(nearby -> {
                nearby.addEffect(new MobEffectInstance(MobEffects.POISON,  200, 2, false, true));
                nearby.addEffect(new MobEffectInstance(MobEffects.WITHER,   100, 1, false, true));
                nearby.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 3, false, true));
                nearby.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1, false, true));
            });

        // 3. Earth permanently corrupts within 5 blocks: grass/dirt → moss block
        BlockPos center = victim.blockPosition();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                if (dx * dx + dz * dz > 25) continue; // circular radius
                BlockPos bp = center.offset(dx, -1, dz);
                var state = sl.getBlockState(bp);
                if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)
                        || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT)) {
                    sl.setBlock(bp, Blocks.MOSS_BLOCK.defaultBlockState(), 3);
                }
            }
        }

        // 4. Massive spore explosion
        sl.sendParticles(ParticleTypes.SNEEZE,
                victim.getX(), victim.getY() + 1.0, victim.getZ(),
                80, 2.0, 1.5, 2.0, 0.15);
        sl.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                victim.getX(), victim.getY() + 1.0, victim.getZ(),
                60, 2.5, 2.5, 2.5, 0.05);
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                victim.getX(), victim.getY() + 1.0, victim.getZ(),
                40, 1.5, 1.5, 1.5, 0.1);

        // 5. Intense sound
        sl.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 3.0f, 0.5f);

        // 6. Server-wide broadcast
        String shooterName = shooter != null ? shooter.getName().getString() : "the forest";
        String victimName  = victim.getName().getString();
        sl.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("§2☠ §a" + shooterName +
                        "§7's Poison Ivy has devoured §a" + victimName +
                        "§7!\n§2§l☠ TOXIC BLOOM §7was unleashed upon the earth§2§l ☠"),
                false);
    }

    private void registerStarForgedPickaxeEvents() {
        // Directional 3x3 mining: mines the 8 surrounding blocks on the plane
        // perpendicular to the player's dominant look direction.
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (player.getMainHandItem().getItem() == ModItems.STARFORGED_PICKAXE
                    && world instanceof ServerLevel sl) {

                Vec3 look = player.getLookAngle();
                double ax = Math.abs(look.x), ay = Math.abs(look.y), az = Math.abs(look.z);

                List<BlockPos> pattern = new ArrayList<>();
                if (ay >= ax && ay >= az) {
                    // Looking mostly up/down → mine horizontal XZ 3x3
                    for (int dx = -1; dx <= 1; dx++)
                        for (int dz = -1; dz <= 1; dz++)
                            if (dx != 0 || dz != 0) pattern.add(pos.offset(dx, 0, dz));
                } else if (ax >= az) {
                    // Looking mostly east/west → mine YZ 3x3
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dz = -1; dz <= 1; dz++)
                            if (dy != 0 || dz != 0) pattern.add(pos.offset(0, dy, dz));
                } else {
                    // Looking mostly north/south → mine XY 3x3
                    for (int dx = -1; dx <= 1; dx++)
                        for (int dy = -1; dy <= 1; dy++)
                            if (dx != 0 || dy != 0) pattern.add(pos.offset(dx, dy, 0));
                }

                for (BlockPos p : pattern) {
                    var s = sl.getBlockState(p);
                    if (!s.isAir() && !s.is(Blocks.BEDROCK) && !s.is(Blocks.BARRIER)) {
                        sl.destroyBlock(p, true, player);
                    }
                }

                // Star-burst particle at broken block center
                sl.sendParticles(ParticleTypes.CRIT,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        12, 0.4, 0.4, 0.4, 0.15);
            }
        });

        // Meteor shower tick: spawn explosive SmallFireballs from above every FIRE_INTERVAL ticks.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (StarForgedPickaxeItem.METEOR_PLAYERS.isEmpty()) return;

            List<UUID> toRemove = new ArrayList<>();
            for (var entry : StarForgedPickaxeItem.METEOR_PLAYERS.entrySet()) {
                UUID uuid     = entry.getKey();
                int remaining = entry.getValue();

                ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
                if (sp == null) { toRemove.add(uuid); continue; }

                ServerLevel sl = (ServerLevel) sp.level();

                if (remaining % StarForgedPickaxeItem.FIRE_INTERVAL == 0) {
                    // Pick a random nearby living entity as target, or fall back to random angle.
                    List<LivingEntity> targets = sl.getEntitiesOfClass(
                            LivingEntity.class,
                            sp.getBoundingBox().inflate(20.0),
                            e -> e != sp);

                    double tx, ty, tz;
                    if (!targets.isEmpty()) {
                        LivingEntity target = targets.get(sl.getRandom().nextInt(targets.size()));
                        tx = target.getX();
                        ty = target.getY() + 0.5;
                        tz = target.getZ();
                    } else {
                        double angle = sl.getRandom().nextDouble() * Math.PI * 2;
                        double dist  = 5 + sl.getRandom().nextDouble() * 10;
                        tx = sp.getX() + Math.cos(angle) * dist;
                        ty = sp.getY();
                        tz = sp.getZ() + Math.sin(angle) * dist;
                    }

                    // Spawn point: random position high above the player.
                    double spawnX = sp.getX() + (sl.getRandom().nextDouble() - 0.5) * 8;
                    double spawnY = sp.getY() + 18 + sl.getRandom().nextDouble() * 6;
                    double spawnZ = sp.getZ() + (sl.getRandom().nextDouble() - 0.5) * 8;

                    Vec3 dir = new Vec3(tx - spawnX, ty - spawnY, tz - spawnZ)
                            .normalize().scale(0.4);

                    LargeFireball fb = new LargeFireball(sl, sp, dir, 2);
                    fb.setPos(spawnX, spawnY, spawnZ);
                    sl.addFreshEntity(fb);

                    sl.playSound(null, spawnX, spawnY, spawnZ,
                            SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS,
                            0.5f, 1.1f + sl.getRandom().nextFloat() * 0.4f);

                    // Flame trail at spawn point
                    sl.sendParticles(ParticleTypes.FLAME,
                            spawnX, spawnY, spawnZ, 6, 0.2, 0.2, 0.2, 0.05);
                }

                // Glowing enchant sparkle around the player every tick
                if (remaining % 2 == 0) {
                    sl.sendParticles(ParticleTypes.ENCHANT,
                            sp.getX(), sp.getY() + 1.0, sp.getZ(),
                            4, 0.4, 0.6, 0.4, 0.1);
                }

                if (remaining <= 1) {
                    toRemove.add(uuid);
                } else {
                    StarForgedPickaxeItem.METEOR_PLAYERS.put(uuid, remaining - 1);
                }
            }
            toRemove.forEach(StarForgedPickaxeItem.METEOR_PLAYERS::remove);
        });
    }

    private void registerNaturesGuardianEvents() {
        // Ancient Awakening: low HP + near trees + gets hit → massive defense + regen
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, killed) -> {
            if (!(entity instanceof Player player)) return;
            if (!(player.level() instanceof ServerLevel sl)) return;
            if (player.getMainHandItem().getItem() != ModItems.NATURES_GUARDIAN
                    && player.getOffhandItem().getItem() != ModItems.NATURES_GUARDIAN) return;
            // Low HP: at or below 4 hearts (8 health)
            if (player.getHealth() > 8.0f) return;
            // Cooldown check
            long now = sl.getGameTime();
            Long last = NaturesGuardianItem.AWAKENING_COOLDOWNS.get(player.getUUID());
            if (last != null && now - last < NaturesGuardianItem.AWAKENING_COOLDOWN) return;
            // Must be near tree logs
            if (!isNearTree(sl, player)) return;

            NaturesGuardianItem.AWAKENING_COOLDOWNS.put(player.getUUID(), now);
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 3, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 2, false, true));
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    player.getX(), player.getY() + 1.0, player.getZ(), 25, 0.6, 0.6, 0.6, 0.1);
            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 0.6f);
            if (player instanceof ServerPlayer sp) {
                sp.displayClientMessage(
                        Component.literal("§2§l✦ Ancient Awakening! §aThe forest shields you!"), true);
            }
        });

        // Shield thorns: holding right-click with Nature's Guardian blocks damage
        // and causes splinter bleed on the attacker
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof Player player)) return true;
            if (!player.isUsingItem()) return true;
            if (player.getUseItem().getItem() != ModItems.NATURES_GUARDIAN) return true;
            if (!(player.level() instanceof ServerLevel sl)) return true;

            if (source.getEntity() instanceof LivingEntity attacker) {
                // Splinter bleed: wither (bleed) + slowness (roots) + weakness
                attacker.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1, false, true));
                attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 2, false, true));
                attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0, false, true));
                sl.sendParticles(ParticleTypes.CRIT,
                        attacker.getX(), attacker.getY() + 1.0, attacker.getZ(),
                        12, 0.3, 0.5, 0.3, 0.1);
                sl.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                        SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 1.0f, 0.5f);
            }
            return false; // Block the damage entirely
        });
    }

    // --- Forest Spirit spawn tracking ---
    private static final Map<UUID, Vec3>    SPIRIT_LAST_POS    = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> SPIRIT_STILL_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long>    SPIRIT_COOLDOWNS   = new ConcurrentHashMap<>();
    private static final long SPIRIT_COOLDOWN_TICKS = 6000L; // 5 minutes

    private void registerForestSpiritEvents() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                UUID uuid = sp.getUUID();
                Vec3 pos  = sp.position();
                Vec3 last = SPIRIT_LAST_POS.get(uuid);

                if (last != null && pos.distanceToSqr(last) < 0.01) {
                    int still = SPIRIT_STILL_TICKS.merge(uuid, 1, Integer::sum);

                    ServerLevel spLevel = (ServerLevel) sp.level();
                    if (still >= 60
                            && sp.getHealth() <= 8.0f
                            && spLevel.getBiome(sp.blockPosition()).is(BiomeTags.IS_FOREST)) {
                        long now      = spLevel.getGameTime();
                        Long lastSpawn = SPIRIT_COOLDOWNS.get(uuid);
                        if (lastSpawn == null || now - lastSpawn >= SPIRIT_COOLDOWN_TICKS) {
                            spawnForestSpirit(sp);
                            SPIRIT_COOLDOWNS.put(uuid, now);
                            SPIRIT_STILL_TICKS.put(uuid, 0);
                        }
                    }
                } else {
                    SPIRIT_STILL_TICKS.put(uuid, 0);
                }
                SPIRIT_LAST_POS.put(uuid, pos);
            }
        });
    }

    private static void spawnForestSpirit(ServerPlayer player) {
        ServerLevel sl = (ServerLevel) player.level();

        BlockPos spawnPos = findSafeSpiritPos(sl, player);
        if (spawnPos == null) return; // No safe spot found, skip this attempt

        ForestSpiritEntity spirit = new ForestSpiritEntity(ForestSpiritEntity.TYPE, sl);
        spirit.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
        sl.addFreshEntity(spirit);

        sl.playSound(null, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(),
                SoundEvents.ENDERMAN_AMBIENT, SoundSource.NEUTRAL, 0.8f, 0.4f);
        player.displayClientMessage(
                Component.literal("§2§o...you feel a presence watching you from the trees..."), true);
    }

    /** Tries up to 12 random positions near the player; returns the first safe ground spot. */
    private static BlockPos findSafeSpiritPos(ServerLevel level, ServerPlayer player) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            double dist  = 4 + level.getRandom().nextDouble() * 5;
            int tx = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
            int tz = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);

            // Scan from 5 above player down to 5 below, looking for a solid floor with 2 clear blocks above
            for (int ty = (int) player.getY() + 5; ty >= (int) player.getY() - 5; ty--) {
                BlockPos floor  = new BlockPos(tx, ty,     tz);
                BlockPos feet   = new BlockPos(tx, ty + 1, tz);
                BlockPos head   = new BlockPos(tx, ty + 2, tz);

                var floorState = level.getBlockState(floor);
                var feetState  = level.getBlockState(feet);
                var headState  = level.getBlockState(head);

                boolean floorSolid    = floorState.isSolid();
                boolean feetClear     = feetState.isAir()  || feetState.is(net.minecraft.tags.BlockTags.REPLACEABLE);
                boolean headClear     = headState.isAir()  || headState.is(net.minecraft.tags.BlockTags.REPLACEABLE);
                // Reject if either body block is leaves (would suffocate)
                boolean notInLeaves   = !feetState.is(net.minecraft.tags.BlockTags.LEAVES)
                                     && !headState.is(net.minecraft.tags.BlockTags.LEAVES);

                if (floorSolid && feetClear && headClear && notInLeaves) {
                    return feet; // Stand on top of the floor block
                }
            }
        }
        return null;
    }

    private static boolean isNearTree(ServerLevel level, Player player) {
        BlockPos center = player.blockPosition();
        for (int dx = -4; dx <= 4; dx++)
            for (int dy = -2; dy <= 4; dy++)
                for (int dz = -4; dz <= 4; dz++)
                    if (level.getBlockState(center.offset(dx, dy, dz)).is(BlockTags.LOGS))
                        return true;
        return false;
    }

    // --- Player head drop + consumption ---
    private static final Map<UUID, Long> HEAD_EAT_COOLDOWNS = new ConcurrentHashMap<>();
    private static final long HEAD_EAT_COOLDOWN_TICKS = 400L; // 20 seconds

    private void registerPlayerHeadEvents() {
        // Drop victim's head on PvP kill
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayer victim)) return;
            if (!(source.getEntity() instanceof Player)) return;

            com.mojang.authlib.GameProfile profile = victim.getGameProfile();
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));

            ItemEntity ie = new ItemEntity(
                    victim.level(),
                    victim.getX(), victim.getY() + 0.5, victim.getZ(),
                    head);
            ie.setPickUpDelay(10);
            victim.level().addFreshEntity(ie);
        });

        // Right-click player head: heal + resistance + random teleport, 20s cooldown
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() != Items.PLAYER_HEAD) return InteractionResult.PASS;
            if (!(world instanceof ServerLevel sl)) return InteractionResult.PASS;

            long now = sl.getGameTime();
            Long last = HEAD_EAT_COOLDOWNS.get(player.getUUID());
            if (last != null && now - last < HEAD_EAT_COOLDOWN_TICKS) {
                long remaining = (HEAD_EAT_COOLDOWN_TICKS - (now - last)) / 20;
                if (player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(
                            Component.literal("§cHead on cooldown! §e" + remaining + "s remaining"), true);
                }
                return InteractionResult.FAIL;
            }

            HEAD_EAT_COOLDOWNS.put(player.getUUID(), now);

            // Heal 4–6 hearts (8–12 HP)
            float heal = 8.0f + sl.getRandom().nextFloat() * 4.0f;
            player.heal(heal);

            // Resistance II for 8 seconds
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 160, 1, false, true));

            // Consume one head
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }

            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 1.0f, 1.0f);
            sl.sendParticles(ParticleTypes.HEART,
                    player.getX(), player.getY() + 1.5, player.getZ(),
                    8, 0.4, 0.4, 0.4, 0.05);

            if (player instanceof ServerPlayer sp) {
                sp.displayClientMessage(
                        Component.literal("§c❤ §eYou consumed the fallen's head... §7+§c" +
                                String.format("%.1f", heal / 2) + " hearts"), true);
            }

            // Random teleport within 30 blocks
            BlockPos tpPos = findHeadTeleportPos(sl, player);
            if (tpPos != null && player instanceof ServerPlayer sp) {
                sp.teleportTo(tpPos.getX() + 0.5, tpPos.getY(), tpPos.getZ() + 0.5);
                sl.playSound(null, tpPos.getX(), tpPos.getY(), tpPos.getZ(),
                        SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
                sl.sendParticles(ParticleTypes.PORTAL,
                        tpPos.getX() + 0.5, tpPos.getY() + 1.0, tpPos.getZ() + 0.5,
                        20, 0.5, 0.5, 0.5, 0.1);
            }

            return InteractionResult.SUCCESS;
        });
    }

    // --- Mimic spawn logic ---
    private static final Map<UUID, Long> MIMIC_COOLDOWNS = new ConcurrentHashMap<>();
    private static final long MIMIC_COOLDOWN_TICKS = 12000L; // 10 minutes

    private void registerMimicEvents() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 100 != 0) return; // Check every 5 seconds

            for (ServerPlayer target : server.getPlayerList().getPlayers()) {
                ServerLevel sl = (ServerLevel) target.level();

                // Night only (day time 13000–23500)
                long dayTime = sl.getDayTime() % 24000;
                if (dayTime < 13000 || dayTime > 23500) continue;

                // Forest/jungle/taiga biome only
                var biome = sl.getBiome(target.blockPosition());
                if (!biome.is(BiomeTags.IS_FOREST)
                        && !biome.is(BiomeTags.IS_JUNGLE)
                        && !biome.is(BiomeTags.IS_TAIGA)) continue;

                // Target must be alone — no other player within 80 blocks
                boolean isAlone = server.getPlayerList().getPlayers().stream()
                        .filter(p -> p != target)
                        .allMatch(p -> p.distanceToSqr(target) > 80.0 * 80.0);
                if (!isAlone) continue;

                // Per-player cooldown
                long now = sl.getGameTime();
                Long lastSpawn = MIMIC_COOLDOWNS.get(target.getUUID());
                if (lastSpawn != null && now - lastSpawn < MIMIC_COOLDOWN_TICKS) continue;

                // No mimic already lurking within 100 blocks
                if (!sl.getEntitiesOfClass(MimicEntity.class,
                        target.getBoundingBox().inflate(100.0)).isEmpty()) continue;

                // ~2% chance per 5-second check
                if (sl.getRandom().nextInt(50) != 0) continue;

                boolean spawned = spawnMimic(sl, target, server);
                if (spawned) MIMIC_COOLDOWNS.put(target.getUUID(), now);
            }
        });
    }

    private boolean spawnMimic(ServerLevel sl, ServerPlayer target,
                               net.minecraft.server.MinecraftServer server) {
        BlockPos spawnPos = findSafeMimicPos(sl, target);
        if (spawnPos == null) return false;

        // 50% chance to steal the target's own identity (seeing yourself in the dark),
        // 50% chance to steal a random other online player's identity
        List<ServerPlayer> online = server.getPlayerList().getPlayers();
        List<ServerPlayer> others = online.stream().filter(p -> p != target).toList();
        ServerPlayer stolenFrom;
        if (others.isEmpty() || sl.getRandom().nextBoolean()) {
            stolenFrom = target;
        } else {
            stolenFrom = others.get(sl.getRandom().nextInt(others.size()));
        }

        MimicEntity mimic = new MimicEntity(MimicEntity.TYPE, sl);
        mimic.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
        mimic.setStolenFrom(stolenFrom);
        sl.addFreshEntity(mimic);

        // Subtle ambient sound to set the mood
        sl.playSound(null, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(),
                SoundEvents.ENDERMAN_AMBIENT, SoundSource.AMBIENT, 0.6f, 0.5f);

        // First whisper
        target.displayClientMessage(
                Component.literal("§7§o...something isn't right."), true);
        return true;
    }

    /** Finds a safe spawn point 12–25 blocks from the player — far enough to be suspicious, close enough to see. */
    private static BlockPos findSafeMimicPos(ServerLevel level, ServerPlayer player) {
        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            double dist  = 12 + level.getRandom().nextDouble() * 13;
            int tx = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
            int tz = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);

            for (int ty = (int) player.getY() + 15; ty >= (int) player.getY() - 15; ty--) {
                BlockPos floor = new BlockPos(tx, ty,     tz);
                BlockPos feet  = new BlockPos(tx, ty + 1, tz);
                BlockPos head  = new BlockPos(tx, ty + 2, tz);

                if (level.getBlockState(floor).isSolid()
                        && (level.getBlockState(feet).isAir() || level.getBlockState(feet).is(BlockTags.REPLACEABLE))
                        && (level.getBlockState(head).isAir() || level.getBlockState(head).is(BlockTags.REPLACEABLE))
                        && !level.getBlockState(feet).is(BlockTags.LEAVES)
                        && !level.getBlockState(head).is(BlockTags.LEAVES)) {
                    return feet;
                }
            }
        }
        return null;
    }

    /** Finds a safe teleport position within 5–30 blocks of the player. */
    private static BlockPos findHeadTeleportPos(ServerLevel level, Player player) {
        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            double dist  = 5 + level.getRandom().nextDouble() * 25;
            int tx = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
            int tz = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);

            for (int ty = (int) player.getY() + 5; ty >= (int) player.getY() - 5; ty--) {
                BlockPos floor = new BlockPos(tx, ty,     tz);
                BlockPos feet  = new BlockPos(tx, ty + 1, tz);
                BlockPos head  = new BlockPos(tx, ty + 2, tz);

                if (level.getBlockState(floor).isSolid()
                        && (level.getBlockState(feet).isAir() || level.getBlockState(feet).is(BlockTags.REPLACEABLE))
                        && (level.getBlockState(head).isAir() || level.getBlockState(head).is(BlockTags.REPLACEABLE))
                        && !level.getBlockState(feet).is(BlockTags.LEAVES)
                        && !level.getBlockState(head).is(BlockTags.LEAVES)) {
                    return feet;
                }
            }
        }
        return null;
    }
}
