package com.example;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BerserkersFangItem extends Item {

    // Per-player hit counter for the every-3rd-hit ability
    public static final Map<UUID, Integer> HIT_COUNTER = new ConcurrentHashMap<>();

    public BerserkersFangItem(Properties props) {
        super(props);
    }

}
