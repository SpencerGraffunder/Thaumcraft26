package thaumcraft.common.items.tools;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.TooltipFlag;
import thaumcraft.init.ItemRegistration;

import java.util.function.Consumer;

/**
 * Node jar (TC6 node system). A glass vessel that captures a nearby aura node
 * when used on it (requires the NODEJARS research). The captured node becomes
 * a dormant node-jar block; a caster gauntlet releases it again.
 */
public class ItemNodeJar extends Item {

    public ItemNodeJar(Properties properties) {
        super(ItemRegistration.id(properties.stacksTo(16)));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        builder.accept(Component.translatable("tooltip.thaumcraft.node_jar"));
    }
}
