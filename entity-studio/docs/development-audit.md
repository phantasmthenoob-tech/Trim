# Entity Studio — development audit (2026-10-26, branch `11-hr`)

Verified by inspection + build. Reported facts were re-checked; only what
passed below is claimed.

## 1. Build configuration (verified)

- Gradle 8.10.2 (local dist), JDK 21 toolchain (`jdk-21.0.13+9`), UTF-8,
  `-Xlint:deprecation -Xlint:unchecked`, JUnit 5.11.4 (jupiter, launcher).
- Repos: Maven Central + `repo.papermc.io` (reachable from this environment).
- Paper API **pinned, not floating** — correct, keep it that way.
- `plugin.yml`: `api-version: '1.21'`, main class, command/permission tree all
  present and consistent with `StudioCommand` + `StudioPermissions`.
- Source: 27 main + 9 test files, `com.entitystudio.{behavior,capability,
  command,gui,history,permission,persistence,property,selection,session,
  snapshot}` + plugin root. Matches `docs/architecture.md`.
- Working tree clean at audit start; nothing outside `entity-studio/` touched.

## 2. Version discrepancy — RESOLVED: target is Paper 1.21.11

- Evidence: `paper-api/1.21.11-R0.1-SNAPSHOT` confirmed present upstream
  (maven metadata lists it; the jar downloaded and compiled here).
- The static `-SNAPSHOT.pom` URL 404s — expected; Gradle resolves
  timestamped snapshots via maven-metadata, which works.
- Migration cost was exactly 3 lines: `Attribute.GENERIC_MAX_HEALTH` no
  longer exists (verified via `javap` on the real 1.21.11 artifact — the
  whole `Attribute` enum dropped the `GENERIC_` prefix) → `MAX_HEALTH`.
- Pin is now `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT`.
- Remaining deprecation warnings (`getCustomName/setCustomName`,
  `getDescription().getVersion()`) are benign.

## 3. Baseline (after corrections): `gradle build` green, 60/60 tests pass

Suites: Gatekeeper 9, SessionManager 12, AllegianceRules 7,
AllegianceService 8, History 6, Validators 4, ContainerPages 6, DataRows 6,
SnapshotService 2. No failures, no errors.

## 4. JAR staleness — checked, fresh at audit start

`releases/EntityStudio-0.1.0.jar` == `releases/EntityStudio-latest.jar` ==
`build/libs/EntityStudio-0.1.0.jar` (identical md5, built from current
sources, embedded `plugin.yml` version matches). Not stale — but the
**version label was**: jars said 0.1.0 while containing post-0.1.0 stages.
Corrected to **0.2.0** (build file, README, property-support doc, fresh
release jars; old 0.1.0 artifacts removed from `releases/`).

## 5. Findings

**Broken:** none (build + tests green; the 1.21.11 migration broke 3 lines,
fixed and re-verified in the same session).

**Incomplete (acknowledged, scheduled):** deep preset merging (selective
apply exists); isolated internal-NBT writes (deliberately out — public API
only); offline player editing (explicitly unsupported, documented).

**Unsafe (remaining gaps, not yet fixed):**
1. ~~Snapshot/preset equipment restore writes slots with no history record~~ —
   FIXED in §10: `applyData` snapshots the loadout + behavior flags and
   records undoable entries.
2. `warnings()` takes an unused `caps` param — dead weight, tiny cleanup.
3. Retaliation handler scans all managed records per damage event — fine
   now, revisit if managed sets grow large.

**Duplicated (tolerable):** session-validation logic exists in both
`SessionManager.validate` and `StudioCommand.validatedListEntity`; container
undo lambdas repeat the backing-inventory lookup. No action — small and
tested.

**Misleading docs (fixed):** README/`property-support.md` said v0.1.0;
`build.gradle.kts` comment implied 1.21.1→1.21.11 coverage without pinning
1.21.11. All corrected.

