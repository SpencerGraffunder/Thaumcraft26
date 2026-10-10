package thaumcraft.common.config;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import thaumcraft.Thaumcraft;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.entities.IEldritchMob;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ScanBlock;
import thaumcraft.api.research.ScanEntity;
import thaumcraft.api.research.ScanItem;
import thaumcraft.api.research.ScanTag;
import thaumcraft.api.research.ScanningManager;
import thaumcraft.api.research.theorycraft.TheorycraftManager;
import thaumcraft.common.entities.EntityFluxRift;
import thaumcraft.common.entities.construct.EntityOwnedConstruct;
import thaumcraft.common.entities.monster.*;
import thaumcraft.common.entities.monster.boss.EntityThaumcraftBoss;
import thaumcraft.common.entities.monster.cult.EntityCultist;
import thaumcraft.common.entities.monster.tainted.*;
import thaumcraft.common.lib.research.ResearchManager;
import thaumcraft.common.lib.research.ScanEnchantment;
import thaumcraft.common.lib.research.ScanGeneric;
import thaumcraft.common.lib.research.ScanPotion;
import thaumcraft.common.lib.research.ScanSky;
import thaumcraft.common.lib.research.theorycraft.ResearchAid;
import thaumcraft.common.lib.research.theorycraft.ResearchCard;
import thaumcraft.common.lib.research.theorycraft.AidBookshelf;
import thaumcraft.common.lib.research.theorycraft.CardAnalyze;
import thaumcraft.common.lib.research.theorycraft.CardBalance;
import thaumcraft.common.lib.research.theorycraft.CardExperimentation;
import thaumcraft.common.lib.research.theorycraft.CardInspired;
import thaumcraft.common.lib.research.theorycraft.CardNotation;
import thaumcraft.common.lib.research.theorycraft.CardPonder;
import thaumcraft.common.lib.research.theorycraft.CardReject;
import thaumcraft.common.lib.research.theorycraft.CardRethink;
import thaumcraft.common.lib.research.theorycraft.CardStudy;
import thaumcraft.init.ModBlocks;
import thaumcraft.init.ModItems;

import java.util.HashMap;
import java.util.Map;

/**
 * ConfigResearch - Initializes research categories, scannables, and theorycraft.
 * Ported from 1.12.2 to 1.20.1
 */
public class ConfigResearch {
    
    public static final String[] TC_CATEGORIES = {
        "BASICS", "ALCHEMY", "AUROMANCY", "ARTIFICE", "INFUSION", "GOLEMANCY", "ELDRITCH", "NODE"
    };
    
    private static final Identifier BACK_OVER = Identifier.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_research_back_over.png");

    /** Research aid entries, keyed by aid key. */
    public static final Map<String, ResearchAid> aids = new HashMap<>();
    /** Research card entries, keyed by card key. */
    public static final Map<String, ResearchCard> cards = new HashMap<>();
    /** Enchantment scanners need the data-driven enchantment registry, which is only
     *  resolvable with a live RegistryAccess - registered lazily on first player use. */
    private static boolean enchantmentScannersRegistered = false;

    private static void registerEnchantmentScanners(RegistryAccess access) {
        if (enchantmentScannersRegistered) {
            return;
        }
        // 1.12 registers a ScanEnchantment for every enchantment in the registry
        access.lookupOrThrow(Registries.ENCHANTMENT).listElements()
                .forEach(e -> ScanningManager.addScannableThing(new ScanEnchantment(e)));
        enchantmentScannersRegistered = true;
    }
    
    /**
     * Initialize all research-related systems.
     * Called during mod common setup.
     */
    public static void init() {
        Thaumcraft.LOGGER.info("Initializing Thaumcraft research system...");
        
        initCategories();
        initScannables();
        initTheorycraft();
        initWarp();
        
        // Register research JSON locations
        for (String cat : TC_CATEGORIES) {
            ThaumcraftApi.registerResearchLocation(Identifier.fromNamespaceAndPath("thaumcraft", "research/" + cat.toLowerCase()));
        }
        ThaumcraftApi.registerResearchLocation(Identifier.fromNamespaceAndPath("thaumcraft", "research/scans"));
        
        Thaumcraft.LOGGER.info("Research categories and JSON locations registered");
    }
    
    /**
     * Post-initialization - parse all research JSON files.
     * Called after all mods have registered their research locations.
     */
    public static void postInit() {
        Thaumcraft.LOGGER.info("Parsing research JSON files...");
        ResearchManager.parseAllResearch();
        Thaumcraft.LOGGER.info("Research system initialized with {} categories", 
                ResearchCategories.researchCategories.size());
    }
    
