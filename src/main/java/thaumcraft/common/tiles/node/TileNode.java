package thaumcraft.common.tiles.node;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.blocks.world.node.BlockJarNode;
import thaumcraft.common.blocks.world.node.BlockNode;
import thaumcraft.common.blocks.world.node.BlockNodeStabilizer;
import thaumcraft.common.blocks.world.node.NodeJarRitual;
import thaumcraft.common.tiles.TileThaumcraft;
import thaumcraft.common.world.aura.AuraHandler;
import thaumcraft.common.world.node.NodeHunger;
import thaumcraft.common.world.node.NodeModifier;
import thaumcraft.common.world.node.NodeRules;
import thaumcraft.common.world.node.NodeType;
import net.minecraft.world.entity.Entity;
import thaumcraft.init.ModSounds;

import java.util.ArrayList;
import java.util.List;

/**
 * Aura node block entity (TC6 node system, Thaumaturge parity).
 *
 * <p>A node holds up to {@link #aspectsBase} points of vis per aspect and slowly
 * refills them from the local aura (flux for tainted nodes). Refill failures
 * degrade the node's quality; fully drained aspects decay; nodes can discharge
 * excess vis to poorer neighbours; hungry nodes devour their surroundings.
 * A stabilizer block below locks the node (slower instability, fading recovery).
 * A transducer on top can energize the node into an infinite (tappable) source.
 */
public class TileNode extends TileThaumcraft {

    public static final int LOCK_NONE = 0;
    public static final int LOCK_BASIC = 1;
    public static final int LOCK_ADVANCED = 2;

    protected NodeType type = NodeType.NORMAL;
    protected NodeModifier modifier;
    protected AspectList held = new AspectList();
    protected AspectList aspectsBase = new AspectList();
    protected AspectList aspectsBaseOriginal;

    protected int tickCounter = 0;
    protected int starvation = 0;
    protected int refillWait = 0;
    protected int refillInterval = -1;
    protected int lock = LOCK_NONE;
    protected int jarring = 0;
    protected boolean energized = false;
    protected boolean naturalTaintBootstrap = false;
    private transient List<Entity> hungerPulled = new ArrayList<>();