## 6. Changes made in this prompt (no features added)

- `paper-api` 1.21.1 → 1.21.11; `Attribute.GENERIC_MAX_HEALTH` →
  `Attribute.MAX_HEALTH` (3 sites).
- Project version 0.1.0 → 0.2.0; release jars refreshed; stale 0.1.0
  artifacts removed from `releases/`.
- This audit file. Docs/README version labels corrected.

## 7. Prompt-2 lifecycle review (2026-10-26) — correctness pass

Traced: interact → session → GUI open → navigation → mutation →
cancel/expire → quit → cleanup. Stale references, UUID handling, offline
players, dead/unloaded entities, casts, slot indices, inventory sync, and
post-permission-loss mutation were each checked at the code path.

**Confirmed bugs fixed (minimal changes, 62/62 tests green):**
1. Chat-typed attribute values accepted `NaN`/`Infinity`/negatives
   (`Double.parseDouble` allows them) straight into `setBaseValue` — now
   bounded `parseDouble(text, 0, MAX_VALUE)`; `range(double)` also rejects
   infinities.
2. Precise-teleport chat input accepted non-finite coordinates — now
   finite-checked before `teleport()` (previously a server-side exception
   with no player feedback).
3. Chat-cancel and 60s-expiry reopens called `openDashboard` with no
   lock/permission gate — both now route through a gated `reopen()`.
4. New `Validators.parseFiniteDouble` with unit tests for NaN/±Infinity/
   garbage/null.

**Checked and deliberately left alone:** effect removal rebuilds the active
list per click (no stale-index bug; undo restores the exact effect);
equipment slot mapping verified against page layout; `Mob → LivingEntity`
casts are type-safe; mirror deposit/withdraw re-resolve backing storage per
click; concurrent opens fail closed via session validation.

**Cannot reproduce headless (need the P1 live server):** actual in-game
targeting mediation under vanilla AI pressure, cross-restart anchor/stash
behavior, two-admin container races. No headless-safe fix attempted.

## 9. Prompt-4 GUI consistency review (2026-10-26) — navigation + safety pass

Scope: all 14 `StudioGUI` pages (dashboard, properties, equipment,
attributes, effects, behavior, trades, item, history, snapshots, presets +
section picker, confirm, player-inv, container mirror, advanced).

**Navigation (done):** every 54-page now shares Back (45) · Help (47) ·
Prev (48) · page action (49) · Next (50) · Refresh (52) · Close (53),
with Refresh/Close/Help handled centrally in `onClick`. Pages where 53 is
a guarded destructive action (dashboard remove, effects clear, trades
remove-all, behavior teleport row) keep 53 destructive and expose Close on
44/46/51 instead. 27-pages use Back (22) + Close (26) + Refresh (7/8/25).
History/snapshots/presets/attributes/effects/trades gained real
pagination (previously first-45-only with silent truncation); properties
now offsets by page instead of indexing slot directly.

**Help + current values:** Help lore on every page (gold = editable,
gray/red semantics; amount clamps; undo is newest-first) plus a chat copy
on Help click; dashboard shows EID/session/size, properties show live
value + type + risk + scope + support badge, attributes show
base/final/default/modifiers, effects show duration/amplifier.

**Destructive routing (done):** clear-effects, remove-offer and
remove-all-offers now open the token confirm GUI (fresh permission +
`resolve()` + `checkTargetAccess` at fire time) instead of bare
shift-clicks; withdraw-all previews the count then confirms; the
remove-entity no-confirm-config dead end is fixed (shift-click still
gates, then a lightweight confirm executes so checks still run).

**Click hardening (done):** `onClick` cancels first, then ignores
NUMBER_KEY / SWAP_OFFHAND / DOUBLE_CLICK / DROP / CONTROL_DROP /
border clicks via `GuiNav.isUnsafeClick` (new `GuiNavTest`, 4 tests);
drags already cancelled; container mirror keeps its top/bottom split but
gains Back/Refresh/Close/Help inside the mirror and a confirm on
withdraw-all. Stale-menu guard is `resolve()` (session-UUID == holder-UUID
+ full revalidation) on every mutation path including confirm fire and
60s chat input; `GuiNav.sameEntity` unit-tests the null-safe comparison.

