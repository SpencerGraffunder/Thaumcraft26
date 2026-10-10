package thaumcraft.init;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import thaumcraft.Thaumcraft;
import thaumcraft.common.world.features.BigMagicTreeFeature;
import thaumcraft.common.world.features.CrystalClusterFeature;
import thaumcraft.common.world.features.GreatwoodTreeFeature;
import thaumcraft.common.world.features.SilverwoodTreeFeature;
import thaumcraft.common.world.features.ThaumcraftPlantFeature;
import thaumcraft.common.world.structures.AncientStoneCircleFeature;
import thaumcraft.common.world.structures.BarrowFeature;
import thaumcraft.common.world.structures.EldritchObeliskFeature;
import thaumcraft.common.world.structures.RuinedTowerFeature;
import thaumcraft.common.world.node.NodeFeature;

/**
 * Registry for all Thaumcraft world generation feature TYPES.
 *
 * In 1.21+/26.3 world generation uses a two-tier model:
 * - FEATURE_TYPE (worldgen/feature_type): the MapCodec for each feature type,
 *   registered in Java here. Addresses a feature's "type" field in data JSON.
 * - FEATURE (worldgen/feature): concrete feature instances, loaded from
 *   datapacks under data/thaumcraft/worldgen/feature/*.json
 *   ({"type": "thaumcraft:<id>", ...parameters}).
 * - PLACED_FEATURE (worldgen/placed_feature): features with placement rules,
 *   loaded from data/thaumcraft/worldgen/placed_feature/*.json.
 */
public class ModFeatures {

    public static final DeferredRegister<MapCodec<? extends Feature>> FEATURE_TYPES =
            DeferredRegister.create(BuiltInRegistries.FEATURE_TYPE, Thaumcraft.MODID);

    // ==================== Tree Features ====================

    /**
     * Greatwood tree - large magical tree with 2x2 trunk.
     * Spawns in forests, plains, and similar biomes.
     * Has a rare spider nest variant.
     */
    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<GreatwoodTreeFeature>> GREATWOOD_TREE =
            FEATURE_TYPES.register("greatwood_tree", () -> GreatwoodTreeFeature.CODEC);

    /**
     * Silverwood tree - magical pale tree with unique trunk shape.
     * Rarer than greatwood, spawns in magical biomes and forests.
     * Spawns shimmerleaf flowers around it.
     */
    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<SilverwoodTreeFeature>> SILVERWOOD_TREE =
            FEATURE_TYPES.register("silverwood_tree", () -> SilverwoodTreeFeature.CODEC);

    /**
     * Big Magic Tree - Large, majestic magical tree with sprawling branches.
     * This is the "fancy" tree variant for magical forest biomes.
     * Taller than regular greatwood/silverwood with more complex branch structure.
     */
    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<BigMagicTreeFeature>> BIG_MAGIC_TREE =
            FEATURE_TYPES.register("big_magic_tree",
                    () -> BigMagicTreeFeature.codecFor(BigMagicTreeFeature.TreeType.GREATWOOD));

    /**
     * Big Silverwood Tree - Large silverwood variant for magical biomes.
     * Uses silverwood logs and leaves instead of greatwood.
     */
    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<BigMagicTreeFeature>> BIG_SILVERWOOD_TREE =
            FEATURE_TYPES.register("big_silverwood_tree",
                    () -> BigMagicTreeFeature.codecFor(BigMagicTreeFeature.TreeType.SILVERWOOD));

    // ==================== Plant Features ====================

    /**
     * Cinderpearl plant cluster - desert fire plants.
     * Spawns in desert biomes on sand.
     */
    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<ThaumcraftPlantFeature>> CINDERPEARL_PATCH =
            FEATURE_TYPES.register("cinderpearl_patch",
                    () -> ThaumcraftPlantFeature.codecFor(ThaumcraftPlantFeature.PlantType.CINDERPEARL));

    /**
     * Shimmerleaf plant cluster - glowing magical flowers.
     * Primarily spawns around silverwood trees, but can appear in magical biomes.
     */
    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<ThaumcraftPlantFeature>> SHIMMERLEAF_PATCH =
            FEATURE_TYPES.register("shimmerleaf_patch",
                    () -> ThaumcraftPlantFeature.codecFor(ThaumcraftPlantFeature.PlantType.SHIMMERLEAF));

    /**
     * Vishroom mushroom cluster - magical cave mushrooms.
     * Spawns underground in caves.
     */
    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<ThaumcraftPlantFeature>> VISHROOM_PATCH =
            FEATURE_TYPES.register("vishroom_patch",
                    () -> ThaumcraftPlantFeature.codecFor(ThaumcraftPlantFeature.PlantType.VISHROOM));

