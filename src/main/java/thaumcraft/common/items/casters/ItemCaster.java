package thaumcraft.common.items.casters;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.casters.FocusPackage;
import thaumcraft.api.casters.ICaster;
import thaumcraft.api.casters.IInteractWithCaster;
import thaumcraft.api.casters.FocusEngine;
import thaumcraft.api.items.IVisDiscountGear;
import thaumcraft.common.world.aura.AuraHandler;

import javax.annotation.Nullable;
import java.text.DecimalFormat;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.entity.LivingEntity;

/**
 * Caster Gauntlet - Main item for casting spells in Thaumcraft.
 * Holds a focus and consumes vis from the local aura to cast.
 */
public class ItemCaster extends Item implements ICaster {
    
    private static final DecimalFormat VIS_FORMAT = new DecimalFormat("#######.#");
    
    /** Area of aura this caster can draw from (0=chunk, 1=cross, 2=3x3) */
    private final int auraArea;
    
    public ItemCaster(int area) {
        super(thaumcraft.init.ItemRegistration.id(new Item.Properties()
                .stacksTo(1)
                .rarity(Rarity.UNCOMMON)));
        this.auraArea = area;
    }
    
    /**
     * Create a basic caster gauntlet (draws from single chunk).
     */
    public static ItemCaster createBasic() {
        return new ItemCaster(0);
    }
    
    /**
     * Create an advanced caster gauntlet (draws from 5 chunks in a cross).
     */
    public static ItemCaster createAdvanced() {
        return new ItemCaster(1);
    }
    
    /**
     * Create a master caster gauntlet (draws from 9 chunks in a 3x3).
     */
    public static ItemCaster createMaster() {
        return new ItemCaster(2);
    }
    
    // ==================== ICaster Implementation ====================
    
    @Override
    public float getConsumptionModifier(ItemStack stack, Player player, boolean crafting) {
        float modifier = 1.0f;
        if (player != null) {
            modifier -= getTotalVisDiscount(player);
        }
        return Math.max(modifier, 0.1f); // Minimum 10% cost
    }
    
