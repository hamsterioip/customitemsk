package com.example;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.EquipmentAssets;

import java.util.List;
import java.util.function.Function;

public class ModItems {

    public static final Item STORM_BOW = register("storm_bow",
            props -> new StormBowItem(props.durability(500)));
    public static final Item BLADES_OF_SATAN = register("blades_of_satan",
            props -> new BladesOfSatanItem(props.sword(ToolMaterial.NETHERITE, 8f, -2.4f)));
    public static final Item EXECUTIONERS_SWORD = register("executioners_sword",
            props -> new ExecutionersSwordItem(props.sword(ToolMaterial.IRON, 6f, -2.8f)));
    public static final Item SWORD_OF_LIGHT = register("sword_of_light",
            props -> new SwordOfLightItem(props.sword(ToolMaterial.DIAMOND, 6f, -2.4f)));
    public static final Item STORM_ARROW = register("storm_arrow",
            props -> new StormArrowItem(props.stacksTo(64)));
    public static final Item GLACIER_LANCE = register("glacier_lance",
            props -> new GlacierLanceItem(props.sword(ToolMaterial.DIAMOND, 5f, -2.4f)));
    public static final Item STARFORGED_PICKAXE = register("starforged_pickaxe",
            props -> new StarForgedPickaxeItem(props.pickaxe(ToolMaterial.NETHERITE, 1f, -2.8f)));
    public static final Item CATACLYSM = register("cataclysm",
            props -> new CataclysmItem(props.sword(ToolMaterial.NETHERITE, 9f, -3.0f)));
    public static final Item NATURES_HEART = register("natures_heart",
            props -> new NaturesHeartItem(props.stacksTo(16)
                    .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            Component.literal("§7The soul of an ancient spirit"),
                            Component.literal("§7sealed within by the forest itself."),
                            Component.literal("§8— Only the worthy may claim it")
                    )))));
    public static final Item NATURES_GUARDIAN = register("natures_guardian",
            props -> new NaturesGuardianItem(props.stacksTo(1)));
    public static final Item TEMPEST_REAVER = register("tempest_reaver",
            props -> new TempestReaverItem(props.sword(ToolMaterial.NETHERITE, 10f, -3.0f)));
    public static final Item POISON_IVY = register("poison_ivy",
            props -> new PoisonIvyItem(props.durability(326)));
    public static final Item BERSERKERS_FANG = register("berserkers_fang",
            props -> new BerserkersFangItem(props
                    .stacksTo(1)
                    .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
                    .attributes(ItemAttributeModifiers.builder()
                            .add(Attributes.ATTACK_DAMAGE,
                                    new AttributeModifier(
                                            Identifier.fromNamespaceAndPath("customitemsk", "damage.berserkers_fang"),
                                            0.10, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                                    EquipmentSlotGroup.MAINHAND)
                            .add(Attributes.ATTACK_SPEED,
                                    new AttributeModifier(
                                            Identifier.fromNamespaceAndPath("customitemsk", "speed.berserkers_fang"),
                                            0.05, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                                    EquipmentSlotGroup.MAINHAND)
                            .build())));
    public static final Item SENTINELS_CHARM = register("sentinels_charm",
            props -> new SentinelsCharmItem(props
                    .stacksTo(1)
                    .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
                    .attributes(ItemAttributeModifiers.builder()
                            .add(Attributes.ATTACK_DAMAGE,
                                    new AttributeModifier(
                                            Identifier.fromNamespaceAndPath("customitemsk", "damage.sentinels_charm"),
                                            0.05, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                                    EquipmentSlotGroup.MAINHAND)
                            .add(Attributes.ARMOR,
                                    new AttributeModifier(
                                            Identifier.fromNamespaceAndPath("customitemsk", "armor.sentinels_charm"),
                                            1.0, AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.MAINHAND)
                            .build())));
    public static final Item PHOENIX_EMBER = register("phoenix_ember",
            props -> new PhoenixEmberItem(props
                    .stacksTo(1)
                    .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
                    .fireResistant()));
    public static final Item TEMPEST_RELIC = register("tempest_relic",
            props -> new TempestCrownItem(props
                    .stacksTo(1)
                    .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
                    .fireResistant()));
    public static final Item KINGS_CROWN = register("kings_crown",
            props -> new KingsCrownItem(props
                    .component(DataComponents.EQUIPPABLE,
                            Equippable.builder(EquipmentSlot.HEAD)
                                    .setAsset(EquipmentAssets.GOLD)
                                    .build())
                    .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
                    .durability(407)
                    .attributes(ItemAttributeModifiers.builder()
                            .add(Attributes.ARMOR,
                                    new AttributeModifier(
                                            Identifier.fromNamespaceAndPath("customitemsk", "armor.kings_crown"),
                                            3.0, AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.HEAD)
                            .add(Attributes.ARMOR_TOUGHNESS,
                                    new AttributeModifier(
                                            Identifier.fromNamespaceAndPath("customitemsk", "toughness.kings_crown"),
                                            3.0, AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.HEAD)
                            .add(Attributes.KNOCKBACK_RESISTANCE,
                                    new AttributeModifier(
                                            Identifier.fromNamespaceAndPath("customitemsk", "knockback.kings_crown"),
                                            0.1, AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.HEAD)
                            .add(Attributes.MAX_HEALTH,
                                    new AttributeModifier(
                                            Identifier.fromNamespaceAndPath("customitemsk", "health.kings_crown"),
                                            4.0, AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.HEAD)
                            .build())));

    private static Item register(String name, Function<Item.Properties, Item> factory) {
        ResourceKey<Item> key = ResourceKey.create(
                Registries.ITEM,
                Identifier.fromNamespaceAndPath(CustomItemsK.MOD_ID, name));
        Item item = factory.apply(new Item.Properties().setId(key));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    public static void registerItems() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(entries -> {
            entries.accept(STORM_BOW);
            entries.accept(STORM_ARROW);
            entries.accept(BLADES_OF_SATAN);
            entries.accept(EXECUTIONERS_SWORD);
            entries.accept(SWORD_OF_LIGHT);
            entries.accept(GLACIER_LANCE);
            entries.accept(CATACLYSM);
            entries.accept(TEMPEST_REAVER);
            entries.accept(POISON_IVY);
        });
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(entries2 -> {
            entries2.accept(STARFORGED_PICKAXE);
            entries2.accept(KINGS_CROWN);
            entries2.accept(NATURES_GUARDIAN);
            entries2.accept(SENTINELS_CHARM);
            entries2.accept(BERSERKERS_FANG);
            entries2.accept(PHOENIX_EMBER);
            entries2.accept(TEMPEST_RELIC);
        });
    }
}
