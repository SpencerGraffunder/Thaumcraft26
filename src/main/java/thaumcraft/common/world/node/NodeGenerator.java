package thaumcraft.common.world.node;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.blocks.world.node.BlockNode;
import thaumcraft.common.tiles.node.TileNode;
import thaumcraft.common.world.biomes.BiomeHandler;
import thaumcraft.init.ModBlocks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aura node world generation (TC6 node system, Thaumaturge parity).
 *
 * <p>Rolls a node's type, quality, aspect mix and vis budget from the biome's
 * aura strength, the surrounding terrain, and the special-rarity config value.
 * The same random source always yields the same node, so nodes are
 * seed-deterministic per world.
 */
public final class NodeGenerator {

    public static final int DEFAULT_SPECIAL_RARITY = 18;
    public static final int DEFAULT_BASE_AURA = 100;

    private static final double PERCENT_TOTAL = 100.0;
    private static final int MODIFIER_RARITY_DIVISOR = 2;
    private static final float TAINTED_BIOME_FACTOR = 1.5F;
    private static final float SMALL_NODE_DIVISOR = 4.0F;
    private static final int MINIMUM_AURA = 8;
    private static final int BONUS_ASPECT_ROLLS = 3;
    private static final float WEIGHT_FLOOR = 0.05F;
    private static final int SURROUNDING_RADIUS = 5;
    private static final int LARGE_BLOCK_COUNT = 100;
    private static final int VERY_LARGE_BLOCK_COUNT = 500;
    private static final double BONUS_NONE_WEIGHT = 0.5;
    private static final int[] FACTORIALS = {1, 1, 2, 6, 24};
    private static final NodeModifier[] MODIFIERS = NodeModifier.values();
    private static final List<Aspect> DARK_ASPECTS = List.of(Aspect.DEATH, Aspect.ENTROPY, Aspect.DARKNESS, Aspect.VOID);

    private record TerrainRule(java.util.function.Predicate<BlockState> matcher, int threshold, List<Aspect> aspects) {}