    /**
     * Initialize all research categories.
     */
    private static void initCategories() {
        // BASICS - always visible, no prerequisite research
        ResearchCategories.registerCategory("BASICS", null,
                new AspectList()
                        .add(Aspect.PLANT, 5).add(Aspect.ORDER, 5).add(Aspect.ENTROPY, 5)
                        .add(Aspect.AIR, 5).add(Aspect.FIRE, 5).add(Aspect.EARTH, 3).add(Aspect.WATER, 5),
                Identifier.fromNamespaceAndPath("thaumcraft", "textures/item/thaumonomicon_cheat.png"),
                Identifier.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_research_back_1.png"),
                BACK_OVER);
        
        // AUROMANCY - requires UNLOCKAUROMANCY
        ResearchCategories.registerCategory("AUROMANCY", "UNLOCKAUROMANCY",
                new AspectList()
                        .add(Aspect.AURA, 20).add(Aspect.MAGIC, 20).add(Aspect.FLUX, 15)
                        .add(Aspect.CRYSTAL, 5).add(Aspect.COLD, 5).add(Aspect.AIR, 5),
                Identifier.fromNamespaceAndPath("thaumcraft", "textures/research/cat_auromancy.png"),
                Identifier.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_research_back_2.png"),
                BACK_OVER);
        
        // NODE - aura node system (TC6/Thaumaturge); unlocked by basic auromancy
        ResearchCategories.registerCategory("NODE", "BASEAUROMANCY",
                new AspectList()
                        .add(Aspect.AURA, 20).add(Aspect.MAGIC, 10).add(Aspect.FLUX, 10)
                        .add(Aspect.CRYSTAL, 5).add(Aspect.LIGHT, 5).add(Aspect.EARTH, 3).add(Aspect.WATER, 3),
                Identifier.fromNamespaceAndPath("thaumcraft", "textures/research/r_nodes.png"),
                Identifier.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_research_back_1.png"),
                BACK_OVER);

        // ALCHEMY - requires UNLOCKALCHEMY
        ResearchCategories.registerCategory("ALCHEMY", "UNLOCKALCHEMY",
                new AspectList()
                        .add(Aspect.ALCHEMY, 30).add(Aspect.FLUX, 10).add(Aspect.MAGIC, 10)
                        .add(Aspect.LIFE, 5).add(Aspect.AVERSION, 5).add(Aspect.DESIRE, 5).add(Aspect.WATER, 5),
                Identifier.fromNamespaceAndPath("thaumcraft", "textures/research/cat_alchemy.png"),
                Identifier.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_research_back_3.png"),
                BACK_OVER);
        
        // ARTIFICE - requires UNLOCKARTIFICE
        ResearchCategories.registerCategory("ARTIFICE", "UNLOCKARTIFICE",
                new AspectList()
                        .add(Aspect.MECHANISM, 10).add(Aspect.CRAFT, 10).add(Aspect.METAL, 10)
                        .add(Aspect.TOOL, 10).add(Aspect.ENERGY, 10).add(Aspect.LIGHT, 5)
                        .add(Aspect.FLIGHT, 5).add(Aspect.TRAP, 5).add(Aspect.FIRE, 5),
                Identifier.fromNamespaceAndPath("thaumcraft", "textures/research/cat_artifice.png"),
                Identifier.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_research_back_4.png"),
                BACK_OVER);
        
        // INFUSION - requires UNLOCKINFUSION
        ResearchCategories.registerCategory("INFUSION", "UNLOCKINFUSION",
                new AspectList()
                        .add(Aspect.MAGIC, 30).add(Aspect.PROTECT, 10).add(Aspect.TOOL, 10)
                        .add(Aspect.FLUX, 5).add(Aspect.CRAFT, 5).add(Aspect.SOUL, 5).add(Aspect.EARTH, 3),
                Identifier.fromNamespaceAndPath("thaumcraft", "textures/research/cat_infusion.png"),
                Identifier.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_research_back_7.png"),
                BACK_OVER);
        
        // GOLEMANCY - requires UNLOCKGOLEMANCY
        ResearchCategories.registerCategory("GOLEMANCY", "UNLOCKGOLEMANCY",
                new AspectList()
                        .add(Aspect.MAN, 20).add(Aspect.MOTION, 10).add(Aspect.MIND, 10)
                        .add(Aspect.MECHANISM, 10).add(Aspect.EXCHANGE, 5).add(Aspect.SENSES, 5)
                        .add(Aspect.BEAST, 5).add(Aspect.ORDER, 5),
                Identifier.fromNamespaceAndPath("thaumcraft", "textures/research/cat_golemancy.png"),
                Identifier.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_research_back_5.png"),
                BACK_OVER);
        
        // ELDRITCH - requires UNLOCKELDRITCH
        ResearchCategories.registerCategory("ELDRITCH", "UNLOCKELDRITCH",
                new AspectList()
                        .add(Aspect.ELDRITCH, 20).add(Aspect.DARKNESS, 10).add(Aspect.MAGIC, 5)
                        .add(Aspect.MIND, 5).add(Aspect.VOID, 5).add(Aspect.DEATH, 5)
                        .add(Aspect.UNDEAD, 5).add(Aspect.ENTROPY, 5),
                Identifier.fromNamespaceAndPath("thaumcraft", "textures/research/cat_eldritch.png"),
                Identifier.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_research_back_6.png"),
                BACK_OVER);
        
        Thaumcraft.LOGGER.info("Registered {} research categories", TC_CATEGORIES.length);
    }
    
