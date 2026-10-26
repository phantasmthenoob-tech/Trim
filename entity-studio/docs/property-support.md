# Entity Studio — property support (v0.2.0)

`SUPPORTED` = public-API read + write. `PARTIAL` = readable but approximate
rollback or conditional effect (shown with a warning). Anything else is hidden
from the data editor — never faked.

## Vitals / movement / presentation

| ID | Scope | Notes |
|---|---|---|
| `health` | Living | 0 kills — confirm before zeroing |
| `max_health` | Living w/ attribute | Rescales current health if above |
| `absorption` | Living | |
| `fire_ticks` | all | −20 = immunity time |
| `freeze_ticks` | all | capped by max freeze ticks |
| `air` | Living | |
| `invulnerable` | all | |
| `gravity` | all | Teleports/knockback still apply |
| `silent` | all | |
| `persistent` | all | Vanilla may still despawn in edge cases |
| `ai` | Living | Off still allows gravity/fire/knockback |
| `aware` | Mob | |
| `custom_name` | all | MiniMessage on input via GUI |
| `name_visible` | all | |
| `glowing` | all | Team colors may override |

## Entity-specific

| ID | Scope | Notes |
|---|---|---|
| `age`, `age_lock`, `baby` | Ageable | Baby rollback uses age 0 (approximate) |
| `tamed` | Tameable | Native flag only — plugin guards are separate |
| `sitting` | Sittable | |
| `villager.profession/level/experience` | Villager | |
| `creeper.powered/fuse` | Creeper | |
| `color` | Colorable (sheep, wolf collars…) | DyeColor names |
| `sheared` | Shearable | |
| `slime.size` | Slime | 1–100 guardrail (huge slimes lag) |
| `horse.jump/chest` | Horses | |
| `wolf.angry` | Wolf | |

## Advanced data browser

The dashboard's **Advanced data** button opens a strictly read-only
inspector: `PersistentDataContainer` entries (scalar + primitive-array
types), a few derived facts (class, EID, UUID, ticks lived, velocity,
passengers, vehicle, portal cooldown), search filter, and paging. Clicking a
row prints its full value to chat. There is deliberately no write path —
Paper's public API is not a writable NBT interface, and a partial-snapshot
rewrite could erase unknown fields. Arbitrary NBT writes stay out until
they can be version-gated and backup-safe (`settings.advanced-data.*`).
