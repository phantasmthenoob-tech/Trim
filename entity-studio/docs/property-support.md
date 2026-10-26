# Entity Studio — property support (v0.1.0)

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

v0.1.0 exposes capabilities + property coverage instead of a raw NBT tree:
Paper's public API is not a writable NBT interface, and a partial-snapshot
rewrite could erase unknown fields. A read-only inspector over the
`PersistentDataContainer` is the planned next step behind
`settings.advanced-data.allow-internal-access`; arbitrary NBT writes stay out
until they can be version-gated and backup-safe.
