package thaumcraft.common.items.tools;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ScanningManager;
import thaumcraft.client.ThaumometerHUD;
import thaumcraft.client.fx.FXDispatcher;
import thaumcraft.common.items.ItemTC;
import thaumcraft.common.lib.network.PacketHandler;
import thaumcraft.common.lib.network.misc.PacketAuraToClient;
import thaumcraft.common.lib.research.ResearchManager;
import thaumcraft.common.lib.utils.EntityUtils;
import thaumcraft.common.tiles.node.TileNode;
import thaumcraft.common.world.node.NodeModifier;
import thaumcraft.common.world.aura.AuraChunk;
import thaumcraft.common.world.aura.AuraHandler;
import thaumcraft.init.ModSounds;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Thaumometer - the basic scanning tool of Thaumcraft.
 * Right-click to scan entities and blocks to discover their aspects
 * and unlock research.
 */
public class ItemThaumometer extends ItemTC {

    private static final double SCAN_RANGE = 9.0;

    public ItemThaumometer() {
        super(new Properties()
                .stacksTo(1)
                .rarity(Rarity.UNCOMMON));
    }

    /**
     * 1.12 ItemThaumometer.onItemRightClick ran before block activation and returned SUCCESS, so
     * pointing at a chest, fence, crop or sign scanned instead of opening/placing. In 26.3 Item.use()
     * runs last, so the scan has to be intercepted here or every block swallows the scan.
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        return scanInteraction(context.getLevel(), player, context.getHand());
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return scanInteraction(level, player, hand);
    }

    private InteractionResult scanInteraction(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            // 1.12 ItemThaumometer.onItemRightClick: client half is drawFX + a local-only
            // playSound(..., false), so only the scanning player hears it.
            drawFX(level, player);
            level.playSound(player, player.getX(), player.getY(), player.getZ(),
                    ModSounds.SCAN.get(), SoundSource.PLAYERS, 0.5f, 1.0f);
            return InteractionResult.SUCCESS;
        }

        // Server-side: perform the scan
        doScan(level, player);
        // 1.12 returned SUCCESS on both sides (arm swing, stack handed back unchanged).
        return InteractionResult.SUCCESS;
    }

    /** 1.12 ItemThaumometer.drawFX: runes over the pointed entity, else over the ray-traced block. */
    private void drawFX(Level level, Player player) {
        Entity target = getScanTarget(level, player);
        if (target != null) {
            for (int a = 0; a < 10; ++a) {
                FXDispatcher.INSTANCE.blockRunes(
                        target.getX() - 0.5, target.getY() + target.getEyeHeight() / 2.0f, target.getZ() - 0.5,
                        0.3f + level.getRandom().nextFloat() * 0.7f, 0.0f, 0.3f + level.getRandom().nextFloat() * 0.7f,
                        (int) (target.getBbHeight() * 15.0f), 0.03f);
            }
            return;
        }

        BlockHitResult mop = rayTraceFromPlayerWild(level, player);
        if (mop != null && mop.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = mop.getBlockPos();
            for (int a = 0; a < 10; ++a) {
                FXDispatcher.INSTANCE.blockRunes(
                        pos.getX(), pos.getY() + 0.25, pos.getZ(),
                        0.3f + level.getRandom().nextFloat() * 0.7f, 0.0f, 0.3f + level.getRandom().nextFloat() * 0.7f,
                        15, 0.03f);
            }
        }
    }

    /**
     * 1.12 ItemThaumometer.onUpdate: while the thaumometer is held the server pushes the
     * player's current aura chunk to the client every 20 ticks (PacketAuraToClient) so the
     * thaumometer HUD gauge has something to draw, and warns about flux build-up by unlocking
     * the hidden FLUX research.
     *
     * NOTE: in 26.3 inventoryTick is handed a ServerLevel, so it only ever runs on the server.
     * The client half of 1.12's onUpdate (the scan highlight) is driven from
     * {@code ThaumometerClientEvents} on the client tick instead.
     */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (!(entity instanceof ServerPlayer player)) return;

        boolean held = slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND;
        if (!held) return;