    private static final List<TerrainRule> TERRAIN_RULES = List.of(
            new TerrainRule(state -> state.getFluidState().is(FluidTags.WATER), LARGE_BLOCK_COUNT, List.of(Aspect.WATER)),
            new TerrainRule(state -> state.getFluidState().is(FluidTags.LAVA), LARGE_BLOCK_COUNT, List.of(Aspect.FIRE, Aspect.EARTH)),
            new TerrainRule(state -> state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE), VERY_LARGE_BLOCK_COUNT, List.of(Aspect.EARTH)),
            new TerrainRule(state -> state.is(BlockTags.LEAVES), LARGE_BLOCK_COUNT, List.of(Aspect.PLANT)));

    private NodeGenerator() {}

    /**
     * Roll random node data for a position. Returns null when the world cannot
     * support a node (no primals/compounds registered).
     */
    public static TileNode.NodeSnapshot rollRandomNodeData(ServerLevelAccessor level, BlockPos pos, RandomSource random,
                                                           boolean silverwood, boolean eerie, boolean small,
                                                           int specialRarity, int baseAura) {
        List<Aspect> primals = Aspect.getPrimalAspects();
        List<Aspect> compounds = Aspect.getCompoundAspects();
        if (primals.isEmpty() || compounds.isEmpty()) {
            return null;
        }
        float biomeStrength = baseAura * BiomeHandler.getAuraModifier(level.getBiome(pos));

        // ---- Type + modifier ----
        NodeType type;
        if (silverwood) {
            type = NodeType.PURE;
        } else if (eerie) {
            type = NodeType.DARK;
        } else {
            type = rollType(random, specialRarity);
        }
        NodeModifier modifier = random.nextInt(Math.max(1, specialRarity / MODIFIER_RARITY_DIVISOR)) == 0
                ? MODIFIERS[random.nextInt(MODIFIERS.length)] : null;

        // ---- Aura scaling (vis budget) ----
        if (type != NodeType.PURE && BiomeHandler.isTaintedBiome(level.getBiome(pos))) {
            biomeStrength *= TAINTED_BIOME_FACTOR;
            if (random.nextBoolean()) {
                type = NodeType.TAINTED;
                biomeStrength *= TAINTED_BIOME_FACTOR;
            }
        }
        if (silverwood || small) {
            biomeStrength /= SMALL_NODE_DIVISOR;
        }
        int aura = Math.max(MINIMUM_AURA, (int) biomeStrength);
        int floor = aura / 2;
        int budget = floor + random.nextInt(Math.max(1, aura - floor));

        // ---- Surrounding terrain tally ----
        int[] terrainCounts = new int[TERRAIN_RULES.size()];
        BlockPos min = pos.offset(-SURROUNDING_RADIUS, -SURROUNDING_RADIUS, -SURROUNDING_RADIUS);
        BlockPos max = pos.offset(SURROUNDING_RADIUS, SURROUNDING_RADIUS, SURROUNDING_RADIUS);
        for (int x = min.getX(); x <= max.getX(); x += 2) {
            for (int y = min.getY(); y <= max.getY(); y += 2) {
                for (int z = min.getZ(); z <= max.getZ(); z += 2) {
                    BlockPos cell = new BlockPos(x, y, z);
                    if (!level.hasChunk(SectionPos.blockToSectionCoord(cell.getX()), SectionPos.blockToSectionCoord(cell.getZ()))) {
                        continue;
                    }
                    int ruleIndex = firstMatchingRule(level.getBlockState(cell));
                    if (ruleIndex >= 0) {
                        terrainCounts[ruleIndex]++;
                    }
                }
            }
        }

        // ---- Aspect sourcing ----
        Map<Aspect, Integer> tally = new LinkedHashMap<>();
        Aspect biomeAspect = BiomeHandler.getBiomeAspect(level.getBiome(pos));
        if (biomeAspect == null) {
            tally.merge(primals.get(random.nextInt(primals.size())), 1, Integer::sum);
            tally.merge(compounds.get(random.nextInt(compounds.size())), 1, Integer::sum);
        } else {
            tally.merge(biomeAspect, 1, Integer::sum);
            tally.merge(biomeAspect, 1, Integer::sum);
        }
        BonusDraw bonus = drawBonus(random, specialRarity);
        for (int i = 0; i < bonus.primals; i++) {
            tally.merge(primals.get(random.nextInt(primals.size())), 1, Integer::sum);
        }
        for (int i = 0; i < bonus.compounds; i++) {
            tally.merge(compounds.get(random.nextInt(compounds.size())), 1, Integer::sum);
        }
        typeFlavour(type, tally, random);

        // ---- Terrain flavour ----
        for (int i = 0; i < TERRAIN_RULES.size(); i++) {
            TerrainRule rule = TERRAIN_RULES.get(i);
            if (terrainCounts[i] > rule.threshold()) {
                for (Aspect aspect : rule.aspects()) {
                    tally.merge(aspect, 1, Integer::sum);
                }
            }
        }

        // ---- Budget allocation ----
        NodeRules.allocateBudget(tally, budget, random);

        AspectList aspects = new AspectList();
        for (Map.Entry<Aspect, Integer> entry : tally.entrySet()) {
            if (entry.getValue() > 0) {
                aspects.add(entry.getKey(), entry.getValue());
            }
        }
        return new TileNode.NodeSnapshot(type, modifier, aspects, aspects.copy());
    }

    /** Place a node block at pos and apply the rolled data to its BE. */
    public static boolean createNodeAt(ServerLevelAccessor level, BlockPos pos, TileNode.NodeSnapshot data) {
        BlockState current = level.getBlockState(pos);
        boolean free = current.isAir() || current.canBeReplaced() || current.is(BlockTags.LEAVES);
        if (!free || !level.setBlock(pos, BlockNode.get().defaultBlockState(), Block.UPDATE_ALL)) {
            return false;
        }
        if (level.getBlockEntity(pos) instanceof TileNode node) {
            node.applyNodeData(data);
            node.setChanged();
            return true;
        }
        return false;
    }

    private static NodeType rollType(RandomSource random, int specialRarity) {
        double scale = (double) DEFAULT_SPECIAL_RARITY / Math.max(1, specialRarity);
        double dark = 1.5 * scale;
        double unstable = 1.0 * scale;
        double pure = 1.0 * scale;
        double tainted = 2.0 * scale;
        double hungry = 0.5 * scale;
        double roll = random.nextDouble() * Math.max(PERCENT_TOTAL, dark + unstable + pure + tainted + hungry);
        if (roll < dark) return NodeType.DARK;
        roll -= dark;
        if (roll < unstable) return NodeType.UNSTABLE;
        roll -= unstable;
        if (roll < pure) return NodeType.PURE;
        roll -= pure;
        if (roll < tainted) return NodeType.TAINTED;
        roll -= tainted;
        return roll < hungry ? NodeType.HUNGRY : NodeType.NORMAL;
    }

    private record BonusDraw(int primals, int compounds) {}

    /** Binomial-ish draw of up to 3 bonus aspect picks (1/18 weighted compounds). */
    private static BonusDraw drawBonus(RandomSource random, int specialRarity) {
        double none = BONUS_NONE_WEIGHT;
        double compound = BONUS_NONE_WEIGHT / Math.max(1, specialRarity);
        double primal = BONUS_NONE_WEIGHT - compound;
        double roll = random.nextDouble();
        BonusDraw last = new BonusDraw(0, 0);
        for (int compounds = 0; compounds <= BONUS_ASPECT_ROLLS; compounds++) {
            for (int primals = 0; primals + compounds <= BONUS_ASPECT_ROLLS; primals++) {
                int empty = BONUS_ASPECT_ROLLS - primals - compounds;
                double arrangements = FACTORIALS[BONUS_ASPECT_ROLLS] / (FACTORIALS[primals] * FACTORIALS[compounds] * FACTORIALS[empty]);
                double weight = arrangements * Math.pow(primal, primals) * Math.pow(compound, compounds) * Math.pow(none, empty);
                last = new BonusDraw(primals, compounds);
                if (roll < weight) {
                    return last;
                }
                roll -= weight;
            }
        }
        return last;
    }

    private static void typeFlavour(NodeType type, Map<Aspect, Integer> tally, RandomSource random) {
        switch (type) {
            case HUNGRY -> {
                tally.merge(Aspect.LIFE, 2, Integer::sum);
                if (random.nextBoolean()) {
                    tally.merge(Aspect.VOID, 1, Integer::sum);
                }
            }
            case PURE -> tally.merge(random.nextBoolean() ? Aspect.ORDER : Aspect.LIFE, 2, Integer::sum);
            case DARK -> {
                for (Aspect aspect : DARK_ASPECTS) {
                    if (random.nextBoolean()) {
                        tally.merge(aspect, 1, Integer::sum);
                    }
                }
            }
            default -> {}
        }
    }

    private static int firstMatchingRule(BlockState block) {
        for (int i = 0; i < TERRAIN_RULES.size(); i++) {
            if (TERRAIN_RULES.get(i).matcher().test(block)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Convenience for tests: roll node data against a live server level.
     */
    public static TileNode.NodeSnapshot rollForTest(ServerLevel level, BlockPos pos, RandomSource random, int specialRarity, int baseAura) {
        return rollRandomNodeData(level, pos, random, false, false, false, specialRarity, baseAura);
    }
}
