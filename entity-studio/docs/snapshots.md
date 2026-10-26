# Snapshots & presets — schema, merge semantics, preview, rollback

Snapshots capture supported entity state; presets are named snapshots
applied selectively. Both are plain YAML (`snapshots.yml`, `presets.yml`),
schema-versioned, and never a raw NBT rewrite.

## Schema

- Current version is **2** (attributes, effects, full behavior record,
  explicit sentinels). **Version 1** files still read, with one warning.
- Any other version — missing, non-numeric, `0`, `3+` — **refuses the whole
  apply** before anything mutates. Unknown top-level sections and unknown
  behavior fields are reported as warnings and never applied.
- The stored `type` (e.g. `ZOMBIE`) is compared to the target. A mismatch
  warns loudly and applies field-by-field: only fields the target supports
  are written. Cross-type application is legitimate (a guard preset fits any
  mob); silent cross-type writes are not.

## Sections (each independently selectable)

| Section | Contents | Permission |
|---|---|---|
| properties | Typed property values (`silent`, `invulnerable`, …) | `data.edit` |
| attributes | Base values per attribute key (`max_health`, …) — living entities | `data.edit` |
| effects | Listed potion effects (type/duration/amplifier) — living entities | `data.edit` |
| equipment | All 6 slots — entities with equipment | `inventory.edit` |
| behavior | Profile + AI flag, owner, trusted, enemies, allowed/excluded types, all rule flags, anchor | `allegiance` (`behavior` for profile brain toggles) |

Behavior application runs the profile first (coherent AI/awareness baseline
via the same `applyProfile` the GUI uses), then overwrites with the stored
scalars — so a `STATIONARY` preset actually stops the brain instead of just
relabeling it.

## Merge semantics

- **Absent field = leave unchanged.** A preset containing only `equipment`
  never touches properties, behavior, or anything else.
- **Explicit resets need intentional sentinels**, because YAML cannot tell an
  explicit null from a missing key:
  - equipment slot `__empty__` → clear that slot (an already-empty slot plans
    as UNCHANGED, not a write);
  - behavior `owner: __none__` → clear the owner;
  - behavior `anchor: {anchored: false}` → clear the anchor;
  - behavior lists (`trusted`, `enemies`, filters) replace when present —
    an empty list is an intentional clear.
- **Effects are additive, never subtractive.** A present `effects` section
  adds/replaces the listed effects; unlisted effects are left alone. There is
  no "remove these effects" representation — use the effects page clear-all.
- Unreadable live values are never captured (no `<unreadable>` poison in
  files); unreadable current values at preview time plan as CHANGE with a
  note rather than refusing.

## Preview statuses

Every stored field gets one status, shown in the preset list and section
flow:

| Status | Meaning |
|---|---|
| CHANGE | Value differs — will be written |
| UNCHANGED | Already matches — skipped, not rewritten |
| UNSUPPORTED | Target does not support it (wrong entity kind, no slots, non-mob profile, missing permission) — skipped |
| INVALID | Stored value fails validation (bad number, unknown type/profile, unloaded anchor world, malformed entry) — skipped |
| UNKNOWN | Not a known field/section — reported, never applied |
| INFO | Applied with an irreversible side note (anchor restores a saved position — verify it is still safe) |

## Validate-first, best-effort rollback

An apply parses and validates **every** field before the first write
(item deserialization, effect ranges, UUIDs, entity-type names, anchor world
loaded, permissions). Execution then runs in section order; each op carries
its own revert. If one fails midway, completed ops are reverted in reverse
and the result reports `rolled back N/M` plus per-op errors. This is
**best-effort, not transactional**: operations the platform cannot reverse
stay changed, forward history entries remain as a truthful audit trail, and
the chat report says so. Individual property/equipment/behavior edits still
record their own undo entries, so manual step-back with `/entitystudio undo`
works after a partial apply.

## Example presets (seeded on first run)

Created only when `presets.yml` does not exist; never overwritten:

- **Armed Elite** (`ZOMBIE`) — full diamond armor + diamond sword,
  `max_health` 40, day-long fire resistance. Equipment/attributes/effects
  only — behavior untouched.
- **Personal Guard** (`ZOMBIE`) — `GUARD` profile, defend owner + allies,
  retaliate, friendly-fire guard, no owner set (assign one in the GUI).
  Behavior only.
- **Stationary Display** (`ZOMBIE`) — `STATIONARY` profile, AI off, silent,
  invulnerable. Properties + behavior.

## Verification status

Unit-tested headlessly: schema decisions, field planning (change /
unchanged / invalid / unknown / unreadable-current), sentinel recognition,
cross-type warnings, midway-failure reverse rollback, revert-failure
tolerance, behavior-capture YAML round-trip, capture default completeness.
Service orchestration against live entities (real property writes, equipment
deserialization on a server, anchor world loading) needs a Paper 1.21.11
test server and is **not** claimed as verified — same restriction as the
rest of this project.
