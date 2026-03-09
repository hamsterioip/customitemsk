package com.example;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

public class StormArrowEntity extends AbstractArrow {

    public static EntityType<StormArrowEntity> TYPE;

    public StormArrowEntity(EntityType<? extends StormArrowEntity> type, Level level) {
        super(type, level);
    }

    public StormArrowEntity(LivingEntity shooter, Level level, ItemStack ammo, ItemStack weapon) {
        super(TYPE, shooter, level, ammo, weapon);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ModItems.STORM_ARROW);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (level() instanceof ServerLevel sl && result.getEntity() instanceof LivingEntity hit) {
            LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, sl);
            bolt.setPos(hit.getX(), hit.getY(), hit.getZ());
            sl.addFreshEntity(bolt);
            sl.getEntitiesOfClass(LivingEntity.class,
                    hit.getBoundingBox().inflate(3.0),
                    e -> e != hit && e != getOwner())
                .forEach(nearby -> nearby.hurt(sl.damageSources().lightningBolt(), 4.0f));
        }
    }
}