    /**
     * Initialize all scannable objects for the Thaumometer.
     */
    private static void initScannables() {
        // Generic scanner for basic items/blocks
        ScanningManager.addScannableThing(new ScanGeneric());
        
        // Enchantment scanners: registered lazily via registerEnchantmentScanners()
        // because the enchantment registry is data-driven and needs a live RegistryAccess.
        
        // 1.12 registers a ScanPotion for every potion (mob effect) in the registry
        BuiltInRegistries.MOB_EFFECT.listElements()
                .forEach(effect -> ScanningManager.addScannableThing(new ScanPotion(effect)));
        
        // Thaumcraft entities
        ScanningManager.addScannableThing(new ScanEntity("!Wisp", EntityWisp.class, true));
        ScanningManager.addScannableThing(new ScanEntity("!ThaumSlime", EntityThaumicSlime.class, true));
        ScanningManager.addScannableThing(new ScanEntity("!Firebat", EntityFireBat.class, true));
        ScanningManager.addScannableThing(new ScanEntity("!Pech", EntityPech.class, true));
        ScanningManager.addScannableThing(new ScanEntity("!BrainyZombie", EntityBrainyZombie.class, true));
        ScanningManager.addScannableThing(new ScanEntity("!EldritchCrab", EntityEldritchCrab.class, true));
        ScanningManager.addScannableThing(new ScanEntity("!EldritchCrab", EntityInhabitedZombie.class, true));
        ScanningManager.addScannableThing(new ScanEntity("!CrimsonCultist", EntityCultist.class, true));
        ScanningManager.addScannableThing(new ScanEntity("!EldritchGuardian", EntityEldritchGuardian.class, true));
        ScanningManager.addScannableThing(new ScanEntity("!TaintCrawler", EntityTaintCrawler.class, true));
        ScanningManager.addScannableThing(new ScanEntity("!Taintacle", EntityTaintacle.class, true));
        ScanningManager.addScannableThing(new ScanEntity("!TaintSeed", EntityTaintSeed.class, true));
        ScanningManager.addScannableThing(new ScanEntity("!TaintSwarm", EntityTaintSwarm.class, true));
        ScanningManager.addScannableThing(new ScanEntity("f_toomuchflux", EntityFluxRift.class, true));
        ScanningManager.addScannableThing(new ScanEntity("!FluxRift", EntityFluxRift.class, true));
        
        // Vanilla entity types for research triggers
        ScanningManager.addScannableThing(new ScanEntity("f_golem", EntityOwnedConstruct.class, true));
        ScanningManager.addScannableThing(new ScanEntity("f_SPIDER", Spider.class, true));
        ScanningManager.addScannableThing(new ScanEntity("f_BAT", Bat.class, true));
        ScanningManager.addScannableThing(new ScanEntity("f_BAT", EntityFireBat.class, true));
        ScanningManager.addScannableThing(new ScanEntity("f_FLY", Bat.class, true));
        ScanningManager.addScannableThing(new ScanEntity("f_FLY", Parrot.class, true));
        ScanningManager.addScannableThing(new ScanEntity("f_FLY", EntityFireBat.class, true));
        ScanningManager.addScannableThing(new ScanEntity("f_FLY", EntityTaintSwarm.class, true));
        ScanningManager.addScannableThing(new ScanEntity("f_FLY", EntityWisp.class, true));
        ScanningManager.addScannableThing(new ScanEntity("f_FLY", Ghast.class, true));
        ScanningManager.addScannableThing(new ScanEntity("f_FLY", Blaze.class, true));
        ScanningManager.addScannableThing(new ScanEntity("!ORMOB", IEldritchMob.class, true));
        ScanningManager.addScannableThing(new ScanEntity("!ORBOSS", EntityThaumcraftBoss.class, true));
        ScanningManager.addScannableThing(new ScanEntity("f_TELEPORT", Enderman.class, true));
        ScanningManager.addScannableThing(new ScanEntity("f_BRAIN", EntityBrainyZombie.class, true));
        ScanningManager.addScannableThing(new ScanEntity("f_arrow", AbstractArrow.class, true));
        ScanningManager.addScannableThing(new ScanEntity("f_fireball", Fireball.class, true));
        ScanningManager.addScannableThing(new ScanEntity("f_spit", LlamaSpit.class, true));
        
        // Thaumcraft blocks - use block registry objects
        ScanningManager.addScannableThing(new ScanBlock("!ORBLOCK1", 
                ModBlocks.ANCIENT_STONE.get(), ModBlocks.ANCIENT_STONE_TILE.get()));
        ScanningManager.addScannableThing(new ScanBlock("!ORBLOCK2", 
                ModBlocks.ELDRITCH_STONE_TILE.get()));
        ScanningManager.addScannableThing(new ScanBlock("!ORBLOCK3", 
                ModBlocks.ANCIENT_STONE_GLYPHED.get()));
        ScanningManager.addScannableThing(new ScanBlock("ORE", 
                ModBlocks.AMBER_ORE.get(), ModBlocks.CINNABAR_ORE.get(),
                ModBlocks.CRYSTAL_AIR.get(), ModBlocks.CRYSTAL_FIRE.get(),
                ModBlocks.CRYSTAL_WATER.get(), ModBlocks.CRYSTAL_EARTH.get(),
                ModBlocks.CRYSTAL_ORDER.get(), ModBlocks.CRYSTAL_ENTROPY.get(),
                ModBlocks.CRYSTAL_FLUX.get()));
        ScanningManager.addScannableThing(new ScanBlock("!OREAMBER", ModBlocks.AMBER_ORE.get()));
        ScanningManager.addScannableThing(new ScanBlock("!ORECINNABAR", ModBlocks.CINNABAR_ORE.get()));
        ScanningManager.addScannableThing(new ScanBlock("!ORECRYSTAL",
                ModBlocks.CRYSTAL_AIR.get(), ModBlocks.CRYSTAL_FIRE.get(),
                ModBlocks.CRYSTAL_WATER.get(), ModBlocks.CRYSTAL_EARTH.get(),
                ModBlocks.CRYSTAL_ORDER.get(), ModBlocks.CRYSTAL_ENTROPY.get(),
                ModBlocks.CRYSTAL_FLUX.get()));
        
        // Plants
        ScanningManager.addScannableThing(new ScanBlock("PLANTS",
                ModBlocks.GREATWOOD_LOG.get(), ModBlocks.SILVERWOOD_LOG.get(),
                ModBlocks.GREATWOOD_SAPLING.get(), ModBlocks.SILVERWOOD_SAPLING.get(),
                ModBlocks.CINDERPEARL.get(), ModBlocks.SHIMMERLEAF.get(), ModBlocks.VISHROOM.get()));
        ScanningManager.addScannableThing(new ScanBlock("!PLANTWOOD", ModBlocks.GREATWOOD_LOG.get()));
        ScanningManager.addScannableThing(new ScanBlock("!PLANTWOOD", ModBlocks.SILVERWOOD_LOG.get()));
        ScanningManager.addScannableThing(new ScanBlock("!PLANTWOOD", ModBlocks.GREATWOOD_SAPLING.get()));
        ScanningManager.addScannableThing(new ScanBlock("!PLANTWOOD", ModBlocks.SILVERWOOD_SAPLING.get()));
        ScanningManager.addScannableThing(new ScanBlock("!PLANTCINDERPEARL", ModBlocks.CINDERPEARL.get()));
        ScanningManager.addScannableThing(new ScanBlock("!PLANTSHIMMERLEAF", ModBlocks.SHIMMERLEAF.get()));
        ScanningManager.addScannableThing(new ScanBlock("!PLANTVISHROOM", ModBlocks.VISHROOM.get()));
        
        // Special items
        ScanningManager.addScannableThing(new ScanItem("PRIMPEARL", new ItemStack(ModItems.PRIMORDIAL_PEARL.get())));
        ScanningManager.addScannableThing(new ScanItem("!DRAGONBREATH", new ItemStack(Items.DRAGON_BREATH)));
        ScanningManager.addScannableThing(new ScanItem("!TOTEMUNDYING", new ItemStack(Items.TOTEM_OF_UNDYING)));
        ScanningManager.addScannableThing(new ScanItem("f_TELEPORT", new ItemStack(Items.ENDER_PEARL)));
        ScanningManager.addScannableThing(new ScanItem("f_BRAIN", new ItemStack(ModItems.ZOMBIE_BRAIN.get())));
        ScanningManager.addScannableThing(new ScanItem("f_arrow", new ItemStack(Items.ARROW)));
        ScanningManager.addScannableThing(new ScanItem("f_VOIDSEED", new ItemStack(ModItems.VOID_SEED.get())));
        ScanningManager.addScannableThing(new ScanItem("f_MATCLAY", new ItemStack(Items.CLAY_BALL)));
        ScanningManager.addScannableThing(new ScanBlock("f_MATCLAY", Blocks.CLAY, Blocks.TERRACOTTA));
        ScanningManager.addScannableThing(new ScanItem("!Pechwand", new ItemStack(ModItems.PECH_WAND.get())));

        // Material scans - 1.12 ScanOreDictionary entries become item tags in 26.3
        ScanningManager.addScannableThing(new ScanTag("f_MATIRON",
                ScanTag.tag("c", "ores/iron"), ScanTag.tag("c", "ingots/iron"),
                ScanTag.tag("c", "storage_blocks/iron"), ScanTag.tag("thaumcraft", "plates/iron")));
        ScanningManager.addScannableThing(new ScanTag("f_MATBRASS",
                ScanTag.tag("c", "ingots/brass"), ScanTag.tag("c", "storage_blocks/brass"),
                ScanTag.tag("thaumcraft", "plates/brass")));
        ScanningManager.addScannableThing(new ScanTag("f_MATTHAUMIUM",
                ScanTag.tag("c", "ingots/thaumium"), ScanTag.tag("c", "storage_blocks/thaumium"),
                ScanTag.tag("thaumcraft", "plates/thaumium")));
        ScanningManager.addScannableThing(new ScanTag("f_MATVOID",
                ScanTag.tag("c", "ingots/void_metal"), ScanTag.tag("c", "storage_blocks/void_metal"),
                ScanTag.tag("thaumcraft", "plates/void")));
        
        // Portals
        ScanningManager.addScannableThing(new ScanBlock("f_TELEPORT", 
                Blocks.NETHER_PORTAL, Blocks.END_PORTAL, Blocks.END_PORTAL_FRAME));
        ScanningManager.addScannableThing(new ScanBlock("f_DISPENSER", Blocks.DISPENSER));
        ScanningManager.addScannableThing(new ScanItem("f_DISPENSER", new ItemStack(Blocks.DISPENSER)));
        
        // Sky scanning
        ScanningManager.addScannableThing(new ScanSky());
        
        Thaumcraft.LOGGER.info("Registered {} scannable objects", ScanningManager.getScannableCount());
    }
    
