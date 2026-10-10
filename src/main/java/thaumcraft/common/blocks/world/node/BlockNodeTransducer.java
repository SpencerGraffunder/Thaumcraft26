package thaumcraft.common.blocks.world.node;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;
import thaumcraft.common.tiles.node.TileNodeTransducer;
import thaumcraft.init.ModBlockEntities;

/**
 * Node transducer — sits on top of a node and, while redstone-powered (with a
 * stabilizer below), charges the node into an infinite "energized" vis source.
 * See {@link TileNodeTransducer}.
 */
public class BlockNodeTransducer extends Block implements EntityBlock {

    public BlockNodeTransducer() {
        super(thaumcraft.init.BlockRegistration.id(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(1.5F)
                .sound(SoundType.METAL)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileNodeTransducer(ModBlockEntities.NODE_TRANSDUCER.get(), pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.NODE_TRANSDUCER.get()) {
            return null;
        }
        return (tickLevel, tickPos, tickState, tile) -> {
            if (tile instanceof TileNodeTransducer transducer) {
                transducer.serverTick(tickLevel, tickPos);
            }
        };
    }
}
