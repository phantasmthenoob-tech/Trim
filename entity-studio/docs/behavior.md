# Entity Studio — behavior, allegiance & anchors (v0.2.0)

Every claim below is verified against the implementation. Anything the
plugin cannot guarantee says so.

## Profiles: exactly what each one does

All six profiles require a `Mob` (native targeting + AI). Non-mobs
(armor stands, projectiles, vehicles, dropped items) cannot take profiles:
the GUI says so instead of pretending. Applying any profile writes
absolute state, never toggles — applying the same profile twice changes
nothing the second time (unit-tested idempotency).

| Profile | Native controls | Custom flags | Notes |
|---|---|---|---|
| `PASSIVE` | AI off, unaware, target cleared | defend/retaliate/join all off | Absolute: reconcile re-asserts all three every cycle, so nothing can silently re-arm it |
| `NEUTRAL` | AI on, aware on | All attack flags off | Vanilla retaliation only; mark enemies / set trusted for more |
| `GUARD` | AI on, aware on | defend-owner + retaliate on; allies/join untouched | Set an owner (behavior page); trusted players optional |
| `AGGRESSIVE` | AI on, aware on | Nothing changed | Vanilla targeting rules; narrow with allowed/excluded type filters |
| `STATIONARY` | AI off, target cleared | All attack flags off | AI-off is NOT an anchor — knockback/water still move it; add an anchor to hold position |
| `CUSTOM` | AI on, aware on | Nothing changed | Brain restored; configure each rule by hand |

Switching between profiles is lossless in the profile dimension (each
application rewrites the full flag set for that profile), except `GUARD`,
which deliberately leaves defend-allies / attack-owner-target as the admin
set them. Toggling AI by hand (slot 10) overrides the profile's brain
setting and the reconcile timer respects the hand-set value.

## Native vs custom — the honest line