    /**
     * Initialize theorycraft cards and aids.
     * Note: Additional aids and cards can be added later as they are ported.
     */
    private static void initTheorycraft() {
        // Register aids - only AidBookshelf is currently ported
        TheorycraftManager.registerAid(new AidBookshelf());
        // Research aids
        // These provide bonuses to research speed or unlock specific research
        aids.put("aids_basic", new ResearchAid("aids_basic", "Basic Research Aids", 1.0f));
        aids.put("aids_advanced", new ResearchAid("aids_advanced", "Advanced Research Aids", 1.5f));
        aids.put("aids_expert", new ResearchAid("aids_expert", "Expert Research Aids", 2.0f));
        // - AidBrainInAJar, AidGlyphedStone, AidPortal, AidBasicAlchemy, etc.
        
        // Basic cards (available in normal draw rotation) - only those already ported
        TheorycraftManager.registerCard(CardStudy.class);
        TheorycraftManager.registerCard(CardAnalyze.class);
        TheorycraftManager.registerCard(CardBalance.class);
        TheorycraftManager.registerCard(CardNotation.class);
        TheorycraftManager.registerCard(CardPonder.class);
        TheorycraftManager.registerCard(CardRethink.class);
        TheorycraftManager.registerCard(CardReject.class);
        TheorycraftManager.registerCard(CardExperimentation.class);
        TheorycraftManager.registerCard(CardInspired.class);
        
        // Research cards
        // These provide one-time research bonuses
        cards.put("card_basic", new ResearchCard("card_basic", "Basic Research Card", 10));
        cards.put("card_advanced", new ResearchCard("card_advanced", "Advanced Research Card", 25));
        cards.put("card_expert", new ResearchCard("card_expert", "Expert Research Card", 50));
        // - CardCurio, CardEnchantment, CardBeacon, CardCelestial, etc.
        
        Thaumcraft.LOGGER.info("Registered {} theorycraft cards and {} aids",
                TheorycraftManager.cards.size(), TheorycraftManager.aids.size());
    }
    