        if (entity.tickCount % 20 == 0) {
            updateAura(player);
        }
    }

    /** 1.12 ItemThaumometer.updateAura. */
    private void updateAura(ServerPlayer player) {
        Level level = player.level();
        AuraChunk ac = AuraHandler.getAuraChunk(level.dimension(),
                player.blockPosition().getX() >> 4, player.blockPosition().getZ() >> 4);
        if (ac == null) return;

        if ((ac.getFlux() > ac.getVis() || ac.getFlux() > ac.getBase() / 3)
                && !ThaumcraftCapabilities.knowsResearch(player, "FLUX")) {
            ResearchManager.startResearchWithPopup(player, "FLUX");
            // 1.12: player.sendMessage(new TextComponentString(DARK_PURPLE + I18n["research.FLUX.warn"]), true)
            // 26.3: ServerPlayer#sendSystemMessage(component, overlay)
            player.sendSystemMessage(Component.translatable("research.FLUX.warn").withStyle(ChatFormatting.DARK_PURPLE), true);
        }

        PacketHandler.sendToPlayer(new PacketAuraToClient(ac), player);
    }

    /**
     * Perform a scan at the player's look target.
     * 1.12 ItemThaumometer.doScan: entity first (getPointedEntity 1.0/9.0/padding 0), then the
     * wild block ray-trace, then a null (sky/moon) scan.
     */
    private void doScan(Level level, Player player) {
        Entity target = getScanTarget(level, player);
        if (target != null) {
            ScanningManager.scanTheThing(player, target);
            return;
        }

        BlockHitResult mop = rayTraceFromPlayerWild(level, player);
        if (mop != null && mop.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = mop.getBlockPos();
            if (level.getBlockEntity(pos) instanceof TileNode node
                    && level instanceof ServerLevel serverLevel
                    && player instanceof ServerPlayer serverPlayer) {
                scanNode(serverPlayer, node);
                return;
            }
            ScanningManager.scanTheThing(player, pos);
            return;
        }

        // No target - scan the sky/void
        ScanningManager.scanTheThing(player, (BlockPos) null);
    }

    /**
     * F108: scanning a node reads out its type, trait and held vis in chat and unlocks the
     * NODE research on first scan. Returns true when the research was newly unlocked.
     */
    public static boolean scanNode(ServerPlayer player, TileNode node) {
        boolean fresh = unlockNodeResearch(player);
        if (player.connection != null) {
            player.sendSystemMessage(nodeScanReadout(node));
        }
        return fresh;
    }

    public static boolean unlockNodeResearch(net.minecraft.world.entity.player.Player player) {
        thaumcraft.api.capabilities.IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);
        if (knowledge == null || knowledge.isResearchKnown("NODE")) {
            return false;
        }
        knowledge.addResearch("NODE");
        return true;
    }

    public static MutableComponent nodeScanReadout(TileNode node) {
        MutableComponent c = Component.translatable("node.thaumcraft.type." + node.kind().getSerializedName());
        NodeModifier trait = node.trait();
        if (trait != null) {
            c = c.append(" ").append(Component.translatable("node.thaumcraft.modifier." + trait.getSerializedName()));
        }
        c = c.append(": ").append(Component.literal(node.getAspects().toString()).withStyle(ChatFormatting.LIGHT_PURPLE));
        return c.withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC);
    }

    /**
     * Highlight scannable things on the client.
     * Called every 5 client ticks by {@code ThaumometerClientEvents} (1.12 drove this from the
     * item's client-side onUpdate, which 26.3 no longer gives items).
     */
    public static void highlightScannables(Level level, Player player) {
        // 1.12 ItemThaumometer.onUpdate client half: getPointedEntity(world, player, 1.0, 16.0, 5.0F, true)
        // every 5 ticks, scanHighlight it, store it as RenderEventHandler.thaumTarget, then
        // scanHighlight the wild ray-traced block too.
        Entity target = EntityUtils.getPointedEntity(level, player, 1.0, 16.0, 5.0F, true);
        // 1.12 RenderEventHandler.thaumTarget: the aspect-tag renderer reads this to draw the
        // target's aspects above it while the thaumometer is held.
        ThaumometerHUD.target = target;
        if (target != null && ScanningManager.isThingStillScannable(player, target)) {
            FXDispatcher.INSTANCE.scanHighlight(target);
        }

        BlockHitResult mop = rayTraceFromPlayerWild(level, player);
        if (mop != null && mop.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = mop.getBlockPos();
            if (ScanningManager.isThingStillScannable(player, pos)) {
                FXDispatcher.INSTANCE.scanHighlight(pos);
            }
        }
    }

    /** 1.12: EntityUtils.getPointedEntity(world, player, 1.0, 9.0, 0.0F, true). */
    private static Entity getScanTarget(Level level, Player player) {
        return EntityUtils.getPointedEntity(level, player, 1.0, SCAN_RANGE, 0.0F, true);
    }

    /**
     * 1.12 ItemThaumometer.getRayTraceResultFromPlayerWild: the scan ray is jittered by up to
     * 25 degrees on both axes (and the position is interpolated between the previous and current
     * position) so a scan sweeps a small cone instead of a single pixel-perfect line.
     * Equivalent 1.12 call: world.rayTraceBlocks(from, to, true, false, false).
     */
    @Nullable
    private static BlockHitResult rayTraceFromPlayerWild(Level level, Player player) {
        float yaw = player.yRotO + (player.getYRot() - player.yRotO)
                + level.getRandom().nextInt(25) - level.getRandom().nextInt(25);
        float pitch = player.xRotO + (player.getXRot() - player.xRotO)
                + level.getRandom().nextInt(25) - level.getRandom().nextInt(25);

        Vec3 prev = player.oldPosition();
        Vec3 cur = player.position();
        Vec3 from = new Vec3(
                prev.x + (cur.x - prev.x),
                prev.y + (cur.y - prev.y) + player.getEyeHeight(),
                prev.z + (cur.z - prev.z));

        float f2 = Mth.cos(-pitch * 0.017453292F - (float) Math.PI);
        float f3 = Mth.sin(-pitch * 0.017453292F - (float) Math.PI);
        float f4 = -Mth.cos(-yaw * 0.017453292F);
        float f5 = Mth.sin(-yaw * 0.017453292F);

        double range = 16.0;
        Vec3 to = from.add(f3 * f4 * range, f5 * f4 * range, f2 * f4 * range);

        BlockHitResult hit = level.clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        return hit.getType() == HitResult.Type.MISS ? null : hit;
    }
}
