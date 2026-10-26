# Entity Studio — property support (v0.2.0)

Every property defines: stable id, display name, description, applicability
scope, data type, validation, getter, setter (where one safely exists), risk
level, reversibility, and failure behavior
(`EntityProperty.failureBehavior()`, default: validated-before-write with
nothing applied on rejection).

## Support states (explicit, GUI-badged, write-gated)

| State | Meaning | GUI | Generic write path |
|---|---|---|---|
| `SUPPORTED` | Public-API read + write | Green badge, editable | Allowed |
| `PARTIAL` | Readable; generic write covers only part of the effect | Yellow badge | Allowed (honest label) |
| `UNAVAILABLE` | Would be supported, but not for this entity right now (e.g. `max_health` with no attribute instance) | Red badge, shown, not editable | Blocked with reason |
| `READ_ONLY` | No setter exists and none is pretended (`entity.uuid`, `entity.type`) | Gray badge, click shows value | Blocked; `write()` throws |
| `SPECIAL` | Needs a dedicated flow, not a typed value (`horse.chest` — disabling can destroy stored items) | Aqua badge, click routes to the container | Blocked with routing |
| `UNSUPPORTED` | Not applicable — hidden from the editor entirely | Never rendered | Blocked |

Unsupported properties never appear as falsely editable controls: the
registry lists everything except `UNSUPPORTED`, the guarded `apply` path
accepts only `writable()` states, and snapshot restore skips the rest.

## Generic vs entity-specific

Generic Bukkit/Paper properties live in `CommonProperties` (vitals,
movement, presentation, AI, identity) and narrow only by `Entity`,
`LivingEntity`, or `Mob`. Entity-specific properties live in
`SpecificProperties` (age, taming, villagers, creepers, colorable,
shearable, slimes, horses, wolves) and narrow by the matching Bukkit
interface. No property chains `instanceof` checks — each declares one
applicability scope string plus a single `support()` gate, and capability
discovery (`CapabilityRegistry`) follows the same per-type pattern.

## Vitals / movement / presentation (generic)

| ID | Scope | Notes |
|---|---|---|
| `health` | Living entities | 0 kills — confirm before zeroing; clamps to max |
| `max_health` | Living entities (requires the max-health attribute) | `UNAVAILABLE` without the attribute; rescales current health if above |
| `absorption` | Living entities | |
| `fire_ticks` | All entities | −20 = immunity time |
| `freeze_ticks` | All entities | Capped by max freeze ticks |
| `air` | Living entities | |
| `invulnerable` | All entities | |
| `gravity` | All entities | Teleports/knockback still apply |
| `silent` | All entities | |
| `persistent` | All entities | Vanilla may still despawn in edge cases |
| `ai` | Living entities | Off still allows gravity/fire/knockback |
| `aware` | Mobs | |
| `custom_name` | All entities | 256-char cap; visibility is separate |
| `name_visible` | All entities | |
| `glowing` | All entities | Team colors may override |
| `entity.uuid` | All entities | `READ_ONLY` identity fact |
| `entity.type` | All entities | `READ_ONLY` identity fact |

## Entity-specific

| ID | Scope | Notes |
|---|---|---|
| `age`, `age_lock`, `baby` | Ageable mobs | Baby rollback uses age 0 (approximate) |
| `tamed` | Tameable animals | Native flag only — plugin guards are separate |
| `sitting` | Sittable mobs | |
| `villager.profession/level/experience` | Villagers | Profession list validated against the live registry on write |
| `creeper.powered/fuse` | Creepers | Fuse capped by max fuse |
| `color` | Colorable entities | DyeColor names |
| `sheared` | Shearable entities | |
| `slime.size` | Slimes | 1–100 guardrail (huge slimes lag) |
| `horse.jump` | Horses | |
| `horse.chest` | Chested horses | `SPECIAL`: routes to the container view |
| `wolf.angry` | Wolves | |

## Advanced data browser

The dashboard's **Advanced data** button opens a strictly read-only
inspector: `PersistentDataContainer` entries (scalar + primitive-array
types), derived facts (class, EID, UUID, ticks lived, velocity,
passengers, vehicle, portal cooldown), search filter, and paging. Clicking a
row prints its full value to chat. There is deliberately no write path —
Paper's public API is not a writable NBT interface, and a partial-snapshot
rewrite could erase unknown fields. Arbitrary NBT writes stay out until
they can be version-gated and backup-safe (`settings.advanced-data.*`).

## Test coverage

`PropertyRegistryTest` (metadata completeness/uniqueness, discovery per
entity kind, type mismatches, invalid values, read-only refusal, writable
matrix) and `CapabilityRegistryTest` (mount/container/stash/player/
merchant/mob discovery) run headless using dynamic-proxy entities, since no
server is available in this environment. Server-dependent enum
initialization (`Villager.Profession`) is covered live, not headless: one
property description was deliberately decoupled from enum class-init so
registry construction stays server-free.