    // ==================== Aura Node Feature ====================

    /**
     * Aura node - underground vis source (TC6 node system).
     * Placed below the world surface via rarity + height placement rules.
     */
    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<NodeFeature>> NODE =
            FEATURE_TYPES.register("node", () -> NodeFeature.CODEC);

    // ==================== Crystal Features ====================

    /**
     * Air crystal cluster - spawns on cave walls.
     * More common at high altitudes.
     */
    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<CrystalClusterFeature>> CRYSTAL_CLUSTER_AIR =
            FEATURE_TYPES.register("crystal_cluster_air",
                    () -> CrystalClusterFeature.codecFor(CrystalClusterFeature.CrystalType.AIR));

    /**
     * Fire crystal cluster - spawns on cave walls.
     * More common near lava and in warm biomes.
     */
    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<CrystalClusterFeature>> CRYSTAL_CLUSTER_FIRE =
            FEATURE_TYPES.register("crystal_cluster_fire",
                    () -> CrystalClusterFeature.codecFor(CrystalClusterFeature.CrystalType.FIRE));

    /**
     * Water crystal cluster - spawns on cave walls.
     * More common near water and in ocean caves.
     */
    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<CrystalClusterFeature>> CRYSTAL_CLUSTER_WATER =
            FEATURE_TYPES.register("crystal_cluster_water",
                    () -> CrystalClusterFeature.codecFor(CrystalClusterFeature.CrystalType.WATER));

    /**
     * Earth crystal cluster - spawns on cave walls.
     * More common at low altitudes (deep caves).
     */
    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<CrystalClusterFeature>> CRYSTAL_CLUSTER_EARTH =
            FEATURE_TYPES.register("crystal_cluster_earth",
                    () -> CrystalClusterFeature.codecFor(CrystalClusterFeature.CrystalType.EARTH));

    /**
     * Order crystal cluster - spawns on cave walls.
     * Rarest primal crystal type.
     */
    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<CrystalClusterFeature>> CRYSTAL_CLUSTER_ORDER =
            FEATURE_TYPES.register("crystal_cluster_order",
                    () -> CrystalClusterFeature.codecFor(CrystalClusterFeature.CrystalType.ORDER));

    /**
     * Entropy crystal cluster - spawns on cave walls.
     * Rarest primal crystal type.
     */
    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<CrystalClusterFeature>> CRYSTAL_CLUSTER_ENTROPY =
            FEATURE_TYPES.register("crystal_cluster_entropy",
                    () -> CrystalClusterFeature.codecFor(CrystalClusterFeature.CrystalType.ENTROPY));

    // ==================== Ore Features ====================
    // Ore generation uses the vanilla minecraft:ore feature type,
    // configured from data/thaumcraft/worldgen/feature/ore_*.json.

    // ==================== Structure Features ====================

    /**
     * Barrow mound - Ancient burial mound with loot and spawners.
     * Underground stone chamber with grass-covered mound entrance.
     * Contains chest, Thaumcraft loot crates/urns, and monster spawners.
     */
    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<BarrowFeature>> BARROW =
            FEATURE_TYPES.register("barrow", () -> BarrowFeature.CODEC);

    /**
     * Ancient Stone Circle - Mysterious stone monuments.
     * Can generate as:
     * - Small circle (4-6 standing stones)
     * - Large circle (8-12 stones with central altar)
     * - Single obelisk with glyphed stones
     */
    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<AncientStoneCircleFeature>> ANCIENT_STONE_CIRCLE =
            FEATURE_TYPES.register("ancient_stone_circle", () -> AncientStoneCircleFeature.CODEC);

    /**
     * Eldritch Obelisk - Tall dark stone monuments.
     * Features:
     * - Central eldritch stone pillar (10-15 blocks tall)
     * - Obsidian-lined base platform
     * - Ancient stone decorations
     * - Scattered debris around the perimeter
     * Hints at eldritch knowledge and may spawn eldritch mobs.
     */
    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<EldritchObeliskFeature>> ELDRITCH_OBELISK =
            FEATURE_TYPES.register("eldritch_obelisk", () -> EldritchObeliskFeature.CODEC);

    /**
     * Ruined Tower - Abandoned wizard towers.
     * Features:
     * - Circular stone tower (radius 3-4, height 8-14)
     * - Partial collapse on one side
     * - Multiple floors with wooden planks
     * - Bookshelves and loot crates/urns
     * - Vegetation growing through the ruins
     * Contains research materials and Thaumcraft loot.
     */
    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<RuinedTowerFeature>> RUINED_TOWER =
            FEATURE_TYPES.register("ruined_tower", () -> RuinedTowerFeature.CODEC);
}