    public TileNode(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public TileNode(BlockPos pos, BlockState state) {
        this(thaumcraft.init.ModBlockEntities.NODE.get(), pos, state);
    }

    // ==================== Data ====================

    public NodeType kind() {
        return type;
    }

    public NodeModifier trait() {
        return modifier;
    }

    public void reclassify(NodeType nodeType) {
        this.type = nodeType;
        setChanged();
    }

    public void assignTrait(NodeModifier nodeModifier) {
        this.modifier = nodeModifier;
        this.refillInterval = -1;
        setChanged();
    }

    public AspectList getAspects() {
        return held;
    }

    public AspectList capacity() {
        return aspectsBase;
    }

    /** Full node data (type + modifier + held + base), e.g. for jars. */
    public NodeSnapshot snapshot() {
        return new NodeSnapshot(type, modifier, copy(held), copy(aspectsBase));
    }

    public void applyNodeData(NodeSnapshot data) {
        this.type = data.type();
        this.modifier = data.modifier();
        this.held = copy(data.held());
        this.aspectsBase = copy(data.base());
        this.refillInterval = -1;
        setChanged();
    }

    private static AspectList copy(AspectList list) {
        return list == null ? new AspectList() : list.copy();
    }

    /**
     * Primordial pearl effect (Thaumaturge parity): mutates the node's held
     * and base aspects and rolls a quality change. The node is NOT removed —
     * the pearl's explosion in BlockNode handles the visual side.
     */
    public void applyPrimordialPearl(net.minecraft.util.RandomSource random, boolean researched) {
        List<Aspect> primals = List.of(
                Aspect.AIR, Aspect.EARTH, Aspect.FIRE, Aspect.WATER, Aspect.ORDER, Aspect.ENTROPY);
        NodeRules.pearl(held, aspectsBase, primals, random, researched);
        this.modifier = NodeRules.pearlModifier(this.modifier, random);
        this.refillInterval = -1;
        setChanged();
    }

    public boolean isEnergized() {
        return energized;
    }

    public void setEnergized(boolean value) {
        if (value == energized) return;
        energized = value;
        if (value) {
            aspectsBaseOriginal = aspectsBase;
            aspectsBase = NodeRules.toPrimals(aspectsBase);
            held = copy(aspectsBase);
        } else if (aspectsBaseOriginal != null) {
            aspectsBase = aspectsBaseOriginal;
            aspectsBaseOriginal = null;
        }
        setChanged();
    }

    public void clearContained() {
        held = new AspectList();
        refillInterval = -1;
    }

    public void invalidateRefill() {
        refillInterval = -1;
        setChanged();
    }

    public int refillInterval() {
        if (refillInterval < 0) {
            refillInterval = NodeRules.baseRefillInterval(modifier);
        }
        return refillInterval;
    }

    public boolean isJarring() {
        return jarring > 0;
    }

    public void beginJarring(int ticks) {
        jarring = ticks;
        setChanged();
    }

    public boolean isJarlable() {
        return !isEnergized() && jarring <= 0;
    }

    public int averageContent() {
        return (held.visSize() + aspectsBase.visSize()) / 2;
    }

    // ==================== Ticking ====================

    public void serverTick(Level tickLevel, BlockPos pos) {
        if (!(tickLevel instanceof ServerLevel serverLevel)) return;
        if (jarring > 0) {
            tickJarring(serverLevel, pos);
            return;
        }
        if (energized) {
            return;
        }
        tickCounter++;
        RandomSource random = serverLevel.getRandom();
        refreshLock(pos);
        upkeepRefill(this, serverLevel, pos, random);
        upkeepDischarge(this, serverLevel, pos, random);
        if (upkeepDecay(this, serverLevel, pos, random)) {
            return;
        }
        upkeepStability(this, serverLevel, pos, random);
        tickTypeBehavior(this, serverLevel, pos, tickCounter, random);
    }

    private void tickJarring(ServerLevel level, BlockPos pos) {
        if (--jarring == 0) {
            // Captured into a dormant node jar block (quality may degrade).
            TileNode.NodeSnapshot data = snapshot();
            NodeModifier degraded = NodeJarRitual.degradeOnCapture(data.modifier(), level.getRandom().nextFloat());
            level.setBlock(pos, BlockJarNode.get().defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof TileJarNode jar) {
                jar.applyNodeData(new TileNode.NodeSnapshot(data.type(), degraded, data.held(), data.base()));
                jar.setChanged();
            }
        }
    }

    /** Re-read the stabilizer below every 50 ticks. */
    private void refreshLock(BlockPos pos) {
        if (tickCounter % 50 != 0) return;
        int newLock = LOCK_NONE;
        if (level.getBlockState(pos.below()).is(BlockNodeStabilizer.get())) {
            if (!level.hasNeighborSignal(pos.below())) {
                newLock = level.getBlockState(pos.below()).is(BlockNodeStabilizer.getAdvanced()) ? LOCK_ADVANCED : LOCK_BASIC;
            }
        }
        if (newLock != lock) {
            lock = newLock;
            setChanged();
        }
    }

    /**
     * Refill one point of a below-capacity aspect from the aura (3.0 raw vis,
     * or raw flux for tainted nodes). 10 consecutive failures degrade quality.
     */
    static void upkeepRefill(TileNode node, ServerLevel level, BlockPos pos, RandomSource random) {
        if (node.refillWait > 0) node.refillWait--;
        int interval = node.refillInterval();
        if (interval <= 0 || node.tickCounter % interval != 0 || node.refillWait > 0) return;

        driftWithChunk(node, level, pos, random);

        List<Aspect> eligible = new ArrayList<>();
        for (Aspect aspect : node.held.getAspects()) {
            if (node.held.getAmount(aspect) < node.aspectsBase.getAmount(aspect)) {
                eligible.add(aspect);
            }
        }
        if (eligible.isEmpty()) {
            // Nothing below capacity: also try to grow held for aspects present in base.
            for (Aspect aspect : node.aspectsBase.getAspects()) {
                if (node.held.getAmount(aspect) < node.aspectsBase.getAmount(aspect)) {
                    eligible.add(aspect);
                }
            }
        }
        if (eligible.isEmpty()) {
            node.starvation = 0;
            return;
        }
        Aspect chosen = eligible.get(random.nextInt(eligible.size()));
        boolean flux = node.kind() == NodeType.TAINTED;
        float taken = flux ? AuraHandler.drainFlux(level, pos, NodeRules.RAW_PER_POINT, false)
                : AuraHandler.drainVis(level, pos, NodeRules.RAW_PER_POINT, false);
        if (taken >= NodeRules.REFILL_SUCCESS_THRESHOLD) {
            node.held.add(chosen, 1);
            node.starvation = 0;
            node.setChanged();
            return;
        }
        if (taken > 0.0F) {
            if (flux) AuraHandler.addFlux(level, pos, taken);
            else AuraHandler.addVis(level, pos, taken);
        }
        if (++node.starvation >= NodeRules.DEGRADE_AFTER_FAILURES) {
            node.starvation = 0;
            degrade(node);
        }
    }

    private static void degrade(TileNode node) {
        if (node.trait() == NodeModifier.FADING) {
            if (node.kind() != NodeType.HUNGRY) {
                node.reclassify(NodeType.HUNGRY);
            }
        } else {
            node.assignTrait(NodeRules.degrade(node.trait()));
        }
        node.invalidateRefill();
    }

    /** Tainted drift (flux) and pure-biome healing. */
    private static void driftWithChunk(TileNode node, ServerLevel level, BlockPos pos, RandomSource random) {
        int auraBase = AuraHandler.getAuraBase(level, pos);
        if (auraBase <= 0) return;
        float flux = AuraHandler.getFlux(level, pos);
        NodeType type = node.kind();
        if (type != NodeType.TAINTED && type != NodeType.PURE && flux > 0.5F * auraBase && random.nextInt(20) == 0) {
            node.reclassify(NodeType.TAINTED);
            node.invalidateRefill();
        }
    }

    /**
     * Transfer one point of vis to a poorer node within a ±4 cube.
     * Fading nodes and basic-locked nodes do not discharge.
     */
    static void upkeepDischarge(TileNode node, ServerLevel level, BlockPos pos, RandomSource random) {
        NodeModifier modifier = node.trait();
        if (modifier == NodeModifier.FADING || node.lock == LOCK_BASIC) return;
        int interval = NodeRules.DISCHARGE_INTERVAL_DEFAULT;
        if (modifier == NodeModifier.BRIGHT || (node.kind() == NodeType.HUNGRY && modifier != null)) {
            interval = NodeRules.DISCHARGE_INTERVAL_FAST;
        } else if (modifier == NodeModifier.PALE) {
            interval = NodeRules.DISCHARGE_INTERVAL_PALE;
        }
        if (node.tickCounter % interval != 0) return;
        if (modifier == NodeModifier.PALE && random.nextBoolean()) return;

        BlockPos partnerPos = pos.offset(dischargeOffset(random), dischargeOffset(random), dischargeOffset(random));
        if (partnerPos.equals(pos) || !level.hasChunkAt(partnerPos)) return;
        if (!(level.getBlockEntity(partnerPos) instanceof TileNode donor)) return;
        if (donor == node) return;
        if (donor.lock != LOCK_NONE || donor.held.visSize() == 0 || donor.isEnergized()) return;
        if (donor.averageContent() >= node.averageContent()) return;

        transfer(node, donor, random);
        if (donor.held.visSize() == 0 && !donor.isEnergized()) {
            donor.removeDepleted(level, donor.worldPosition);
        } else {
            donor.refillWait = donor.refillInterval() / 2;
            donor.setChanged();
        }
        node.invalidateRefill();
    }

    private static int dischargeOffset(RandomSource random) {
        return random.nextInt(NodeRules.DISCHARGE_RANGE + 1) - random.nextInt(NodeRules.DISCHARGE_RANGE + 1);
    }

    private static void transfer(TileNode receiver, TileNode donor, RandomSource random) {
        Aspect[] offered = donor.held.getAspects();
        if (offered.length == 0) return;
        Aspect aspect = offered[random.nextInt(offered.length)];
        boolean fits = receiver.held.getAmount(aspect) < receiver.aspectsBase.getAmount(aspect);
        donor.held.remove(aspect, 1);
        if (fits) {
            receiver.held.add(aspect, 1);
            return;
        }
        // Capacity full: chance to grow the receiver's base instead.
        boolean elevated = receiver.kind() == NodeType.HUNGRY || receiver.trait() == NodeModifier.BRIGHT;
        int base = receiver.aspectsBase.getAmount(aspect);
        int divisor = elevated ? 1 + (int) (base / NodeRules.ELEVATED_DIVISOR) : 1 + base;
        if (random.nextInt(divisor) != 0) return;
        receiver.aspectsBase.add(aspect, 1);
        if (receiver.trait() == NodeModifier.PALE && random.nextInt(NodeRules.PALE_RECOVERY_ODDS) == 0) {
            receiver.assignTrait(null);
        }
        if (random.nextInt(NodeRules.DONOR_BASE_LOSS_ODDS) == 0) {
            donor.aspectsBase.remove(aspect, 1);
        }
    }

    /**
     * Decay: every 1200 ticks a fully drained aspect loses one base point
     * (or the whole aspect with 1/20 odds). Returns true if the node vanished.
     */
    static boolean upkeepDecay(TileNode node, ServerLevel level, BlockPos pos, RandomSource random) {
        if (node.tickCounter % NodeRules.DECAY_INTERVAL != 0) return false;
        for (Aspect aspect : node.aspectsBase.getAspects()) {
            if (node.held.getAmount(aspect) > 0) continue;
            decayAspect(node, aspect, random);
            node.invalidateRefill();
            break;
        }
        if (node.aspectsBase.visSize() == 0) {
            node.removeDepleted(level, pos);
            return true;
        }
        return false;
    }

    private static void decayAspect(TileNode node, Aspect aspect, RandomSource random) {
        AspectList reduced = node.aspectsBase.remove(aspect, 1);
        if (reduced.getAmount(aspect) > 0 && random.nextInt(NodeRules.DECAY_REMOVAL_ODDS) != 0) {
            node.aspectsBase = reduced;
            return;
        }
        node.aspectsBase = reduced.remove(aspect);
        node.held = node.held.remove(aspect);
        NodeModifier modifier = node.trait();
        if (random.nextInt(NodeRules.DECAY_MODIFIER_ODDS) == 0) {
            if (modifier == NodeModifier.BRIGHT) modifier = null;
            else if (modifier == null) modifier = NodeModifier.PALE;
        }
        if (modifier == NodeModifier.PALE && random.nextInt(NodeRules.DECAY_FADE_ODDS) == 0) {
            modifier = NodeModifier.FADING;
        }
        node.assignTrait(modifier);
    }

    /**
     * Stability: unstable nodes revert to normal (locked nodes survive longer);
     * fading nodes recover to pale while stabilized.
     */
    static void upkeepStability(TileNode node, ServerLevel level, BlockPos pos, RandomSource random) {
        if (node.tickCounter % NodeRules.STABILITY_INTERVAL != 0) return;
        int lock = node.lock;
        if (node.kind() == NodeType.UNSTABLE) {
            if (lock == LOCK_NONE) {
                if (random.nextBoolean()) {
                    releaseOrb(node, level, pos, random);
                }
            } else if (random.nextBoolean()
                    && random.nextInt(lock == LOCK_ADVANCED ? NodeRules.UNSTABLE_ADVANCED_ODDS : NodeRules.UNSTABLE_BASIC_ODDS) == 0) {
                node.reclassify(NodeType.NORMAL);
                node.invalidateRefill();
            }
        }
        if (node.trait() == NodeModifier.FADING && lock != LOCK_NONE
                && random.nextInt(lock == LOCK_ADVANCED ? NodeRules.FADING_ADVANCED_ODDS : NodeRules.FADING_BASIC_ODDS) == 0) {
            node.assignTrait(NodeModifier.PALE);
            node.invalidateRefill();
        }
    }

    private static void releaseOrb(TileNode node, Level level, BlockPos pos, RandomSource random) {
        List<Aspect> primals = new ArrayList<>();
        for (Aspect aspect : node.held.getAspects()) {
            if (aspect.isPrimal()) primals.add(aspect);
        }
        if (primals.isEmpty()) return;
        Aspect chosen = primals.get(random.nextInt(primals.size()));
        node.held.remove(chosen, 1);
        for (int i = 0; i < 3; i++) {
            level.addParticle(net.minecraft.core.particles.ParticleTypes.PORTAL,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0.1, 0.1, 0.1);
        }
        node.invalidateRefill();
    }

    /** Per-type behavior: tainted pollution, pure erosion, hungry devouring. */
    private void tickTypeBehavior(TileNode node, ServerLevel level, BlockPos pos, int counter, RandomSource random) {
        switch (node.kind()) {
            case HUNGRY -> {
                NodeHunger.tick(node, level, pos, counter, node.hungerPulled);
                if (NodeHunger.eatDue(counter)) {
                    NodeHunger.eatBlock(node, level, pos, random);
                }
            }
            case TAINTED -> {
                if (random.nextBoolean()) {
                    int taintOffset = random.nextInt(9) - 4;
                    thaumcraft.common.blocks.world.taint.TaintHelper.spreadFibres(
                            level, pos.offset(taintOffset, random.nextInt(5) - 2, taintOffset), true);
                }
                if (counter % 200 == 0) {
                    float saturation = AuraHandler.getFlux(level, pos) / Math.max(1, AuraHandler.getAuraBase(level, pos));
                    if (random.nextFloat() > 0.8F * saturation) {
                        double root = Math.sqrt(Math.max(1.0, node.aspectsBase.visSize() / 3.0));
                        int steps = (int) Math.max(1.0, root);
                        AuraHandler.addFlux(level, pos, Math.max(1.0F, 0.2F * steps));
                    }
                }
            }
            case PURE -> {
                if (AuraHandler.drainFlux(level, pos, 0.25F, false) <= 0.0F) return;
                if (counter % 200 != 0 || random.nextFloat() < 0.025F) return;
                Aspect[] entries = node.aspectsBase.getAspects();
                if (entries.length == 0) return;
                Aspect eroded = entries[random.nextInt(entries.length)];
                node.aspectsBase.remove(eroded, 1);
                int cap = node.aspectsBase.getAmount(eroded);
                int heldAmount = node.held.getAmount(eroded);
                if (heldAmount > cap) {
                    node.held.remove(eroded, heldAmount - cap);
                }
                node.invalidateRefill();
                if (node.aspectsBase.visSize() == 0) {
                    node.removeDepleted(level, pos);
                }
            }
            default -> {}
        }
    }

    /** Remove the block when fully depleted (no drops — nodes are wild vis). */
    void removeDepleted(ServerLevel level, BlockPos pos) {
        level.removeBlock(pos, false);
        level.removeBlockEntity(pos);
    }

    /** Burst on break: no drops, one particle puff per aspect point (capped). */
    public void burstIntoParticles(ServerLevel level, BlockPos pos) {
        Aspect[] aspects = held.getAspects();
        int total = 0;
        for (Aspect aspect : aspects) total += held.getAmount(aspect);
        int count = Math.min(8, Math.max(1, total / 4));
        for (int i = 0; i < count; i++) {
            level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0.2, 0.2, 0.2);
        }
        if (aspects.length > 0) {
            level.playSound(null, pos, ModSounds.CRYSTAL.get(), SoundSource.BLOCKS, 0.5F, 1.0F);
        }
    }

