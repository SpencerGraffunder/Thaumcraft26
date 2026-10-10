package thaumcraft.init;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import thaumcraft.Thaumcraft;
import thaumcraft.common.items.armor.ItemGoggles;
import thaumcraft.common.items.armor.ItemRobeArmor;
import thaumcraft.common.items.armor.ItemThaumiumArmor;
import thaumcraft.common.items.armor.ItemVoidArmor;
import thaumcraft.common.items.armor.ItemBootsTraveller;
import thaumcraft.common.items.armor.ItemFortressArmor;
import thaumcraft.common.items.armor.ItemVoidRobeArmor;
import thaumcraft.common.items.armor.ItemCultistRobeArmor;
import thaumcraft.common.items.tools.ItemNodeJar;
import thaumcraft.common.items.armor.ItemCultistPlateArmor;
import thaumcraft.common.items.armor.ItemCultistBoots;
import thaumcraft.common.items.armor.ItemCultistLeaderArmor;
import thaumcraft.common.items.consumables.ItemPhial;
import thaumcraft.common.items.curios.ItemThaumonomicon;
import thaumcraft.common.items.resources.ItemCrystalEssence;
import thaumcraft.common.items.resources.ItemMaterial;
import thaumcraft.common.items.resources.ItemVisCrystal;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.items.resources.ItemMagicDust;
import thaumcraft.common.items.tools.ItemScribingTools;
import thaumcraft.common.items.tools.ItemThaumometer;
import thaumcraft.common.items.tools.ItemThaumiumSword;
import thaumcraft.common.items.tools.ItemThaumiumPickaxe;
import thaumcraft.common.items.tools.ItemThaumiumAxe;
import thaumcraft.common.items.tools.ItemThaumiumShovel;
import thaumcraft.common.items.tools.ItemThaumiumHoe;
import thaumcraft.common.items.tools.ItemVoidSword;
import thaumcraft.common.items.tools.ItemVoidPickaxe;
import thaumcraft.common.items.tools.ItemVoidAxe;
import thaumcraft.common.items.tools.ItemVoidShovel;
import thaumcraft.common.items.tools.ItemVoidHoe;
import thaumcraft.common.items.casters.ItemCaster;
import thaumcraft.common.items.casters.ItemFocus;
import thaumcraft.common.items.baubles.ItemAmuletVis;
import thaumcraft.common.items.baubles.ItemCloudRing;
import thaumcraft.common.items.baubles.ItemCuriosityBand;
import thaumcraft.common.items.baubles.ItemCharmUndying;
import thaumcraft.common.items.baubles.ItemVerdantCharm;
import thaumcraft.common.items.baubles.ItemVoidseerCharm;
import thaumcraft.common.items.tools.ItemElementalPickaxe;
import thaumcraft.common.items.tools.ItemElementalAxe;
import thaumcraft.common.items.tools.ItemElementalSword;
import thaumcraft.common.items.tools.ItemElementalShovel;
import thaumcraft.common.items.tools.ItemPrimalCrusher;
import thaumcraft.common.items.tools.ItemCrimsonBlade;
import thaumcraft.common.items.tools.ItemElementalHoe;
import thaumcraft.common.items.tools.ItemResonator;
import thaumcraft.common.items.tools.ItemSanityChecker;
import thaumcraft.common.items.tools.ItemHandMirror;
import thaumcraft.common.items.tools.ItemGrappleGun;
import thaumcraft.common.items.consumables.ItemBathSalts;
import thaumcraft.common.items.consumables.ItemSanitySoap;
import thaumcraft.common.items.consumables.ItemBottleTaint;
import thaumcraft.common.items.consumables.ItemCausalityCollapser;
import thaumcraft.common.items.consumables.ItemLabel;
import thaumcraft.common.items.curios.ItemCelestialNotes;
import thaumcraft.common.items.curios.ItemCurio;
import thaumcraft.common.items.curios.ItemLootBag;
import thaumcraft.common.items.curios.ItemPechWand;
import thaumcraft.common.items.curios.ItemPrimordialPearl;
import thaumcraft.common.items.casters.ItemFocusPouch;
import thaumcraft.common.golems.ItemGolemBell;
import thaumcraft.common.golems.ItemGolemPlacer;
import thaumcraft.common.items.golems.ItemGolemModule;
import thaumcraft.common.golems.seals.ItemSealPlacer;
import thaumcraft.common.entities.construct.ItemTurretPlacer;
import thaumcraft.common.items.misc.ItemCreativeFluxSponge;
import thaumcraft.common.items.misc.ItemCreativePlacer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import java.util.function.Supplier;

/**
 * Registry for all Thaumcraft items.
 * Uses DeferredRegister for 1.20.1 Forge.
 */
public class ModItems {

    public static final DeferredRegister<Item> ITEMS = 
            DeferredRegister.create(BuiltInRegistries.ITEM, Thaumcraft.MODID);

    // ==================== Tools ====================

    public static final DeferredHolder<Item, Item> THAUMONOMICON = registerItem("thaumonomicon",
            ItemThaumonomicon::new);