**Requires a live server (not verifiable headless):** in-game rendering
of the new bottom rows, shift/number/offhand/drag behavior with a real
client, double-inventory concurrency, and feel of paginated lists. Headless
proves routing + math only — `gradle build` green, 78/78 tests pass
(74 existing + 4 GuiNav).

## 10. Prompt-6 inventory audit (2026-10-26) — equipment/item/player/mount/container

Traced: equipment swap, drop-chance cycle, clear-all, item editor
(rename/lore/amount/damage/enchants), player-inv + ender hub, mount/native
inventories, container withdraw/deposit/withdraw-all, stash persistence,
snapshot/preset equipment + behavior restore.

**Slot mappings (verified):** labels 10-15 with items exactly +9 (19-24),
matching `slotStack`/`setSlot` order 0-5 (main, off, helm, chest, legs,
boots); container content rows 0-44 with nav bar 45-53; mapping now pinned
headless by `InventoryOps.equipIndexForSlot`/`realSlotFor` (`InventoryOpsTest`, 6 tests).
Display copies are always `clone()`d; clicks/drags are cancelled first, so
fake-GUI items can never be taken and player items never leak in.

**Fixed bugs:**
1. **View-permission live edit (real hole):** the hub needed only
   `inventory.view` to open the LIVE player inventory/ender chest, whose
   vanilla view edits with no Studio undo. Live opens now need
   `inventory.edit`; view-only staff stop at the hub.
2. **P2 snapshot/preset silent restore (from §5.1):** equipment + behavior
   restores wrote slots/flags with no history. `applyData` now snapshots
   the 6-slot loadout and profile + friendly-fire flag first and records
   one undoable entry each when anything actually changes.
3. **Misleading damage feedback:** typing damage for a non-damageable item
   recorded nothing yet answered "Damage → x done". Now refuses honestly
   (`not damageable`) with no history entry.
4. **Silent non-mob label clicks:** label rows on non-mobs did nothing;
   now explain drop chances are mobs-only and point at the swap action.

**Checked and kept:** withdraw-one/two-phase put-back on full player
inventories (no dup, no loss); withdraw-all overflow re-added then
undo-drops excess naturally instead of deleting; deposit clones the offer
and explicitly sets the clicked slot to the leftover under a cancelled
event (no double-move); stash `saveOne` runs on every mirror open (which
follows every mutation); amount/damage/enchant clamps enforced
(`clampAmount` floors at 1 so zero-amount deletion is impossible;
enchants 1-255 via `addUnsafeEnchantment`, labeled as unsafe-admin in the
GUI); clear-equipment snapshots + confirms; `breed` stays
`reversible=false` with adult validation; ender/player live views are
viewing-hub + edit-gated live, never offline (no persistence mechanism
attempted — `edit` resolves online players only).

**Headless-tested:** `InventoryOps` (slot mapping, amount/damage clamps,
drop-chance wrap, page→real-slot, deposit take math). Live `ItemStack`
behavior (stacking, durability, enchant conflicts, holder inventories,
ender chests, two-admin races, stash reload across restarts) needs the P1
server and is NOT claimed here.

**Server-only verification required:** in-game swap/withdraw/deposit round
trips, full-player overflow paths, ender-chest live edits, llama/horse/
hopper mirrors, stash file reload after restart, two admins racing one
container, and confirm/undo feel. `gradle build` green, 89/89 tests pass
(83 prior + 6 `InventoryOpsTest`).

## 11. Prompt-7 behavior review (2026-10-26) — AI, profiles, targeting, anchors

