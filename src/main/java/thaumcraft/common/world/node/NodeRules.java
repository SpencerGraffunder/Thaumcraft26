package thaumcraft.common.world.node;

import net.minecraft.util.RandomSource;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Node behavior constants and pure helpers (TC6 node system, Thaumaturge parity).
 */
public final class NodeRules {

    /** Refill interval (ticks) per quality modifier. FADING = 0 (never refills). */
    public static final int REFILL_AVERAGE = 600;
    public static final int REFILL_BRIGHT = 400;
    public static final int REFILL_PALE = 900;
    public static final int REFILL_FADING = 0;

    /** Raw aura vis/flux consumed per point of node vis refilled. */
    public static final float RAW_PER_POINT = 3.0F;
    /** A refill attempt that takes at least this much counts as success. */
    public static final float REFILL_SUCCESS_THRESHOLD = 2.99F;
    /** Consecutive failed refills before the node degrades one quality step. */
    public static final int DEGRADE_AFTER_FAILURES = 10;

    /** Decay check interval; a fully drained aspect loses one base point per pass. */
    public static final int DECAY_INTERVAL = 1200;
    public static final int DECAY_REMOVAL_ODDS = 20;
    public static final int DECAY_MODIFIER_ODDS = 5;
    public static final int DECAY_FADE_ODDS = 5;

    /** Stability check interval. */
    public static final int STABILITY_INTERVAL = 100;
    public static final int UNSTABLE_BASIC_ODDS = 10000;
    public static final int UNSTABLE_ADVANCED_ODDS = 5000;
    public static final int FADING_BASIC_ODDS = 12500;
    public static final int FADING_ADVANCED_ODDS = 6250;

    /** Discharge: transfer one point of vis to a poorer node within ±4 blocks. */
    public static final int DISCHARGE_RANGE = 4;
    public static final int DISCHARGE_INTERVAL_FAST = 1;
    public static final int DISCHARGE_INTERVAL_PALE = 3;
    public static final int DISCHARGE_INTERVAL_DEFAULT = 2;
    public static final float ELEVATED_DIVISOR = 1.5F;
    public static final int PALE_RECOVERY_ODDS = 100;
    public static final int DONOR_BASE_LOSS_ODDS = 3;

    /** Primordial pearl (Thaumaturge NodeRules.pearl constants). */
    private static final int PEARL_PRIMAL_PENALTY = 2;
    private static final int PEARL_PRIMAL_SPREAD = 6;
    private static final int PEARL_PRIMAL_SPREAD_RESEARCHED = 9;
    private static final int PEARL_REPLACEMENT_SPREAD = 3;
    private static final int PEARL_REPLACEMENT_SPREAD_RESEARCHED = 4;
    private static final int PEARL_BRIGHTEN_ODDS = 5;

    private static final float WEIGHT_FLOOR = 0.05F;

    /**
     * Pearl quality roll: fading has a 50% chance to recover to pale, pale a
     * 50% chance to lose its modifier, and unmodified nodes have a 1 in 5
     * chance to become bright.
     */
    public static NodeModifier pearlModifier(NodeModifier modifier, RandomSource random) {
        if (modifier == NodeModifier.FADING) {
            return random.nextBoolean() ? NodeModifier.PALE : modifier;
        }
        if (modifier == NodeModifier.PALE) {
            return random.nextBoolean() ? null : modifier;
        }
        if (modifier == null && random.nextInt(PEARL_BRIGHTEN_ODDS) == 0) {
            return NodeModifier.BRIGHT;
        }
        return modifier;
    }

    /**
     * Pearl aspect mutation (Thaumaturge parity): each base aspect is reduced
     * (primals by PEARL_PRIMAL_PENALTY with a random spread; compounds by a
     * coin-flip 1 point), and each primal may be boosted to a fresh random
     * level, topping up the contained list by one point where it was lower.
     * Mutates and returns nothing — callers re-read the lists.
     */
    public static void pearl(AspectList held, AspectList base, List<Aspect> primals, RandomSource random, boolean researched) {
        int primalSpread = researched ? PEARL_PRIMAL_SPREAD_RESEARCHED : PEARL_PRIMAL_SPREAD;
        for (Aspect aspect : base.getAspects().clone()) {
            int previous = base.getAmount(aspect);
            int next = aspect.isPrimal()
                    ? Math.max(0, previous - PEARL_PRIMAL_PENALTY + random.nextInt(primalSpread))
                    : random.nextBoolean() ? Math.max(0, previous - 1) : previous;
            if (next <= 0) {
                base.aspects.remove(aspect);
            } else {
                base.aspects.put(aspect, next);
            }
            if (next < previous) {
                int contained = held.getAmount(aspect);
                if (contained > next) {
                    held.remove(aspect, contained - next);
                }
            }
        }
        int replacementSpread = researched ? PEARL_REPLACEMENT_SPREAD_RESEARCHED : PEARL_REPLACEMENT_SPREAD;
        for (Aspect primal : primals) {
            int replacement = random.nextInt(replacementSpread);
            if (replacement > 0 && replacement > base.getAmount(primal)) {
                base.aspects.put(primal, replacement);
                if (held.getAmount(primal) < replacement) {
                    held.add(primal, 1);
                }
            }
        }
    }

