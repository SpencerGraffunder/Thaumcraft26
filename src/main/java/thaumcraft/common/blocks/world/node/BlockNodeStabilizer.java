package thaumcraft.common.blocks.world.node;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.init.ModBlocks;

/**
 * Node stabilizer — a stone pedestal placed below a node. While present (and
 * unpowered), it locks the node: basic stabilizers block node discharge and
 * slow unstable reversion; advanced stabilizers (thaumium) recover fading
 * nodes. Redstone power disables the lock.
 */
public class BlockNodeStabilizer extends Block {

    private final boolean advanced;

    public BlockNodeStabilizer(boolean advanced) {
        super(thaumcraft.init.BlockRegistration.id(BlockBehaviour.Properties.of()
                .mapColor(net.minecraft.world.level.material.MapColor.STONE)
                .strength(2.0F, 3.0F)
                .sound(SoundType.STONE)));
        this.advanced = advanced;
    }

    public static Block get() {
        return ModBlocks.NODE_STABILIZER.get();
    }

    public static Block getAdvanced() {
        return ModBlocks.NODE_STABILIZER_ADVANCED.get();
    }

    public boolean isAdvanced() {
        return advanced;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Block.box(0.0, 0.0, 0.0, 16.0, 8.0, 16.0);
    }
}
