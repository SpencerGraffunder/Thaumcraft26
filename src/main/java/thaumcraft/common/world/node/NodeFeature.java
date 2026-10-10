package thaumcraft.common.world.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import thaumcraft.common.tiles.node.TileNode;

/**
 * Aura node world generation feature (TC6 node system, Thaumaturge parity).
 *
 * <p>Data JSON parameters: {@code silverwood} (pure nodes, silverwood areas),
 * {@code eerie} (dark nodes), {@code small} (reduced budget), {@code special_rarity}
 * (modifier/type rarity divisor, default 18) and {@code base_aura} (budget scale,
 * default 100).
 */
public class NodeFeature implements Feature {

    public static final MapCodec<NodeFeature> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.BOOL.optionalFieldOf("silverwood", false).forGetter(f -> f.silverwood),
                    Codec.BOOL.optionalFieldOf("eerie", false).forGetter(f -> f.eerie),
                    Codec.BOOL.optionalFieldOf("small", false).forGetter(f -> f.small),
                    Codec.intRange(1, 1000).optionalFieldOf("special_rarity", NodeGenerator.DEFAULT_SPECIAL_RARITY).forGetter(f -> f.specialRarity),
                    Codec.intRange(1, 500).optionalFieldOf("base_aura", NodeGenerator.DEFAULT_BASE_AURA).forGetter(f -> f.baseAura))
            .apply(instance, NodeFeature::new));

    private static final int MAXIMUM_RISE = 3;

    private final boolean silverwood;
    private final boolean eerie;
    private final boolean small;
    private final int specialRarity;
    private final int baseAura;

    public NodeFeature(boolean silverwood, boolean eerie, boolean small, int specialRarity, int baseAura) {
        this.silverwood = silverwood;
        this.eerie = eerie;
        this.small = small;
        this.specialRarity = specialRarity;
        this.baseAura = baseAura;
    }

    @Override
    public MapCodec<? extends Feature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        BlockPos start = level.getBlockState(origin.above()).isAir() ? origin.above() : origin;
        BlockPos raised = start.above(random.nextInt(MAXIMUM_RISE + 1));
        BlockState raisedState = level.getBlockState(raised);
        BlockPos target = raisedState.isAir() || raisedState.canBeReplaced() ? raised : start;
        if (target.getY() >= level.dimensionType().minY() + level.dimensionType().height()) {
            return false;
        }
        TileNode.NodeSnapshot data = NodeGenerator.rollRandomNodeData(
                level, target, random, silverwood, eerie, small, specialRarity, baseAura);
        if (data == null) {
            return false;
        }
        return NodeGenerator.createNodeAt(level, target, data);
    }
}