Established exactly what each profile does (table in `docs/behavior.md`):
absolute-state writes, mobs only, idempotent double-application (now unit
tested), with native toggles honestly separated from event-listener custom
logic. No API toggle is presented as a complete behavior mode anywhere in
the GUI copy or docs.

**Correctness fixes:** PASSIVE reconcile now also forces AI off (before, a
re-enabled brain survived while the record claimed passive); snapshot /
preset restores skip the profile label on non-Mobs (counted as skipped)
while still applying the friendly-fire flag; entity removal and death now
`forget()` the managed record (a UUID never returns — previously ghosts
accumulated in the reconcile loop and `managed.yml`); the single reconcile
timer's handle is stored, `start()` cancels any previous timer, and
`onDisable` calls `stop()` — no uncontrolled repeaters by construction.
World unloads need no handler (null-resolve skip, no cross-world teleport).

**Small gap closed:** AGGRESSIVE's hint referenced allowed/excluded type
filters with no editor. Behavior page now has allowed (48) / excluded (50)
slots with comma-separated chat input validated against `EntityType`
(whole-input rejection, blank clears, right-click clears). Filters gate
guard engagement only — documented, not widened silently.

**Documented limits:** Warden vibration anger, dragon/Wither phases,
flyer/swimmer anchor jitter, competing plugins (last-writer-wins),
`pursueOutsideRange` stored-but-unenforced, STATIONARY-with-retaliate
absurdity. `docs/behavior.md` is the canonical reference.

**Tests:** `AllegianceServiceTest` (11 tests: per-profile flags,
full-matrix idempotency, passive→guard brain restore, family/enemy-override
matrix, type-filter parse, anchor thresholds, conservative defaults) +
existing `AllegianceRulesTest` (7). 92/92 green on Paper 1.21.11.
**Live-server needs:** in-game guard retaliation vs arrows/melee, Warden
behavior under PASSIVE, anchor jitter feel, two-plugin target fights.

## 12. Prompt-8 allegiance audit (2026-10-26) — custom allegiance hardening

**Native non-interference verified by search:** no `setOwner` call exists
anywhere; the only `Tameable` writes are the honestly-labeled native
`tamed`/`sitting` toggles. Allegiance owner is a UUID in the plugin record
only; `tame.owner_uuid` stays read-only.

**Safety fixes:** `engageable()` now refuses cross-world targets (a mob
can never path across dimensions — previously a meaningless cross-world
aggro assignment); `onDeath` skips players (same UUID on respawn keeps
its record) while dead non-player entities are forgotten *and* pruned
from every record's trusted/enemy/owner references; owner matching is
UUID-only so offline owners keep working with no online lookup on the hot
path. Loop-freedom re-verified: friendly-fire HIGH cancel precedes MONITOR
retaliation (`ignoreCancelled`), null-target returns terminate clear
chains, the timer never assigns attack targets.

**Persistence fixed + tested:** allowed/excluded types, pursue-range, and
clear-invalid-target flags were GUI-editable but never saved — now
persisted via the new pure `ManagedCodec` (encode/decode on plain maps;
`ManagedStore` load/save delegate to it). Per-field quarantine with log
lines, bad-UUID entry skips, unknown-field tolerance, identical defaults.

**Tests:** `ManagedCodecTest` (6: full round-trip, defaults parity,
per-field quarantine, null entry, future-field tolerance, YAML-shape
contract) + new `AllegianceRulesTest` priority cases (trusted-beats-enemy,
excluded-beats-allowed, case-insensitivity, null type, no freelancing).
99/99 green on Paper 1.21.11.
**Live-server needs:** guard retaliation vs real arrows/melee, owner-offline
defense, cross-world non-aggression, managed.yml reload after restart.

## 13. Prompt-9 history review (2026-10-26) — undo, snapshots, irreversible labels

