package thaumcraft.common.blocks.world.node;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.casters.ICaster;
import thaumcraft.common.tiles.node.TileJarNode;
import thaumcraft.common.tiles.node.TileNode;
import thaumcraft.init.ModBlockEntities;
import thaumcraft.init.ModBlocks;

/**
 * Node jar block — a dormant captured node (TC6 node system).
 * Right-click with a caster gauntlet to release the node back into the world.
 */
public class BlockJarNode extends Block implements EntityBlock {

    public static final VoxelShape SHAPE = Block.box(4.0, 2.0, 4.0, 12.0, 14.0, 12.0);

    public BlockJarNode() {
        super(thaumcraft.init.BlockRegistration.id(BlockBehaviour.Properties.of()
                .mapColor(net.minecraft.world.level.material.MapColor.STONE)
                .strength(0.5F)
                .sound(SoundType.GLASS)));
    }

    public static Block get() {
        return ModBlocks.NODE_JAR.get();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                       net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(stack.getItem() instanceof ICaster)) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.FAIL;
        }
        if (!(serverLevel.getBlockEntity(pos) instanceof TileJarNode jar)) {
            return InteractionResult.FAIL;
        }
        serverLevel.setBlock(pos, BlockNode.get().defaultBlockState(), Block.UPDATE_ALL);
        if (serverLevel.getBlockEntity(pos) instanceof TileNode node) {
            node.applyNodeData(jar.snapshot());
            node.setChanged();
            serverLevel.sendBlockUpdated(pos, node.getBlockState(), node.getBlockState(), Block.UPDATE_ALL);
        }
        serverLevel.levelEvent(2001, pos, Block.getId(state));
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileJarNode(ModBlockEntities.NODE_JAR.get(), pos, state);
    }
}
