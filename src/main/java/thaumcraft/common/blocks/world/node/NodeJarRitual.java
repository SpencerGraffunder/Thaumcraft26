package thaumcraft.common.blocks.world.node;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import thaumcraft.common.lib.capabilities.ThaumcraftCapabilities;
import thaumcraft.common.tiles.node.TileNode;
import thaumcraft.common.world.node.NodeModifier;
import thaumcraft.common.world.node.NodeRules;

/**
 * Node jarring ritual (TC6 node system, Thaumaturge parity).
 *
 * <p>Right-clicking a node with a node jar (requires the NODEJARS research)
 * starts a short settling countdown; when it finishes the node is captured
 * into a dormant node-jar block (75% chance its quality degrades one step).
 * Right-clicking the jar block with a caster gauntlet releases the node.
 */
public final class NodeJarRitual {

    public static final String RESEARCH_NODE_JAR = "NODEJARS";

    private static final int JAR_SETTLE_TICKS = 40;
    private static final float MODIFIER_DEGRADE_CHANCE = 0.75F;

    private NodeJarRitual() {}

    public static boolean tryJarNode(ServerLevel level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof TileNode node)) {
            return false;
        }
        if (node.isEnergized() || node.isJarring()) {
            return false;
        }
        if (!ThaumcraftCapabilities.isResearchComplete(player, RESEARCH_NODE_JAR)) {
            return false;
        }
        node.beginJarring(JAR_SETTLE_TICKS);
        level.playSound(null, pos, thaumcraft.init.ModSounds.JAR.get(),
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    /** Quality degradation applied when a node is captured. */
    public static NodeModifier degradeOnCapture(NodeModifier trait, float roll) {
        return roll < MODIFIER_DEGRADE_CHANCE ? NodeRules.degrade(trait) : trait;
    }
}
