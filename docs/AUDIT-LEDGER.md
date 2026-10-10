# Audit Ledger — Thaumcraft26 (MC 26.3 / NeoForge)

The machine-enforced definition of done. **`tools/run_all_audits.sh` runs every
row below and exits non-zero on any red row.** A row is green only when its
oracle exits 0; no subjective "looks complete" calls.

Layers (per the verification plan):

1. **Ledger** — this file: every subsystem with its oracle and green state.
2. **Build gate** — `tools/run_all_audits.sh` (static oracles + smoke).
3. **In-game smoke** — headless dedicated server, dev world, `TC_SMOKE=1`,
   28-check assertion battery at `ServerStartedEvent` (`ThaumcraftSmoke`).
4. **Unit tests** — regression net only (86 tests, `./gradlew test`); discovery
   is the job of layers 2–3, never the unit tests.

## Coverage matrix

| Subsystem | Oracle | Green state | Status |
|---|---|---|---|
| Aspect values (1.12 `ConfigAspects` vs port) | `tools/audit_aspects.py` | 242/242 pairs match, 0 divergent | GREEN |
| Recipe pairs (1.12 vs port: ingredients/gates/aspects) | `tools/recipe_parity.py` | 152/152 pairs match | GREEN |
| Research JSON internal refs (items, parents, stages) | `tools/audit_k_research.py` | 0 unresolved refs | GREEN |
| Research ↔ recipe cross-refs (gates + page recipe ids, incl. fake catalog + `RecipeRenderer` aliases) | `tools/audit_k2_resources.py` | 0 unknown gates, 0 unresolvable page ids | GREEN |
| Client item definitions (every registered item has `items/<id>.json` + resolvable model) | `tools/audit_client_items.py` | 0 missing / 0 stale / 0 dangling | GREEN |
| Models / textures / blockstates wiring | `tools/audit_models.py` | 0 problems | GREEN |
| Java → resource file refs (textures, models, lang, data) | `tools/audit_resources.py` | 0 missing file references | GREEN |
| Inventory read-while-write loops (live handler mutated mid-iteration) | `tools/audit_inv_loops.py` | 0 loops | GREEN |
| In-game core systems (registrations, recipe load, research gates, enchantments, loot modifiers, aspects, lang) | `tools/run_smoke.sh` → `ThaumcraftSmoke` | SMOKE: 35/35 checks passed, server boots to `Done` | GREEN |
| In-game behavioral loops (research progression, crucible, infusion, refining loot, golem tick, **golem follow-owner movement**, phial fill, biome aura modifiers, aura base seed-determinism, thaumatorium queue, reservoir phial I/O, seal matching + config GUIs, collapser→rift, taint bottle, smelter vents, research auto-unlock, flux pressure events, **aura-node system (generation determinism, wand tap, node jar, primordial pearl, thaumometer readout)**) | `tools/run_smoke.sh` → `ThaumcraftSmoke` (behavioral half of the 35) | all behavioral checks pass against live server state | GREEN |
| Thaumaturge (26.1.2 TC6 port) fidelity comparison | `docs/THAUMATURGE-COMPARISON.md` | 1.12-parity items verified; missing 1.12 subsystems (aura nodes, golem AI, flux pressure, cultist/pech AI) catalogued with a prioritized roadmap | GREEN (tracked) |
| Compilation + regression unit tests | `./gradlew build` | rc 0, 86/86 tests, 0 `TODO` in `src/` | GREEN |

**Gate status (last run): GREEN** — 2026-10-10, 8/8 oracles + SMOKE 35/35 (smoke grew 12→18→27→29→30→35; the newest checks kept catching real bugs: the `golem-follow` check exposed `EntityOwnedConstruct.getOwner()` using `getPlayerByUUID`, which made non-player owners invisible to the follow/target AI — 1.12 `getOwnerEntity()` resolves ANY living entity). 1.12-parity batch: golem follow-owner teleport fallback + water wading ported (F124/F125); cult AI, crab riding, barrier, effect blocks, flux goo/death fluid, brain box verified (F021/F029–F031/F072/F130–F134). TC6/Thaumaturge aura-node subsystem landed (F100–F110): worldgen, wand tap, node jar, stabilizer, transducer, hungry nodes, aspect orbs, node research set, thaumometer node readout (F108) — 5 dedicated smoke checks.

## Conventions

- **1.12 reference** lives in `.reference/thaumcraft-1.12/` (decompiled BETA26
  source + assets); vanilla 26.3 data cache in `.reference/vdata/`
  (gitignored; `run_all_audits.sh` regenerates it from the gradle/Modrinth
  caches if missing).
- Oracles are **bounded diffs** with a numeric gap; each prints its headline
  number (e.g. `242 pairs`, `matched pairs: 152`) and exits 1 when the gap > 0.
- Runtime findings go to the smoke battery, not unit tests: a check that needs
  a booted server belongs in `ThaumcraftSmoke` (gated on `TC_SMOKE=1`, so a
  normal run is unaffected).
- A new subsystem added to the port gets a ledger row the same session; a row
  without an oracle is a red row.
- `nitorcolor`-style display-only research-page recipe ids must resolve to a
  real recipe, the fake catalog (`FakeRecipes` / `ConfigRecipes` `addFake`), or
  a `RecipeRenderer` alias — enforced by `audit_k2_resources`.