**Undo gaps closed (all fresh-resolving, UUID-keyed, no live references):**
chat-input allegiance edits (owner set/clear, trust add/del, enemy add/del,
allowed/excluded filter set/clear — previously silent), target-clear
(restores the old target by UUID, fails with `Previous target is gone` when
it despawned), behavior-profile apply (two records: plugin-side flags +
profile, and entity brain AI/awareness; the old attack target is NOT
re-acquired — stated in the label), and both teleport paths (prior location
restored; `Original world is gone` when the world unloaded).

**Hardening:** `EditRecord` timestamp now stamped at creation (was
`Instant.now()` on read, so every row showed "now"); `HistoryService`
drops null records / missing actor-entity identity and null-guards all
actor-keyed queries instead of throwing on the `ConcurrentHashMap`.
History GUI already showed entity type + short UUID, before/after, risk,
timestamp, and reversible markers — verified, no change needed. Limits:
per-player bound from `settings.history.max-entries-per-player` (floor 10),
wired on enable + reload.

**Tests:** +3 `HistoryServiceTest` (sequential-edits reverse-order walk,
irreversible-pops-then-older-still-undoes, invalid/null-entry drops).
102/102 green headless on Paper 1.21.11 (full `gradle` unavailable in this
environment; verified via `javac` + JUnit platform launcher against the
cached dependency set — same sources the Gradle build compiles).
**Docs:** new `docs/history.md` (exact restore table, non-reversible list,
live-server verification gaps); README history bullet points at it.
**Live-server needs:** undo vs despawned entity, undo after chunk
unload/restart, teleport-back across world unload, fights with other
plugins over the same entity.

## 14. Prompt-10 snapshots/presets review (2026-10-26) — schema, merge, rollback

**Planning core extracted (pure, tested):** new `SnapshotPlan` owns schema
decisions (v2 current, v1 legacy-warn, anything else refuses), per-field
statuses (CHANGE/UNCHANGED/UNSUPPORTED/INVALID/UNKNOWN/INFO), sentinel
recognition (`__empty__`, `__none__`), cross-type warnings, and a
best-effort reverse-order `runAll` executor. `SnapshotService` plans every
field before the first write and executes validated ops only.

**Coverage extended:** new ATTRIBUTES and EFFECTS sections (base values;
additive-only effect semantics — unlisted effects are never removed),
behavior now captures/applies ALL managed fields (was: profile +
friendly-fire only, silently dropping defend/trust/enemy/filter/anchor
state), behavior apply runs `applyProfile` first so STATIONARY/GUARD presets
set a real brain baseline before scalar overrides. Capture skips unreadable
properties instead of poisoning files with `<unreadable>`.

**Honesty:** unknown fields/sections reported, never applied; type mismatch
warns but applies supported fields; midway failure reverts completed ops in
reverse (tolerating revert failures) and reports rolled-back counts plus
per-op errors — documented as best-effort, not transactional. Preview rows
carry statuses; the preset list shows change/skip counts. `ApplyResult`
replaces the opaque `int[]` at all three GUI call sites with warning lines.
Section picker gains attributes/effects buttons. Three example presets seed
on first run only (`Armed Elite`, `Personal Guard`, `Stationary Display`).

**Tests:** `SnapshotPlanTest` (11: schema, planning, sentinels,
cross-type, midway rollback order, revert-failure tolerance) +
`SnapshotServiceTest` (2 new: behavior-capture YAML round-trip,
default-completeness so restores can turn flags back off). 115/115 green
headless on Paper 1.21.11 (javac + JUnit launcher; no Gradle dist in VM).
**Docs:** new `docs/snapshots.md`; README + audit updated.
**Live-server needs:** real equipment deserialization, anchor world loading,
profile-brain application, seed-preset file creation, cross-type preset
application on mobs.

## 15. Read-only advanced-data inspector (2026-10-26) — public API only

