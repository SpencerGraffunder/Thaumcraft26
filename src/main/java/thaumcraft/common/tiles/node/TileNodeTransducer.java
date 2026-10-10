package thaumcraft.common.tiles.node;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import thaumcraft.common.blocks.world.node.BlockNodeStabilizer;
import thaumcraft.common.world.aura.AuraHandler;
import thaumcraft.init.ModBlocks;

/**
 * Node transducer (TC6 node system, Thaumaturge parity).
 *
 * <p>Sits on top of a node. While redstone-powered (with a stabilizer two
 * blocks below the transducer), it charges the node up to 1000, at which point
 * the node becomes "energized": its aspects convert to primals and it no longer
 * refills, decays or discharges — it acts as an infinite tappable vis source.
 * If the stabilizer is removed while the node is energized the transducer
 * catastrophically overloads: explosion + flux + the node reverts.
 */
public class TileNodeTransducer extends TileNode {

    public static final int CHARGE_TARGET = 1000;
    public static final int REVERT_THRESHOLD = 50;

    public static final int STATUS_IDLE = 0;
    public static final int STATUS_NODE = 1;
    public static final int STATUS_ENERGIZED = 2;

    private static final int CHARGE_RATE = 10;
    private static final int DRAIN_RATE = 2;
    private static final int STATUS_RECHECK_INTERVAL = 40;
    private static final int STABILIZER_DEPTH = 2;
    private static final float CATASTROPHE_FLUX = 32.0F;
    private static final float CATASTROPHE_POWER = 3.0F;
    private static final int LOST_STABILIZER_COUNT = 50;
    private static final int UNSET = -1;

    private int count = UNSET;
    private int status = STATUS_IDLE;
    private int syncedCount = Integer.MIN_VALUE;
    private int syncedStatus = Integer.MIN_VALUE;

    public TileNodeTransducer(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public TileNodeTransducer(BlockPos pos, BlockState state) {
        this(thaumcraft.init.ModBlockEntities.NODE_TRANSDUCER.get(), pos, state);
    }

    public int getCount() {
        return Math.max(0, count);
    }

    public int getStatus() {
        return status;
    }

    @Override
    public void serverTick(Level tickLevel, BlockPos pos) {
        if (!(tickLevel instanceof ServerLevel serverLevel)) return;
        BlockPos nodePos = pos.below();
        TileNode node = serverLevel.getBlockEntity(nodePos) instanceof TileNode found && !(found instanceof TileNodeTransducer) ? found : null;
        BlockPos stabilizerPos = pos.below(STABILIZER_DEPTH);
        BlockState stabilizerState = serverLevel.getBlockState(stabilizerPos);
        boolean stabilizerPresent = stabilizerState.is(BlockNodeStabilizer.get()) && !serverLevel.hasNeighborSignal(stabilizerPos);
        boolean powered = serverLevel.hasNeighborSignal(pos);
        long time = serverLevel.getGameTime();
        int previousCount = count;
        int previousStatus = status;
        boolean lostStabilizer = false;

        if (count == UNSET || time % STATUS_RECHECK_INTERVAL == 0) {
            if (node != null && node.isEnergized() && !stabilizerPresent) {
                catastrophe(serverLevel, nodePos, node);
                node = null;
                lostStabilizer = true;
                status = STATUS_IDLE;
                count = LOST_STABILIZER_COUNT;
            } else {
                followNode(node);
            }
        }

        if (!lostStabilizer) {
            charge(node, powered, stabilizerPresent, serverLevel.getRandom());
        }
        if (node != null && status == STATUS_NODE && count >= CHARGE_TARGET && !node.isEnergized()) {
            node.setEnergized(true);
            status = STATUS_ENERGIZED;
        }
        if (node != null && node.isEnergized() && count <= REVERT_THRESHOLD) {
            node.setEnergized(false);
            node.clearContained();
            status = STATUS_IDLE;
        }
        if (count != previousCount || status != previousStatus) {
            setChanged();
        }
        if (time % 10 == 0 && (count != syncedCount || status != syncedStatus)) {
            syncedCount = count;
            syncedStatus = status;
            BlockState state = getBlockState();
            serverLevel.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private void followNode(TileNode node) {
        if (node == null) {
            if (status != STATUS_IDLE || count != 0) {
                status = STATUS_IDLE;
                count = 0;
            }
            return;
        }
        status = node.isEnergized() ? STATUS_ENERGIZED : STATUS_NODE;
        if (count == UNSET) {
            count = node.isEnergized() ? CHARGE_TARGET : 0;
        }
    }

    private void charge(TileNode node, boolean powered, boolean stabilizerPresent, RandomSource random) {
        if (node == null || !stabilizerPresent) {
            if (count > REVERT_THRESHOLD) {
                count = Math.max(0, count - DRAIN_RATE);
            }
            return;
        }
        if (powered) {
            count += CHARGE_RATE + random.nextInt(CHARGE_RATE);
        } else if (!node.isEnergized()) {
            count = Math.max(0, count - DRAIN_RATE);
        }
    }

    private void catastrophe(ServerLevel level, BlockPos nodePos, TileNode node) {
        level.explode(null, nodePos.getX() + 0.5, nodePos.getY() + 1.5, nodePos.getZ() + 0.5,
                CATASTROPHE_POWER, Level.ExplosionInteraction.NONE);
        AuraHandler.addFlux(level, nodePos, CATASTROPHE_FLUX);
        node.setEnergized(false);
        node.clearContained();
        node.setChanged();
    }

    // ==================== NBT ====================

    @Override
    protected void writeSyncNBT(ValueOutput output) {
        super.writeSyncNBT(output);
        output.putInt("TransCount", count);
        output.putInt("TransStatus", status);
    }

    @Override
    protected void readSyncNBT(ValueInput input) {
        super.readSyncNBT(input);
        count = input.getInt("TransCount").orElse(UNSET);
        status = input.getInt("TransStatus").orElse(STATUS_IDLE);
        syncedCount = Integer.MIN_VALUE;
        syncedStatus = Integer.MIN_VALUE;
    }
}