    private NodeRules() {}

    public static int baseRefillInterval(NodeModifier modifier) {
        if (modifier == null) return REFILL_AVERAGE;
        return switch (modifier) {
            case BRIGHT -> REFILL_BRIGHT;
            case PALE -> REFILL_PALE;
            case FADING -> REFILL_FADING;
        };
    }

    /** Degrade one quality step: null -> PALE, BRIGHT -> null, PALE/FADING -> FADING. */
    public static NodeModifier degrade(NodeModifier modifier) {
        if (modifier == null) return NodeModifier.PALE;
        return switch (modifier) {
            case BRIGHT -> null;
            case PALE, FADING -> NodeModifier.FADING;
        };
    }

    /** Improve one quality step: FADING -> PALE, PALE -> null, null -> BRIGHT. */
    public static NodeModifier improve(NodeModifier modifier) {
        if (modifier == null) return NodeModifier.BRIGHT;
        return switch (modifier) {
            case FADING -> NodeModifier.PALE;
            case PALE -> null;
            case BRIGHT -> NodeModifier.BRIGHT;
        };
    }

    /**
     * Decompose a compound aspect list into its primal components (max depth 8).
     * Used when a node becomes "energized" — it then only holds primals.
     */
    public static AspectList toPrimals(AspectList source) {
        Map<Aspect, Integer> totals = new LinkedHashMap<>();
        for (Aspect aspect : source.getAspects()) {
            expand(aspect, source.getAmount(aspect), 0, totals);
        }
        AspectList out = new AspectList();
        for (Map.Entry<Aspect, Integer> entry : totals.entrySet()) {
            if (entry.getValue() > 0) {
                out.add(entry.getKey(), entry.getValue());
            }
        }
        return out;
    }

    private static void expand(Aspect aspect, int amount, int depth, Map<Aspect, Integer> totals) {
        if (aspect.isPrimal() || depth >= 8) {
            totals.merge(aspect, amount, Integer::sum);
            return;
        }
        for (Aspect component : aspect.getComponents()) {
            expand(component, amount, depth + 1, totals);
        }
    }

    /**
     * Distribute a remaining budget of vis points across the aspects in the map,
     * weighted by random rolls (largest-remainder method).
     */
    public static void allocateBudget(Map<Aspect, Integer> target, int budget, RandomSource random) {
        int committed = target.values().stream().mapToInt(Integer::intValue).sum();
        int extra = Math.max(0, budget - committed);
        List<Aspect> keys = new ArrayList<>(target.keySet());
        int count = keys.size();
        if (count == 0 || extra <= 0) return;
        double[] weights = new double[count];
        double weightSum = 0.0;
        for (int i = 0; i < count; i++) {
            weights[i] = random.nextFloat() + WEIGHT_FLOOR;
            weightSum += weights[i];
        }
        int[] shares = new int[count];
        double[] remainders = new double[count];
        int assigned = 0;
        for (int i = 0; i < count; i++) {
            double quota = extra * weights[i] / weightSum;
            shares[i] = (int) quota;
            remainders[i] = quota - shares[i];
            assigned += shares[i];
        }
        List<Integer> byRemainder = new ArrayList<>(count);
        for (int i = 0; i < count; i++) byRemainder.add(i);
        byRemainder.sort(Comparator.comparingDouble((Integer i) -> remainders[i]).reversed());
        int leftover = extra - assigned;
        for (int rank = 0; rank < leftover; rank++) {
            shares[byRemainder.get(rank % count)]++;
        }
        for (int i = 0; i < count; i++) {
            target.merge(keys.get(i), shares[i], Integer::sum);
        }
    }
}