**API investigation (evidence, not guesses):** `javap` against the pinned
`paper-api:1.21.11-R0.1-SNAPSHOT` jar confirms `Entity#getAsString()`,
`Entity#createSnapshot()` → `EntitySnapshot#getAsString()`,
`Entity#getScoreboardTags`, and the full `PersistentDataType` family
(incl. `LIST.*` and `TAG_CONTAINER`). Conclusion: the full entity data is
readable through the public contract — no NMS/reflection probe was needed,
so none ships. Reads sit behind the narrow `data.DataInspector` seam, which
fails closed per section and documents where a version-gated probe would go.

**New `data` package (pure, tested):** `SnbtParser` (recursive-descent SNBT →
typed scalars, strict on malformed input, 64 KiB / 4,000-node / depth-8 caps),
`DataBrowser` (sorted flatten with types, per-section budgets, search,
200/400-char truncation, credential-pattern redaction at build time),
`PublicApiInspector` (identity/tags/pdc/snbt sections; every getter isolated;
other-player targets pre-redacted without `player.edit`). GUI re-rendered:
section icons, type/source/status lore, row-count help, redacted rows refuse
chat echo. `DataRows` removed; its behaviors (sorting, depth cap, row cap,
pretty-printing, null handling) are covered by the new tests.

**Tests:** `SnbtParserTest` (5), `DataBrowserTest` (5), `PublicApiInspectorTest`
(4: null-getter proxies → UNAVAILABLE, player redaction on/off, PDC typed vs
unknown reads). **123/123 green** headless (was 115; −6 retired DataRows
tests, +14 new). Docs: new `docs/advanced-data.md` (API table, caps,
redaction, player policy, `allow-internal-access` reserved-off); README +
`property-support.md` updated.
**Live-server needs:** open the page for zombie/horse/chest-boat/villager,
search/pagination/row-click chat echo, no console errors; snapshot refusal
for players on implementations that reject it.

## 16. Targeting retention, equipment honesty, boss properties (2026-10-26)

**Targeting (the "loses interest" bug):** allegiance-assigned targets are
now remembered on the record (`commandedTarget`, transient — never
persisted) and the reconcile timer re-asserts a *lost* order while the mob
is target-less, the target is still legal (exclusions + type filters
re-checked), same-world, and within 64 blocks. It never overrides a
different target vanilla acquired and never teleports. Two more fixes in
the same area: the soft anchor no longer clears an active commanded target
(it was yanking guards off owner-ordered kills every cycle), and death now
clears `commandedTarget` references to the dead entity.

**Equipment honesty:** every `getEquipment()` call goes through
`safeEquipment()` (exotic entities can throw, not just return null); the
equipment page explains entities with no vanilla slots instead of dying
silently. Swaps are write-verified (`InventoryOps.slotMatches`): if vanilla
discards the item (e.g. armor on a dragon — no render slots exist), the
player is told and the cursor item is restored. Nothing is lost silently.

**Boss coverage:** new `dragon.phase` (validated against the live `Phase`
enum) and `wither.invulnerable_ticks` properties; generic living-entity
properties (health, AI, ...) were verified to list for dragons (EnderDragon
is a `Mob`), so boss pages are never empty. Warden anger stays out:
per-entity anger needs a target-entity input the scalar editor cannot
represent honestly.

**Tests:** `CommandedTargetTest` (range gate + command lifecycle),
`BossPropertiesTest` (dragon/wither gating + live-enum parse, headless via
real `Phase.valueOf`), `slotMatches` cases in `InventoryOpsTest`.
**129/129 green** headless (was 123). Docs: `behavior.md` (command memory,
anchor yield, follow-range note), `property-support.md`, README bullets.
**Live-server needs:** guard holds an owner-ordered target through sight
loss; soft-anchored guard keeps pursuing while ordered; armor swap on
zombie vs honest discard message on dragon; dragon phase change visible
in-fight.

## 17. Bloodline inheritance + true aggressive mode (2026-10-26)