    /**
     * Calculate total vis discount from equipped gear.
     */
    private float getTotalVisDiscount(Player player) {
        float discount = 0.0f;
        
        // Check armor slots
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD}) {
            ItemStack armor = player.getItemBySlot(slot);
            if (!armor.isEmpty() && armor.getItem() instanceof IVisDiscountGear gear) {
                discount += gear.getVisDiscount(armor, player) / 100.0f;
            }
        }
        
        // Check held items (other hand)
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            ItemStack held = player.getItemBySlot(slot);
            if (!held.isEmpty() && held.getItem() instanceof IVisDiscountGear gear) {
                discount += gear.getVisDiscount(held, player) / 100.0f;
            }
        }
        
        // Cap at 50%
        return Math.min(discount, 0.5f);
    }
    
    @Override
    public boolean consumeVis(ItemStack stack, Player player, float amount, boolean crafting, boolean simulate) {
        amount *= getConsumptionModifier(stack, player, crafting);

        // The wand's own vis buffer (filled by tapping aura nodes) is spent first.
        int bufferPoints = Math.min(getVisBuffer(stack).visSize(), (int) Math.floor(amount));
        if (bufferPoints > 0) {
            float available = bufferPoints + getAuraPool(player);
            if (available < amount) {
                return false;
            }
            if (simulate) {
                return true;
            }
            drainBuffer(stack, bufferPoints);
            amount -= bufferPoints;
        }

        if (amount <= 0.0F) {
            return true;
        }

        float available = getAuraPool(player);
        if (available < amount) {
            return false;
        }

        if (simulate) {
            return true;
        }

        // Drain vis from the aura
        return drainFromAura(player, amount);
    }

    // ==================== Wand Vis Buffer (node tapper) ====================

    /** Vis capacity of this gauntlet tier, for the node-tapped buffer. */
    public int getBufferCapacity() {
        return switch (auraArea) {
            case 2 -> 100;
            case 1 -> 60;
            default -> 40;
        };
    }

    public static int bufferCapacity(ItemStack stack) {
        return stack.getItem() instanceof ItemCaster caster ? caster.getBufferCapacity() : 0;
    }

    /** The per-aspect vis buffer stored on the gauntlet (TC6 wand vis parity). */
    public static AspectList getVisBuffer(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        AspectList buffer = new AspectList();
        if (data.contains("vis")) {
            buffer.readFromNBT(data.getCompoundOrEmpty("vis"));
        }
        return buffer;
    }

    private static void setVisBuffer(ItemStack stack, AspectList buffer) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag vis = new CompoundTag();
        buffer.writeToNBT(vis);
        if (vis.isEmpty()) {
            tag.remove("vis");
        } else {
            tag.put("vis", vis);
        }
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
    }

    public static int bufferAmountOf(ItemStack stack, Aspect aspect) {
        return getVisBuffer(stack).getAmount(aspect);
    }

    /**
     * Add up to {@code offered} points of {@code aspect} to the buffer.
     * Returns the number of points NOT accepted (buffer full).
     */
    public static int topUpBuffer(ItemStack stack, Aspect aspect, int offered) {
        if (offered <= 0 || !(stack.getItem() instanceof ItemCaster)) {
            return offered;
        }
        AspectList buffer = getVisBuffer(stack);
        int room = Math.max(0, bufferCapacity(stack) - buffer.visSize());
        int accepted = Math.min(offered, room);
        if (accepted > 0) {
            buffer.add(aspect, accepted);
            setVisBuffer(stack, buffer);
        }
        return offered - accepted;
    }

    /** Drain up to {@code points} int vis from the buffer, across held aspects. */
    private static void drainBuffer(ItemStack stack, int points) {
        AspectList buffer = getVisBuffer(stack);
        for (Aspect aspect : buffer.getAspects()) {
            if (points <= 0) break;
            int take = Math.min(buffer.getAmount(aspect), points);
            buffer.remove(aspect, take);
            points -= take;
        }
        setVisBuffer(stack, buffer);
    }
    
    /**
     * Get total available vis from the aura pool this caster can access.
     */
    private float getAuraPool(Player player) {
        Level level = player.level();
        BlockPos pos = player.blockPosition();
        
        return switch (auraArea) {
            case 1 -> { // Cross pattern (5 chunks)
                float total = AuraHandler.getVis(level, pos);
                for (Direction face : Direction.Plane.HORIZONTAL) {
                    total += AuraHandler.getVis(level, pos.relative(face, 16));
                }
                yield total;
            }
            case 2 -> { // 3x3 grid (9 chunks)
                float total = 0.0f;
                for (int xx = -1; xx <= 1; xx++) {
                    for (int zz = -1; zz <= 1; zz++) {
                        total += AuraHandler.getVis(level, pos.offset(xx * 16, 0, zz * 16));
                    }
                }
                yield total;
            }
            default -> AuraHandler.getVis(level, pos); // Single chunk
        };
    }
    
    /**
     * Drain vis from the aura using this caster's pattern.
     */
    private boolean drainFromAura(Player player, float amount) {
        Level level = player.level();
        BlockPos pos = player.blockPosition();
        
        switch (auraArea) {
            case 1 -> { // Cross pattern
                float perChunk = amount / 5.0f;
                float remaining = amount;
                remaining -= AuraHandler.drainVis(level, pos, Math.min(perChunk, remaining), false);
                for (Direction face : Direction.Plane.HORIZONTAL) {
                    if (remaining <= 0) break;
                    remaining -= AuraHandler.drainVis(level, pos.relative(face, 16), 
                            Math.min(perChunk, remaining), false);
                }
                return remaining <= 0;
            }
            case 2 -> { // 3x3 grid
                float perChunk = amount / 9.0f;
                float remaining = amount;
                for (int xx = -1; xx <= 1; xx++) {
                    for (int zz = -1; zz <= 1; zz++) {
                        if (remaining <= 0) break;
                        remaining -= AuraHandler.drainVis(level, pos.offset(xx * 16, 0, zz * 16),
                                Math.min(perChunk, remaining), false);
                    }
                }
                return remaining <= 0;
            }
            default -> { // Single chunk
                return AuraHandler.drainVis(level, pos, amount, false) >= amount;
            }
        }
    }
    
    @Override
    @Nullable
    public Item getFocus(ItemStack stack) {
        ItemStack focusStack = getFocusStack(stack);
        if (focusStack != null && !focusStack.isEmpty()) {
            return focusStack.getItem();
        }
        return null;
    }
    
    @Override
    @Nullable
    public ItemStack getFocusStack(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (data != null && data.contains("focus")) {
            CompoundTag focusTag = data.getCompoundOrEmpty("focus");
            return ItemStack.OPTIONAL_CODEC.parse(NbtOps.INSTANCE, focusTag).resultOrPartial().orElse(ItemStack.EMPTY);
        }
        return null;
    }
    
    @Override
    public void setFocus(ItemStack stack, ItemStack focus) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (focus == null || focus.isEmpty()) {
            tag.remove("focus");
        } else {
            tag.put("focus", (CompoundTag) ItemStack.OPTIONAL_CODEC.encodeStart(NbtOps.INSTANCE, focus).resultOrPartial().orElse(new CompoundTag()));
        }
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
    }
    
    @Override
    public ItemStack getPickedBlock(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (data != null && data.contains("picked")) {
            return ItemStack.OPTIONAL_CODEC.parse(NbtOps.INSTANCE, data.getCompoundOrEmpty("picked")).resultOrPartial().orElse(ItemStack.EMPTY);
        }
        return ItemStack.EMPTY;
    }
    
    /**
     * Store a picked block for Equal Trade focus.
     */
    public void storePickedBlock(ItemStack stack, ItemStack pickedBlock) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.put("picked", (CompoundTag) ItemStack.OPTIONAL_CODEC.encodeStart(NbtOps.INSTANCE, pickedBlock).resultOrPartial().orElse(new CompoundTag()));
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
    }
    
    // ==================== Item Behavior ====================
    
    @Override
    public InteractionResult onItemUseFirst(ItemStack theStack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        Direction side = context.getClickedFace();
        InteractionHand hand = context.getHand();
        
        // Check for IInteractWithCaster blocks
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof IInteractWithCaster casterBlock) {
            if (casterBlock.onCasterRightClick(level, stack, player, pos, side, hand)) {
                return InteractionResult.SUCCESS;
            }
        }
        
        // Check tile entities
        BlockEntity tile = level.getBlockEntity(pos);
        if (tile instanceof IInteractWithCaster casterTile) {
            if (casterTile.onCasterRightClick(level, stack, player, pos, side, hand)) {
                return InteractionResult.SUCCESS;
            }
        }
        
        // Focus-specific block interactions
        
        return InteractionResult.PASS;
    }
    
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ItemStack focusStack = getFocusStack(stack);
        
        if (focusStack == null || focusStack.isEmpty()) {
            return InteractionResult.PASS;
        }
        
        if (!(focusStack.getItem() instanceof ItemFocus focus)) {
            return InteractionResult.PASS;
        }
        
        // Calculate vis cost
        float visCost = focus.getVisCost(focusStack);
        
        // Try to consume vis
        if (!consumeVis(stack, player, visCost, false, false)) {
            // Not enough vis
            return InteractionResult.FAIL;
        }
        
        // Cast the spell!
        if (!level.isClientSide()) {
            FocusPackage focusPackage = ItemFocus.getPackage(focusStack);
            if (focusPackage != null) {
                FocusEngine.castFocusPackage(player, focusPackage);
            }
        }
        
        player.swingAndResetAttackStrength(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
        
        // Apply cooldown
        int cooldown = focus.getActivationTime(focusStack);
        player.getCooldowns().addCooldown(stack, cooldown);
        
        return InteractionResult.SUCCESS;
    }
    
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        // 1.12 ItemCaster.onUpdate: every 10 ticks the server pushes the aura around the player
        // to the client (PacketAuraToClient) so the caster HUD gauge has something to draw.
        if (entity instanceof net.minecraft.server.level.ServerPlayer player
                && entity.tickCount % 10 == 0) {
            updateAura(stack, level, player);
        }
    }

    /**
     * 1.12 ItemCaster.updateAura: sum vis/flux/base over the chunks this wand's dial area
     * covers (0 = current chunk, 1 = current chunk plus the four horizontal neighbours,
     * 2 = the full 3x3) and send the result to the client.
     */
    private void updateAura(ItemStack stack, ServerLevel level,
                            net.minecraft.server.level.ServerPlayer player) {
        int cx = player.blockPosition().getX() >> 4;
        int cz = player.blockPosition().getZ() >> 4;
        thaumcraft.common.world.aura.AuraChunk ac =
                thaumcraft.common.world.aura.AuraHandler.getAuraChunk(level.dimension(), cx, cz);
        if (ac == null) return;

        float cv = ac.getVis();
        float cf = ac.getFlux();
        short bv = ac.getBase();

        if (auraArea == 1) {
            for (Direction face : Direction.Plane.HORIZONTAL.stream().toList()) {
                thaumcraft.common.world.aura.AuraChunk neighbour = thaumcraft.common.world.aura.AuraHandler
                        .getAuraChunk(level.dimension(), cx + face.getStepX(), cz + face.getStepZ());
                if (neighbour != null) {
                    cv += neighbour.getVis();
                    cf += neighbour.getFlux();
                    bv += neighbour.getBase();
                }
            }
        } else if (auraArea == 2) {
            for (int xx = -1; xx <= 1; ++xx) {
                for (int zz = -1; zz <= 1; ++zz) {
                    thaumcraft.common.world.aura.AuraChunk neighbour = thaumcraft.common.world.aura.AuraHandler
                            .getAuraChunk(level.dimension(), cx + xx, cz + zz);
                    if (neighbour != null) {
                        cv += neighbour.getVis();
                        cf += neighbour.getFlux();
                        bv += neighbour.getBase();
                    }
                }
            }
        }

        thaumcraft.common.lib.network.PacketHandler.sendToPlayer(
                new thaumcraft.common.lib.network.misc.PacketAuraToClient(
                        new thaumcraft.common.world.aura.AuraChunk(
                                (net.minecraft.world.level.chunk.LevelChunk) null, bv, cv, cf)),
                player);
    }
    
    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {        return 72000; // Long duration for channeled spells
    }
    
    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW;
    }
    
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        // Only re-animate if focus changed
        if (oldStack.getItem() == this && newStack.getItem() == this) {
            ItemStack oldFocus = getFocusStack(oldStack);
            ItemStack newFocus = getFocusStack(newStack);
            
            if (oldFocus == null && newFocus == null) return false;
            if (oldFocus == null || newFocus == null) return true;
            
            // Compare focus configurations
            if (oldFocus.getItem() instanceof ItemFocus oldF && newFocus.getItem() instanceof ItemFocus newF) {
                String oldSort = oldF.getSortingHelper(oldFocus);
                String newSort = newF.getSortingHelper(newFocus);
                if (oldSort != null && newSort != null) {
                    return !oldSort.equals(newSort);
                }
            }
            return !ItemStack.isSameItemSameComponents(oldFocus, newFocus);
        }
        return oldStack.getItem() != newStack.getItem();
    }
    
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        ItemStack focusStack = getFocusStack(stack);
        
        if (focusStack != null && !focusStack.isEmpty() && focusStack.getItem() instanceof ItemFocus focus) {
            // Show vis cost
            float visCost = focus.getVisCost(focusStack);
            if (visCost > 0) {
                builder.accept(Component.translatable("tc.vis.cost")
                        .append(" ")
                        .append(Component.literal(VIS_FORMAT.format(visCost)))
                        .withStyle(ChatFormatting.ITALIC, ChatFormatting.AQUA));
            }
            
            // Show focus name
            builder.accept(Component.empty());
            builder.accept(focusStack.getHoverName()
                    .copy()
                    .withStyle(ChatFormatting.BOLD, ChatFormatting.ITALIC, ChatFormatting.GREEN));
            
            // Add focus details
            List<Component> focusTooltip = new java.util.ArrayList<>();
            focus.addFocusInformation(focusStack, null, focusTooltip, flag);
            focusTooltip.forEach(builder);
        } else {
            builder.accept(Component.translatable("item.thaumcraft.caster.no_focus")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }
}
