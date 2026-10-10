package thaumcraft.common.blocks.world.node;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.casters.ICaster;
import thaumcraft.common.blocks.world.node.NodeJarRitual;
import thaumcraft.common.items.tools.ItemNodeJar;
import thaumcraft.common.world.node.NodeWandTap;
import thaumcraft.common.tiles.node.TileNode;
import thaumcraft.init.ModBlockEntities;
import thaumcraft.init.ModBlocks;

/**
 * Aura node — a wild source of vis generated in the world (TC6 node system).
 *
 * <p>Right-click with a caster gauntlet to tap vis into the wand; right-click
 * with a node jar to capture the node. Breaking a node bursts its contents
 * into the air (no drops).
 */
public class BlockNode extends Block implements EntityBlock {

    /** Primordial pearl constants (Thaumaturge BlockNode parity). */
    private static final float PEARL_FLUX = 25.0F;
    private static final float PEARL_EXPLOSION_BASE = 3.0F;
    private static final float PEARL_EXPLOSION_SPREAD = 5.0F;
    private static final float PEARL_EXPLOSION_SPREAD_RESEARCHED = 3.0F;
    private static final double PEARL_EXPLOSION_LIFT = 1.5;
    private static final int PEARL_SPILLS = 33;
    private static final int PEARL_SPILL_SPREAD = 6;

    public static final VoxelShape SHAPE = Block.box(4.8F, 4.8F, 4.8F, 11.2F, 11.2F, 11.2F);

    public BlockNode() {
        super(thaumcraft.init.BlockRegistration.id(BlockBehaviour.Properties.of()
                .mapColor(net.minecraft.world.level.material.MapColor.STONE)
                .strength(3.0F)
                .sound(SoundType.AMETHYST)
                .lightLevel(state -> 7)
                .noOcclusion()));
    }

    public static Block get() {
        return ModBlocks.NODE.get();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof TileNode node) {
            // Bare hand: show what the node holds via action bar.
            String typeName = "node.thaumcraft.type." + node.snapshot().type().getSerializedName();
            player.sendSystemMessage(
                    net.minecraft.network.chat.Component.translatable("tooltip.thaumcraft.node.hold",
                            net.minecraft.network.chat.Component.translatable(typeName)));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.FAIL;
        }
        if (!(serverLevel.getBlockEntity(pos) instanceof TileNode node)) {
            return InteractionResult.PASS;
        }
        if (stack.getItem() instanceof ItemNodeJar) {
            return NodeJarRitual.tryJarNode(serverLevel, pos, player) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        if (stack.getItem() instanceof ICaster) {
            return NodeWandTap.tap(node, serverLevel, player, stack) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        if (stack.is(thaumcraft.init.ModItems.PRIMORDIAL_PEARL.get())) {
            return tryPrimordialPearl(stack, serverLevel, pos, player, node);
        }
        return InteractionResult.PASS;
    }

    /** Primordial pearl on a node: mutate it, pollute the aura, explode and spill flux goo. */
    private static InteractionResult tryPrimordialPearl(ItemStack stack, ServerLevel serverLevel, BlockPos pos,
                                                        Player player, TileNode node) {
        if (node.isEnergized()) {
            return InteractionResult.FAIL;
        }
        boolean researched = thaumcraft.common.lib.capabilities.ThaumcraftCapabilities
                .isResearchComplete(player, "NODEPEARLS");
        RandomSource random = serverLevel.getRandom();
        node.applyPrimordialPearl(random, researched);
        stack.shrink(1);
        thaumcraft.api.aura.AuraHelper.polluteAura(serverLevel, pos, PEARL_FLUX, true);
        float strength = PEARL_EXPLOSION_BASE + random.nextFloat() * (researched ? PEARL_EXPLOSION_SPREAD_RESEARCHED : PEARL_EXPLOSION_SPREAD);
        serverLevel.explode(null, pos.getX() + 0.5, pos.getY() + PEARL_EXPLOSION_LIFT, pos.getZ() + 0.5,
                strength, Level.ExplosionInteraction.BLOCK);
        for (int i = 0; i < PEARL_SPILLS; i++) {
            BlockPos target = pos.offset(spillOffset(random), spillOffset(random), spillOffset(random));
            if (target.getY() < pos.getY()) {
                // Goo falls; above the node we skip (no flux-gas block in this port).
                thaumcraft.common.world.aura.pressure.FluxGooUtil.placeGoo(serverLevel, target);
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static int spillOffset(RandomSource random) {
        return random.nextInt(PEARL_SPILL_SPREAD) - random.nextInt(PEARL_SPILL_SPREAD);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof TileNode node) {
            node.burstIntoParticles(serverLevel, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileNode(ModBlockEntities.NODE.get(), pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.NODE.get() && type != ModBlockEntities.NODE_JAR.get()) {
            return null;
        }
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, tickPos, tickState, tile) -> {
            if (tile instanceof TileNode node) {
                node.serverTick(tickLevel, tickPos);
            }
        };
    }
}