    public static final DeferredHolder<Item, Item> CRIMSON_RITES = registerItem("crimson_rites",
            () -> new Item(ItemRegistration.id(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE))));

    public static final DeferredHolder<Item, Item> THAUMOMETER = registerItem("thaumometer",
            ItemThaumometer::new);

    public static final DeferredHolder<Item, Item> SCRIBING_TOOLS = registerItem("scribing_tools",
            ItemScribingTools::new);

    // ==================== Phials ====================

    public static final DeferredHolder<Item, Item> PHIAL_EMPTY = registerItem("phial_empty",
            ItemPhial::createEmpty);

    public static final DeferredHolder<Item, Item> PHIAL_FILLED = registerItem("phial_filled",
            ItemPhial::createFilled);

    // ==================== Basic Materials ====================

    public static final DeferredHolder<Item, Item> QUICKSILVER = registerItem("quicksilver",
            ItemMaterial::basic);

    public static final DeferredHolder<Item, Item> ALUMENTUM = registerItem("alumentum",
            ItemMaterial::basic);

    public static final DeferredHolder<Item, Item> NITOR = registerItem("nitor",
            ItemMaterial::uncommon);

    public static final DeferredHolder<Item, Item> SALIS_MUNDUS = registerItem("salis_mundus",
            ItemMagicDust::new);

    public static final DeferredHolder<Item, Item> BALANCED_SHARD = registerItem("balanced_shard",
            ItemMaterial::uncommon);

    // ==================== Metal Ingots & Nuggets ====================

    public static final DeferredHolder<Item, Item> THAUMIUM_INGOT = registerItem("thaumium_ingot",
            ItemMaterial::basic);

    public static final DeferredHolder<Item, Item> THAUMIUM_NUGGET = registerItem("thaumium_nugget",
            ItemMaterial::basic);

    public static final DeferredHolder<Item, Item> VOID_METAL_INGOT = registerItem("void_metal_ingot",
            () -> new ItemMaterial(new Item.Properties().rarity(Rarity.RARE)));

    public static final DeferredHolder<Item, Item> VOID_METAL_NUGGET = registerItem("void_metal_nugget",
            () -> new ItemMaterial(new Item.Properties().rarity(Rarity.RARE)));

    public static final DeferredHolder<Item, Item> BRASS_INGOT = registerItem("brass_ingot",
            ItemMaterial::basic);

    public static final DeferredHolder<Item, Item> BRASS_NUGGET = registerItem("brass_nugget",
            ItemMaterial::basic);

    public static final DeferredHolder<Item, Item> QUICKSILVER_NUGGET = registerItem("quicksilver_nugget",
            ItemMaterial::basic);

    public static final DeferredHolder<Item, Item> QUARTZ_NUGGET = registerItem("quartz_nugget",
            ItemMaterial::basic);
    public static final DeferredHolder<Item, Item> NUGGET_RARE_EARTH = registerItem("nugget_rareearth",
            ItemMaterial::uncommon);

    // ==================== Primal Shards ====================

    public static final DeferredHolder<Item, Item> SHARD_AIR = registerItem("shard_air",
            ItemMaterial::basic);

    public static final DeferredHolder<Item, Item> SHARD_FIRE = registerItem("shard_fire",
            ItemMaterial::basic);

    public static final DeferredHolder<Item, Item> SHARD_WATER = registerItem("shard_water",
            ItemMaterial::basic);

    public static final DeferredHolder<Item, Item> SHARD_EARTH = registerItem("shard_earth",
            ItemMaterial::basic);

    public static final DeferredHolder<Item, Item> SHARD_ORDER = registerItem("shard_order",
            ItemMaterial::basic);

    public static final DeferredHolder<Item, Item> SHARD_ENTROPY = registerItem("shard_entropy",
            ItemMaterial::basic);

    // ==================== Vis Crystals ====================

    public static final DeferredHolder<Item, Item> VIS_CRYSTAL_AIR = registerItem("vis_crystal_air",
            () -> new ItemVisCrystal(Aspect.AIR));

    public static final DeferredHolder<Item, Item> VIS_CRYSTAL_FIRE = registerItem("vis_crystal_fire",
            () -> new ItemVisCrystal(Aspect.FIRE));

    public static final DeferredHolder<Item, Item> VIS_CRYSTAL_WATER = registerItem("vis_crystal_water",
            () -> new ItemVisCrystal(Aspect.WATER));

    public static final DeferredHolder<Item, Item> VIS_CRYSTAL_EARTH = registerItem("vis_crystal_earth",
            () -> new ItemVisCrystal(Aspect.EARTH));

    public static final DeferredHolder<Item, Item> VIS_CRYSTAL_ORDER = registerItem("vis_crystal_order",
            () -> new ItemVisCrystal(Aspect.ORDER));

    public static final DeferredHolder<Item, Item> VIS_CRYSTAL_ENTROPY = registerItem("vis_crystal_entropy",
            () -> new ItemVisCrystal(Aspect.ENTROPY));

    // 1.12 has a crystal for EVERY aspect (ConfigRecipes.java:131 loops all
    // Aspects); these are the non-primal ones actually used as recipe
    // ingredients in 1.12 (Verdant Heart: LIFE+PLANT, Verdant Heart Life:
    // LIFE+MAN, Verdant Heart Sustain: DESIRE+AIR).
    public static final DeferredHolder<Item, Item> VIS_CRYSTAL_LIFE = registerItem("vis_crystal_life",
            () -> new ItemVisCrystal(Aspect.LIFE));

    public static final DeferredHolder<Item, Item> VIS_CRYSTAL_PLANT = registerItem("vis_crystal_plant",
            () -> new ItemVisCrystal(Aspect.PLANT));

    public static final DeferredHolder<Item, Item> VIS_CRYSTAL_MAN = registerItem("vis_crystal_man",
            () -> new ItemVisCrystal(Aspect.MAN));

    public static final DeferredHolder<Item, Item> VIS_CRYSTAL_DESIRE = registerItem("vis_crystal_desire",
            () -> new ItemVisCrystal(Aspect.DESIRE));

    // ==================== Crafting Components ====================

    public static final DeferredHolder<Item, Item> AMBER = registerItem("amber",
            ItemMaterial::basic);

    public static final DeferredHolder<Item, Item> AMBER_BEAD = registerItem("amber_bead",
            ItemMaterial::basic);

    public static final DeferredHolder<Item, Item> ENCHANTED_FABRIC = registerItem("enchanted_fabric",
            ItemMaterial::uncommon);

    public static final DeferredHolder<Item, Item> PRIMAL_CHARM = registerItem("primal_charm",
            ItemMaterial::uncommon);

    public static final DeferredHolder<Item, Item> SALISITE = registerItem("salisite",
            ItemMaterial::basic);

    public static final DeferredHolder<Item, Item> MIRRORED_GLASS = registerItem("mirrored_glass",
            ItemMaterial::uncommon);

    public static final DeferredHolder<Item, Item> FILTER = registerItem("filter",
            ItemMaterial::basic);

    public static final DeferredHolder<Item, Item> MORPHIC_RESONATOR = registerItem("morphic_resonator",
            ItemMaterial::uncommon);

    public static final DeferredHolder<Item, Item> VIS_RESONATOR = registerItem("vis_resonator",
            ItemMaterial::uncommon);

    public static final DeferredHolder<Item, Item> TALLOW = registerItem("tallow",
            ItemMaterial::basic);

    public static final DeferredHolder<Item, Item> JAR_BRACE = registerItem("jar_brace",
            ItemMaterial::basic);

    public static final DeferredHolder<Item, Item> MIND = registerItem("mind",
            ItemMaterial::basic);

    public static final DeferredHolder<Item, Item> MECHANISM_SIMPLE = registerItem("mechanism_simple",
            ItemMaterial::basic);

    public static final DeferredHolder<Item, Item> MECHANISM_COMPLEX = registerItem("mechanism_complex",
            ItemMaterial::uncommon);

    // ==================== Plates ====================

    public static final DeferredHolder<Item, Item> PLATE_IRON = registerItem("plate_iron",
            ItemMaterial::basic);

    public static final DeferredHolder<Item, Item> PLATE_BRASS = registerItem("plate_brass",
            ItemMaterial::basic);

    public static final DeferredHolder<Item, Item> PLATE_THAUMIUM = registerItem("plate_thaumium",
            ItemMaterial::basic);

    public static final DeferredHolder<Item, Item> PLATE_VOID = registerItem("plate_void",
            ItemMaterial::rare);

    // ==================== Clusters (Raw Ores) ====================

    public static final DeferredHolder<Item, Item> CLUSTER_IRON = registerItem("cluster_iron",
            ItemMaterial::basic);

    public static final DeferredHolder<Item, Item> CLUSTER_GOLD = registerItem("cluster_gold",
            ItemMaterial::basic);

    public static final DeferredHolder<Item, Item> CLUSTER_COPPER = registerItem("cluster_copper",
            ItemMaterial::basic);

    public static final DeferredHolder<Item, Item> CLUSTER_CINNABAR = registerItem("cluster_cinnabar",
            ItemMaterial::basic);

    public static final DeferredHolder<Item, Item> CLUSTER_QUARTZ = registerItem("cluster_quartz",
            ItemMaterial::basic);

    public static final DeferredHolder<Item, Item> CLUSTER_TIN = registerItem("cluster_tin",
            ItemMaterial::basic);

    public static final DeferredHolder<Item, Item> CLUSTER_SILVER = registerItem("cluster_silver",
            ItemMaterial::basic);

    public static final DeferredHolder<Item, Item> CLUSTER_LEAD = registerItem("cluster_lead",
            ItemMaterial::basic);

    // ==================== Golem Materials ====================

    public static final DeferredHolder<Item, Item> BRAIN_CURIOUS = registerItem("brain_curious",
            ItemMaterial::uncommon);

    // ==================== Research Notes ====================

    public static final DeferredHolder<Item, Item> RESEARCH_NOTES = registerItem("research_notes",
            () -> new Item(ItemRegistration.id(new Item.Properties().stacksTo(1))));

    public static final DeferredHolder<Item, Item> COMPLETE_NOTES = registerItem("complete_notes",
            () -> new Item(ItemRegistration.id(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))));

    // ==================== Curiosities ====================

    public static final DeferredHolder<Item, Item> PRIMORDIAL_PEARL = registerItem("primordial_pearl",
            ItemPrimordialPearl::new);

    public static final DeferredHolder<Item, Item> NODE_JAR = registerItem("node_jar",
            () -> new ItemNodeJar(new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<Item, Item> TAINT_SLIME = registerItem("taint_slime",
            ItemMaterial::basic);

    public static final DeferredHolder<Item, Item> TAINT_TENDRIL = registerItem("taint_tendril",
            ItemMaterial::basic);

    public static final DeferredHolder<Item, Item> ZOMBIE_BRAIN = registerItem("zombie_brain",
            ItemMaterial::basic);

    public static final DeferredHolder<Item, Item> FLUX_CRYSTAL = registerItem("flux_crystal",
            ItemMaterial::uncommon);

    public static final DeferredHolder<Item, Item> VOID_SEED = registerItem("void_seed",
            () -> new ItemMaterial(new Item.Properties().rarity(Rarity.RARE)));

    // ==================== Food ====================

    // Note: Foods need FoodProperties - simplified for now
    public static final DeferredHolder<Item, Item> TRIPLE_MEAT_TREAT = registerItem("triple_meat_treat",
            () -> new Item(ItemRegistration.id(new Item.Properties()
                    .food(new net.minecraft.world.food.FoodProperties.Builder()
                            .nutrition(8)
                            .saturationModifier(1.0f)
                            .build()))));

    // 1.12 ItemChunksEdible: nutrition 1, saturation 0.3, 6 meats (beef/chicken/pork/fish/rabbit/mutton)
    public static final DeferredHolder<Item, Item> CHUNKS_BEEF = registerItem("chunks_beef",
            () -> new Item(ItemRegistration.id(new Item.Properties()
                    .food(new net.minecraft.world.food.FoodProperties.Builder()
                            .nutrition(1)
                            .saturationModifier(0.3f)
                            .build()))));

    public static final DeferredHolder<Item, Item> CHUNKS_CHICKEN = registerItem("chunks_chicken",
            () -> new Item(ItemRegistration.id(new Item.Properties()
                    .food(new net.minecraft.world.food.FoodProperties.Builder()
                            .nutrition(1)
                            .saturationModifier(0.3f)
                            .build()))));

    public static final DeferredHolder<Item, Item> CHUNKS_PORK = registerItem("chunks_pork",
            () -> new Item(ItemRegistration.id(new Item.Properties()
                    .food(new net.minecraft.world.food.FoodProperties.Builder()
                            .nutrition(1)
                            .saturationModifier(0.3f)
                            .build()))));

    public static final DeferredHolder<Item, Item> CHUNKS_FISH = registerItem("chunks_fish",
            () -> new Item(ItemRegistration.id(new Item.Properties()
                    .food(new net.minecraft.world.food.FoodProperties.Builder()
                            .nutrition(1)
                            .saturationModifier(0.3f)
                            .build()))));

    public static final DeferredHolder<Item, Item> CHUNKS_RABBIT = registerItem("chunks_rabbit",
            () -> new Item(ItemRegistration.id(new Item.Properties()
                    .food(new net.minecraft.world.food.FoodProperties.Builder()
                            .nutrition(1)
                            .saturationModifier(0.3f)
                            .build()))));

    public static final DeferredHolder<Item, Item> CHUNKS_MUTTON = registerItem("chunks_mutton",
            () -> new Item(ItemRegistration.id(new Item.Properties()
                    .food(new net.minecraft.world.food.FoodProperties.Builder()
                            .nutrition(1)
                            .saturationModifier(0.3f)
                            .build()))));

    // ==================== Armor - Goggles ====================

    public static final DeferredHolder<Item, Item> GOGGLES = registerItem("goggles",
            ItemGoggles::new);

    // ==================== Armor - Thaumaturge Robes ====================

    public static final DeferredHolder<Item, Item> CLOTH_CHEST = registerItem("cloth_chest",
            ItemRobeArmor::createChest);

    public static final DeferredHolder<Item, Item> CLOTH_LEGS = registerItem("cloth_legs",
            ItemRobeArmor::createLegs);

    public static final DeferredHolder<Item, Item> CLOTH_BOOTS = registerItem("cloth_boots",
            ItemRobeArmor::createBoots);

    // ==================== Tools - Thaumium ====================

    public static final DeferredHolder<Item, Item> THAUMIUM_SWORD = registerItem("thaumium_sword",
            ItemThaumiumSword::new);

    public static final DeferredHolder<Item, Item> THAUMIUM_PICK = registerItem("thaumium_pick",
            ItemThaumiumPickaxe::new);

    public static final DeferredHolder<Item, Item> THAUMIUM_AXE = registerItem("thaumium_axe",
            ItemThaumiumAxe::new);

    public static final DeferredHolder<Item, Item> THAUMIUM_SHOVEL = registerItem("thaumium_shovel",
            ItemThaumiumShovel::new);

    public static final DeferredHolder<Item, Item> THAUMIUM_HOE = registerItem("thaumium_hoe",
            ItemThaumiumHoe::new);

    // ==================== Tools - Void Metal ====================

    public static final DeferredHolder<Item, Item> VOID_SWORD = registerItem("void_sword",
            ItemVoidSword::new);

    public static final DeferredHolder<Item, Item> VOID_PICK = registerItem("void_pick",
            ItemVoidPickaxe::new);

    public static final DeferredHolder<Item, Item> VOID_AXE = registerItem("void_axe",
            ItemVoidAxe::new);

    public static final DeferredHolder<Item, Item> VOID_SHOVEL = registerItem("void_shovel",
            ItemVoidShovel::new);

    public static final DeferredHolder<Item, Item> VOID_HOE = registerItem("void_hoe",
            ItemVoidHoe::new);

    // ==================== Armor - Thaumium ====================

    public static final DeferredHolder<Item, Item> THAUMIUM_HELM = registerItem("thaumium_helm",
            ItemThaumiumArmor::createHelmet);

    public static final DeferredHolder<Item, Item> THAUMIUM_CHEST = registerItem("thaumium_chest",
            ItemThaumiumArmor::createChestplate);

    public static final DeferredHolder<Item, Item> THAUMIUM_LEGS = registerItem("thaumium_legs",
            ItemThaumiumArmor::createLeggings);

    public static final DeferredHolder<Item, Item> THAUMIUM_BOOTS = registerItem("thaumium_boots",
            ItemThaumiumArmor::createBoots);

    // ==================== Armor - Void Metal ====================

    public static final DeferredHolder<Item, Item> VOID_HELM = registerItem("void_helm",
            ItemVoidArmor::createHelmet);

    public static final DeferredHolder<Item, Item> VOID_CHEST = registerItem("void_chest",
            ItemVoidArmor::createChestplate);

    public static final DeferredHolder<Item, Item> VOID_LEGS = registerItem("void_legs",
            ItemVoidArmor::createLeggings);

    public static final DeferredHolder<Item, Item> VOID_BOOTS = registerItem("void_boots",
            ItemVoidArmor::createBoots);

    // ==================== Caster Gauntlets ====================

    public static final DeferredHolder<Item, Item> CASTER_BASIC = registerItem("caster_basic",
            ItemCaster::createBasic);

    public static final DeferredHolder<Item, Item> CASTER_ADVANCED = registerItem("caster_advanced",
            ItemCaster::createAdvanced);

    public static final DeferredHolder<Item, Item> CASTER_MASTER = registerItem("caster_master",
            ItemCaster::createMaster);

    // ==================== Focus Items ====================

    public static final DeferredHolder<Item, Item> FOCUS_1 = registerItem("focus_1",
            ItemFocus::createLesser);

    public static final DeferredHolder<Item, Item> FOCUS_2 = registerItem("focus_2",
            ItemFocus::createAdvanced);

    public static final DeferredHolder<Item, Item> FOCUS_3 = registerItem("focus_3",
            ItemFocus::createGreater);

    // ==================== Special Armor ====================

    public static final DeferredHolder<Item, Item> TRAVELLER_BOOTS = registerItem("traveller_boots",
            ItemBootsTraveller::new);

    // ==================== Fortress Armor ====================

    public static final DeferredHolder<Item, Item> FORTRESS_HELM = registerItem("fortress_helm",
            ItemFortressArmor::createHelmet);

    public static final DeferredHolder<Item, Item> FORTRESS_CHEST = registerItem("fortress_chest",
            ItemFortressArmor::createChestplate);

    public static final DeferredHolder<Item, Item> FORTRESS_LEGS = registerItem("fortress_legs",
            ItemFortressArmor::createLeggings);

    // ==================== Void Robe Armor ====================

    public static final DeferredHolder<Item, Item> VOID_ROBE_HELM = registerItem("void_robe_helm",
            ItemVoidRobeArmor::createHelmet);

    public static final DeferredHolder<Item, Item> VOID_ROBE_CHEST = registerItem("void_robe_chest",
            ItemVoidRobeArmor::createChestplate);

    public static final DeferredHolder<Item, Item> VOID_ROBE_LEGS = registerItem("void_robe_legs",
            ItemVoidRobeArmor::createLeggings);

    // ==================== Crimson Cult Robe Armor ====================

    public static final DeferredHolder<Item, Item> CRIMSON_ROBE_HELM = registerItem("crimson_robe_helm",
            ItemCultistRobeArmor::createHelmet);

    public static final DeferredHolder<Item, Item> CRIMSON_ROBE_CHEST = registerItem("crimson_robe_chest",
            ItemCultistRobeArmor::createChestplate);

    public static final DeferredHolder<Item, Item> CRIMSON_ROBE_LEGS = registerItem("crimson_robe_legs",
            ItemCultistRobeArmor::createLeggings);

    // ==================== Crimson Cult Plate Armor ====================

    public static final DeferredHolder<Item, Item> CRIMSON_PLATE_HELM = registerItem("crimson_plate_helm",
            ItemCultistPlateArmor::createHelmet);

    public static final DeferredHolder<Item, Item> CRIMSON_PLATE_CHEST = registerItem("crimson_plate_chest",
            ItemCultistPlateArmor::createChestplate);

    public static final DeferredHolder<Item, Item> CRIMSON_PLATE_LEGS = registerItem("crimson_plate_legs",
            ItemCultistPlateArmor::createLeggings);

    // ==================== Crimson Cult Boots (shared) ====================

    public static final DeferredHolder<Item, Item> CRIMSON_BOOTS = registerItem("crimson_boots",
            ItemCultistBoots::new);

    // ==================== Crimson Praetor Armor (Cultist Leader) ====================

    public static final DeferredHolder<Item, Item> CRIMSON_PRAETOR_HELM = registerItem("crimson_praetor_helm",
            ItemCultistLeaderArmor::createHelmet);

    public static final DeferredHolder<Item, Item> CRIMSON_PRAETOR_CHEST = registerItem("crimson_praetor_chest",
            ItemCultistLeaderArmor::createChestplate);

    public static final DeferredHolder<Item, Item> CRIMSON_PRAETOR_LEGS = registerItem("crimson_praetor_legs",
            ItemCultistLeaderArmor::createLeggings);

    // ==================== Baubles / Curios ====================

    public static final DeferredHolder<Item, Item> AMULET_VIS_FOUND = registerItem("amulet_vis_found",
            ItemAmuletVis::createFound);

    public static final DeferredHolder<Item, Item> AMULET_VIS_CRAFTED = registerItem("amulet_vis_crafted",
            ItemAmuletVis::createCrafted);

    public static final DeferredHolder<Item, Item> CLOUD_RING = registerItem("cloud_ring",
            ItemCloudRing::new);

    public static final DeferredHolder<Item, Item> CURIOSITY_BAND = registerItem("curiosity_band",
            ItemCuriosityBand::new);

    public static final DeferredHolder<Item, Item> CHARM_UNDYING = registerItem("charm_undying",
            ItemCharmUndying::new);

    // Verdant Charms (3 variants)
    public static final DeferredHolder<Item, Item> VERDANT_CHARM = registerItem("verdant_charm",
            ItemVerdantCharm::createBasic);

    public static final DeferredHolder<Item, Item> VERDANT_CHARM_LIFE = registerItem("verdant_charm_life",
            ItemVerdantCharm::createLife);

    public static final DeferredHolder<Item, Item> VERDANT_CHARM_SUSTAIN = registerItem("verdant_charm_sustain",
            ItemVerdantCharm::createSustain);

    // Fortresses masks (3 variants)
    public static final DeferredHolder<Item, Item> MASK_GRINNING_DEVIL = registerItem("mask_grinning_devil",
            () -> new Item(ItemRegistration.id(new Item.Properties()).stacksTo(1)));

    public static final DeferredHolder<Item, Item> MASK_ANGRY_GHOST = registerItem("mask_angry_ghost",
            () -> new Item(ItemRegistration.id(new Item.Properties()).stacksTo(1)));

    public static final DeferredHolder<Item, Item> MASK_SIPPING_FIEND = registerItem("mask_sipping_fiend",
            () -> new Item(ItemRegistration.id(new Item.Properties()).stacksTo(1)));

    public static final DeferredHolder<Item, Item> VOIDSEER_CHARM = registerItem("voidseer_charm",
            ItemVoidseerCharm::new);
    
    // ==================== Vis Discount Gear (Mundane/Fancy) ====================
    
    public static final DeferredHolder<Item, Item> AMULET_MUNDANE = registerItem("amulet_mundane",
            thaumcraft.common.items.baubles.ItemMundaneGear::new);
    
    public static final DeferredHolder<Item, Item> RING_MUNDANE = registerItem("ring_mundane",
            thaumcraft.common.items.baubles.ItemMundaneGear::new);
    
    public static final DeferredHolder<Item, Item> GIRDLE_MUNDANE = registerItem("girdle_mundane",
            thaumcraft.common.items.baubles.ItemMundaneGear::new);
    
    public static final DeferredHolder<Item, Item> RING_APPRENTICE = registerItem("ring_apprentice",
            thaumcraft.common.items.baubles.ItemApprenticeRing::new);
    
    public static final DeferredHolder<Item, Item> AMULET_FANCY = registerItem("amulet_fancy",
            thaumcraft.common.items.baubles.ItemFancyGear::new);
    
    public static final DeferredHolder<Item, Item> RING_FANCY = registerItem("ring_fancy",
            thaumcraft.common.items.baubles.ItemFancyGear::new);
    
    public static final DeferredHolder<Item, Item> GIRDLE_FANCY = registerItem("girdle_fancy",
            thaumcraft.common.items.baubles.ItemFancyGear::new);
    
    // ==================== Elemental Tools ====================

    public static final DeferredHolder<Item, Item> ELEMENTAL_PICK = registerItem("elemental_pick",
            ItemElementalPickaxe::new);

    public static final DeferredHolder<Item, Item> ELEMENTAL_AXE = registerItem("elemental_axe",
            ItemElementalAxe::new);

    public static final DeferredHolder<Item, Item> ELEMENTAL_SWORD = registerItem("elemental_sword",
            ItemElementalSword::new);

    public static final DeferredHolder<Item, Item> ELEMENTAL_SHOVEL = registerItem("elemental_shovel",
            ItemElementalShovel::new);

    // ==================== Special Tools ====================

    public static final DeferredHolder<Item, Item> PRIMAL_CRUSHER = registerItem("primal_crusher",
            ItemPrimalCrusher::new);

    public static final DeferredHolder<Item, Item> CRIMSON_BLADE = registerItem("crimson_blade",
            ItemCrimsonBlade::new);

    public static final DeferredHolder<Item, Item> ELEMENTAL_HOE = registerItem("elemental_hoe",
            ItemElementalHoe::new);

    // ==================== Utility Tools ====================

    public static final DeferredHolder<Item, Item> RESONATOR = registerItem("resonator",
            ItemResonator::new);

    public static final DeferredHolder<Item, Item> SANITY_CHECKER = registerItem("sanity_checker",
            ItemSanityChecker::new);

    public static final DeferredHolder<Item, Item> HAND_MIRROR = registerItem("hand_mirror",
            ItemHandMirror::new);

    public static final DeferredHolder<Item, Item> GRAPPLE_GUN = registerItem("grapple_gun",
            ItemGrappleGun::new);

    // ==================== Consumables ====================

    public static final DeferredHolder<Item, Item> BATH_SALTS = registerItem("bath_salts",
            ItemBathSalts::new);

    public static final DeferredHolder<Item, Item> SANITY_SOAP = registerItem("sanity_soap",
            ItemSanitySoap::new);

    public static final DeferredHolder<Item, Item> BOTTLE_TAINT = registerItem("bottle_taint",
            ItemBottleTaint::new);

    public static final DeferredHolder<Item, Item> CAUSALITY_COLLAPSER = registerItem("causality_collapser",
            ItemCausalityCollapser::new);

    public static final DeferredHolder<Item, Item> LABEL_BLANK = registerItem("label_blank",
            ItemLabel::createBlank);

    public static final DeferredHolder<Item, Item> LABEL_FILLED = registerItem("label_filled",
            ItemLabel::createFilled);

    // ==================== Loot Bags ====================

    public static final DeferredHolder<Item, Item> LOOT_BAG_COMMON = registerItem("loot_bag_common",
            ItemLootBag::createCommon);

    public static final DeferredHolder<Item, Item> LOOT_BAG_UNCOMMON = registerItem("loot_bag_uncommon",
            ItemLootBag::createUncommon);

    public static final DeferredHolder<Item, Item> LOOT_BAG_RARE = registerItem("loot_bag_rare",
            ItemLootBag::createRare);

    // ==================== Curios ====================

    public static final DeferredHolder<Item, Item> PECH_WAND = registerItem("pech_wand",
            ItemPechWand::new);

    public static final DeferredHolder<Item, Item> CURIO = registerItem("curio",
            ItemCurio::new);

    // ==================== Celestial Notes ====================

    public static final DeferredHolder<Item, Item> CELESTIAL_NOTES_SUN = registerItem("celestial_notes_sun",
            ItemCelestialNotes::createSun);

    public static final DeferredHolder<Item, Item> CELESTIAL_NOTES_STARS_1 = registerItem("celestial_notes_stars_1",
            ItemCelestialNotes::createStars1);

    public static final DeferredHolder<Item, Item> CELESTIAL_NOTES_STARS_2 = registerItem("celestial_notes_stars_2",
            ItemCelestialNotes::createStars2);

    public static final DeferredHolder<Item, Item> CELESTIAL_NOTES_STARS_3 = registerItem("celestial_notes_stars_3",
            ItemCelestialNotes::createStars3);

    public static final DeferredHolder<Item, Item> CELESTIAL_NOTES_STARS_4 = registerItem("celestial_notes_stars_4",
            ItemCelestialNotes::createStars4);

    public static final DeferredHolder<Item, Item> CELESTIAL_NOTES_MOON_1 = registerItem("celestial_notes_moon_1",
            ItemCelestialNotes::createMoon1);

    public static final DeferredHolder<Item, Item> CELESTIAL_NOTES_MOON_2 = registerItem("celestial_notes_moon_2",
            ItemCelestialNotes::createMoon2);

    public static final DeferredHolder<Item, Item> CELESTIAL_NOTES_MOON_3 = registerItem("celestial_notes_moon_3",
            ItemCelestialNotes::createMoon3);

    public static final DeferredHolder<Item, Item> CELESTIAL_NOTES_MOON_4 = registerItem("celestial_notes_moon_4",
            ItemCelestialNotes::createMoon4);

    public static final DeferredHolder<Item, Item> CELESTIAL_NOTES_MOON_5 = registerItem("celestial_notes_moon_5",
            ItemCelestialNotes::createMoon5);

    public static final DeferredHolder<Item, Item> CELESTIAL_NOTES_MOON_6 = registerItem("celestial_notes_moon_6",
            ItemCelestialNotes::createMoon6);

    public static final DeferredHolder<Item, Item> CELESTIAL_NOTES_MOON_7 = registerItem("celestial_notes_moon_7",
            ItemCelestialNotes::createMoon7);

    public static final DeferredHolder<Item, Item> CELESTIAL_NOTES_MOON_8 = registerItem("celestial_notes_moon_8",
            ItemCelestialNotes::createMoon8);

    // ==================== Focus Accessories ====================

    public static final DeferredHolder<Item, Item> FOCUS_POUCH = registerItem("focus_pouch",
            ItemFocusPouch::new);

    // ==================== Golem Items ====================

    public static final DeferredHolder<Item, Item> GOLEM_PLACER = registerItem("golem_placer",
            ItemGolemPlacer::new);

    public static final DeferredHolder<Item, Item> GOLEM_BELL = registerItem("golem_bell",
            ItemGolemBell::new);

    // ==================== Golem Seals ====================

    public static final DeferredHolder<Item, Item> SEAL_BLANK = registerItem("seal_blank",
            ItemSealPlacer::createBlank);

    // Seal types - one item per seal
    public static final DeferredHolder<Item, Item> SEAL_PICKUP = registerItem("seal_pickup",
            () -> ItemSealPlacer.create("thaumcraft:pickup"));

    public static final DeferredHolder<Item, Item> SEAL_EMPTY = registerItem("seal_empty",
            () -> ItemSealPlacer.create("thaumcraft:empty"));

    public static final DeferredHolder<Item, Item> SEAL_FILL = registerItem("seal_fill",
            () -> ItemSealPlacer.create("thaumcraft:fill"));

    public static final DeferredHolder<Item, Item> SEAL_GUARD = registerItem("seal_guard",
            () -> ItemSealPlacer.create("thaumcraft:guard"));

    public static final DeferredHolder<Item, Item> SEAL_BUTCHER = registerItem("seal_butcher",
            () -> ItemSealPlacer.create("thaumcraft:butcher"));

    public static final DeferredHolder<Item, Item> SEAL_HARVEST = registerItem("seal_harvest",
            () -> ItemSealPlacer.create("thaumcraft:harvest"));

    public static final DeferredHolder<Item, Item> SEAL_LUMBER = registerItem("seal_lumber",
            () -> ItemSealPlacer.create("thaumcraft:lumber"));

    public static final DeferredHolder<Item, Item> SEAL_BREAKER = registerItem("seal_breaker",
            () -> ItemSealPlacer.create("thaumcraft:breaker"));

    public static final DeferredHolder<Item, Item> SEAL_PROVIDE = registerItem("seal_provide",
            () -> ItemSealPlacer.create("thaumcraft:provide"));

    public static final DeferredHolder<Item, Item> SEAL_STOCK = registerItem("seal_stock",
            () -> ItemSealPlacer.create("thaumcraft:stock"));

    public static final DeferredHolder<Item, Item> SEAL_USE = registerItem("seal_use",
            () -> ItemSealPlacer.create("thaumcraft:use"));

    public static final DeferredHolder<Item, Item> SEAL_BREAKER_ADVANCED = registerItem("seal_breaker_advanced",
            () -> ItemSealPlacer.create("thaumcraft:breaker_advanced"));
    public static final DeferredHolder<Item, Item> SEAL_PICKUP_ADVANCED = registerItem("seal_pickup_advanced",
            () -> ItemSealPlacer.create("thaumcraft:pickup_advanced"));

    public static final DeferredHolder<Item, Item> SEAL_EMPTY_ADVANCED = registerItem("seal_empty_advanced",
            () -> ItemSealPlacer.create("thaumcraft:empty_advanced"));
    public static final DeferredHolder<Item, Item> SEAL_FILL_ADVANCED = registerItem("seal_fill_advanced",
            () -> ItemSealPlacer.create("thaumcraft:fill_advanced"));
    public static final DeferredHolder<Item, Item> SEAL_GUARD_ADVANCED = registerItem("seal_guard_advanced",
            () -> ItemSealPlacer.create("thaumcraft:guard_advanced"));
    // ==================== Crystal Essence ====================

    public static final DeferredHolder<Item, Item> CRYSTAL_ESSENCE = registerItem("crystal_essence",
            () -> new ItemCrystalEssence());

    // ==================== Turret Placers ====================

    public static final DeferredHolder<Item, Item> TURRET_PLACER_BASIC = registerItem("turret_placer_basic",
            ItemTurretPlacer::createBasic);

    public static final DeferredHolder<Item, Item> TURRET_PLACER_ADVANCED = registerItem("turret_placer_advanced",
            ItemTurretPlacer::createAdvanced);

    public static final DeferredHolder<Item, Item> TURRET_PLACER_BORE = registerItem("turret_placer_bore",
            ItemTurretPlacer::createBore);

    // ==================== Grapple Gun Components ====================

    public static final DeferredHolder<Item, Item> GRAPPLE_GUN_SPOOL = registerItem("grapple_gun_spool",
            ItemMaterial::uncommon);

    public static final DeferredHolder<Item, Item> GRAPPLE_GUN_TIP = registerItem("grapple_gun_tip",
            ItemMaterial::uncommon);

    // ==================== Golem Modules ====================

    public static final DeferredHolder<Item, Item> GOLEM_MODULE_AGGRESSION = registerItem("golem_module_aggression",
            () -> ItemGolemModule.create(thaumcraft.api.golems.EnumGolemTrait.FIGHTER));

    public static final DeferredHolder<Item, Item> GOLEM_MODULE_VISION = registerItem("golem_module_vision",
            () -> ItemGolemModule.create(thaumcraft.api.golems.EnumGolemTrait.SCOUT));

    // ==================== Creative-Only Items ====================

    public static final DeferredHolder<Item, Item> CREATIVE_FLUX_SPONGE = registerItem("creative_flux_sponge",
            () -> new ItemCreativeFluxSponge(new Item.Properties()));

    public static final DeferredHolder<Item, Item> CREATIVE_PLACER = registerItem("creative_placer",
            () -> new ItemCreativePlacer(new Item.Properties()));
    /**
     * Register an item with the pending registration id visible to its
     * constructor, which applies it to the {@link Item.Properties} before the
     * Item super-constructor runs (see {@link ItemRegistration}).
     */
    private static <T extends Item> DeferredHolder<Item, T> registerItem(String name, Supplier<T> sup) {
        return ITEMS.register(name, id -> {
            ItemRegistration.set(ResourceKey.create(Registries.ITEM, id));
            T item = sup.get();
            ItemRegistration.clear();
            return item;
        });
    }
}
