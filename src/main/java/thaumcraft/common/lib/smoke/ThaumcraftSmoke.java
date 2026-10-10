package thaumcraft.common.lib.smoke;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import thaumcraft.api.FluidTanks;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.common.blocks.essentia.BlockSmelter;
import thaumcraft.common.entities.EntityFluxRift;
import thaumcraft.common.entities.projectile.EntityBottleTaint;
import thaumcraft.common.entities.projectile.EntityCausalityCollapser;
import thaumcraft.common.golems.EntityThaumcraftGolem;
import thaumcraft.common.golems.GolemProperties;
import thaumcraft.common.golems.seals.SealEmpty;
import thaumcraft.common.golems.seals.SealGuard;
import thaumcraft.common.golems.seals.SealStock;
import thaumcraft.common.golems.seals.SealUse;
import thaumcraft.common.items.consumables.ItemPhial;
import thaumcraft.common.lib.capabilities.PlayerKnowledge;
import thaumcraft.common.lib.capabilities.ThaumcraftCapabilities;
import thaumcraft.common.lib.crafting.CrucibleRecipeType;
import thaumcraft.common.lib.crafting.InfusionRecipeType;
import thaumcraft.common.lib.enchantment.EnumInfusionEnchantment;
import thaumcraft.common.lib.research.ResearchManager;
import thaumcraft.common.menu.SealMenuProvider;
import thaumcraft.common.tiles.crafting.TileCrucible;
import thaumcraft.common.tiles.crafting.TileThaumatorium;
import thaumcraft.common.tiles.essentia.TileEssentiaReservoir;
import thaumcraft.common.tiles.essentia.TileJar;
import thaumcraft.common.tiles.essentia.TileSmelter;
import thaumcraft.init.ModRecipeTypes;
import org.slf4j.Logger;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.AspectHelper;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.common.world.biomes.BiomeHandler;
import thaumcraft.init.ModBlocks;
import thaumcraft.init.ModEffects;
import thaumcraft.init.ModEntities;
import thaumcraft.init.ModItems;
import thaumcraft.init.ModSounds;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dev-only in-game assertion battery (the "smoke" layer of the audit pipeline).
 *
 * Static audits (tools/audit_*.py) compare files on disk; this battery boots a
 * REAL dedicated server and asserts the runtime state once every registry,
 * datapack (recipes / research / loot) and runtime registration is final
 * (ServerStartedEvent). Catches what static audits cannot: codec parse
 * failures, serializer mismatches, runtime registration misses, lang holes.
 *
 * Enable: TC_SMOKE=1 (env) or -Dtc.smoke=true (property) when launching the
 * server. Every check logs "[SMOKE] PASS name" / "[SMOKE] FAIL name: detail";
 * the final line is "SMOKE: <n> checks passed" or
 * "SMOKE: <n> passed, <m> FAILED: ..." (grepped by tools/run_smoke.sh and
 * tools/run_all_audits.sh).
 */
public final class ThaumcraftSmoke {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ThaumcraftSmoke() {
    }

    public static boolean enabled() {
        return "1".equals(System.getenv("TC_SMOKE")) || Boolean.getBoolean("tc.smoke");
    }

    private static int passed = 0;
    private static int failed = 0;
    private static final List<String> failures = new ArrayList<>();

    private interface KeyFn {
        Identifier key(Object obj);
    }

    private static void pass(String name) {
        passed++;
        LOGGER.info("[SMOKE] PASS {}", name);
    }

    private static void fail(String name, String detail) {
        failed++;
        failures.add(name + ": " + detail);
        LOGGER.warn("[SMOKE] FAIL {}: {}", name, detail);
    }

    public static void run(MinecraftServer server) {
        passed = 0;
        failed = 0;
        failures.clear();
        try {
            checkHolders("items", ModItems.class,
                    obj -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey((Item) obj));
            checkHolders("blocks", ModBlocks.class,
                    obj -> net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey((Block) obj));
            checkHolders("entities", ModEntities.class,
                    obj -> net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(
                            (net.minecraft.world.entity.EntityType<?>) obj));
            checkHolders("sounds", ModSounds.class,
                    obj -> net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getKey(
                            (SoundEvent) obj));
            checkSoundsJson();
            checkRecipeCounts(server.getRecipeManager());
            checkResearchGates(server.getRecipeManager());
            checkLootModifier();
            checkLootTableResources(server);
            checkEnchantments(server);
            checkAspects();
            checkLangCoverage();
            checkResearchProgress(server);
            checkCrucibleCraft(server);
            checkInfusionRecipes(server);
            checkRefiningLoot(server);
            checkGolemTick(server);
            checkGolemFollow(server);
            checkPhialFill(server);
            checkBiomeAura(server);
            checkAuraDeterminism(server);
            checkThaumatoriumQueue(server);
            checkReservoirPhial(server);
            checkSealStockMatching();
            checkSealGuiProviders(server);
            checkCollapserRift(server);
            checkTaintBottle(server);
            checkSmelterVents(server);
            checkResearchAutoUnlock(server);
            checkFluxPressure(server);
            checkNodeGeneration(server);
            checkNodeWandTap(server);
            checkNodeJar(server);
            checkNodePearl(server);
        } catch (Throwable t) {
            fail("smoke-harness", t.toString());
        }
        if (failed == 0) {
            LOGGER.info("SMOKE: {} checks passed", passed);
        } else {
            LOGGER.warn("SMOKE: {} passed, {} FAILED: {}", passed, failed,
                    String.join("; ", failures.subList(0, Math.min(8, failures.size()))));
        }
    }

