package thaumcraft.common.world.node;

import net.minecraft.util.StringRepresentable;

/**
 * Node quality modifier (TC6 node system). BRIGHT refills faster and discharges
 * more often; PALE slower; FADING refills not at all and decays toward removal —
 * a stabilizer below can recover a fading node to pale.
 */
public enum NodeModifier implements StringRepresentable {
    BRIGHT("bright"),
    PALE("pale"),
    FADING("fading");

    private final String name;

    NodeModifier(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public static NodeModifier fromName(String name) {
        for (NodeModifier m : values()) {
            if (m.name.equals(name)) return m;
        }
        return null;
    }
}