    /**
     * Initialize warp values for items.
     */
    private static void initWarp() {
        // Brain in a Jar gives warp
        ThaumcraftApi.addWarpToItem(new ItemStack(ModBlocks.JAR_BRAIN.get()), 1);
    }
    
    /**
     * Check periodic research triggers for a player.
     * Called from PlayerEvents tick handler.
     */
    public static void checkPeriodicResearch(Player player) {
        if (player.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        
        // Enchantment scanners need a live registry access - register once here.
        registerEnchantmentScanners(player.level().registryAccess());
        
        IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);
        if (knowledge == null) return;
        
        // Check for dimension-based research
        Identifier dimKey = player.level().dimension().identifier();
        
        // Nether discovery
        if (!knowledge.isResearchKnown("m_hellandback") && dimKey.getPath().contains("nether")) {
            knowledge.addResearch("m_hellandback");
            knowledge.sync(serverPlayer);
            player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("\u00A75" + 
                            net.minecraft.network.chat.Component.translatable("got.hellandback").getString()));
        }
        
        // End discovery
        if (!knowledge.isResearchKnown("m_endoftheworld") && dimKey.getPath().contains("end")) {
            knowledge.addResearch("m_endoftheworld");
            knowledge.sync(serverPlayer);
            player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("\u00A75" + 
                            net.minecraft.network.chat.Component.translatable("got.endoftheworld").getString()));
        }
        
        // Height-based discoveries (only if auromancy is partially unlocked)
        if (knowledge.isResearchKnown("UNLOCKAUROMANCY@1") && !knowledge.isResearchKnown("UNLOCKAUROMANCY@2")) {
            // Deep underground
            if (player.getY() < 10 && !knowledge.isResearchKnown("m_deepdown")) {
                knowledge.addResearch("m_deepdown");
                knowledge.sync(serverPlayer);
                player.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal("\u00A75" + 
                                net.minecraft.network.chat.Component.translatable("got.deepdown").getString()));
            }
            
            // High up
            int worldHeight = player.level().getMaxY();
            if (player.getY() > worldHeight * 0.4 && !knowledge.isResearchKnown("m_uphigh")) {
                knowledge.addResearch("m_uphigh");
                knowledge.sync(serverPlayer);
                player.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal("\u00A75" + 
                                net.minecraft.network.chat.Component.translatable("got.uphigh").getString()));
            }
        }
        
        // Movement milestones (1.12 ConfigResearch.onPlayerTick): the four stat
        // thresholds that gate the FOOTNOTE/traveller-boot research stage
        // (infusion.json requires m_walker, m_runner, m_swimmer, m_jumper).
        ServerStatsCounter sms = serverPlayer.getStats();
        if (sms != null) {
            if (!knowledge.isResearchKnown("m_walker")
                    && sms.getValue(Stats.CUSTOM, Stats.WALK_ONE_CM) > 160000) {
                knowledge.addResearch("m_walker");
                knowledge.sync(serverPlayer);
            }
            if (!knowledge.isResearchKnown("m_runner")
                    && sms.getValue(Stats.CUSTOM, Stats.SPRINT_ONE_CM) > 80000) {
                knowledge.addResearch("m_runner");
                knowledge.sync(serverPlayer);
            }
            if (!knowledge.isResearchKnown("m_jumper")
                    && sms.getValue(Stats.CUSTOM, Stats.JUMP) > 500) {
                knowledge.addResearch("m_jumper");
                knowledge.sync(serverPlayer);
            }
            if (!knowledge.isResearchKnown("m_swimmer")
                    && sms.getValue(Stats.CUSTOM, Stats.SWIM_ONE_CM) > 8000) {
                knowledge.addResearch("m_swimmer");
                knowledge.sync(serverPlayer);
            }
        }
    }
}
