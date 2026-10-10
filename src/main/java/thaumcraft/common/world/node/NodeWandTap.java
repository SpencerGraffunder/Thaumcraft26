package thaumcraft.common.world.node;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.items.casters.ItemCaster;
import thaumcraft.common.lib.capabilities.ThaumcraftCapabilities;
import thaumcraft.common.tiles.node.TileNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Node tapper (TC6 node system, Thaumaturge parity).
 *
 * <p>Right-clicking a node with a caster gauntlet taps one primal aspect from
 * the node into the wand's vis buffer. Base strength is 1 vis per tap; the
 * NODETAPPER1 and NODETAPPER2 researches add +1 each. Tapping an energized
 * node does not deplete it.
 */
public final class NodeWandTap {

    public static final String RESEARCH_TAPPER_ONE = "NODETAPPER1";
    public static final String RESEARCH_TAPPER_TWO = "NODETAPPER2";

    private NodeWandTap() {}

    public static boolean tap(TileNode node, ServerLevel level, Player player, ItemStack wand) {
        int strength = 1
                + (ThaumcraftCapabilities.isResearchComplete(player, RESEARCH_TAPPER_ONE) ? 1 : 0)
                + (ThaumcraftCapabilities.isResearchComplete(player, RESEARCH_TAPPER_TWO) ? 1 : 0);

        List<Aspect> tappable = new ArrayList<>();
        for (Aspect aspect : node.getAspects().getAspects()) {
            if (!aspect.isPrimal()) continue;
            if (node.getAspects().getAmount(aspect) < 1) continue;
            if (ItemCaster.bufferAmountOf(wand, aspect) >= ItemCaster.bufferCapacity(wand)) continue;
            tappable.add(aspect);
        }
        if (tappable.isEmpty()) {
            return false;
        }
        Aspect chosen = tappable.get(level.getRandom().nextInt(tappable.size()));
        int stock = node.getAspects().getAmount(chosen);
        int offered = Math.min(strength, stock);
        int before = ItemCaster.bufferAmountOf(wand, chosen);
        ItemCaster.topUpBuffer(wand, chosen, offered);
        int accepted = ItemCaster.bufferAmountOf(wand, chosen) - before;
        if (accepted <= 0) {
            return false;
        }
        if (!node.isEnergized()) {
            node.getAspects().remove(chosen, accepted);
            node.invalidateRefill();
        }
        node.setChanged();
        return true;
    }

    /** Total vis stored in a wand buffer (for tooltips). */
    public static int bufferTotal(ItemStack wand) {
        AspectList buffer = ItemCaster.getVisBuffer(wand);
        return buffer == null ? 0 : buffer.visSize();
    }
}
