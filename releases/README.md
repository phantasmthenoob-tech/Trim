# Releases

This folder always carries the **current** plugin jar, refreshed automatically
on every `gradle build` (the `releaseJar` task in `build.gradle.kts` copies the
fresh jar here as `Unbound-latest.jar`).

- **`Unbound-latest.jar`** — drop into a Paper 26.2+ server's `plugins/` folder
  (Minecraft 26.x requires Java 25). See the [README](../README.md) for install
  steps and the smoke-test checklist in the repo docs.

Version history for each published jar:

| Jar version | Plugin version | Changes |
|---|---|---|
| `Unbound-latest.jar` | 1.3.2 | Fixes & inspection: Riptide self-launch now charges per right-click (each click adds ~5 charge ticks — plain items send only one use packet per click, so hold-to-charge never accumulated; ~3 clicks to full charge, minimum 2 clicks before a launch, `min-charge-ticks` default 4 → 10). Admin inspection opens a mirrored 45-slot view (armor, off-hand, main-hand, hotbar, storage — no filler), all slots genuinely editable and synced back to the target's real inventory. New `/inspect <username>` command opens the same view without sneak-right-click; `/inspect` and `/enderchest edit` are restricted to the ImNotAllocate account. Targets must be online (Paper's API cannot open offline player data). |
| `Unbound-latest.jar` | 1.3.1 | Riptide self-launch rework: hold right-click to charge (escalating riptide sound + particle swirl), aim freely while charging to set direction, release to launch with charge-scaled power; quick-click guard, item-switch cancels, configurable charge/min-charge/input-gap ticks. Replaces the instant-launch. |
| `Unbound-latest.jar` | 1.3.0 | Admin inspection: sneak-right-click a player to open their live inventory (armor, hands, storage — take/remove/replace edits the real inventory), and `/enderchest edit <username>` to open a target's live ender chest. Permissions `unbound.inspect` / `unbound.enderchest` (op default). |
| `Unbound-latest.jar` | 1.2.0 | Infinity extended: restores thrown wind charges, and cheap placed blocks (dirt, cobblestone, cobwebs, ...) one tick after placing — with an anti-duplication exclusion list (ore/valuable blocks, containers, shulkers, spawners, anvils, etc.). Blocks must still be the placed type one tick later; breaking first means no restore. |
| `Unbound-latest.jar` | 1.1.0 | 18 new interpretations: protection-family veils (timed extra-armor damage reduction), Fire Aspect ↔ Flame cross-over with infinite-burn combo, Sweeping Edge on melee+projectiles, Thorns-as-sweep, Silk Touch player heads, held Luck, universal Channeling, Riptide (projectile speed + melee self-launch without water), Impaling line pierce, armor-piercing Breach, shield-piercing Piercing (new key), Wind Burst charge bursts, Infinity now preserves totems. 61→70 unit tests. |
| `Unbound-latest.jar` | 1.0.0 | Initial implementation: 11 universal enchantments (Sharpness delta rebase, Power/Punch extension, Multishot melee+bow, Infinity preservation, Unbreaking, Mending, Efficiency attack-speed, Quick Charge cooldowns, Fortune/Looting additive rolls), 28 pass-through placeholders, `/unbound` command suite, guard-based recursion/budget safety, config-driven behavior, 61 unit tests green. |

> Note: `Unbound-latest.jar` is a copy of the jar produced by
> `build/libs/Unbound-<version>.jar`; rebuilding always overwrites it, so it
> is never stale after a change.
