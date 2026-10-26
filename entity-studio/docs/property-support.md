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
| `ticks_lived` | All entities | 1+; reset to 1 refreshes age behavior |
| `portal_cooldown` | All entities | 0 = can travel now |
| `fall_distance` | All entities | Reset to 0 forgives a fall |
| `no_damage_ticks` | Living entities | 0 = can be hit again now |
| `max_no_damage_ticks` | Living entities | Default 20 |
| `last_damage` | Living entities | Informational |
| `can_pickup_items` | Living entities | Mob item pickup |
| `collidable` | Living entities | Off = pass-through, still targetable |
| `gliding` | Living entities | Needs elytra to look right |
| `swimming` | Living entities | Flag only; water decides actual swim |
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
| `wolf.collar` | Wolves | DyeColor names |
| `cat.collar/lying_down` | Cats | Registry-backed coat types deliberately excluded |
| `fox.crouching/sleeping/type` | Foxes | Type: red, snow |
| `parrot.variant` | Parrots | red, blue, green, cyan, gray |
| `axolotl.playing_dead/variant` | Axolotls | lucy, wild, gold, cyan, blue |
| `mushroomcow.variant` | Mooshrooms | red, brown |
| `goat.left_horn/right_horn/screaming` | Goats | Ramming can remove horns |
| `bee.nectar/anger` | Bees | Anger 0 = calm |
| `enderman.screaming` | Endermen | Aggro display |
| `steerable.saddle` | Pigs, striders | |
| `tame.owner_uuid` | Tameable animals | `READ_ONLY`: assign by taming in-game |
| `breed` | Ageable animals | `reversible=false`: love mode is consumable; adults only |
| `armorstand.visible/small/arms/base_plate/marker/can_move/can_tick` | Armor stands | Marker = non-interactive display hitbox |
| `creeper.explosion_radius/max_fuse/ignited` | Creepers | Radius is `DESTRUCTIVE`; max-fuse clamps live fuse |
| `slime.wander` | Slimes | Off = stays put unless pushed |
| `phantom.size/burn_in_day` | Phantoms | Size 0 = normal |
| `item.pickup_delay/unlimited_lifetime/mob_pickup/player_pickup/will_age` | Dropped items | Stack contents need a dedicated editor (not covered) |
| `boat.max_speed/work_on_land/type` | Boats | Type: oak, spruce, birch, jungle, acacia, cherry, dark_oak, mangrove, bamboo |
| `tnt.fuse` | Primed TNT | `DESTRUCTIVE`; 20 ticks = 1 s |
| `falling.drop_item/hurt_entities/max_damage` | Falling blocks | |
| `explosive.yield/incendiary` | Explosives (fireballs, skulls, TNT) | Creepers use their own radius, not this |
| `projectile.bounce` | Projectiles | |
| `dragon.phase` | Ender dragons | Validated against the live `Phase` enum on write |
| `wither.invulnerable_ticks` | Withers | 0 = vulnerable now |

## Advanced data browser

The dashboard's **Advanced data** button opens a strictly read-only
inspector (full detail in `docs/advanced-data.md`): identity facts, scoreboard
tags, `PersistentDataContainer` entries (scalars, primitive arrays, `LIST.*`,
one level of nested `TAG_CONTAINER`), and the full entity SNBT via the public
`Entity#getAsString()` (parsed for display, never written back). Every row shows
its type, source (`public-api`), and status; sensitive keys and other players'
private data are redacted at build time. Search filter, pagination, and row-click
full-value-in-chat are included. There is deliberately no write path — Paper's
public API is not a writable NBT interface, and a partial-snapshot rewrite could
erase unknown fields. No NMS or reflection is used; `DataInspector` is the seam
where a future version-gated probe could fail closed if one is ever needed.

## Deliberately NOT covered (and why)

- **Item-stack contents** of dropped items, horse chests beyond the
  container mirror, and villager offer cost/result items: need dedicated
  item editors, not scalar properties.
- **Poses/rotations** (armor-stand EulerAngles, head/body vectors): not
  scalar-typed; the equipment page + teleport page cover the common cases.
- **Registry-backed variants** (`Cat.Type`, `Wolf.Variant`, `Pig/Cow`
  variants, `Villager.Profession` values list): `Keyed`/registry types need
  a live server for enumeration, so they are validated on write and covered
  live, not headless. Plain Java enums (`Fox.Type`, `Parrot.Variant`,
  `Axolotl.Variant`, `MushroomCow.Variant`, `Boat.Type`, `DyeColor`) are
  parsed and unit-tested headless.
- **Warden anger, wither skull direction, vehicle passengers**:
  multi-step or event-driven mechanics that a single scalar write
  misrepresents; covered by the behavior page or not at all. (Dragon
  phase and wither spawn-armor are plain setters and ARE covered above.)
- **UUID-typed owners** (`Tameable`): assigning needs a live
  `AnimalTamer`; the editor shows `tame.owner_uuid` read-only instead of
  pretending a typed UUID string is equivalent.
- **Health never exceeds the live maximum**: `health` validates against
  the current max and clamps on write; `max_health` rescales current
  health down when the ceiling drops; `creeper.max_fuse` clamps the live
  fuse the same way. No property silently changes an unrelated field.

## Test coverage

`PropertyRegistryTest` (metadata completeness/uniqueness, discovery per
entity kind, type mismatches, invalid values, read-only refusal, writable
matrix, plus generic combat/physics discovery, new numeric bounds,
plain-enum parses, cross-entity mismatch matrix, and owner-UUID read-only
refusal) and `CapabilityRegistryTest` (mount/container/stash/player/
merchant/mob discovery) run headless using dynamic-proxy entities, since no
server is available in this environment. Server-dependent enum
initialization (`Villager.Profession`, `Cat.Type`/`Wolf.Variant`-style
`Keyed` variants) is covered live, not headless: registry construction
stays server-free by deferring those to write-time `valueOf`.