    // ==================== NBT ====================

    @Override
    protected void writeSyncNBT(ValueOutput output) {
        super.writeSyncNBT(output);
        output.putString("Type", type.getSerializedName());
        output.putString("Modifier", modifier == null ? "" : modifier.getSerializedName());
        held.writeToNBT(output, "Held");
        aspectsBase.writeToNBT(output, "Base");
        output.putInt("TickCounter", tickCounter);
        output.putInt("Starvation", starvation);
        output.putInt("RefillWait", refillWait);
        output.putInt("Lock", lock);
        output.putInt("Jarring", jarring);
        output.putBoolean("Energized", energized);
        if (aspectsBaseOriginal != null) {
            aspectsBaseOriginal.writeToNBT(output, "BaseOriginal");
        }
    }

    @Override
    protected void readSyncNBT(ValueInput input) {
        super.readSyncNBT(input);
        type = NodeType.fromName(input.getString("Type").orElse("normal"));
        modifier = NodeModifier.fromName(input.getString("Modifier").orElse(""));
        held = new AspectList();
        held.readFromNBT(input, "Held");
        aspectsBase = new AspectList();
        aspectsBase.readFromNBT(input, "Base");
        tickCounter = input.getInt("TickCounter").orElse(0);
        starvation = input.getInt("Starvation").orElse(0);
        refillWait = input.getInt("RefillWait").orElse(0);
        lock = input.getInt("Lock").orElse(LOCK_NONE);
        jarring = input.getInt("Jarring").orElse(0);
        energized = input.getBooleanOr("Energized", false);
        aspectsBaseOriginal = null;
        if (input.keySet().contains("BaseOriginal")) {
            aspectsBaseOriginal = new AspectList();
            aspectsBaseOriginal.readFromNBT(input, "BaseOriginal");
        }
        refillInterval = -1;
    }

    /** Immutable node description (for jars and worldgen). */
    public record NodeSnapshot(NodeType type, NodeModifier modifier, AspectList held, AspectList base) {}
}
