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