A Bukkit toggle is not a behavior mode. Setting AI off does not stop
gravity, knockback, fall damage, or another plugin assigning a target; it
only stops vanilla pathfinding. The custom layer (defend owner/allies,
retaliate, join owner's attacks, friendly-fire guard, type filters) runs in
event listeners (`EntityDamageByEntityEvent`, `EntityTargetEvent`) plus a
10-tick reconcile timer, evaluated in the deterministic order in
`AllegianceRules`: validity → exclusions (protected, owner, trusted) →
type filters → explicit enemies. A protected-player rule always beats an
enemy rule.

## Targeting rules & scope limits

- **Type filters** (behavior page, slots 48/50) gate *guard engagement*
  (retaliation, defense, join-owner-attacks) only. They do not suppress
  vanilla targeting: an excluded type can still be attacked by vanilla AI
  unless `PASSIVE` clears it. Empty allow-list = any type engages.
- **`pursueOutsideRange`** is stored but currently unenforced — the plugin
  never measures pursuit distance. Documented here so nobody relies on it.
- **Retaliation counts as an explicit-enemy signal**, but owner/trusted
  exclusions still win inside the evaluation.
- **A `STATIONARY` mob with retaliate re-enabled** accepts a target it
  cannot path to (AI off). Harmless but pointless; the GUI leaves the
  combination available because the flags are orthogonal.
- **Invalid/unloaded/dead targets** are dropped at every layer:
  `engageable()` rejects invalid, dead, or self targets; the reconcile
  timer skips entities that resolve to null; anchors never teleport across
  worlds (world-name mismatch = skip, no cross-dimension yoink).

## Stationary & anchors

Two separate concepts, combinable:

- **STATIONARY profile**: AI off. Nothing holds the position.
- **Soft anchor**: no teleport, ever. Past the tolerance
  (`settings.behavior.soft-anchor-tolerance`, default 3 blocks) the mob's
  target is cleared and lateral velocity is zeroed so it settles instead
  of pursuing. Gravity, knockback, and vehicles still move it.
- **Hard anchor**: teleports back beyond ~0.2 blocks of drift
  (`shouldCorrectHard`) and zeroes velocity, holding exact position +
  rotation. Can jitter against strong knockback or riding — that is the
  documented trade-off, not a bug.

Anchors persist across restarts (`managed.yml`) and chunk unloads
(records are UUID-keyed; unload is not death, so nothing is forgotten).
Clearing the anchor is one click; removing the profile does not clear an
anchor and vice versa.

## Task lifecycle (no uncontrolled repeaters)

Exactly **one** repeating task exists: the reconcile timer
(`anchor-check-ticks`, default 10), which iterates managed records only —
never a full-entity scan. Its handle is stored and `stop()` cancels it on
`onDisable` (Bukkit would drop it anyway; the explicit handle means no
work can outlive the plugin by construction). `start()` cancels any
previous timer first, so reload cycles cannot stack timers. World unloads
need no handler: unloaded entities resolve to null and are skipped.
Editor closure changes nothing (records persist by design);
**entity removal and death forget the record** — a UUID never returns, so
keeping it would reconcile a ghost and accumulate dead entries in
`managed.yml`.

## Custom allegiance: priority, scope, persistence

Plugin-managed allegiance is **not vanilla taming** and never touches it:
no code path calls `Tameable.setOwner`, and assigning a guard owner writes
a UUID into the plugin record only. A guarded zombie is not a tamed wolf —
death messages, breeding, and other plugins all still see an untamed mob.
The behavior page labels this explicitly, and `tame.owner_uuid` is a
read-only display of the *native* owner for comparison.

**Deterministic targeting priority** (every engagement passes through it):
1. Validity — invalid, dead, or self targets are dropped.
2. World — cross-world targets are refused (a mob can never path there).
3. Exclusions — owner, trusted allies, protected players, and allegiance
   family always win, even over an explicit-enemy mark.
4. Type filters — excluded types dropped; a non-empty allow-list drops
   everything not listed.
5. Explicit enemy / retaliation / defense / join signal → attack.
Conflicts resolve in that order: trusted beats enemy, owner beats all,
excluded beats allowed. Guards never freelance — a valid bystander with no
rule is ignored.

**Owner-selected opponents** work through "attack owner's target": when
the owner damages a valid living entity, managed guards engage it if it
passes the priority above. The owner is matched by UUID, so **offline
owners keep working** — no online lookup is ever required on the hot path.
Dead, unloaded, or removed targets resolve to null and end the session or
skip the tick; nothing ever edits the entity at a stale location.

**No targeting loops, by construction:** retaliation runs on MONITOR with
`ignoreCancelled`, after the HIGH friendly-fire cancel (family hits never
reach it); target-clearing `setTarget(null)` terminates because null
targets return early from the target handler; vanilla-set family targets
are cancelled once, not fought every tick; the timer only re-asserts
PASSIVE suppression and AI state, never assigns attack targets on a loop.

**Per-category support:**
- Full (retaliate / defend / join / family / filters / anchors): all `Mob`s
  with working `setTarget` (zombies, skeletons, illagers, wolves-as-mobs, etc.).
- Best-effort: Warden (vibration anger bypasses awareness), bosses
  (phases override targets), flyers/swimmers (anchors fight movement).
- Partial: non-Mob living entities — friendly-fire protection only (any
  living attacker/victim pair); profiles and engagement need `Mob`.
- None: non-living entities (projectiles, vehicles, items, blocks).

**Persistence (`managed.yml`, schema 1):** UUID-keyed records holding
owner, trusted, enemies, allowed/excluded types, all rule flags, profile,
AI state, and anchor. Managed by the pure `ManagedCodec` (encode/decode on
plain maps, headless-tested): corrupt fields are quarantined individually
with a log line while valid fields still load; bad-UUID entries are
skipped; unknown future fields are ignored. Player records survive death
(same UUID on respawn); dead non-player entities are forgotten *and*
pruned from every other record's trusted/enemy/owner references; entity
removal by the editor forgets immediately so no ghost accumulates.

## Known limitations (complex mobs & bosses)

- **Warden**: vibration-driven anger, sniffing, and sonic boom ignore
  `setAware(false)` and target-clearing. PASSIVE + hard anchor makes one
  *mostly* harmless and stationary, not guaranteed inert.
- **Ender dragon / Wither / other bosses**: phased AI and scripted attacks
  are not modeled; profiles set the same toggles but the boss brain wins
  most arguments. Use at your own risk; no boss-specific handler exists.
- **Flying / swimming / climbing mobs**: AI-off still allows drifting;
  hard anchors fight their movement every cycle (jitter expected).
- **Other plugins** (claims, combat, custom-mob AI) compete over the same
  `setTarget` calls. Last writer wins per event; the reconcile timer
  re-asserts PASSIVE and AI state every cycle but does not (and cannot)
  arbitrate against a plugin acting on the same tick.
- **Non-Mob LivingEntities** (e.g. armor stands): snapshot/preset restores
  skip the profile label (counted as skipped) but still apply the
  friendly-fire flag, which works for any living attacker/victim pair.
