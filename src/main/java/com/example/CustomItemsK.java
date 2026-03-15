package com.example;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
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

        ResourceKey<EntityType<?>> watcherKey = ResourceKey.create(
                Registries.ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(MOD_ID, "watcher"));
        WatcherEntity.TYPE = Registry.register(
                BuiltInRegistries.ENTITY_TYPE,
                watcherKey,
                EntityType.Builder.<WatcherEntity>of(WatcherEntity::new, MobCategory.MONSTER)
                        .sized(0.6f, 1.8f)
                        .clientTrackingRange(64)
                        .build(watcherKey));
        FabricDefaultAttributeRegistry.register(WatcherEntity.TYPE, WatcherEntity.createAttributes().build());

        // Register Stalker entity
        ResourceKey<EntityType<?>> stalkerKey = ResourceKey.create(
                Registries.ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(MOD_ID, "stalker"));
        StalkerEntity.TYPE = Registry.register(
                BuiltInRegistries.ENTITY_TYPE,
                stalkerKey,
                EntityType.Builder.<StalkerEntity>of(StalkerEntity::new, MobCategory.MONSTER)
                        .sized(0.6f, 1.8f)
                        .clientTrackingRange(32)
                        .build(stalkerKey));
        FabricDefaultAttributeRegistry.register(StalkerEntity.TYPE, StalkerEntity.createAttributes().build());

        // Register Changeling entity
        ResourceKey<EntityType<?>> changelingKey = ResourceKey.create(
                Registries.ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(MOD_ID, "changeling"));
        ChangelingEntity.TYPE = Registry.register(
                BuiltInRegistries.ENTITY_TYPE,
                changelingKey,
                EntityType.Builder.<ChangelingEntity>of(ChangelingEntity::new, MobCategory.MONSTER)
                        .sized(0.9f, 1.4f) // Similar to cow size
                        .clientTrackingRange(32)
                        .build(changelingKey));
        FabricDefaultAttributeRegistry.register(ChangelingEntity.TYPE, ChangelingEntity.createAttributes().build());

        // Register The Hollow entity
        ResourceKey<EntityType<?>> hollowKey = ResourceKey.create(
                Registries.ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(MOD_ID, "hollow"));
        HollowEntity.TYPE = Registry.register(
                BuiltInRegistries.ENTITY_TYPE,
                hollowKey,
                EntityType.Builder.<HollowEntity>of(HollowEntity::new, MobCategory.MONSTER)
                        .sized(0.6f, 1.95f)
                        .clientTrackingRange(32)
                        .build(hollowKey));
        FabricDefaultAttributeRegistry.register(HollowEntity.TYPE, HollowEntity.createAttributes().build());

        // Register the jumpscare packet for server → client delivery
        PayloadTypeRegistry.playS2C().register(WatcherJumpscarePacket.ID, WatcherJumpscarePacket.CODEC);

        ModItems.registerItems();
        registerStormBowEvents();
        registerPoisonIvyEvents();
        registerStarForgedPickaxeEvents();
        registerNaturesGuardianEvents();
        registerForestSpiritEvents();
        registerMimicEvents();
        registerPlayerHeadEvents();
        registerCombatLogEvents();
        registerBerserkersFangEvents();
        registerWatcherEvents();
        registerStalkerEvents();
        registerChangelingEvents();
        registerHollowCommands();
        registerTeleportCommands();

        // /spawnmimic <target> <skin> — operator command to manually spawn a mimic
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(
                Commands.literal("spawnmimic")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("skin", EntityArgument.player())
                            .executes(ctx -> {
                                ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                ServerPlayer skin   = EntityArgument.getPlayer(ctx, "skin");
                                ServerLevel sl = (ServerLevel) target.level();
                                BlockPos spawnPos = findSafeMimicPos(sl, target);
                                if (spawnPos == null) {
                                    ctx.getSource().sendFailure(Component.literal("No safe spawn position found near " + target.getName().getString()));
                                    return 0;
                                }
                                MimicEntity mimic = new MimicEntity(MimicEntity.TYPE, sl);
                                mimic.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                                mimic.setStolenFrom(skin);
                                sl.addFreshEntity(mimic);
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                    "§aSpawned Mimic near §f" + target.getName().getString() +
                                    "§a wearing §f" + skin.getName().getString() + "§a's skin."), true);
                                return 1;
                            })))
            )
        );

        // /spawnwatcher <target> [stage] — operator command to manually spawn The Watcher
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(
                Commands.literal("spawnwatcher")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.argument("target", EntityArgument.player())
                        // Without stage: defaults to stage 1
                        .executes(ctx -> spawnWatcherCommand(ctx, 1))
                        // With stage: /spawnwatcher <target> <stage 1-5>
                        .then(Commands.argument("stage", IntegerArgumentType.integer(1, 5))
                            .executes(ctx -> spawnWatcherCommand(ctx,
                                    IntegerArgumentType.getInteger(ctx, "stage")))))
            )
        );

        // /viewinv <player> — OP command to view and manage a player's inventory
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(
                Commands.literal("viewinv")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof ServerPlayer viewer)) {
                                ctx.getSource().sendFailure(Component.literal("Only players can use this command."));
                                return 0;
                            }
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            viewer.openMenu(new SimpleMenuProvider(
                                (id, viewerInv, p) -> new ViewInventoryMenu(id, viewerInv, target.getInventory()),
                                Component.literal("§9" + target.getName().getString() + "'s Inventory")
                            ));
                            return 1;
                        }))
            )
        );

        // Block single player: kick any player who joins a non-dedicated server
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!server.isDedicatedServer()) {
                handler.disconnect(Component.literal(
                        "§cCustomItemsK requires a multiplayer server. Single player is not supported."));
            }
        });

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

                // ~0.5% chance per 5-second check
                if (sl.getRandom().nextInt(200) != 0) continue;

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

    // --- Combat log ---
    private static final Map<UUID, Long> COMBAT_TIMESTAMPS = new ConcurrentHashMap<>();
    private static final long COMBAT_DURATION_TICKS = 300L; // 15 seconds

    private void registerCombatLogEvents() {
        // Mark combat when a player takes damage or deals damage to something
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            // Only trigger for player-vs-player combat
            if (!(entity instanceof ServerPlayer victim)) return true;
            if (!(source.getEntity() instanceof ServerPlayer attacker)) return true;
            if (!(entity.level() instanceof ServerLevel sl)) return true;
            long now = sl.getServer().getTickCount();
            COMBAT_TIMESTAMPS.put(victim.getUUID(), now);
            COMBAT_TIMESTAMPS.put(attacker.getUUID(), now);
            return true;
        });

        // Kill player if they disconnect while in combat
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer sp = handler.getPlayer();
            Long last = COMBAT_TIMESTAMPS.remove(sp.getUUID());
            if (last == null) return;
            if (server.getTickCount() - last < COMBAT_DURATION_TICKS) {
                sp.kill((ServerLevel) sp.level());
            }
        });

        // Action-bar countdown ticker
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (COMBAT_TIMESTAMPS.isEmpty()) return;
            long now = server.getTickCount();
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                Long last = COMBAT_TIMESTAMPS.get(sp.getUUID());
                if (last == null) continue;
                long elapsed = now - last;
                if (elapsed >= COMBAT_DURATION_TICKS) {
                    COMBAT_TIMESTAMPS.remove(sp.getUUID());
                } else {
                    long secs = (COMBAT_DURATION_TICKS - elapsed + 19) / 20;
                    sp.displayClientMessage(
                        Component.literal("§c⚔ Combat §7| §f" + secs + "s"),
                        true
                    );
                }
            }
        });
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

    // --- Watcher ---
    private static final Map<UUID, Long> WATCHER_COOLDOWNS  = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> WATCHER_ENCOUNTERS = new ConcurrentHashMap<>();
    private static final long WATCHER_COOLDOWN_TICKS = 12000L; // 10 minutes between spawns per player

    /**
     * The Watcher — spawns at night in forest biomes when the player is alone.
     * Prefers elevated positions with line-of-sight to the player.
     * ~1% chance per 5-second server tick check.
     */
    private void registerWatcherEvents() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 100 != 0) return; // every 5 seconds

            for (ServerPlayer target : server.getPlayerList().getPlayers()) {
                if (!(target.level() instanceof ServerLevel sl)) continue;

                // Nighttime only
                long dayTime = sl.getDayTime() % 24000;
                if (dayTime < 13000 || dayTime > 23500) continue;

                // Forest/jungle/taiga biome only
                var biome = sl.getBiome(target.blockPosition());
                if (!biome.is(BiomeTags.IS_FOREST)
                        && !biome.is(BiomeTags.IS_JUNGLE)
                        && !biome.is(BiomeTags.IS_TAIGA)) continue;

                // Player must be alone — no other players within 60 blocks
                boolean isAlone = server.getPlayerList().getPlayers().stream()
                        .filter(p -> p != target)
                        .allMatch(p -> p.distanceToSqr(target) > 60.0 * 60.0);
                if (!isAlone) continue;

                // Per-player cooldown
                long now = sl.getGameTime();
                Long lastSpawn = WATCHER_COOLDOWNS.get(target.getUUID());
                if (lastSpawn != null && now - lastSpawn < WATCHER_COOLDOWN_TICKS) continue;

                // No Watcher already nearby
                if (!sl.getEntitiesOfClass(WatcherEntity.class,
                        target.getBoundingBox().inflate(80.0)).isEmpty()) continue;

                // ~1% chance per check
                if (sl.getRandom().nextInt(100) != 0) continue;

                BlockPos spawnPos = findWatcherPos(sl, target);
                if (spawnPos == null) continue;

                // Track encounters and set stage
                int encounters = WATCHER_ENCOUNTERS.getOrDefault(target.getUUID(), 0);
                WATCHER_ENCOUNTERS.put(target.getUUID(), encounters + 1);
                int autoStage = Math.min(encounters + 1, 5);

                WatcherEntity watcher = new WatcherEntity(WatcherEntity.TYPE, sl);
                watcher.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                watcher.stage = autoStage;
                watcher.targetPlayerUUID = target.getUUID();
                sl.addFreshEntity(watcher);
                WATCHER_COOLDOWNS.put(target.getUUID(), now);
            }
        });
    }

    /** Shared logic for /spawnwatcher — spawns a watcher at the given stage near the target. */
    private static int spawnWatcherCommand(CommandContext<CommandSourceStack> ctx, int stage)
            throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        ServerLevel sl = (ServerLevel) target.level();
        BlockPos spawnPos = findWatcherPos(sl, target);
        if (spawnPos == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "No safe spawn position found near " + target.getName().getString()));
            return 0;
        }
        WatcherEntity watcher = new WatcherEntity(WatcherEntity.TYPE, sl);
        watcher.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
        watcher.stage = stage;
        watcher.targetPlayerUUID = target.getUUID();
        sl.addFreshEntity(watcher);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§8Spawned The Watcher §7(stage " + stage + ")§8 near §f"
                        + target.getName().getString()), true);
        return 1;
    }

    /**
     * Finds a spawn position for The Watcher:
     *
     * Pass 1 — In the player's field of view (±65°), elevated, in the OPEN (no trees
     *           directly adjacent), with clear line of sight to the player.
     * Pass 2 — Relax to ±80° and allow nearby tree canopy, still line of sight.
     * Pass 3 — Any valid elevated position (fallback, no direction constraint).
     */
    private static BlockPos findWatcherPos(ServerLevel level, ServerPlayer player) {
        int playerY = (int) player.getY();

        // Horizontal look direction (normalised)
        Vec3 look = player.getLookAngle();
        double hLen = Math.sqrt(look.x * look.x + look.z * look.z);
        double lookX = hLen > 0.001 ? look.x / hLen : 0;
        double lookZ = hLen > 0.001 ? look.z / hLen : 1;

        // --- Pass 1: within ±65° of player's gaze, open area, line of sight ---
        for (int attempt = 0; attempt < 35; attempt++) {
            double angleOffset = (level.getRandom().nextDouble() - 0.5) * Math.toRadians(130);
            double[] dir = rotateHorizontal(lookX, lookZ, angleOffset);
            double dist  = 18 + level.getRandom().nextDouble() * 12;
            int tx = (int) Math.floor(player.getX() + dir[0] * dist);
            int tz = (int) Math.floor(player.getZ() + dir[1] * dist);

            for (int ty = playerY + 8; ty >= playerY - 3; ty--) {
                BlockPos feet = new BlockPos(tx, ty + 1, tz);
                if (!isClearStand(level, tx, ty, tz)) continue;
                if (isInsideOrAdjacentToTree(level, feet)) continue;
                if (hasLineOfSight(level, player.getEyePosition(),
                        new Vec3(tx + 0.5, ty + 1.62, tz + 0.5))) {
                    return feet;
                }
            }
        }

        // --- Pass 2: ±80°, trees nearby allowed, still line of sight ---
        for (int attempt = 0; attempt < 25; attempt++) {
            double angleOffset = (level.getRandom().nextDouble() - 0.5) * Math.toRadians(160);
            double[] dir = rotateHorizontal(lookX, lookZ, angleOffset);
            double dist  = 18 + level.getRandom().nextDouble() * 12;
            int tx = (int) Math.floor(player.getX() + dir[0] * dist);
            int tz = (int) Math.floor(player.getZ() + dir[1] * dist);

            for (int ty = playerY + 8; ty >= playerY - 5; ty--) {
                BlockPos feet = new BlockPos(tx, ty + 1, tz);
                if (!isClearStand(level, tx, ty, tz)) continue;
                if (hasLineOfSight(level, player.getEyePosition(),
                        new Vec3(tx + 0.5, ty + 1.62, tz + 0.5))) {
                    return feet;
                }
            }
        }

        // --- Pass 3: any valid elevated position (no directional constraint) ---
        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            double dist  = 18 + level.getRandom().nextDouble() * 12;
            int tx = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
            int tz = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);

            for (int ty = playerY + 8; ty >= playerY - 5; ty--) {
                if (!isClearStand(level, tx, ty, tz)) continue;
                if (ty + 1 >= playerY) return new BlockPos(tx, ty + 1, tz);
            }
        }
        return null;
    }

    /** Rotates the 2D horizontal vector (x, z) by the given angle (radians). */
    private static double[] rotateHorizontal(double x, double z, double angle) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        return new double[]{ x * cos - z * sin, x * sin + z * cos };
    }

    /** Returns true if the position at (tx, ty, tz) has a solid floor and 2 clear blocks above. */
    private static boolean isClearStand(ServerLevel level, int tx, int ty, int tz) {
        BlockPos floor = new BlockPos(tx, ty,     tz);
        BlockPos feet  = new BlockPos(tx, ty + 1, tz);
        BlockPos head  = new BlockPos(tx, ty + 2, tz);
        if (!level.getBlockState(floor).isSolid()) return false;
        if (!level.getBlockState(feet).isAir() && !level.getBlockState(feet).is(BlockTags.REPLACEABLE)) return false;
        if (!level.getBlockState(head).isAir() && !level.getBlockState(head).is(BlockTags.REPLACEABLE)) return false;
        if (level.getBlockState(feet).is(BlockTags.LEAVES)) return false;
        if (level.getBlockState(head).is(BlockTags.LEAVES)) return false;
        return true;
    }

    /**
     * Returns true if the position is directly adjacent to (or inside) tree foliage/logs.
     * Prevents the Watcher from spawning hidden behind or inside a tree.
     */
    private static boolean isInsideOrAdjacentToTree(ServerLevel level, BlockPos feet) {
        for (BlockPos adj : new BlockPos[]{
                feet, feet.above(),
                feet.north(), feet.south(), feet.east(), feet.west(),
                feet.north().east(), feet.north().west(),
                feet.south().east(), feet.south().west()}) {
            if (level.getBlockState(adj).is(BlockTags.LOGS)
             || level.getBlockState(adj).is(BlockTags.LEAVES)) return true;
        }
        return false;
    }

    /** True if no solid block obstructs the straight line from {@code from} to {@code to}. */
    private static boolean hasLineOfSight(ServerLevel level, Vec3 from, Vec3 to) {
        ClipContext ctx = new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty());
        return level.clip(ctx).getType() == HitResult.Type.MISS;
    }

    /**
     * Berserker's Fang — Blood Rush:
     * When a player kills a mob while holding the fang, grant Speed I + Strength I for 4 seconds.
     */
    private void registerBerserkersFangEvents() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, killed) -> {
            if (!killed) return;
            if (!(source.getEntity() instanceof Player player)) return;
            ItemStack held = player.getMainHandItem();
            if (!(held.getItem() instanceof BerserkersFangItem)) return;
            if (!(player.level() instanceof ServerLevel sl)) return;

            // Blood Rush: Speed I + Strength I for 4 seconds (80 ticks)
            player.addEffect(new MobEffectInstance(MobEffects.SPEED,    80, 0, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 80, 0, false, true));

            player.displayClientMessage(Component.literal("§c🩸 Blood Rush!"), true);

            // Red burst particles at kill location
            sl.sendParticles(ParticleTypes.CRIT,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    24, 0.6, 0.8, 0.6, 0.6);
            sl.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 0.6f);
        });
    }

    // --- Stalker ---
    private static final Map<UUID, Long> STALKER_COOLDOWNS = new ConcurrentHashMap<>();
    private static final long STALKER_COOLDOWN_TICKS = 8000L; // ~6.5 minutes between spawns per player

    /**
     * The Stalker — spawns in dark caves and at night in dark areas.
     * Hunts players from shadows, invisible until it attacks.
     * ~0.5% chance per 5-second server tick check in valid conditions.
     */
    private void registerStalkerEvents() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 100 != 0) return; // every 5 seconds

            for (ServerPlayer target : server.getPlayerList().getPlayers()) {
                if (!(target.level() instanceof ServerLevel sl)) continue;

                // Check if player is in darkness (light level 0-6)
                int lightLevel = sl.getMaxLocalRawBrightness(target.blockPosition());
                if (lightLevel > 6) continue;

                // Must be relatively alone - no other players within 30 blocks
                boolean isAlone = server.getPlayerList().getPlayers().stream()
                        .filter(p -> p != target)
                        .allMatch(p -> p.distanceToSqr(target) > 30.0 * 30.0);
                if (!isAlone) continue;

                // Per-player cooldown
                long now = sl.getGameTime();
                Long lastSpawn = STALKER_COOLDOWNS.get(target.getUUID());
                if (lastSpawn != null && now - lastSpawn < STALKER_COOLDOWN_TICKS) continue;

                // No Stalker already nearby
                if (!sl.getEntitiesOfClass(StalkerEntity.class,
                        target.getBoundingBox().inflate(50.0)).isEmpty()) continue;

                // ~0.5% chance per check
                if (sl.getRandom().nextInt(200) != 0) continue;

                BlockPos spawnPos = findStalkerPos(sl, target);
                if (spawnPos == null) continue;

                StalkerEntity stalker = new StalkerEntity(StalkerEntity.TYPE, sl);
                stalker.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                stalker.setInvisible(true); // Start invisible
                sl.addFreshEntity(stalker);
                STALKER_COOLDOWNS.put(target.getUUID(), now);
                
                // Subtle hint to the player
                target.displayClientMessage(Component.literal("§8§o...you feel a cold presence..."), true);
            }
        });
        
        // /spawnstalker <target> — operator command to manually spawn a stalker
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(
                Commands.literal("spawnstalker")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                            ServerLevel sl = (ServerLevel) target.level();
                            BlockPos spawnPos = findStalkerPos(sl, target);
                            if (spawnPos == null) {
                                ctx.getSource().sendFailure(Component.literal(
                                        "No safe spawn position found near " + target.getName().getString()));
                                return 0;
                            }
                            StalkerEntity stalker = new StalkerEntity(StalkerEntity.TYPE, sl);
                            stalker.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                            stalker.setInvisible(true);
                            sl.addFreshEntity(stalker);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "§0Spawned §8The Stalker §0near §7" + target.getName().getString()), true);
                            return 1;
                        }))
            )
        );
    }

    /** Finds a dark spawn position for The Stalker near the player. */
    private static BlockPos findStalkerPos(ServerLevel level, ServerPlayer player) {
        for (int attempt = 0; attempt < 30; attempt++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            double dist = 8 + level.getRandom().nextDouble() * 12; // 8-20 blocks away
            int tx = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
            int tz = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);

            // Scan vertically for a valid dark spot
            for (int ty = (int) player.getY() + 8; ty >= (int) player.getY() - 8; ty--) {
                BlockPos floor = new BlockPos(tx, ty, tz);
                BlockPos feet = new BlockPos(tx, ty + 1, tz);
                BlockPos head = new BlockPos(tx, ty + 2, tz);

                if (!level.getBlockState(floor).isSolid()) continue;
                if (!level.getBlockState(feet).isAir() && !level.getBlockState(feet).is(BlockTags.REPLACEABLE)) continue;
                if (!level.getBlockState(head).isAir() && !level.getBlockState(head).is(BlockTags.REPLACEABLE)) continue;
                if (level.getBlockState(feet).is(BlockTags.LEAVES)) continue;
                if (level.getBlockState(head).is(BlockTags.LEAVES)) continue;
                
                // Check light level - must be dark
                int light = level.getMaxLocalRawBrightness(feet);
                if (light <= 6) {
                    return feet;
                }
            }
        }
        return null;
    }

    // =========================================================================
    // TELEPORT COMMANDS - /sethome, /home, /tpa, /tpaccept, /tpdeny
    // =========================================================================

    // Home storage: Player UUID -> BlockPos
    private static final Map<UUID, BlockPos> PLAYER_HOMES = new ConcurrentHashMap<>();
    private static final Map<UUID, String> PLAYER_HOME_DIMENSIONS = new ConcurrentHashMap<>();

    // TPA requests: Target UUID -> Requester UUID
    private static final Map<UUID, UUID> TPA_REQUESTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> TPA_REQUEST_TIMES = new ConcurrentHashMap<>();
    private static final long TPA_TIMEOUT_TICKS = 1200; // 60 seconds

    private void registerTeleportCommands() {
        // /sethome - Set your home location
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(
                Commands.literal("sethome")
                    .executes(ctx -> {
                        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
                            ctx.getSource().sendFailure(Component.literal("Only players can use this command!"));
                            return 0;
                        }
                        
                        ServerLevel level = (ServerLevel) player.level();
                        BlockPos pos = player.blockPosition();
                        String dimension = level.dimension().toString();
                        
                        PLAYER_HOMES.put(player.getUUID(), pos);
                        PLAYER_HOME_DIMENSIONS.put(player.getUUID(), dimension);
                        
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                "§a✦ Home set at §f[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "] §7in §f" + dimension),
                                false);
                        
                        // Particle effect
                        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                                player.getX(), player.getY() + 1, player.getZ(),
                                20, 0.5, 0.5, 0.5, 0.1);
                        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 1.0f);
                        
                        return 1;
                    })
            )
        );

        // /home - Teleport to your home
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(
                Commands.literal("home")
                    .executes(ctx -> {
                        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
                            ctx.getSource().sendFailure(Component.literal("Only players can use this command!"));
                            return 0;
                        }
                        
                        UUID uuid = player.getUUID();
                        BlockPos homePos = PLAYER_HOMES.get(uuid);
                        String homeDim = PLAYER_HOME_DIMENSIONS.get(uuid);
                        
                        if (homePos == null) {
                            ctx.getSource().sendFailure(Component.literal("§cYou haven't set a home yet! Use /sethome first."));
                            return 0;
                        }
                        
                        ServerLevel currentLevel = (ServerLevel) player.level();
                        String currentDim = currentLevel.dimension().toString();
                        
                        // Check if player is in the same dimension
                        if (!currentDim.equals(homeDim)) {
                            ctx.getSource().sendFailure(Component.literal("§cYour home is in a different dimension (" + homeDim + ")!"));
                            return 0;
                        }
                        
                        // Teleport with effects
                        teleportPlayer(player, currentLevel, homePos, "§a✦ Welcome home!");
                        
                        return 1;
                    })
            )
        );

        // /tpa <player> - Request to teleport to another player
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(
                Commands.literal("tpa")
                    .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof ServerPlayer requester)) {
                                ctx.getSource().sendFailure(Component.literal("Only players can use this command!"));
                                return 0;
                            }
                            
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                            
                            if (requester == target) {
                                ctx.getSource().sendFailure(Component.literal("§cYou can't teleport to yourself!"));
                                return 0;
                            }
                            
                            // Check if there's already a pending request to this player
                            if (TPA_REQUESTS.containsKey(target.getUUID()) && 
                                TPA_REQUESTS.get(target.getUUID()).equals(requester.getUUID())) {
                                ctx.getSource().sendFailure(Component.literal("§cYou already have a pending request to this player!"));
                                return 0;
                            }
                            
                            // Store the request
                            TPA_REQUESTS.put(target.getUUID(), requester.getUUID());
                            TPA_REQUEST_TIMES.put(target.getUUID(), System.currentTimeMillis());
                            
                            // Notify both players
                            requester.displayClientMessage(
                                    Component.literal("§eTeleport request sent to §f" + target.getName().getString()), false);
                            
                            target.displayClientMessage(
                                    Component.literal("\n§e§lTPA Request\n§f" + requester.getName().getString() + " §ewants to teleport to you.\n" +
                                            "§a/tpaccept §7to accept | §c/tpdeny §7to deny\n"), false);
                            
                            // Play sound to target
                            if (target.level() instanceof ServerLevel sl) {
                                sl.playSound(null, target.getX(), target.getY(), target.getZ(),
                                        SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
                            }
                            
                            return 1;
                        }))
            )
        );

        // /tpaccept - Accept a teleport request
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(
                Commands.literal("tpaccept")
                    .executes(ctx -> {
                        if (!(ctx.getSource().getEntity() instanceof ServerPlayer target)) {
                            ctx.getSource().sendFailure(Component.literal("Only players can use this command!"));
                            return 0;
                        }
                        
                        UUID targetUUID = target.getUUID();
                        UUID requesterUUID = TPA_REQUESTS.get(targetUUID);
                        
                        if (requesterUUID == null) {
                            ctx.getSource().sendFailure(Component.literal("§cYou don't have any pending teleport requests!"));
                            return 0;
                        }
                        
                        // Check if request expired
                        Long requestTime = TPA_REQUEST_TIMES.get(targetUUID);
                        if (requestTime != null && System.currentTimeMillis() - requestTime > 60000) {
                            TPA_REQUESTS.remove(targetUUID);
                            TPA_REQUEST_TIMES.remove(targetUUID);
                            ctx.getSource().sendFailure(Component.literal("§cThis teleport request has expired!"));
                            return 0;
                        }
                        
                        ServerPlayer requester = ctx.getSource().getServer().getPlayerList().getPlayer(requesterUUID);
                        
                        if (requester == null) {
                            ctx.getSource().sendFailure(Component.literal("§cThe player who requested teleport is no longer online!"));
                            TPA_REQUESTS.remove(targetUUID);
                            TPA_REQUEST_TIMES.remove(targetUUID);
                            return 0;
                        }
                        
                        // Check if same dimension
                        if (!requester.level().equals(target.level())) {
                            target.displayClientMessage(Component.literal("§cYou can't accept: " + requester.getName().getString() + " is in a different dimension!"), false);
                            TPA_REQUESTS.remove(targetUUID);
                            TPA_REQUEST_TIMES.remove(targetUUID);
                            return 0;
                        }
                        
                        // Teleport the requester to target
                        ServerLevel level = (ServerLevel) target.level();
                        BlockPos targetPos = target.blockPosition();
                        
                        // Clear the request
                        TPA_REQUESTS.remove(targetUUID);
                        TPA_REQUEST_TIMES.remove(targetUUID);
                        
                        // Teleport with effects
                        teleportPlayer(requester, level, targetPos, "§a✦ Teleport accepted!");
                        
                        // Notify target
                        target.displayClientMessage(
                                Component.literal("§a✦ You accepted §f" + requester.getName().getString() + "§a's teleport request."), false);
                        
                        return 1;
                    })
            )
        );

        // /tpdeny - Deny a teleport request
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(
                Commands.literal("tpdeny")
                    .executes(ctx -> {
                        if (!(ctx.getSource().getEntity() instanceof ServerPlayer target)) {
                            ctx.getSource().sendFailure(Component.literal("Only players can use this command!"));
                            return 0;
                        }
                        
                        UUID targetUUID = target.getUUID();
                        UUID requesterUUID = TPA_REQUESTS.get(targetUUID);
                        
                        if (requesterUUID == null) {
                            ctx.getSource().sendFailure(Component.literal("§cYou don't have any pending teleport requests!"));
                            return 0;
                        }
                        
                        ServerPlayer requester = ctx.getSource().getServer().getPlayerList().getPlayer(requesterUUID);
                        
                        // Clear the request
                        TPA_REQUESTS.remove(targetUUID);
                        TPA_REQUEST_TIMES.remove(targetUUID);
                        
                        target.displayClientMessage(Component.literal("§c✗ You denied the teleport request."), false);
                        
                        if (requester != null) {
                            requester.displayClientMessage(
                                    Component.literal("§c✗ §f" + target.getName().getString() + " §cdenied your teleport request."), false);
                        }
                        
                        return 1;
                    })
            )
        );
    }

    /**
     * Teleports a player to a position with visual and sound effects
     */
    private void teleportPlayer(ServerPlayer player, ServerLevel level, BlockPos pos, String message) {
        // Particles at old location
        level.sendParticles(ParticleTypes.PORTAL,
                player.getX(), player.getY() + 1, player.getZ(),
                50, 0.5, 1.0, 0.5, 0.1);
        
        // Play sound at old location
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
        
        // Teleport
        player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        
        // Particles at new location
        level.sendParticles(ParticleTypes.PORTAL,
                player.getX(), player.getY() + 1, player.getZ(),
                50, 0.5, 1.0, 0.5, 0.1);
        
        // Play sound at new location
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
        
        // Send message
        player.displayClientMessage(Component.literal(message), false);
    }

    // =========================================================================
    // CHANGELING - Shapeshifting mimic
    // =========================================================================
    private static final Map<UUID, Long> CHANGELING_COOLDOWNS = new ConcurrentHashMap<>();
    private static final long CHANGELING_COOLDOWN_TICKS = 6000L; // 5 minutes

    private void registerChangelingEvents() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 200 != 0) return; // Check every 10 seconds

            for (ServerPlayer target : server.getPlayerList().getPlayers()) {
                ServerLevel sl = (ServerLevel) target.level();

                // Only spawn in overworld during day
                if (!sl.dimension().equals(ServerLevel.OVERWORLD)) continue;
                long dayTime = sl.getDayTime() % 24000;
                if (dayTime < 1000 || dayTime > 11000) continue; // Day time only

                // Check biomes - villages, plains, forests
                var biome = sl.getBiome(target.blockPosition());
                boolean validBiome = biome.is(BiomeTags.IS_OVERWORLD) && 
                        (sl.getBlockState(target.blockPosition().below()).is(Blocks.GRASS_BLOCK) ||
                         sl.getBlockState(target.blockPosition().below()).is(Blocks.DIRT));
                if (!validBiome) continue;

                // Per-player cooldown
                long now = sl.getGameTime();
                Long lastSpawn = CHANGELING_COOLDOWNS.get(target.getUUID());
                if (lastSpawn != null && now - lastSpawn < CHANGELING_COOLDOWN_TICKS) continue;

                // No Changeling already nearby
                if (!sl.getEntitiesOfClass(ChangelingEntity.class,
                        target.getBoundingBox().inflate(50.0)).isEmpty()) continue;

                // ~0.5% chance per check
                if (sl.getRandom().nextInt(200) != 0) continue;

                BlockPos spawnPos = findChangelingPos(sl, target);
                if (spawnPos == null) continue;

                ChangelingEntity changeling = new ChangelingEntity(ChangelingEntity.TYPE, sl);
                changeling.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                sl.addFreshEntity(changeling);
                CHANGELING_COOLDOWNS.put(target.getUUID(), now);
            }
        });
        
        // /spawnchangeling <target> [disguise] — operator command
        SuggestionProvider<CommandSourceStack> disguiseSuggestions = (ctx, builder) -> {
            for (ChangelingEntity.DisguiseType t : ChangelingEntity.DisguiseType.values()) {
                builder.suggest(t.name.toLowerCase());
            }
            return builder.buildFuture();
        };

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(
                Commands.literal("spawnchangeling")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.argument("target", EntityArgument.player())
                        // /spawnchangeling <target>  → random disguise
                        .executes(ctx -> spawnChangeling(ctx, null))
                        // /spawnchangeling <target> <disguise>  → specific disguise
                        .then(Commands.argument("disguise", StringArgumentType.word())
                            .suggests(disguiseSuggestions)
                            .executes(ctx -> spawnChangeling(ctx,
                                    StringArgumentType.getString(ctx, "disguise")))))
            )
        );
    }

    private static int spawnChangeling(CommandContext<CommandSourceStack> ctx, String disguiseName)
            throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        ServerLevel sl = (ServerLevel) target.level();
        BlockPos spawnPos = findChangelingPos(sl, target);
        if (spawnPos == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "No safe spawn position found near " + target.getName().getString()));
            return 0;
        }
        ChangelingEntity changeling = new ChangelingEntity(ChangelingEntity.TYPE, sl);
        changeling.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

        if (disguiseName != null && !disguiseName.isEmpty()) {
            ChangelingEntity.DisguiseType type = ChangelingEntity.disguiseByName(disguiseName);
            if (type == null) {
                ctx.getSource().sendFailure(Component.literal(
                        "Unknown disguise '" + disguiseName + "'. Valid: cow, pig, sheep, villager, chicken, rabbit, horse, cat, fox, mooshroom"));
                return 0;
            }
            changeling.setDisguise(type);
            sl.addFreshEntity(changeling);
            final String typeName = type.name;
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§5Spawned a §dChangeling §5disguised as a §f" + typeName + " §5near §f" + target.getName().getString()), true);
        } else {
            sl.addFreshEntity(changeling);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§5Spawned a §dChangeling §5near §f" + target.getName().getString()), true);
        }
        return 1;
    }

    // ──────────────────────── The Hollow commands ─────────────────────────────

    private void registerHollowCommands() {
        // /spawnhollow <target> — spawns The Hollow near the target player
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(
                Commands.literal("spawnhollow")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                            ServerLevel sl = (ServerLevel) target.level();
                            BlockPos pos = findDarkSpawnPos(sl, target);
                            if (pos == null) {
                                ctx.getSource().sendFailure(
                                        Component.literal("No dark spawn position found near "
                                                + target.getName().getString()));
                                return 0;
                            }
                            HollowEntity hollow = new HollowEntity(HollowEntity.TYPE, sl);
                            hollow.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                            sl.addFreshEntity(hollow);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "§8Spawned §7The Hollow §8near §f"
                                            + target.getName().getString()), true);
                            return 1;
                        }))
            )
        );
    }

    /** Finds a dark (light ≤ 4), two-block-tall air gap within 20–35 blocks of the player. */
    private static BlockPos findDarkSpawnPos(ServerLevel level, ServerPlayer player) {
        for (int attempt = 0; attempt < 40; attempt++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            double dist  = 20 + level.getRandom().nextDouble() * 15;
            int tx = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
            int tz = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
            for (int ty = (int) player.getY() + 4; ty >= (int) player.getY() - 6; ty--) {
                BlockPos floor = new BlockPos(tx, ty,     tz);
                BlockPos feet  = new BlockPos(tx, ty + 1, tz);
                BlockPos head  = new BlockPos(tx, ty + 2, tz);
                if (!level.getBlockState(floor).isSolid()) continue;
                if (!level.getBlockState(feet).isAir())   continue;
                if (!level.getBlockState(head).isAir())   continue;
                if (level.getMaxLocalRawBrightness(feet) <= 4) return feet;
            }
        }
        return null;
    }

    private static BlockPos findChangelingPos(ServerLevel level, ServerPlayer player) {
        for (int attempt = 0; attempt < 30; attempt++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            double dist = 15 + level.getRandom().nextDouble() * 20; // 15-35 blocks away
            int tx = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
            int tz = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);

            for (int ty = (int) player.getY() + 5; ty >= (int) player.getY() - 5; ty--) {
                BlockPos floor = new BlockPos(tx, ty, tz);
                BlockPos feet = new BlockPos(tx, ty + 1, tz);
                BlockPos head = new BlockPos(tx, ty + 2, tz);

                if (!level.getBlockState(floor).isSolid()) continue;
                if (!level.getBlockState(feet).isAir() && !level.getBlockState(feet).is(BlockTags.REPLACEABLE)) continue;
                if (!level.getBlockState(head).isAir() && !level.getBlockState(head).is(BlockTags.REPLACEABLE)) continue;
                if (level.getBlockState(feet).is(BlockTags.LEAVES)) continue;
                if (level.getBlockState(head).is(BlockTags.LEAVES)) continue;
                
                // Good lighting for passive mob disguise
                int light = level.getMaxLocalRawBrightness(feet);
                if (light >= 8) {
                    return feet;
                }
            }
        }
        return null;
    }
}
