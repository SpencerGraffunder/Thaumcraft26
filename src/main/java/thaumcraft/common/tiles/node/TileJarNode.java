package thaumcraft.common.tiles.node;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dormant node captured inside a node jar block. Inactive until released
 * with a caster gauntlet (see {@link thaumcraft.common.blocks.world.node.BlockJarNode}).
 */
public class TileJarNode extends TileNode {

    public TileJarNode(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public TileJarNode(BlockPos pos, BlockState state) {
        this(thaumcraft.init.ModBlockEntities.NODE_JAR.get(), pos, state);
    }

    @Override
    public void serverTick(Level tickLevel, BlockPos pos) {
        // Dormant: no upkeep while jarred.
    }
}