    // ---------------------------------------------------------------- holders
    /**
     * Reflect over every public static final DeferredHolder field of a
     * registration class and assert each resolves to a registry-known object.
     * Self-maintaining: a newly added registration is covered automatically.
     */
    private static void checkHolders(String label, Class<?> regClass, KeyFn keyFn) {
        int total = 0;
        List<String> bad = new ArrayList<>();
        for (Field f : regClass.getFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || !Modifier.isFinal(f.getModifiers())
                    || !f.getType().getName().contains("DeferredHolder")) {
                continue;
            }
            total++;
            try {
                Object holder = f.get(null);
                Object obj = holder.getClass().getMethod("get").invoke(holder);
                Identifier id = obj == null ? null : keyFn.key(obj);
                if (obj == null || id == null || !Thaumcraft.MODID.equals(id.getNamespace())) {
                    bad.add(f.getName());
                }
            } catch (Throwable t) {
                bad.add(f.getName() + "(" + t.getClass().getSimpleName() + ")");
            }
        }
        if (bad.isEmpty() && total > 0) {
            pass(label + " (" + total + " holders, all registered)");
        } else {
            fail(label, total + " holders, " + bad.size() + " unresolved: "
                    + bad.subList(0, Math.min(8, bad.size())));
        }
    }

    // ---------------------------------------------------------------- sounds
    private static void checkSoundsJson() {
        try (InputStream in = ThaumcraftSmoke.class.getResourceAsStream("/assets/thaumcraft/sounds.json")) {
            if (in == null) {
                fail("sounds.json", "resource not on classpath");
                return;
            }
            JsonObject root = com.google.gson.JsonParser.parseString(
                    new String(in.readAllBytes())).getAsJsonObject();
            List<String> missing = new ArrayList<>();
            for (Map.Entry<String, com.google.gson.JsonElement> e : root.entrySet()) {
                if (net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT
                        .getValue(Identifier.fromNamespaceAndPath(Thaumcraft.MODID, e.getKey())) == null) {
                    missing.add(e.getKey());
                }
            }
            if (missing.isEmpty() && root.size() > 0) {
                pass("sounds (" + root.size() + " ids, all registered)");
            } else {
                fail("sounds", missing.size() + " unregistered: " + missing);
            }
        } catch (Exception e) {
            fail("sounds.json", e.toString());
        }
    }

    // ---------------------------------------------------------------- recipes
    private static void checkRecipeCounts(RecipeManager rm) {
        List<String> problems = new ArrayList<>();
        int total = 0;

        // arcane workbench (shaped + shapeless share one type and data dir)
        int arcaneParsed = 0;
        for (RecipeHolder<?> h : rm.recipeMap().byType(ModRecipeTypes.ARCANE_WORKBENCH.get())) {
            arcaneParsed++;
            if (!(h.value() instanceof thaumcraft.api.crafting.IArcaneRecipe)) {
                problems.add("arcane deserialized as " + h.value().getClass().getSimpleName());
            }
        }
        total += arcaneParsed;
        Integer arcaneDisk = countFiles("data/thaumcraft/recipe/arcane_workbench", null);
        if (arcaneDisk != null && arcaneDisk != arcaneParsed) {
            problems.add("arcane on-disk=" + arcaneDisk + " parsed=" + arcaneParsed);
        }

        int crucibleParsed = 0;
        for (RecipeHolder<?> h : rm.recipeMap().byType(ModRecipeTypes.CRUCIBLE.get())) {
            if (h.value() instanceof CrucibleRecipeType) {
                crucibleParsed++;
            } else {
                problems.add("crucible deserialized as " + h.value().getClass().getSimpleName());
            }
        }
        total += crucibleParsed;
        Integer crucibleDisk = countFiles("data/thaumcraft/recipe/crucible", "thaumcraft:crucible");
        if (crucibleDisk != null && crucibleDisk != crucibleParsed) {
            problems.add("crucible on-disk=" + crucibleDisk + " parsed=" + crucibleParsed);
        }

        int infusionParsed = 0;
        for (RecipeHolder<?> h : rm.recipeMap().byType(ModRecipeTypes.INFUSION.get())) {
            if (h.value() instanceof InfusionRecipeType) {
                infusionParsed++;
            } else {
                problems.add("infusion deserialized as " + h.value().getClass().getSimpleName());
            }
        }
        total += infusionParsed;
        // IE recipes extend InfusionRecipeType (same RecipeType), so both dirs
        // parse into the INFUSION type list.
        Integer infusionDisk = countFiles("data/thaumcraft/recipe/infusion", null);
        Integer ieDisk = countFiles("data/thaumcraft/recipe/infusion_enchantment", null);
        int diskTotal = (infusionDisk == null || ieDisk == null) ? -1 : infusionDisk + ieDisk;
        if (diskTotal >= 0 && diskTotal != infusionParsed) {
            problems.add("infusion on-disk=" + diskTotal + " parsed=" + infusionParsed);
        }

        if (problems.isEmpty() && total > 0) {
            pass("recipes (" + total + " TC recipes parsed, all match on-disk counts)");
        } else {
            fail("recipes", total + " parsed; " + String.join("; ", problems));
        }
    }

    /**
     * Count recipe JSONs under a classpath dir (dev runs have a file dir).
     * {@code typeFilter} = exact "type" value to count, or null = every .json.
     * Returns null when the comparison cannot be made (packaged jar).
     */
    private static Integer countFiles(String classpathDir, String typeFilter) {
        try {
            var url = ThaumcraftSmoke.class.getResource("/" + classpathDir);
            if (url == null || !"file".equals(url.getProtocol())) {
                return null;
            }
            File dir = new File(url.toURI());
            if (!dir.isDirectory()) {
                return null;
            }
            int count = 0;
            for (File f : dir.listFiles()) {
                if (f == null || !f.getName().endsWith(".json")) {
                    continue;
                }
                if (typeFilter == null) {
                    count++;
                } else {
                    JsonObject d = com.google.gson.JsonParser.parseString(
                            new String(Files.readAllBytes(f.toPath()))).getAsJsonObject();
                    if (typeFilter.equals(d.has("type") ? d.get("type").getAsString() : "")) {
                        count++;
                    }
                }
            }
            return count;
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------- research
    private static void checkResearchGates(RecipeManager rm) {
        List<String> missing = new ArrayList<>();
        int gated = 0;
        List<RecipeHolder<?>> all = new ArrayList<>();
        for (RecipeHolder<?> h : rm.recipeMap().byType(ModRecipeTypes.ARCANE_WORKBENCH.get())) {
            all.add(h);
        }
        for (RecipeHolder<?> h : rm.recipeMap().byType(ModRecipeTypes.CRUCIBLE.get())) {
            all.add(h);
        }
        for (RecipeHolder<?> h : rm.recipeMap().byType(ModRecipeTypes.INFUSION.get())) {
            all.add(h);
        }
        for (RecipeHolder<?> h : all) {
            Object r = h.value();
            String gate;
            try {
                gate = (String) r.getClass().getMethod("getResearch").invoke(r);
            } catch (Exception e) {
                continue;
            }
            if (gate == null || gate.isEmpty()) {
                continue;
            }
            gated++;
            // gates may be compound ("A&&B") and stage-suffixed ("A@2") —
            // every part must resolve to a registered research key
            for (String part : gate.split("&&")) {
                String key = part.contains("@") ? part.substring(0, part.indexOf('@')) : part;
                if (ResearchCategories.getResearch(key) == null) {
                    missing.add(gate + " (" + h.value().getClass().getSimpleName() + ")");
                    break;
                }
            }
        }
        if (missing.isEmpty() && gated > 0) {
            pass("research gates (" + gated + " gated recipes, all keys resolve)");
        } else {
            fail("research gates", gated + " gated; " + missing.size() + " unresolved: "
                    + missing.subList(0, Math.min(8, missing.size())));
        }
    }

    // ----------------------------------------------------------------- loot
    private static void checkLootModifier() {
        var id = Identifier.fromNamespaceAndPath(Thaumcraft.MODID, "refining_mining");
        if (NeoForgeRegistries.GLOBAL_LOOT_MODIFIER_SERIALIZERS.containsKey(id)) {
            pass("loot modifier (thaumcraft:refining_mining registered)");
        } else {
            fail("loot modifier", "thaumcraft:refining_mining serializer not registered");
        }
    }

    private static void checkLootTableResources(MinecraftServer server) {
        List<String> problems = new ArrayList<>();
        for (String block : List.of("cinnabar_ore", "deepslate_cinnabar_ore")) {
            var id = Identifier.fromNamespaceAndPath(Thaumcraft.MODID, "loot_table/blocks/" + block + ".json");
            boolean ok;
            try {
                ok = server.getResourceManager().getResource(id).isPresent();
            } catch (Exception e) {
                ok = false;
            }
            if (!ok) {
                problems.add(id.toString());
            }
        }
        if (problems.isEmpty()) {
            pass("loot tables (TC ore block loot resources present)");
        } else {
            fail("loot tables", "missing: " + problems);
        }
    }

    // ----------------------------------------------------------- enchantments
    private static void checkEnchantments(MinecraftServer server) {
        List<String> tc = new ArrayList<>();
        var registry = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for (var enchantment : registry) {
            var id = registry.getKey(enchantment);
            if (id != null && Thaumcraft.MODID.equals(id.getNamespace())) {
                tc.add(id.getPath());
            }
        }
        if (!tc.isEmpty()) {
            pass("enchantments (" + tc.size() + " TC enchantments: " + tc + ")");
        } else {
            fail("enchantments", "no thaumcraft: enchantments registered");
        }
    }

    // --------------------------------------------------------------- aspects
    private static void checkAspects() {
        int objects = AspectHelper.objectTagCount();
        int entities = AspectHelper.entityTagCount();
        List<String> problems = new ArrayList<>();
        if (objects < 100) {
            problems.add("only " + objects + " object tags registered (expected >100)");
        }
        if (entities < 10) {
            problems.add("only " + entities + " entity tags registered (expected >10)");
        }
        for (Object[] spot : new Object[][]{
                { ModBlocks.ARCANE_STONE.get().asItem(), "arcane stone" },
                { net.minecraft.world.level.block.Blocks.DIRT.asItem(), "dirt" },
                { ModBlocks.CINNABAR_ORE.get().asItem(), "cinnabar ore" } }) {
            var list = AspectHelper.getObjectAspects(new ItemStack((Item) spot[0]));
            if (list == null || list.getAspects().length == 0) {
                problems.add("no aspects for " + spot[1]);
            }
        }
        if (problems.isEmpty()) {
            pass("aspects (" + objects + " object tags, " + entities + " entity tags, spot-checks ok)");
        } else {
            fail("aspects", String.join("; ", problems));
        }
    }

    // ------------------------------------------------------------------ lang
    private static void checkLangCoverage() {
        try (InputStream in = ThaumcraftSmoke.class.getResourceAsStream("/assets/thaumcraft/lang/en_us.json")) {
            if (in == null) {
                fail("lang", "en_us.json not on classpath");
                return;
            }
            JsonObject lang = com.google.gson.JsonParser.parseString(
                    new String(in.readAllBytes())).getAsJsonObject();
            List<String> missing = new ArrayList<>();
            for (Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
                if (id == null || !Thaumcraft.MODID.equals(id.getNamespace())) {
                    continue;
                }
                // block items inherit their name from block.<ns>.<id>
                if (item instanceof BlockItem) {
                    continue;
                }
                if (!lang.has("item." + Thaumcraft.MODID + "." + id.getPath())) {
                    missing.add("item." + id.getPath());
                }
            }
            for (Block block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
                var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
                if (id == null || !Thaumcraft.MODID.equals(id.getNamespace())) {
                    continue;
                }
                if (!lang.has("block." + Thaumcraft.MODID + "." + id.getPath())) {
                    missing.add("block." + id.getPath());
                }
            }
            if (missing.isEmpty()) {
                pass("lang (every TC item/block has an en_us entry)");
            } else {
                fail("lang", missing.size() + " missing: " + missing.subList(0, Math.min(8, missing.size())));
            }
        } catch (Exception e) {
            fail("lang", e.toString());
        }
    }

    // ============================================================ behavior
    // The checks below do not assert "registered and loaded". They EXECUTE
    // core 1.12 gameplay loops on the live server (crafting stations, mining,
    // research progression, entity AI, item interactions) so a regression
    // that breaks a loop is caught here, not in a player's world.

    /** A real, throwaway ServerPlayer for capability/interaction checks. */
    private static ServerPlayer testPlayer(MinecraftServer server) {
        var profile = new GameProfile(java.util.UUID.randomUUID(), "smoketest");
        return new ServerPlayer(server, server.overworld(), profile,
                ClientInformation.createDefault());
    }

    /** An air BlockPos three above the motion-blocking surface at (x, z). */
    private static BlockPos airPos(ServerLevel level, int x, int z) {
        level.getChunkSource().getChunk(x >> 4, z >> 4, true);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 3;
        return new BlockPos(x, y, z);
    }

    /** Content equality for AspectList (no equals() override on the class). */
    private static boolean aspectsEqual(thaumcraft.api.aspects.AspectList a, thaumcraft.api.aspects.AspectList b) {
        if (a == null || b == null) return a == b;
        var ca = a.copy(); var cb = b.copy();
        for (thaumcraft.api.aspects.Aspect aspect : ca.getAspects()) {
            if (ca.getAmount(aspect) != cb.getAmount(aspect)) return false;
            cb.remove(aspect);
        }
        return cb.size() == 0;
    }

    // -- aura nodes (TC6/Thaumaturge node system) ------------------------
    /** Deterministic generation: same seed/position rolls the same node. */
    private static void checkNodeGeneration(MinecraftServer server) {
        try {
            var level = (net.minecraft.server.level.ServerLevel) server.overworld();
            var pos = new net.minecraft.core.BlockPos(1600, 0, 1600);
            var a = thaumcraft.common.world.node.NodeGenerator.rollForTest(
                    level, pos, net.minecraft.util.RandomSource.create(1234), 18, 100);
            var b = thaumcraft.common.world.node.NodeGenerator.rollForTest(
                    level, pos, net.minecraft.util.RandomSource.create(1234), 18, 100);
            if (a == null || b == null) {
                fail("node-generation", "generator returned no node data");
                return;
            }
            if (a.type() != b.type() || a.modifier() != b.modifier()
                    || !aspectsEqual(a.held(), b.held())
                    || !aspectsEqual(a.base(), b.base())) {
                fail("node-generation", "same seed rolled different nodes: " + a + " vs " + b);
                return;
            }
            if (a.held().visSize() <= 0) {
                fail("node-generation", "rolled node holds no vis: " + a);
                return;
            }
            pass("node-generation (seed-deterministic roll, type=" + a.type() + ", modifier=" + a.modifier() + ", held=" + a.held() + ")");
        } catch (Throwable t) {
            fail("node-generation", t.toString());
        }
    }

    /** Wand tap drains the node and fills the gauntlet buffer. */
    private static void checkNodeWandTap(MinecraftServer server) {
        try {
            var level = (net.minecraft.server.level.ServerLevel) server.overworld();
            var player = testPlayer(server);
            var knowledge = thaumcraft.common.lib.capabilities.ThaumcraftCapabilities
                    .getKnowledge(player).orElse(null);
            if (knowledge == null) {
                fail("node-wand-tap", "no knowledge attachment on test player");
                return;
            }
            var nodePos = airPos(level, 1700, 1700);
            level.setBlock(nodePos, ModBlocks.NODE.get().defaultBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_ALL);
            if (!(level.getBlockEntity(nodePos) instanceof thaumcraft.common.tiles.node.TileNode node)) {
                fail("node-wand-tap", "placed node has no TileNode");
                return;
            }
            var held = new thaumcraft.api.aspects.AspectList();
            held.add(thaumcraft.api.aspects.Aspect.AIR, 5);
            var base = held.copy();
            node.applyNodeData(new thaumcraft.common.tiles.node.TileNode.NodeSnapshot(
                    thaumcraft.common.world.node.NodeType.NORMAL, null, held, base));

            var wand = new net.minecraft.world.item.ItemStack(ModItems.CASTER_BASIC.get());
            // Base strength 1: one tap moves exactly one AIR vis into the buffer.
            boolean ok = thaumcraft.common.world.node.NodeWandTap.tap(node, level, player, wand);
            if (!ok || thaumcraft.common.items.casters.ItemCaster.bufferAmountOf(wand, thaumcraft.api.aspects.Aspect.AIR) != 1
                    || node.getAspects().getAmount(thaumcraft.api.aspects.Aspect.AIR) != 4) {
                fail("node-wand-tap", "base tap wrong: ok=" + ok
                        + " buffer=" + thaumcraft.common.items.casters.ItemCaster.bufferAmountOf(wand, thaumcraft.api.aspects.Aspect.AIR)
                        + " node=" + node.getAspects().getAmount(thaumcraft.api.aspects.Aspect.AIR));
                return;
            }
            // NODETAPPER1/NODETAPPER2 add +1 each: three per tap.
            knowledge.addResearch("NODETAPPER1");
            knowledge.setResearchStage("NODETAPPER1", 1);
            knowledge.addResearch("NODETAPPER2");
            knowledge.setResearchStage("NODETAPPER2", 1);
            int beforeNode = node.getAspects().getAmount(thaumcraft.api.aspects.Aspect.AIR);
            int beforeBuffer = thaumcraft.common.items.casters.ItemCaster.bufferAmountOf(wand, thaumcraft.api.aspects.Aspect.AIR);
            ok = thaumcraft.common.world.node.NodeWandTap.tap(node, level, player, wand);
            int gained = thaumcraft.common.items.casters.ItemCaster.bufferAmountOf(wand, thaumcraft.api.aspects.Aspect.AIR) - beforeBuffer;
            if (!ok || gained != 3 || node.getAspects().getAmount(thaumcraft.api.aspects.Aspect.AIR) != beforeNode - 3) {
                fail("node-wand-tap", "researched tap wrong: ok=" + ok + " gained=" + gained);
                return;
            }
            level.setBlock(nodePos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_ALL);
            pass("node-wand-tap (1/tap base, 3/tap with both tapper researches, node drained)");
        } catch (Throwable t) {
            fail("node-wand-tap", t.toString());
        }
    }

    /** Jarring a node converts it into a dormant node-jar block. */
    private static void checkNodeJar(MinecraftServer server) {
        try {
            var level = (net.minecraft.server.level.ServerLevel) server.overworld();
            var pos = airPos(level, 1800, 1800);
            level.setBlock(pos, ModBlocks.NODE.get().defaultBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_ALL);
            if (!(level.getBlockEntity(pos) instanceof thaumcraft.common.tiles.node.TileNode node)) {
                fail("node-jar", "placed node has no TileNode");
                return;
            }
            var held = new thaumcraft.api.aspects.AspectList();
            held.add(thaumcraft.api.aspects.Aspect.FIRE, 7);
            var base = held.copy();
            node.applyNodeData(new thaumcraft.common.tiles.node.TileNode.NodeSnapshot(
                    thaumcraft.common.world.node.NodeType.NORMAL,
                    thaumcraft.common.world.node.NodeModifier.BRIGHT, held, base));
            node.beginJarring(40);
            if (!node.isJarring()) {
                fail("node-jar", "beginJarring did not start the countdown");
                return;
            }
            for (int i = 0; i < 41 && level.getBlockEntity(pos) instanceof thaumcraft.common.tiles.node.TileNode; i++) {
                ((thaumcraft.common.tiles.node.TileNode) level.getBlockEntity(pos)).serverTick(level, pos);
            }
            if (!level.getBlockState(pos).is(ModBlocks.NODE_JAR.get())) {
                fail("node-jar", "node did not convert to node jar after countdown, is " + level.getBlockState(pos).getBlock());
                return;
            }
            if (!(level.getBlockEntity(pos) instanceof thaumcraft.common.tiles.node.TileJarNode jar)) {
                fail("node-jar", "node jar block has no TileJarNode");
                return;
            }
            if (jar.kind() != thaumcraft.common.world.node.NodeType.NORMAL
                    || jar.getAspects().getAmount(thaumcraft.api.aspects.Aspect.FIRE) != 7) {
                fail("node-jar", "jar lost node data: type=" + jar.kind() + " held=" + jar.getAspects());
                return;
            }
            // Modifier degrades 75% of the time; whatever it is, it must be
            // BRIGHT/PALE/FADING/null - a corrupt value would fail the check above.
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_ALL);
            pass("node-jar (40-tick capture, data preserved, modifier=" + jar.trait() + ")");
        } catch (Throwable t) {
            fail("node-jar", t.toString());
        }
    }

    /** Primordial pearl mutates node aspects deterministically per seed. */
    private static void checkNodePearl(MinecraftServer server) {
        try {
            var level = (net.minecraft.server.level.ServerLevel) server.overworld();
            BlockPos[] pos = { airPos(level, 1900, 1900), airPos(level, 1900, 1920) };
            var nodes = new java.util.ArrayList<thaumcraft.common.tiles.node.TileNode>();
            for (BlockPos p : pos) {
                level.setBlock(p, ModBlocks.NODE.get().defaultBlockState(),
                        net.minecraft.world.level.block.Block.UPDATE_ALL);
                if (!(level.getBlockEntity(p) instanceof thaumcraft.common.tiles.node.TileNode n)) {
                    fail("node-pearl", "placed node has no TileNode");
                    return;
                }
                var held = new thaumcraft.api.aspects.AspectList();
                held.add(thaumcraft.api.aspects.Aspect.AIR, 6);
                held.add(thaumcraft.api.aspects.Aspect.LIFE, 4);
                var base = held.copy();
                n.applyNodeData(new thaumcraft.common.tiles.node.TileNode.NodeSnapshot(
                        thaumcraft.common.world.node.NodeType.NORMAL, null, held, base));
                nodes.add(n);
            }
            nodes.get(0).applyPrimordialPearl(net.minecraft.util.RandomSource.create(99), false);
            nodes.get(1).applyPrimordialPearl(net.minecraft.util.RandomSource.create(99), false);
            var a = nodes.get(0).snapshot();
            var b = nodes.get(1).snapshot();
            if (a.modifier() != b.modifier()
                    || !aspectsEqual(a.held(), b.held())
                    || !aspectsEqual(a.base(), b.base())) {
                fail("node-pearl", "same seed gave different pearl results: " + a + " vs " + b);
                return;
            }
            // The pearl must actually change something: base or held differs
            // from the original, and held never exceeds base per aspect.
            var orig = new thaumcraft.api.aspects.AspectList();
            orig.add(thaumcraft.api.aspects.Aspect.AIR, 6);
            orig.add(thaumcraft.api.aspects.Aspect.LIFE, 4);
            boolean changed = !aspectsEqual(a.base(), orig)
                    || a.modifier() != null
                    || !aspectsEqual(a.held(), orig);
            if (!changed) {
                fail("node-pearl", "pearl changed nothing (base=" + a.base() + ", modifier=" + a.modifier() + ")");
                return;
            }
            for (thaumcraft.api.aspects.Aspect aspect : a.held().getAspects()) {
                if (a.held().getAmount(aspect) > a.base().getAmount(aspect)) {
                    fail("node-pearl", "held " + aspect + " exceeds base after pearl");
                    return;
                }
            }
            for (BlockPos p : pos) {
                level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                        net.minecraft.world.level.block.Block.UPDATE_ALL);
            }
            pass("node-pearl (deterministic mutation, base=" + a.base() + ", modifier=" + a.modifier() + ")");
        } catch (Throwable t) {
            fail("node-pearl", t.toString());
        }
    }

    // -- research: the progression mechanism behind the nomicon flow (the
    //    path the 2026-09-30 FIRSTSTEPS deadlock broke) ------------------
    private static void checkResearchProgress(MinecraftServer server) {
        try {
            var p1 = testPlayer(server);
            IPlayerKnowledge k1 = ThaumcraftCapabilities.getKnowledge(p1).orElse(null);
            if (k1 == null) {
                fail("research-progress", "no knowledge attachment on fresh player");
                return;
            }
            if (k1.isResearchKnown("FIRSTSTEPS@1")) {
                fail("research-progress", "fresh player already knows FIRSTSTEPS@1");
                return;
            }
            k1.addResearch("!gotthaumonomicon"); // 1.12: pickup-flag parent
            if (!ResearchManager.progressResearch(p1, "FIRSTSTEPS", false)) {
                fail("research-progress", "progressResearch(FIRSTSTEPS) rejected with parent flag set");
                return;
            }
            if (k1.getResearchStage("FIRSTSTEPS") != 1 || !k1.isResearchKnown("FIRSTSTEPS@1")) {
                fail("research-progress", "stage=" + k1.getResearchStage("FIRSTSTEPS") + " after first progress");
                return;
            }
            if (!ResearchManager.progressResearch(p1, "FIRSTSTEPS", false)) {
                fail("research-progress", "second progress rejected");
                return;
            }
            if (k1.getResearchStage("FIRSTSTEPS") != 2 || !k1.isResearchKnown("FIRSTSTEPS@2")) {
                fail("research-progress", "stage=" + k1.getResearchStage("FIRSTSTEPS") + " after second progress");
                return;
            }
            // control: without the parent flag, progression must be blocked
            var p2 = testPlayer(server);
            IPlayerKnowledge k2 = ThaumcraftCapabilities.getKnowledge(p2).orElse(null);
            if (k2 != null && ResearchManager.progressResearch(p2, "FIRSTSTEPS", false)) {
                fail("research-progress", "progressed WITHOUT parent flag (gate bypass)");
                return;
            }
            p1.discard();
            p2.discard();
            pass("research-progress (FIRSTSTEPS 0->1->2 with parent flag; blocked without)");
        } catch (Throwable t) {
            fail("research-progress", t.toString());
        }
    }

    // -- crucible: the full 1.12 melt loop. The crucible can only gain
    //    aspects by DISSOLVING items (setAspects is a deliberate no-op), so
    //    this simulates real play: heat + water, dissolve funders, then
    //    throw in the catalyst and demand the result.
    //    hedge_clay: catalyst dirt, needs aqua 5 + terra 5 -> clay_ball.
    //    water_bucket dissolves to WATER 20 (+VOID/METAL), dirt to EARTH 5.
    private static void checkCrucibleCraft(MinecraftServer server) {
        try {
            var level = server.overworld();
            var pos = airPos(level, 128, 128);
            // Clear any crucible left by a previous smoke run (the dev world is
            // persistent): setBlock with the same state would keep the stale tile.
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(pos, ModBlocks.CRUCIBLE.get().defaultBlockState(), 3);
            var tile = (TileCrucible) level.getBlockEntity(pos);
            if (tile == null) {
                fail("crucible-craft", "no TileCrucible created at " + pos);
                return;
            }
            tile.heat = 200;
            FluidTanks.fill(tile.getTankHandler(), new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000), false);

            // 1) 1.12: a machine-dropped item (no thrower player) never crafts,
            //    it only dissolves. Water bucket must fund WATER 20, NOT craft.
            var afterBucket = tile.attemptSmelt(new ItemStack(Items.WATER_BUCKET), (net.minecraft.world.entity.player.Player) null);
            if (afterBucket != null) {
                fail("crucible-craft", "water bucket survived dissolution: " + afterBucket);
                return;
            }
            if (tile.aspects.getAmount(Aspect.WATER) != 20) {
                fail("crucible-craft", "dissolved water bucket did not add WATER 20 (has "
                        + tile.aspects.getAmount(Aspect.WATER) + ", pool=" + tile.aspects + ")");
                return;
            }
            // 2) dissolve dirt (still no player) -> must fund EARTH 5
            var afterDirt = tile.attemptSmelt(new ItemStack(Items.DIRT), (net.minecraft.world.entity.player.Player) null);
            if (afterDirt != null) {
                fail("crucible-craft", "funding dirt survived dissolution: " + afterDirt);
                return;
            }
            if (tile.aspects.getAmount(Aspect.EARTH) != 5) {
                fail("crucible-craft", "dissolved dirt did not add EARTH 5 (pool=" + tile.aspects + ")");
                return;
            }
            // 3) 1.12 strict research gate: no player -> no recipe at all;
            //    player lacking HEDGEALCHEMY -> no recipe; complete player -> hedge_clay.
            var dirt = new ItemStack(Items.DIRT);
            if (thaumcraft.common.lib.crafting.ThaumcraftCraftingManager
                    .findMatchingCrucibleRecipe(tile.aspects, dirt, null, level) != null) {
                fail("crucible-craft", "recipe matched with a null player (1.12 requires a live thrower)");
                return;
            }
            var ignorant = testPlayer(server);
            if (thaumcraft.common.lib.crafting.ThaumcraftCraftingManager
                    .findMatchingCrucibleRecipe(tile.aspects, dirt, ignorant, level) != null) {
                fail("crucible-craft", "hedge_clay matched a player who lacks HEDGEALCHEMY (strict gate bypassed)");
                return;
            }
            var scholar = testPlayer(server);
            var scholarKnowledge = ThaumcraftCapabilities.getKnowledge(scholar).orElse(null);
            if (scholarKnowledge == null) {
                fail("crucible-craft", "no knowledge attachment on test player");
                return;
            }
            scholarKnowledge.addResearch("HEDGEALCHEMY");
            scholarKnowledge.setResearchStage("HEDGEALCHEMY", 4); // 4 stages -> COMPLETE
            var expected = thaumcraft.common.lib.crafting.ThaumcraftCraftingManager
                    .findMatchingCrucibleRecipe(tile.aspects, dirt, scholar, level);
            if (expected == null || !expected.getResultItem().is(Items.CLAY_BALL)) {
                fail("crucible-craft", "HEDGEALCHEMY-complete player got no hedge_clay (got "
                        + (expected == null ? "null"
                        : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(expected.getResultItem().getItem())) + ")");
                return;
            }

            // 4) full craft through the player path: pool drained, 50 mb water
            //    drained, clay_ball ejected above the crucible.
            //    NOTE: the 1.12-mechanics side effects (pool/water drain,
            //    result ejection) all run BEFORE the ItemCraftedEvent. The
            //    unconnected smoke player has no network connection, so the
            //    event's recipe-book packet listener NPEs — a real (connected)
            //    player never hits that, and we verify the mechanics below
            //    regardless of whether the event fired.
            int waterBefore = FluidTanks.getAmount(tile.getTankHandler());
            ItemStack out;
            Throwable craftError = null;
            try {
                out = tile.attemptSmelt(dirt, scholar);
            } catch (Throwable t) {
                craftError = t;
                out = null;
            }
            if (craftError == null && out != null) {
                fail("crucible-craft", "catalyst survived (expected craft): " + out + " (pool=" + tile.aspects + ")");
                return;
            }
            if (tile.aspects.getAmount(Aspect.WATER) != 15 || tile.aspects.getAmount(Aspect.EARTH) != 0) {
                fail("crucible-craft", "pool not drained by the recipe (aqua 5, terra 5): " + tile.aspects);
                return;
            }
            if (FluidTanks.getAmount(tile.getTankHandler()) != waterBefore - 50) {
                fail("crucible-craft", "craft did not drain 50 mb water (" + waterBefore + " -> "
                        + FluidTanks.getAmount(tile.getTankHandler()) + ")");
                return;
            }
            boolean dropped = false;
            for (var e : level.getEntities().getAll()) {
                if (e instanceof ItemEntity ie && e.position().distanceTo(Vec3.atCenterOf(pos)) < 4
                        && !ie.getItem().isEmpty() && ie.getItem().is(Items.CLAY_BALL)) {
                    dropped = true;
                    break;
                }
            }
            if (!dropped) {
                fail("crucible-craft", "clay_ball never left the crucible as an ItemEntity (pool=" + tile.aspects + ")");
                return;
            }
            if (craftError != null && !(craftError instanceof NullPointerException
                    && String.valueOf(craftError.getMessage()).contains("player.connection"))) {
                fail("crucible-craft", "craft threw unexpectedly: " + craftError);
                return;
            }
            pass("crucible-craft (no-player dissolve; strict HEDGEALCHEMY gate; player craft -> clay_ball, water " + waterBefore + "->" + (waterBefore - 50) + ")");
        } catch (Throwable t) {
            fail("crucible-craft", t.toString());
        }
    }

    // -- infusion: every parsed recipe's ingredients resolve + assemble ---
    private static void checkInfusionRecipes(MinecraftServer server) {
        try {
            List<String> problems = new ArrayList<>();
            int total = 0, withResult = 0, assembled = 0;
            for (RecipeHolder<?> h : server.getRecipeManager().recipeMap().byType(ModRecipeTypes.INFUSION.get())) {
                if (!(h.value() instanceof InfusionRecipeType r)) {
                    continue;
                }
                total++;
                // IE* infusion-enchantment recipes have no central item (the tool
                // in the player's matrix is the input at craft time) — null is valid there.
                if (r.getCentralItem() != null && r.getCentralItem().isEmpty()) {
                    problems.add(h.id().toString() + ": unresolvable central item");
                }
                for (Ingredient comp : r.getComponents()) {
                    if (comp.isEmpty()) {
                        problems.add(h.id().toString() + ": unresolvable component");
                    }
                }
                var res = r.getResultItem();
                if (!res.isEmpty()) {
                    withResult++;
                    if (assembled < 3) {
                        var a = r.assemble(null);
                        if (a.isEmpty() || !a.is(res.getItem())) {
                            problems.add(h.id().toString() + ": assemble() != result");
                        } else {
                            assembled++;
                        }
                    }
                }
            }
            if (total == 0) {
                fail("infusion-recipes", "no infusion recipes parsed");
                return;
            }
            if (problems.isEmpty()) {
                pass("infusion-recipes (" + total + " parsed, " + withResult + " with result item, "
                        + assembled + " assemble() spot-checks ok)");
            } else {
                fail("infusion-recipes", total + " parsed, problems: " + problems.subList(0, Math.min(5, problems.size())));
            }
        } catch (Throwable t) {
            fail("infusion-recipes", t.toString());
        }
    }

    // -- refining: real iron-ore loot table runs, REFINING pickaxe --------
    //    converts raw iron to clusters through the live global-modifier
    //    pipeline (LootTable -> CommonHooks.modifyLoot -> our modifier).
    private static void checkRefiningLoot(MinecraftServer server) {
        try {
            var level = server.overworld();
            var tableKey = net.minecraft.resources.ResourceKey.create(
                    Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("minecraft", "blocks/iron_ore"));
            var table = server.reloadableRegistries().getLootTable(tableKey);
            var pick = new ItemStack(Items.IRON_PICKAXE);
            EnumInfusionEnchantment.addInfusionEnchantment(pick, EnumInfusionEnchantment.REFINING, 4);
            if (EnumInfusionEnchantment.getInfusionEnchantmentLevel(pick, EnumInfusionEnchantment.REFINING) != 4) {
                fail("refining-loot", "REFINING NBT write/read round-trip broken");
                return;
            }
            int converted = 0;
            for (int seed = 0; seed < 64; seed++) {
                var drops = rollOreLoot(server, level, table, pick, spreadSeed(seed));
                for (var s : drops) {
                    if (s.is(ModItems.CLUSTER_IRON.get())) {
                        converted++;
                    }
                }
            }
            // chance = (1+4)*0.125 = 0.625 per drop; 64 rolls expect ~40
            // ([20,60] is a ~5-sigma band, so flakes are impossible)
            if (converted < 20 || converted > 60) {
                fail("refining-loot", converted + "/64 rolls converted (expected ~40); loaded global modifiers: "
                        + loadedGlobalModifiers(server)
                        + "; specialMiningResult keys: " + thaumcraft.common.lib.utils.Utils.specialMiningResult.keySet()
                        + "; sample drops: " + describeDrops(rollOreLoot(server, level, table, pick, spreadSeed(1)))
                        + "; direct findSpecialMiningResult(raw_iron, 0.625): " + directMiningResult());
                return;
            }
            int plainConverted = 0;
            var plain = new ItemStack(Items.IRON_PICKAXE);
            for (int seed = 0; seed < 16; seed++) {
                for (var s : rollOreLoot(server, level, table, plain, spreadSeed(seed + 1000))) {
                    if (s.is(ModItems.CLUSTER_IRON.get())) {
                        plainConverted++;
                    }
                }
            }
            if (plainConverted != 0) {
                fail("refining-loot", plainConverted + " conversions with a PLAIN pickaxe (no REFINING); loaded global modifiers: "
                        + loadedGlobalModifiers(server));
                return;
            }
            pass("refining-loot (" + converted + "/64 REFINING-4 rolls -> cluster_iron; 0/16 plain rolls)");
        } catch (Throwable t) {
            fail("refining-loot", t.toString());
        }
    }

    /** 20 direct (modifier-independent) findSpecialMiningResult rolls: cluster count. */
    private static String directMiningResult() {
        var rand = net.minecraft.util.RandomSource.create();
        int clusters = 0;
        for (int i = 0; i < 20; i++) {
            if (thaumcraft.common.lib.utils.Utils.findSpecialMiningResult(
                    new ItemStack(Items.RAW_IRON), 0.625f, rand).is(ModItems.CLUSTER_IRON.get())) {
                clusters++;
            }
        }
        return clusters + "/20";
    }

    private static String describeDrops(it.unimi.dsi.fastutil.objects.ObjectArrayList<ItemStack> drops) {
        var sb = new StringBuilder("[");
        int n = 0;
        for (var s : drops) {
            if (n++ > 0) sb.append(", ");
            if (n > 6) { sb.append(", ..."); break; }
            sb.append(s.isEmpty() ? "<empty>"
                    : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()) + "x" + s.getCount());
        }
        return sb.append("]").toString();
    }

    /** Which global loot modifiers did the datapack load (diagnostic for refining-loot)? */
    private static String loadedGlobalModifiers(MinecraftServer server) {
        try {
            var mgr = (net.neoforged.neoforge.common.loot.LootModifierManager) server.getServerResources()
                    .managers().getListener(net.neoforged.neoforge.resource.NeoForgeReloadListeners.LOOT_MODIFIERS_KEY);
            var ids = new java.util.ArrayList<String>();
            for (var m : mgr.getSortedModifiers()) ids.add(mgr.getId(m).toString());
            return ids.toString();
        } catch (Throwable t) {
            return "(manager unavailable: " + t + ")";
        }
    }

    private static ObjectArrayList<ItemStack> rollOreLoot(MinecraftServer server, ServerLevel level,
                                                          net.minecraft.world.level.storage.loot.LootTable table,
                                                          ItemStack tool, long seed) {
        var orePos = new net.minecraft.core.BlockPos(0, 64, 0);
        var params = new LootParams.Builder(level)
                .withParameter(LootContextParams.BLOCK_STATE, Blocks.IRON_ORE.defaultBlockState())
                .withParameter(LootContextParams.TOOL, tool)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(orePos))
                .withLuck(0.0f)
                .create(table.getParamSet());
        // A distinct, well-spread seed per roll. A fixed seed would make every
        // "roll" the same deterministic draw; small consecutive seeds are also
        // no good, because the loot random is an LCG (LegacyRandomSource) whose
        // first outputs are correlated for nearby seeds. spreadSeed() mixes the
        // index (splitmix64) so the first draws are uniformly distributed.
        return table.getRandomItems(params, seed);
    }

    /** splitmix64 finalizer: spreads small consecutive inputs across the 64-bit space. */
    private static long spreadSeed(int i) {
        long z = i + 1L;
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    // -- golem: spawn a default (BASIC/BASIC/WALKER) golem and tick it ----
    private static void checkGolemTick(MinecraftServer server) {
        try {
            var level = server.overworld();
            var props = GolemProperties.fromLong(0L);
            var comps = ((GolemProperties) props).generateComponents();
            if (comps.length == 0) {
                fail("golem-tick", "default golem properties generated no components");
                return;
            }
            // A headless smoke world has NO players, so nothing keeps chunks
            // loaded. Force-load the spawn chunk synchronously (we are on the
            // server thread, and no server tick — the only thing that could
            // unload it — runs between here and the manual g.tick() calls).
            var spawn = level.getRespawnData().pos();
            var at = new net.minecraft.core.BlockPos(spawn.getX() + 3, spawn.getY(), spawn.getZ() + 3);
            // getChunkAt does NOT force a load; an unloaded chunk would make
            // addFreshEntity silently fail (isRemoved() == true). Force it.
            level.getChunkSource().getChunk(at.getX() >> 4, at.getZ() >> 4, true);
            var g = new EntityThaumcraftGolem(ModEntities.THAUMCRAFT_GOLEM.get(), level);
            g.setProperties(props);
            g.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            level.addFreshEntity(g);
            // A construct only persists if it was placed properly (the placer
            // items call setValidSpawn). Simulate that, as a player-placed golem
            // would — otherwise the safety guard discards it on the first tick.
            g.setValidSpawn();
            for (int i = 0; i < 20 && !g.isRemoved(); i++) {
                g.tick();
            }
            if (g.isRemoved()) {
                fail("golem-tick", "golem died/removed within 20 ticks of spawning at the world spawn (health="
                        + g.getHealth() + ", pos=" + g.blockPosition() + ", deadOrDying=" + g.isDeadOrDying() + ")");
                return;
            }
            if (!java.lang.Double.isFinite(g.getX()) || !java.lang.Double.isFinite(g.getY())) {
                fail("golem-tick", "golem position NaN/Inf after ticking");
                return;
            }
            g.discard();
            pass("golem-tick (default golem spawned, 20 ticks alive, parts registry resolved " + comps.length + " components)");
        } catch (Throwable t) {
            fail("golem-tick", t.toString());
        }
    }

    // -- golem follow-owner: deterministic movement toward a distant owner --
    //    (F125: proves the follow AI actually closes the gap, via navigation
    //    or the 1.12 no-path teleport fallback — either way the distance
    //    must shrink.)
    private static void checkGolemFollow(MinecraftServer server) {
            net.minecraft.world.entity.Mob owner = null;
        EntityThaumcraftGolem g = null;
        try {
            var level = server.overworld();
            var spawn = level.getRespawnData().pos();

            // Build a flat stone platform in chunk-aligned coordinates so the
            // follow AI has guaranteed-walkable terrain to act on (a raw
            // headless world has uneven terrain + unloaded chunks, which makes
            // both pathfinding and the 1.12 teleport-ring fallback
            // non-deterministic).
            int bx = spawn.getX() & ~15;
            int bz = spawn.getZ() & ~15;
            int floorY = 100;
            for (int dx = 0; dx < 32; dx++) {
                for (int dz = 0; dz < 32; dz++) {
                    level.setBlock(new net.minecraft.core.BlockPos(bx + dx, floorY, bz + dz),
                            net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                    for (int dy = 1; dy <= 6; dy++) {
                        level.setBlock(new net.minecraft.core.BlockPos(bx + dx, floorY + dy, bz + dz),
                                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
            // Force-load the whole 2x2 chunk block under the platform.
            for (int cx = 0; cx < 2; cx++) {
                for (int cz = 0; cz < 2; cz++) {
                    level.getChunkSource().getChunk((bx >> 4) + cx, (bz >> 4) + cz, true);
                }
            }

            // Golem and a stationary no-AI owner, 14 blocks apart on the floor.
            var golemAt = new net.minecraft.core.BlockPos(bx + 4, floorY + 1, bz + 4);
            var ownerAt = new net.minecraft.core.BlockPos(bx + 18, floorY + 1, bz + 4);

            var ironType = (net.minecraft.world.entity.EntityType<net.minecraft.world.entity.animal.golem.IronGolem>)
                    BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath("minecraft", "iron_golem"));
            owner = new net.minecraft.world.entity.animal.golem.IronGolem(ironType, level);
            owner.setNoAi(true);
            owner.setPos(ownerAt.getX() + 0.5, ownerAt.getY(), ownerAt.getZ() + 0.5);
            level.addFreshEntity(owner);

            g = new EntityThaumcraftGolem(ModEntities.THAUMCRAFT_GOLEM.get(), level);
            g.setProperties(GolemProperties.fromLong(0L));
            g.setPos(golemAt.getX() + 0.5, golemAt.getY(), golemAt.getZ() + 0.5);
            level.addFreshEntity(g);
            g.setValidSpawn();
            // The firstRun guard teleports a golem to its home when the two
            // differ; pin the home to the spawn point so it stays put.
            g.setHomeTo(golemAt, 32);
            g.setOwnerUUID(owner.getUUID());
            g.setFollowingOwner(true);

            double startDist = g.distanceToSqr(owner);
            if (startDist < 100.0) {
                fail("golem-follow", "setup: owner too close (" + startDist + ")");
                g.discard(); owner.discard();
                return;
            }
            // Ticking an entity is what inserts it into its chunk's entity
            // list; LevelEntityGetter.get(UUID) (the golem's owner-resolution
            // path) only sees entities ticked at least once. Tick both.
            for (int i = 0; i < 200 && !g.isRemoved(); i++) {
                g.tick();
                owner.tick();
            }
            if (g.isRemoved()) {
                fail("golem-follow", "golem removed during follow (health=" + g.getHealth() + ")");
                owner.discard();
                return;
            }
            double endDist = g.distanceToSqr(owner);
            if (endDist >= startDist) {
                java.util.List<String> goals = new java.util.ArrayList<>();
                try {
                    var gf = net.minecraft.world.entity.Mob.class.getDeclaredField("goalSelector");
                    gf.setAccessible(true);
                    var gs = (net.minecraft.world.entity.ai.goal.GoalSelector) gf.get(g);
                    for (var w : gs.getAvailableGoals()) goals.add(w.getGoal().getClass().getSimpleName() + (w.isRunning() ? "*" : ""));
                } catch (Throwable rt) { goals.add("reflect-fail:" + rt); }
                fail("golem-follow", "golem did not move toward owner (start=" + startDist
                        + ", end=" + endDist + ", pos=" + g.blockPosition()
                        + ", ownerResolved=" + (g.getOwner() != null)
                        + ", onGround=" + g.onGround()
                        + ", goals=" + goals + ")");
                g.discard(); owner.discard();
                return;
            }
            pass("golem-follow (follow-owner closed gap " + startDist + " -> " + endDist + " in 200 ticks)");
            g.discard();
            owner.discard();
        } catch (Throwable t) {
            fail("golem-follow", t.toString());
            if (g != null) g.discard();
            if (owner != null) owner.discard();
        }
    }

    // -- phial: full right-click fill against a live jar block ------------
    private static void checkPhialFill(MinecraftServer server) {
        try {
            var level = server.overworld();
            var jarPos = airPos(level, 130, 128);
            level.setBlock(jarPos, ModBlocks.JAR_NORMAL.get().defaultBlockState(), 3);
            var jar = (TileJar) level.getBlockEntity(jarPos);
            if (jar == null) {
                fail("phial-jar", "no TileJar created at " + jarPos);
                return;
            }
            jar.setAspects(new AspectList().add(Aspect.AIR, 100));
            if (!jar.doesContainerContainAmount(Aspect.AIR, 10)) {
                fail("phial-jar", "jar with 100 aer claims it lacks 10");
                return;
            }
            var p = testPlayer(server);
            var empty = new ItemStack(ModItems.PHIAL_EMPTY.get());
            p.getInventory().setSelectedItem(empty);
            var hit = new BlockHitResult(Vec3.atCenterOf(jarPos), Direction.UP, jarPos, false);
            var ctx = new UseOnContext(level, p, InteractionHand.MAIN_HAND, empty, hit) {
            };
            var res = ((ItemPhial) empty.getItem()).onItemUseFirst(empty, ctx);
            if (res != net.minecraft.world.InteractionResult.CONSUME) {
                fail("phial-jar", "right-click on a full jar returned " + res + " (expected CONSUME)");
                return;
            }
            if (jar.containerContains(Aspect.AIR) != 90) {
                fail("phial-jar", "jar holds " + jar.containerContains(Aspect.AIR) + " aer after a 10-aer fill (expected 90)");
                return;
            }
            ItemStack filledInInv = ItemStack.EMPTY;
            for (var s : p.getInventory().getNonEquipmentItems()) {
                if (s.is(ModItems.PHIAL_FILLED.get())) {
                    filledInInv = s;
                    break;
                }
            }
            if (filledInInv.isEmpty()) {
                fail("phial-jar", "no filled phial in the player's inventory after filling");
                return;
            }
            var got = ((ItemPhial) filledInInv.getItem()).getAspects(filledInInv);
            if (got == null || got.getAmount(Aspect.AIR) != 10) {
                fail("phial-jar", "filled phial has wrong contents: " + got);
                return;
            }
            p.discard();
            pass("phial-jar (right-click filled phial from live jar: CONSUME, jar 100->90 aer, phial = 10 aer)");
        } catch (Throwable t) {
            fail("phial-jar", t.toString());
        }
    }

    /** 26.3 Biomes constants are ResourceKeys; the handler wants Holders. */
    private static Holder<net.minecraft.world.level.biome.Biome> biomeHolder(RegistryAccess access, net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> key) {
        return access.lookupOrThrow(Registries.BIOME).get(key.identifier()).orElseThrow();
    }

    // -- biome aura modifiers (1.12 BiomeHandler parity) -------------------
    private static void checkBiomeAura(MinecraftServer server) {
        try {
            var access = server.registryAccess();
            float plains = BiomeHandler.getAuraModifier(biomeHolder(access, Biomes.PLAINS));
            float mushroom = BiomeHandler.getAuraModifier(biomeHolder(access, Biomes.MUSHROOM_FIELDS));
            float ocean = BiomeHandler.getAuraModifier(biomeHolder(access, Biomes.OCEAN));
            float deepDark = BiomeHandler.getAuraModifier(biomeHolder(access, Biomes.DEEP_DARK));
            if (plains != 0.3f || mushroom != 0.75f || ocean != 0.33f || deepDark != 0.5f) {
                fail("biome-aura", "modifiers off: plains=" + plains + " (want 0.3 specific), mushroom="
                        + mushroom + " (want 0.75 specific), ocean=" + ocean + " (want 0.33 IS_OCEAN tag),"
                        + " deep_dark=" + deepDark + " (want 0.5 no-match default)");
                return;
            }
            var aspectPlains = BiomeHandler.getBiomeAspect(biomeHolder(access, Biomes.PLAINS));
            var aspectMushroom = BiomeHandler.getBiomeAspect(biomeHolder(access, Biomes.MUSHROOM_FIELDS));
            if (aspectPlains != Aspect.AIR || aspectMushroom != Aspect.ORDER) {
                fail("biome-aura", "aspects off: plains=" + aspectPlains + " (want AIR), mushroom="
                        + aspectMushroom + " (want ORDER)");
                return;
            }
            pass("biome-aura (plains 0.3, mushroom 0.75, ocean 0.33 via tag, deep_dark 0.5 default; aspects AIR/ORDER)");
        } catch (Throwable t) {
            fail("biome-aura", t.toString());
        }
    }

    // -- aura base is deterministic per world seed + chunk position --------
    private static void checkAuraDeterminism(MinecraftServer server) {
        try {
            var level = server.overworld();
            // Force-load chunks far from the spawn area. The persistent dev world
            // may hold stale bases from before the deterministic fix near spawn,
            // so try several distant positions and use the first freshly generated
            // chunk that matches the formula (one that doesn't match means stale
            // pre-fix data at that spot, not a formula failure).
            int[][] candidates = { { 1000, 1000 }, { 2000, 1000 }, { 1000, 2000 }, { 3000, 3000 } };
            for (int[] c : candidates) {
                var chunkPos = new net.minecraft.world.level.ChunkPos(c[0] << 4, c[1] << 4);
                level.getChunkSource().getChunk(chunkPos.x(), chunkPos.z(), true);
                var ac = thaumcraft.common.world.aura.AuraHandler.getAuraChunk(level.dimension(), chunkPos.x(), chunkPos.z());
                if (ac == null) continue;
                short stored = ac.getBase();
                short expected = thaumcraft.common.world.aura.AuraHandler.computeBaseAura(
                        level, chunkPos, thaumcraft.common.world.aura.AuraHandler.chunkAuraRandom(level.getSeed(), chunkPos));
                if (stored == expected) {
                    // Two recomputations with fresh (but identically seeded) sources must agree
                    short again = thaumcraft.common.world.aura.AuraHandler.computeBaseAura(
                            level, chunkPos, thaumcraft.common.world.aura.AuraHandler.chunkAuraRandom(level.getSeed(), chunkPos));
                    if (again != expected) {
                        fail("aura-determinism", "recomputation not reproducible: " + expected + " vs " + again);
                        return;
                    }
                    pass("aura-determinism (chunk " + chunkPos.x() + "," + chunkPos.z() + " base=" + stored + " matches the worldSeed^chunkPos formula)");
                    return;
                }
            }
            fail("aura-determinism", "no freshly generated distant chunk matched the seed-deterministic formula (all stale?)");
        } catch (Throwable t) {
            fail("aura-determinism", t.toString());
        }
    }

    // -- flux pressure events (saturated-chunk trigger, exact cost, pick) --
    private static void checkFluxPressure(MinecraftServer server) {
        try {
            var level = server.overworld();
            // Force-load a distant chunk and saturate its flux (well above the
            // 0.75*base event threshold and the test cost).
            thaumcraft.common.world.aura.AuraChunk ac = null;
            net.minecraft.world.level.ChunkPos used = null;
            for (int[] c : new int[][]{ { 1500, 1500 }, { 2500, 1500 }, { 1500, 2500 } }) {
                var chunkPos = new net.minecraft.world.level.ChunkPos(c[0] << 4, c[1] << 4);
                level.getChunkSource().getChunk(chunkPos.x(), chunkPos.z(), true);
                var candidate = thaumcraft.common.world.aura.AuraHandler.getAuraChunk(
                        level.dimension(), chunkPos.x(), chunkPos.z());
                if (candidate == null) continue;
                float b = Math.max(1.0f, candidate.getBase());
                candidate.setFlux(b * 2.0f + 50.0f);
                ac = candidate;
                used = chunkPos;
                break;
            }
            if (ac == null) {
                fail("flux-pressure", "no distant chunk had aura data to saturate");
                return;
            }

            // A no-op event with a known cost: trigger() must fire and drain
            // exactly the cost from the chunk flux.
            thaumcraft.common.world.aura.pressure.FluxPressureEvent test =
                    new thaumcraft.common.world.aura.pressure.FluxPressureEvent() {
                        public String name() { return "smoke"; }
                        public int weight() { return 1; }
                        public float cost() { return 10.0f; }
                        public boolean allowedNearTaint() { return true; }
                        public boolean fire(net.minecraft.server.level.ServerLevel l,
                                net.minecraft.core.BlockPos p,
                                thaumcraft.common.world.aura.pressure.FluxPressureState s) {
                            return true;
                        }
                    };
            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(
                    used.x() << 4, 0, used.z() << 4);
            float before = ac.getFlux();
            boolean fired = thaumcraft.common.world.aura.pressure.FluxPressureEvents.trigger(
                    (net.minecraft.server.level.ServerLevel) level, pos, test);
            float drained = before - ac.getFlux();
            if (!fired) {
                fail("flux-pressure", "trigger returned false on a saturated chunk");
                return;
            }
            if (Math.abs(drained - 10.0f) > 0.01f) {
                fail("flux-pressure", "cost not drained exactly: " + drained);
                return;
            }

            // The weighted pick must draw from the catalog and vary.
            java.util.Set<String> names = new java.util.HashSet<>();
            for (int i = 0; i < 300; i++) {
                names.add(thaumcraft.common.world.aura.pressure.FluxPressureEventTypes
                        .choose(level.getRandom()).name());
            }
            if (names.size() < 2) {
                fail("flux-pressure", "weighted pick never varied: " + names);
                return;
            }

            // State queue/poll round-trip.
            var state = new thaumcraft.common.world.aura.pressure.FluxPressureState();
            state.queue(net.minecraft.core.BlockPos.ZERO);
            if (!net.minecraft.core.BlockPos.ZERO.equals(state.pollPending())
                    || state.pollPending() != null) {
                fail("flux-pressure", "queue/poll round-trip broken");
                return;
            }
            pass("flux-pressure (saturated trigger drains exact cost, weighted pick over "
                    + names.size() + " event types, queue round-trip)");
        } catch (Throwable t) {
            fail("flux-pressure", t.toString());
        }
    }

    // -- thaumatorium recipe queue (cap, remove, NBT roundtrip) -----------
    private static void checkThaumatoriumQueue(MinecraftServer server) {
        try {
            var level = server.overworld();
            var pos = airPos(level, 128, 140);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(pos, ModBlocks.THAUMATORIUM.get().defaultBlockState(), 3);
            var tile = (TileThaumatorium) level.getBlockEntity(pos);
            if (tile == null) {
                fail("thaumatorium-queue", "no TileThaumatorium created at " + pos);
                return;
            }
            // 1.12 parity: base capacity is 1 recipe; brain boxes raise it.
            if (tile.maxRecipes != 1) {
                fail("thaumatorium-queue", "default maxRecipes=" + tile.maxRecipes + " (want 1, 1.12 parity)");
                return;
            }
            // One brain box on a side (facing the thaumatorium) adds +2 slots.
            level.setBlock(pos.relative(Direction.EAST),
                    ModBlocks.BRAIN_BOX.get().defaultBlockState()
                            .setValue(thaumcraft.common.blocks.devices.BlockBrainBox.FACING, Direction.WEST), 3);
            tile.recountBrainBoxes();
            if (tile.maxRecipes != 3) {
                fail("thaumatorium-queue", "brain box did not raise maxRecipes to 3 (got " + tile.maxRecipes + ")");
                return;
            }
            tile.setMaxRecipes(5);
            for (int i = 1; i <= 5; i++) {
                if (!tile.addRecipeToQueue(i * 1000, new AspectList().add(Aspect.AIR, i), "smoketest")) {
                    fail("thaumatorium-queue", "add #" + i + " rejected although queue not full (maxRecipes=" + tile.maxRecipes + ")");
                    return;
                }
            }
            if (tile.addRecipeToQueue(9999, new AspectList(), "smoketest")) {
                fail("thaumatorium-queue", "6th recipe accepted despite maxRecipes=5");
                return;
            }
            if (tile.getRecipeCount() != 5 || tile.getRecipeHash(4) != 5000) {
                fail("thaumatorium-queue", "queue wrong after 5 adds: count=" + tile.getRecipeCount() + ", hash[4]=" + tile.getRecipeHash(4));
                return;
            }
            tile.removeRecipeFromQueue(0);
            if (tile.getRecipeCount() != 4 || tile.getRecipeHash(0) != 2000) {
                fail("thaumatorium-queue", "remove(0) wrong: count=" + tile.getRecipeCount() + ", hash[0]=" + tile.getRecipeHash(0) + " (want 4, 2000)");
                return;
            }
            // NBT roundtrip must preserve the queue
            // Full metadata (the chunk save format, incl. "id") is what loadStatic expects
            CompoundTag tag = tile.saveWithFullMetadata(level.registryAccess());
            var tile2 = (TileThaumatorium) BlockEntity.loadStatic(pos, tile.getBlockState(), tag, level.registryAccess());
            if (tile2.getRecipeCount() != 4 || tile2.getRecipeHash(3) != 5000) {
                fail("thaumatorium-queue", "NBT roundtrip lost the queue: count=" + tile2.getRecipeCount() + ", hash[3]=" + tile2.getRecipeHash(3));
                return;
            }
            tile.clearRecipeQueue();
            if (tile.getRecipeCount() != 0) {
                fail("thaumatorium-queue", "clearRecipeQueue left " + tile.getRecipeCount() + " entries");
                return;
            }
            pass("thaumatorium-queue (default cap 1, brain box -> 3, cap 5 enforced, remove shifts, NBT roundtrip 4/4, clear)");
        } catch (Throwable t) {
            fail("thaumatorium-queue", t.toString());
        }
    }

    // -- essentia reservoir phial fill/extract (1.12 right-click I/O) ------
    private static void checkReservoirPhial(MinecraftServer server) {
        try {
            var level = server.overworld();
            var pos = airPos(level, 128, 145);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(pos, ModBlocks.ESSENTIA_RESERVOIR.get().defaultBlockState(), 3);
            var tile = (TileEssentiaReservoir) level.getBlockEntity(pos);
            if (tile == null) {
                fail("reservoir-phial", "no TileEssentiaReservoir created at " + pos);
                return;
            }
            var p = testPlayer(server);
            var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            var state = level.getBlockState(pos);
            // fill: one phial right-click moves 1 essentia phial -> reservoir
            var phial = new ItemStack(ModItems.PHIAL_EMPTY.get());
            ((IEssentiaContainerItem) phial.getItem()).setAspects(phial, new AspectList().add(Aspect.WATER, 3));
            p.getInventory().setSelectedItem(phial);
            if (state.useWithoutItem(level, p, hit) != InteractionResult.CONSUME) {
                fail("reservoir-phial", "fill right-click did not return CONSUME");
                return;
            }
            var held = p.getMainHandItem();
            var heldAspects = ((IEssentiaContainerItem) held.getItem()).getAspects(held);
            if (tile.getEssentiaAmount(Direction.UP) != 1 || heldAspects == null || heldAspects.getAmount(Aspect.WATER) != 2) {
                fail("reservoir-phial", "fill moved wrong amount: reservoir=" + tile.getEssentiaAmount(Direction.UP) + " (want 1), phial=" + heldAspects + " (want 2 aqua)");
                return;
            }
            // extract: empty phial right-click takes 1 essentia reservoir -> phial
            var empty = new ItemStack(ModItems.PHIAL_EMPTY.get());
            p.getInventory().setSelectedItem(empty);
            if (state.useWithoutItem(level, p, hit) != InteractionResult.CONSUME) {
                fail("reservoir-phial", "extract right-click did not return CONSUME");
                return;
            }
            var held2 = p.getMainHandItem();
            var held2Aspects = ((IEssentiaContainerItem) held2.getItem()).getAspects(held2);
            if (tile.getEssentiaAmount(Direction.UP) != 0 || held2Aspects == null || held2Aspects.getAmount(Aspect.WATER) != 1) {
                fail("reservoir-phial", "extract wrong: reservoir=" + tile.getEssentiaAmount(Direction.UP) + " (want 0), phial=" + held2Aspects + " (want 1 aqua)");
                return;
            }
            p.discard();
            pass("reservoir-phial (fill moved 1 aqua phial->reservoir; extract moved 1 aqua reservoir->empty phial)");
        } catch (Throwable t) {
            fail("reservoir-phial", t.toString());
        }
    }

    // -- seal stock matching toggles (exact / tag / mod) -------------------
    private static void checkSealStockMatching() {
        try {
            var seal = new SealStock();
            var m = SealStock.class.getDeclaredMethod("matchesItem", ItemStack.class, ItemStack.class);
            m.setAccessible(true);
            var cobble = new ItemStack(Blocks.COBBLESTONE);
            var stone = new ItemStack(Blocks.STONE);
            boolean same = (boolean) m.invoke(seal, cobble, cobble);
            boolean diffDefault = (boolean) m.invoke(seal, cobble, stone);
            seal.getToggles()[2].setValue(true); // pore (tag matching)
            boolean tagMatch = (boolean) m.invoke(seal, cobble, stone);
            seal.getToggles()[2].setValue(false);
            seal.getToggles()[3].setValue(true); // pmod (mod matching)
            boolean modMatch = (boolean) m.invoke(seal, new ItemStack(ModItems.PHIAL_EMPTY.get()), new ItemStack(ModItems.PHIAL_FILLED.get()));
            boolean modMismatch = (boolean) m.invoke(seal, cobble, new ItemStack(ModItems.PHIAL_EMPTY.get()));
            if (!same || diffDefault || !tagMatch || !modMatch || modMismatch) {
                fail("seal-stock", "matchesItem: same=" + same + " (want true), diffDefault=" + diffDefault
                        + " (want false), tagMatch=" + tagMatch + " (want true), modMatch=" + modMatch
                        + " (want true), modMismatch=" + modMismatch + " (want false)");
                return;
            }
            pass("seal-stock (exact match; c:stones tag match; TC-vs-TC mod match, TC-vs-vanilla rejected)");
        } catch (Throwable t) {
            fail("seal-stock", t.toString());
        }
    }

    // -- seal config GUIs (guard/filtered/use all return a menu provider) --
    private static void checkSealGuiProviders(MinecraftServer server) {
        try {
            var level = server.overworld();
            var p = testPlayer(server);
            var pos = airPos(level, 128, 150);
            Object guard = new SealGuard().returnContainer(level, p, pos, Direction.SOUTH, null);
            // SealFiltered is abstract; SealEmpty is a concrete subclass that does NOT
            // override returnContainer, so it exercises the base implementation.
            Object filtered = new SealEmpty().returnContainer(level, p, pos, Direction.SOUTH, null);
            Object use = new SealUse().returnContainer(level, p, pos, Direction.SOUTH, null);
            if (!(guard instanceof SealMenuProvider) || !(filtered instanceof SealMenuProvider) || !(use instanceof SealMenuProvider)) {
                fail("seal-gui", "returnContainer: guard=" + guard + ", filtered=" + filtered + ", use=" + use
                        + " (all want a SealMenuProvider)");
                return;
            }
            // SealStock deliberately has no config GUI of its own (1.12: stock is
            // configured through the provide/golem GUIs)
            if (new SealStock().returnContainer(level, p, pos, Direction.SOUTH, null) != null) {
                fail("seal-gui", "SealStock unexpectedly returns a container (want null)");
                return;
            }
            p.discard();
            pass("seal-gui (guard/filtered/use return SealMenuProvider; stock intentionally has none)");
        } catch (Throwable t) {
            fail("seal-gui", t.toString());
        }
    }

    // -- causality collapser collapses a nearby flux rift on impact --------
    private static void checkCollapserRift(MinecraftServer server) {
        try {
            var level = server.overworld();
            var spawn = level.getRespawnData().pos();
            var at = new BlockPos(spawn.getX() + 3, spawn.getY(), spawn.getZ() + 3);
            level.getChunkSource().getChunk(at.getX() >> 4, at.getZ() >> 4, true);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()) + 2;
            var pos = new BlockPos(at.getX(), y, at.getZ());
            for (int dy = -1; dy <= 1; dy++) {
                level.setBlock(pos.offset(4, dy, 0), Blocks.STONE.defaultBlockState(), 3);
            }
            var player = testPlayer(server);
            player.setPos(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5);
            var rift = new EntityFluxRift(level);
            rift.setPos(pos.getX() + 2.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            level.addFreshEntity(rift);
            var proj = new EntityCausalityCollapser(level, player);
            proj.setPos(pos.getX() + 0.6, pos.getY() + 0.5, pos.getZ() + 0.5);
            level.addFreshEntity(proj);
            proj.shoot(1.0, 0.0, 0.0, 1.0f, 0.0f);
            for (int i = 0; i < 40 && !proj.isRemoved(); i++) {
                proj.tick();
            }
            if (!proj.isRemoved()) {
                fail("collapser-rift", "projectile never hit the wall in 40 ticks (pos=" + proj.getX() + "," + proj.getY() + "," + proj.getZ() + ")");
                return;
            }
            if (!rift.isCollapsing()) {
                fail("collapser-rift", "flux rift ~1.4 blocks from impact was not set collapsing");
                return;
            }
            proj.discard();
            rift.discard();
            player.discard();
            pass("collapser-rift (thrown collapser hit the wall, nearby flux rift set collapsing)");
        } catch (Throwable t) {
            fail("collapser-rift", t.toString());
        }
    }

    // -- taint bottle taints nearby living entities on impact --------------
    private static void checkTaintBottle(MinecraftServer server) {
        try {
            var level = server.overworld();
            var spawn = level.getRespawnData().pos();
            var at = new BlockPos(spawn.getX() + 10, spawn.getY(), spawn.getZ() + 3);
            level.getChunkSource().getChunk(at.getX() >> 4, at.getZ() >> 4, true);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()) + 2;
            var pos = new BlockPos(at.getX(), y, at.getZ());
            for (int dy = -1; dy <= 1; dy++) {
                level.setBlock(pos.offset(4, dy, 0), Blocks.STONE.defaultBlockState(), 3);
            }
            var player = testPlayer(server);
            player.setPos(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5);
            var sheepType = (EntityType<Sheep>) BuiltInRegistries.ENTITY_TYPE
                    .getValue(Identifier.fromNamespaceAndPath("minecraft", "sheep"));
            var sheep = new Sheep(sheepType, level);
            sheep.setPos(pos.getX() + 2.0, pos.getY() + 0.1, pos.getZ() + 0.5);
            level.addFreshEntity(sheep);
            var bottle = new EntityBottleTaint(level, player);
            bottle.setPos(pos.getX() + 0.6, pos.getY() + 0.5, pos.getZ() + 0.5);
            level.addFreshEntity(bottle);
            bottle.shoot(1.0, 0.0, 0.0, 1.0f, 0.0f);
            for (int i = 0; i < 40 && !bottle.isRemoved(); i++) {
                bottle.tick();
            }
            if (!bottle.isRemoved()) {
                fail("taint-bottle", "bottle never hit the wall in 40 ticks (pos=" + bottle.getX() + "," + bottle.getY() + "," + bottle.getZ() + ")");
                return;
            }
            boolean tainted = sheep.getActiveEffects().stream()
                    .anyMatch(e -> e.getEffect().is(ModEffects.FLUX_TAINT.getId()));
            if (!tainted) {
                fail("taint-bottle", "sheep ~2 blocks from impact has no FLUX_TAINT effect");
                return;
            }
            int goo = 0;
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (level.getBlockState(pos.offset(dx, dy, dz)).getBlock() == ModBlocks.FLUX_GOO.get()) {
                            goo++;
                        }
                    }
                }
            }
            sheep.discard();
            player.discard();
            pass("taint-bottle (landed on the wall, nearby sheep got FLUX_TAINT; flux goo placed: " + goo + ")");
        } catch (Throwable t) {
            fail("taint-bottle", t.toString());
        }
    }

    // -- smelter vents absorb flux pollution (statistical, wide margin) ----
    private static void checkSmelterVents(MinecraftServer server) {
        try {
            var level = server.overworld();
            var base = new BlockPos(220,
                    level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 220, 220) + 2, 220);
            level.getChunkSource().getChunk(base.getX() >> 4, base.getZ() >> 4, true);
            int unvented = smeltBatchWithVents(server, base, false);
            int vented = smeltBatchWithVents(server, base.offset(20, 0, 0), true);
            if (unvented <= 0) {
                fail("smelter-vents", "unvented smelter produced no flux pollution (flux=" + unvented + ")");
                return;
            }
            if (vented >= unvented * 0.6f) {
                fail("smelter-vents", "vents did not reduce pollution: vented=" + vented
                        + " vs unvented=" + unvented + " (want < 60%)");
                return;
            }
            pass("smelter-vents (48 dirt smelts: pollution " + unvented + " unvented vs " + vented + " with 3 vents)");
        } catch (Throwable t) {
            fail("smelter-vents", t.toString());
        }
    }

    /** Smelts 48 dirt through a smelter (with 3 vents when requested) and returns the flux pollution it added. */
    private static int smeltBatchWithVents(MinecraftServer server, BlockPos pos, boolean vented) {
        var level = server.overworld();
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        // Face SOUTH explicitly so the vents below (E/W/N) are all on non-front faces
        level.setBlock(pos, ModBlocks.SMELTER.get().defaultBlockState()
                .setValue(BlockSmelter.FACING, Direction.SOUTH).setValue(BlockSmelter.LIT, true), 3);
        var tile = (TileSmelter) level.getBlockEntity(pos);
        if (tile == null) {
            throw new IllegalStateException("no TileSmelter at " + pos);
        }
        if (vented) {
            // smelter faces south; vents go on the other 3 horizontal faces, aimed at the smelter
            for (Direction d : List.of(Direction.EAST, Direction.WEST, Direction.NORTH)) {
                level.setBlock(pos.relative(d),
                        ModBlocks.SMELTER_VENT.get().defaultBlockState().setValue(BlockSmelter.FACING, d.getOpposite()), 3);
            }
        }
        tile.setItem(TileSmelter.SLOT_INPUT, new ItemStack(Items.DIRT, 48));
        tile.furnaceBurnTime = 100000;
        float fluxBefore = AuraHelper.getFlux(level, pos);
        for (int i = 0; i < 5000 && tile.getItem(TileSmelter.SLOT_INPUT).getCount() > 0; i++) {
            TileSmelter.serverTick(level, pos, level.getBlockState(pos), tile);
        }
        float fluxAfter = AuraHelper.getFlux(level, pos);
        int left = tile.getItem(TileSmelter.SLOT_INPUT).getCount();
        if (left > 0) {
            throw new IllegalStateException("smelter stalled with " + left + " input left (vis=" + tile.vis + ")");
        }
        return (int) Math.round(fluxAfter - fluxBefore);
    }

    // -- research auto-unlock on knowledge load (1.12 PlayerKnowledge) ----- 
    private static void checkResearchAutoUnlock(MinecraftServer server) {
        try {
            List<String> expected = new ArrayList<>();
            for (var cat : ResearchCategories.researchCategories.values()) {
                for (var ri : cat.research.values()) {
                    if (ri.hasMeta(thaumcraft.api.research.ResearchEntry.EnumResearchMeta.AUTOUNLOCK)) {
                        expected.add(ri.getKey());
                    }
                }
            }
            var k1 = new PlayerKnowledge.DefaultImpl();
            if (!k1.addResearch("FIRSTSTEPS")) {
                fail("research-autounlock", "addResearch(FIRSTSTEPS) failed");
                return;
            }
            var k2 = new PlayerKnowledge.DefaultImpl();
            k2.deserializeNBT(k1.serializeNBT());
            if (!k2.isResearchKnown("FIRSTSTEPS")) {
                fail("research-autounlock", "NBT roundtrip lost FIRSTSTEPS");
                return;
            }
            Set<String> known = new HashSet<>(k2.getResearchList());
            Set<String> want = new HashSet<>(expected);
            want.add("FIRSTSTEPS");
            if (!known.equals(want)) {
                fail("research-autounlock", "known after load = " + known + ", expected FIRSTSTEPS + AUTOUNLOCK(" + expected + ")");
                return;
            }
            pass("research-autounlock (NBT roundtrip preserved FIRSTSTEPS; auto-unlock set = " + expected.size() + " entries, matches research data)");
        } catch (Throwable t) {
            fail("research-autounlock", t.toString());
        }
    }
}
