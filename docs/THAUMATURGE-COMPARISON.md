# Thaumaturge Comparison (2026-10-09)

[Thaumaturge](https://github.com/Leclowndu93150/Thaumaturge) ("a port of Thaumcraft
by Azanor") is a complete, actively maintained TC6 port for **MC 26.1.2 / NeoForge**
(2,417 Java files, pushed 2026-10-10). Cloned at `~/Documents/Thaumaturge`. It is a
modern *re-architecture* of 1.12 TC (content/ + api/ + mixin/ + compat/), not a
line-port — so it is useful to us as a **second source of truth for 1.12 behavior**,
especially for the parts of our 1.12 decompile reference that are missing.

**Key context: `.reference/thaumcraft-1.12/` is a PARTIAL decompile.** It lacks the
aura-node system, the full AuraHandler chunk-tick loop, golem AI wiring, several
block packages, and node research data. Our port is 1:1 against what the reference
*does* contain (148/148 research keys, all block/tile/entity names, aspect and
recipe parity enforced by the gate) — so the real fidelity gaps are **subsystems the
reference never showed us**, which Thaumaturge makes visible.

## 1. Major 1.12 subsystems missing from our port (biggest fidelity gaps)

| # | Subsystem | 1.12 role | Thaumaturge evidence | Our state |
|---|-----------|-----------|----------------------|-----------|
| 1 | **Aura nodes** — node worldgen, node blocks (per-aspect, size 1–3, hungry), node stabilizers, transducers, node jars, node tappers (wand), vis relays, node orbs, AURAPRESERVE | Core auromancy resource system (TC6/Thaumaturge feature, absent from the 1.12 BETA26 reference) | `content/aura/node/` — 26 files, ~3,300 lines (NodeGenerator, NodeHunger, NodeUpkeep, NodeFeature, BlockEntityNode*, BlockNode*, NodeWandTap, NodeBiomeSpread) + `data/.../research_entry/node*.json` research set | **DONE 2026-10-10** (F100–F110): 5 node blocks + BEs (Node, Stabilizer, StabilizerAdvanced, Transducer, JarNode), `NodeFeature` 3-stage worldgen + `NodeGenerator` deterministic roll pipeline, `NodeWandTap` (wand vis buffer), `NodeJarRitual` capture with modifier degrade, `NodeHunger` wired into the node tick, `EntityAspectOrb`, NODE research category, thaumometer node readout (F108); 5 smoke checks (node-generation/wand-tap/jar/pearl/thaumometer) |
| 2 | **Golem AI + movement** — ground/air pathfinding, arrow attack, follow-owner, flight/wheel movement | Golems work autonomously; levitator legs = flying golems, wheels = haulers | golem content + commit "rewrite golem darts, flyer legs and return home" | **DONE 2026-10-10** (B7 F120-F125): modern nav stack (Ground/WallClimber/Flying) + `GolemFlyingMoveControl` == 1.12 behavior; arrow AI params exact vs 1.12 `AIArrowAttack`; follow-owner 1.12 fallbacks ported (12-block pathfail + 144-dist teleport-ring, water-wading malus reset); wheel/levitator parts confirmed client-only in 1.12 (NOISE); `golem-follow` smoke check green (gap 196→8 in 200 ticks) |
| 3 | **Flux pressure events** — chunk flux high → lightning, flux rain, wisps, warp, rift spawns | Iconic 1.12 flux consequences | `content/aura/pressure/` — 11 files (FluxLightning, RainPressureEvent, WispPressureEvent, WarpPressureEvent, NodeMutationPressureEvent, …) | **Absent.** We accumulate flux (smelter/vent check verifies) but nothing ever *does* with it |
| 4 | **Cultist / Pech / altar-focus AI** | Crimson Cult raiders, Pech trading, altar focus | `content/pech/`, altar content | **DONE 2026-10-10** (B6 F130-F134): combat AI verified equivalent; PortalLesser spawn (10-tick activation, 32-block range, difficulty caps, 66/34 split) ported; Pech trading goals ported; F133/F134 (altar focus / summon ritual) confirmed dead 1.12 code — the `ritualist` flag is never set (NOISE) |

## 2. Verified PARITY (we match 1.12 — no action)

- `AURA_CEILING = 500`, base = mean of 5 biome modifiers × 500 × `(1 + 0.1·gaussian)`,
  clamp 0..500 — identical structure to 1.12 `AuraHandler.generateAura` and Thaumaturge.
- Smelter vent absorption = per-flux `nextFloat() < 0.333` roll per facing vent —
  matches 1.12 `TileSmelter` (line 266) exactly (smoke-verified).
- Research data: 148/148 keys 1:1 with the 1.12 reference set.
- Crucible mechanics (strict research gate, 50 mb water drain, dissolve-vs-craft) —
  smoke-verified against the 1.12 ref this week.
- Taint spread config shape (`taintSpreadRate`/`taintSpreadArea`, fibre spread rates
  `rate/100·mod` and `rate/100·0.33`) matches 1.12 `TaintHelper`.

## 3. Deviations found & fixed from this comparison

- **Aura base was NOT seed-deterministic** (fixed 2026-10-09): we used the shared
  `level.getRandom()`, so a chunk's base aura depended on *chunk-generation order*.
  1.12 used the chunk-gen random (deterministic per world seed) and Thaumaturge uses
  `worldSeed ^ chunkKey`. Now `AuraHandler.chunkAuraRandom(worldSeed, chunkPos)` +
  pure `computeBaseAura(...)`; verified by the `aura-determinism` smoke check. Also
  removed dead swamp/desert `biomeMod` code in `generateAura`.
- **1.12 smelter vents are plain (non-BE) blocks** (`BlockSmelterVent extends
  BlockTC`, no tile). We model vent/aux/thaumium/void as `BlockSmelter` variants
  sharing one BE type — it works (fixed + smoke-verified 2026-10-09) but the vents
  carry a useless BE. Known deviation, low priority.
- **Biome aura modifiers**: 1.12 averaged over *all BiomeDictionary types* of a
  biome (a biome can carry several, e.g. forest+jungle). We approximate with a
  smaller vanilla-tag set (`IS_FOREST`, `IS_JUNGLE`, …) + specific-biome overrides.
  Close in spirit; exact 1.12 values for multi-type biomes differ slightly.

## 4. Thaumaturge techniques worth borrowing (if we do the big items)

- **Deterministic per-chunk randoms** everywhere (`seed ^ chunkPos key`) — adopted
  for aura; apply the same pattern to node gen and flux pressure if ported.
- **Aura data as a data component / per-chunk `AuraData`** with lazy init +
  pristine detection — cleaner than our `AuraWorld`/`AuraChunkHandler` split.
- **Flux pressure as typed events** (`FluxPressureEvent` + registry of types) —
  extensible shape for our future pressure system.
- **Node rules/hunger/upkeep split** (`NodeRules`, `NodeHunger`, `NodeUpkeep`) —
  a good decomposition to copy when porting nodes.
- They use **mixins for vanilla integration** (we avoid mixins — fine, but note it).

## 5. Suggested order of work (by fidelity impact / effort)

1. ~~Aura base determinism~~ — **done** (2026-10-09, `aura-determinism` smoke).
2. ~~Flux pressure events~~ — **done** (2026-10-10): `pressure/` package
   (lightning, rain, wisps, warp, exhaust) wired into `AuraScheduler.raiseEvents`
   with 1.12 weight table; 5 smoke checks.
3. ~~Aura node system~~ — **done** (2026-10-10, F100–F110): 5 node blocks + BEs,
   `NodeFeature` worldgen, `NodeWandTap`, `NodeJarRitual`, `NodeHunger`,
   `EntityAspectOrb`, NODE research set, thaumometer readout; 5 smoke checks.
4. ~~Golem AI + levitator/wheels~~ — **done** (2026-10-10, B7 F120-F125):
   nav-stack parity verified, arrow AI exact, follow-owner 1.12 fallbacks +
   `golem-follow` smoke check; wheel/levitator parts are client-only in 1.12.
5. ~~Cultist/Pech AI~~ — **done** (2026-10-10, B8 F130-F134): combat AI verified,
   PortalLesser + Pech trading ported, F133/F134 dead 1.12 code.
6. Smelter vent/aux as plain blocks (deviation cleanup) — low priority.
7. **Golem leg part blocks** (1.12 `parts/` — wheels, levitators, darts, etc. as
   craftable items with per-part behavior) — the remaining golem-depth item;
   1.12 parts are mostly cosmetic, darts have the ranged AI (already ported).