**Inheritance:** babies and summons of managed mobs keep the family.
`EntityBreedEvent` copies the managed mother/father (mother wins) exactly;
`SLIME_SPLIT` and `SPELL` (evoker vexes) take the nearest managed mob
within 16 blocks, same-type required for splits. `copyAllegiance` moves
every persistent setting (owner/trust/enemies/filters/flags/profile/brain)
and deliberately skips anchors and commanded targets; the child's brain is
matched via `applyProfile`. Non-`Mob` offspring are left alone (profiles
need `setTarget`).

**AGGRESSIVE rewritten as plugin hunting, not vanilla:** the reconcile
loop gives target-less aggressive mobs the nearest legal prey in their own
follow-range (4–64 clamp, 16 default), skipping family/allies/creative/
filtered targets; commanded-target memory makes sight loss survivable.
Hostiles keep vanilla movement/melee (targets only); peaceful mobs get a
Paper-pathfinder walk-in plus 1.0 contact damage at 1 Hz through
`damage(victim, mob)` so kill credit and friendly-fire still work. The
peaceful/hostile split keys off the attack-damage attribute (absent or
non-positive = assist). New `aggressive` flag persisted in the codec
(transient commanded targets stay unpersisted as before).

**Tests:** `InheritanceTest` (copy semantics, donor pick/radius/type/
world gates), `AggressionTest` (profile arming/disarming, pure assist +
range math, attribute-unavailable degradation), codec round-trip extended.
**135/135 green** headless (was 129). Docs: `behavior.md` (aggression +
inheritance), README, audit. **Live-server needs:** evoker→vex handoff
in a real raid, bred-baby guard behavior, aggressive cow actually walking
to and hitting a zombie, aggressive zombie still using vanilla melee only
(no double-hit from the assist path).

## 18. Live-server verification (2026-10-26) — real Paper 1.21.11, build 132

Hosted a throwaway Paper 1.21.11 server in-VM (flat world, offline mode,
no players) with EntityStudio v0.2.0 plus a harness plugin that drives the
real API and logs PASS/FAIL (harness lives in untracked `mc-test/`, never
shipped). Headless suites can't tick entities, fire spawn events, or split
slimes, so this closed the loop.

**Result: `split=PASS aggro=PASS breed=PASS`** (all inside ~10 seconds):
an aggressive cow acquired a nearby pig, walked to it, and killed it with
the 1.0 contact assist (`ENTITY_ATTACK damager=COW` in the log); a baby of
a managed pair carried the owner's record; all four size-2 slime children of
a managed size-4 slime carried the donor's record and GUARD profile.
EntityStudio enabled with zero errors on the real build-132 server.

**Two real bugs found and fixed by this exercise:**
1. *No-player chunk purge*: with zero players online, spawn chunks unload
   within a second and take test mobs with them — harness now force-loads
   its arena (test-only lesson, no plugin change).
2. *Death-before-split ordering*: on this Paper line `EntityDeathEvent`
   fires before `SlimeSplitEvent`, so the first implementation's same-tick
   record removal orphaned every split child. Dying records now rest in a
   60-second `recentlyDead` hold (pruned in reconcile) instead of
   vanishing; split snapshots consult it. Verified PASS after the fix.

**Still player-gated (needs a human client):** GUI click-throughs, shift-
click selection feel, nameplate/filter UX. Live-verified server mechanics
are listed per-stage above.

## 8. Prioritized blockers for next stages

1. **P1 — live-server verification.** Headless green is not proof on a
   server: shift-click entry, container mirror on llamas/horses/hoppers,
   guard retaliation vs arrows, anchors across restarts, two-admin
   concurrency. Needs a Paper 1.21.11 test server (forbidden here).
2. **P2 — snapshot equipment restore without history.** FIXED in §10.
3. **P3 — deep preset merging** (still the major incomplete feature).
4. **P4 — dependency drift.** Re-check the pin when targeting newer 1.21.x;
   never float it.
