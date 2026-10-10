package thaumcraft.common.world.node;

import net.minecraft.util.StringRepresentable;

/**
 * Aura node types (TC6 node system, ported from the Thaumaturge design).
 *
 * <p>NORMAL — standard vis node. TAINTED — flux-fed, pollutes the aura, spawns
 * taint fibre. PURE — drains flux, slowly erodes itself, found in silverwood
 * areas. DARK — found in eerie biomes, spawns a guardian when players are near.
 * UNSTABLE — unstable variant that reverts to normal unless stabilized.
 * HUNGRY — rare, grows by devouring items/creatures/blocks around it.
 */
public enum NodeType implements StringRepresentable {
    NORMAL("normal"),
    TAINTED("tainted"),
    PURE("pure"),
    DARK("dark"),
    UNSTABLE("unstable"),
    HUNGRY("hungry");

    private final String name;

    NodeType(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public static NodeType fromName(String name) {
        for (NodeType t : values()) {
            if (t.name.equals(name)) return t;
        }
        return NORMAL;
    }
}
