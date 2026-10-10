package thaumcraft.common.world.node;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectHelper;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.tiles.node.TileNode;

import java.util.List;

/**
 * Hungry node behavior (TC6 node system, Thaumaturge parity).
 *
 * <p>Hungry nodes pull nearby entities toward them and devour anything that
 * gets close (converting the victim to one point of a primal aspect, growing
 * the node's base when full). They also periodically eat a nearby soft block.
 * Reach scales with quality: bright 6, none 5, pale 3, fading 2.
 */
public final class NodeHunger {

    private static final int PULL_REFRESH_INTERVAL = 10;
    private static final double GLOBAL_PULL_RANGE = 15.0;
    private static final double ITEM_RANGE_PADDING = 0.5;
    private static final double HORIZONTAL_PULL = 0.15;
    private static final double VERTICAL_PULL = 0.25;
    private static final double MINIMUM_PULL_DISTANCE = 1.0E-4;
    private static final double DEVOUR_DISTANCE = 1.4;
    private static final float DEVOUR_DAMAGE = 1.0F;
    private static final int EAT_INTERVAL = 20;
    private static final float BASE_GROWTH_FACTOR = 2.0F;
    private static final double BLOCK_HARDNESS_CAP = 1.0;

    private NodeHunger() {}

    /** Eat reach in blocks, scaled by quality modifier. */
    public static int eatRange(TileNode node) {
        NodeModifier modifier = node.trait();
        if (modifier == NodeModifier.BRIGHT) return 6;
        if (modifier == NodeModifier.FADING) return 2;
        if (modifier == NodeModifier.PALE) return 3;
        return 5;
    }

    public static boolean eatDue(int counter) {
        return counter % EAT_INTERVAL == 0;
    }

    /**
     * Pull entities toward the node and devour whatever reaches it.
     * {@code pulled} is the cached entity list, refreshed every 10 ticks.
     */
    public static void tick(TileNode node, ServerLevel level, BlockPos pos, int counter, List<Entity> pulled) {
        double itemRange = eatRange(node) + ITEM_RANGE_PADDING;
        if (counter % PULL_REFRESH_INTERVAL == 0) {
            pulled.clear();
            pulled.addAll(level.getEntities((Entity) null, new AABB(pos).inflate(Math.max(itemRange, GLOBAL_PULL_RANGE)), NodeHunger::isPullable));
        }
        pullAndDevour(node, level, pos, itemRange, pulled);
    }

    /** Eat a random soft block below the surface in reach. */
    public static void eatBlock(TileNode node, ServerLevel level, BlockPos pos, RandomSource random) {
        int range = eatRange(node);
        BlockPos candidate = pos.offset(spread(random, range), spread(random, range), spread(random, range));
        if (!level.hasChunkAt(candidate)) return;
        if (candidate.getY() >= level.getHeight(Heightmap.Types.WORLD_SURFACE, candidate.getX(), candidate.getZ())) return;
        BlockState state = level.getBlockState(candidate);
        float hardness = state.getDestroySpeed(level, candidate);
        if (state.isAir() || hardness < 0.0F || hardness >= BLOCK_HARDNESS_CAP) return;

        level.destroyBlock(candidate, false);
        AspectList primals = NodeRules.toPrimals(AspectHelper.getObjectAspects(new ItemStack(state.getBlock())));
        Aspect[] primalAspects = primals.getAspects();
        Aspect gained = primalAspects.length > 0
                ? primalAspects[random.nextInt(primalAspects.length)]
                : Aspect.getPrimalAspects().get(random.nextInt(Aspect.getPrimalAspects().size()));
        devour(node, gained, random);
    }

    /** Add one point of a primal, growing the base when the node is full. */
    private static void devour(TileNode node, Aspect primal, RandomSource random) {
        int base = node.capacity().getAmount(primal);
        if (node.getAspects().getAmount(primal) < base) {
            node.getAspects().add(primal, 1);
        } else if (random.nextFloat() < Math.min(1.0F, 1.0F / (1.0F + BASE_GROWTH_FACTOR * base))) {
            node.capacity().add(primal, 1);
        }
        node.invalidateRefill();
        node.setChanged();
    }

    private static int spread(RandomSource random, int range) {
        return random.nextInt(range) - random.nextInt(range);
    }

    private static boolean isPullable(Entity entity) {
        return !entity.isRemoved()
                && !(entity instanceof Player player && (player.isCreative() || player.isSpectator()));
    }

    private static void pullAndDevour(TileNode node, ServerLevel level, BlockPos pos, double itemRange, List<Entity> pulled) {
        Vec3 center = Vec3.atCenterOf(pos);
        DamageSource source = level.damageSources().fellOutOfWorld();
        for (int i = pulled.size() - 1; i >= 0; i--) {
            Entity entity = pulled.get(i);
            if (!isPullable(entity)) {
                if (entity.isRemoved()) {
                    pulled.remove(i);
                }
                continue;
            }
            double dx = center.x - entity.getX();
            double dy = center.y - entity.getY();
            double dz = center.z - entity.getZ();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double strength = 1.0 - distance / (entity instanceof ItemEntity ? itemRange : GLOBAL_PULL_RANGE);
            if (strength <= 0.0) {
                continue;
            }
            if (distance > MINIMUM_PULL_DISTANCE) {
                double weight = strength * strength;
                entity.setDeltaMovement(entity.getDeltaMovement().add(
                        dx / distance * weight * HORIZONTAL_PULL,
                        dy / distance * weight * VERTICAL_PULL,
                        dz / distance * weight * HORIZONTAL_PULL));

            }
            if (distance >= DEVOUR_DISTANCE) {
                continue;
            }
            if (!entity.hurtServer(level, source, DEVOUR_DAMAGE)) {
                continue;
            }
            if (entity instanceof ItemEntity item) {
                if (!item.getItem().isEmpty()) {
                    AspectList primals = NodeRules.toPrimals(AspectHelper.getObjectAspects(item.getItem()));
                    Aspect[] primalAspects = primals.getAspects();
                    if (primalAspects.length > 0) {
                        devour(node, primalAspects[0], level.getRandom());
                    }
                }
            } else if (entity instanceof net.minecraft.world.entity.LivingEntity le && le.isDeadOrDying()) {
                AspectList entityAspects = AspectHelper.getEntityAspects(entity);
                if (entityAspects != null) {
                    Aspect[] primalAspects = NodeRules.toPrimals(entityAspects).getAspects();
                    if (primalAspects.length > 0) {
                        devour(node, primalAspects[0], level.getRandom());
                    }
                }
            }
        }
    }
}
