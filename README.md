# Unbound

**Universal enchantment mechanics for Paper 26.2** — any vanilla enchantment can be applied to
any item, and its behavior *adapts* to what the item is actually doing. No economy, no teams,
no TPA: only re-imagined enchantment mechanics.

```
Minecraft event -> handler -> EnchantmentContext
      -> ApplicabilityResolver -> effect.execute() -> guard-limited mutation
```

## The idea

Vanilla locks enchantments to item classes (Sharpness only on swords, Multishot only on
crossbows). Unbound removes the *arbitrary* restrictions but keeps the game **meaningful**:
an effect only fires when the item is genuinely capable of the action — Sharpness works on any
melee attack, Multishot splashes melee hits, Infinity preserves any consumable or thrown item.
When an item/action combination has no meaningful interaction, effects do **nothing** — no
invented gimmicks.

## Architecture (event → context → resolver → dispatcher)

| Layer | Package | Responsibility |
|---|---|---|
| Handlers | `enchant.handlers` | Translate Bukkit events into `EnchantmentContext`s, open/close guards. |
| Context | `enchant.EnchantmentContext` | Immutable snapshot: action, actor, item, capabilities, level, guard. |
| Resolver | `enchant.ApplicabilityResolver` | Layered gate: enabled → effect exists → action supported → level > 0 (or level-agnostic) → capabilities → effect's own `applies()`. |
| Dispatcher | `enchant.EffectDispatcher` | Runs every applicable definition's effect, isolated per-effect try/catch, timing via `DebugSink`. |
| Effects | `enchant.effects` | The actual behaviors; pure math lives in `math/` and is unit tested. |
| Engine | `enchant.UniversalEnchantmentEngine` | Registry of definitions; level reading/clamping; universal `applyToItem`. |

### Two-phase combat

* **`MELEE_ATTACK` / `PROJECTILE_HIT`** at `NORMAL` priority — damage modification (Sharpness, Power).
* **`MELEE_IMPACT` / `PROJECTILE_IMPACT`** at `MONITOR` priority — actions that must only happen
  for attacks that actually land (Multishot melee splash, Punch).

A cancelled attack therefore never triggers impact effects.

### Recursion protection & budgets

`ProcessingGuard` (thread-local, since Bukkit events are synchronous) gives every top-level
action a scope with:

* **depth** — nested synthetic events (our own `damage()` / `spawnArrow()` calls) are never
  re-processed by handlers (`ProcessingGuard.isActive()`), and chaining stops at
  `limits.max-effect-chain-depth`;
* **shared budgets** — entities affected, blocks affected, projectiles created per action
  (`limits.*` in config.yml).

### Config-driven

Everything lives in `config.yml` with documented defaults (`config.DefaultConfig` is the code
source of truth). `ConfigParser` is pure (Map → typed config, Bukkit-free) and unit tested;
invalid values fall back to documented defaults instead of bricking the plugin.

## Implemented enchantments

| Enchantment | Unbound behavior |
|---|---|
| **Sharpness** | Vanilla already adds `0.5 + 0.5·lvl` to *any* melee attack; Unbound applies only the **delta** vs its own formula (default config = delta 0 = untouched, never double-counted). |
| **Power** | Arrows keep vanilla Power; other player-launched projectiles (wind charge, snowball, egg — configurable) gain flat damage per level. |
| **Punch** | Arrows keep vanilla Punch; melee hits and configured projectiles get knockback scaled by level (applied once, at MONITOR). |
| **Multishot** | Melee hits splash to nearby targets (nearest-first, deduplicated, budget-capped, pets/armor stands/creative excluded). Bows fire extra non-pickup arrows in a spread. Crossbows keep vanilla Multishot; tridents excluded (item duplication risk). |
| **Infinity** | Consumed/thrown items are restored **1 tick later** (preserve, never duplicate; creative & excluded materials skipped). Durability damage is cancelled outright — deterministic precedence over Unbreaking. |
| **Unbreaking** | Extra skip chance stacked on vanilla (default `0.0` = exact vanilla, hard-capped so items can't become indestructible). |
| **Mending** | XP repairs main hand → off hand → armor → hotbar (configurable), 2 durability per XP; leftover XP stays on the bar. |
| **Efficiency** | Vanilla already speeds mining for effective tools; Unbound adds the missing piece: a transient attack-speed modifier while a melee weapon is held. |
| **Quick Charge** | Reduces item cooldowns (ender pearls, chorus fruit, ...). Crossbows keep vanilla reload. |
| **Fortune** | Vanilla Fortune already multiplies ore drops for any tool; Unbound adds an additive bonus roll on qualifying drops (chance per level, capped duplicates per action). |
| **Looting** | Same additive-roll approach for mob drops; rare drops only when `boost-rare-drops` is on. |
| **Protection / Fire / Blast / Projectile Protection / Feather Falling** | **Veils**: while the item is held/worn, combat involvement grants a timed "veil" (2s + 1s per level) that reduces the family's damage like an extra armor piece of that protection level. |
| **Fire Aspect** | Now also ignites targets hit by the holder's projectiles; with **Flame** on the same item, victims keep burning until they enter water. |
| **Flame** | Now also ignites melee targets; see Fire Aspect combo. |
| **Sweeping Edge** | A real area sweep on melee hits *and* projectile hits (nearest-first, budget-capped selection). |
| **Thorns** | Wearing it sweeps nearby enemies when you are hit (armor version of Sweeping Edge). |
| **Silk Touch** | Killing a player yields their head as a bonus drop. |
| **Luck of the Sea** | Luck while the item is held (5s refresh on held-item changes; fades shortly after switching away). |
| **Channeling** | Any strike — melee or projectile — summons lightning (thunderstorm requirement configurable). |
| **Riptide** | Projectiles launch with riptide speed at reduced damage (default 30% kept); right-click on a melee item self-launches like a trident — without water. Tridents keep vanilla. |
| **Impaling** | Strikes pierce a line of up to `min(5, level)` mobs. |
| **Breach** | Projectile hits pierce a fraction of the victim's armor (16% per level, capped at 80%). |
| **Piercing** | Hits go through shields: the shield is disabled briefly (mace-smash style) and the damage flows. |
| **Wind Burst** | Impacts burst into a ring of wind charges (mace smash on anything), guard-budgeted. |

All remaining vanilla enchantments (Respiration, Aqua Affinity, Depth Strider, Frost Walker,
the curses, Knockback, Lure, Loyalty, Soul Speed, Swift Sneak, Density) are registered as
**honest pass-through** definitions: they show in `/unbound info`, can be applied anywhere
with `/unbound enchant`, and no invented behavior is attached to them yet.

**Infinity also preserves Totems of Undying** (restored to the original hand after
resurrection) and **cheap placed blocks** (dirt, cobblestone, cobwebs, ...): the block is
returned one tick after placing *only if the placed block is still there* — valuable blocks
(netherite/diamond/gold/iron/emerald, ancient debris, beacons, ...) and containers (chests,
shulkers, furnaces, hoppers, ...) are excluded in `infinity.blocks.excluded`, so Infinity
stays a preserve mechanic and can never duplicate wealth.

## Commands

```
/unbound info                                    list definitions (implemented vs pass-through)
/unbound reload                                  reload config.yml
/unbound debug                                   toggle personal debug action-bar output
/unbound enchant <player> <key> <level> [force]  apply any enchantment to the held item,
                                                 bypassing vanilla compatibility; max level is
                                                 the configured one unless `force` is given
```

Permissions: `unbound.admin` (base), `unbound.reload`, `unbound.debug`, `unbound.enchant`
(children of admin, default op).

## Building

Requires **JDK 25** (Minecraft 26.x is Java 25).

```bash
./gradlew build          # or: gradle build
./gradlew test           # unit tests (JUnit 5)
```

The plugin jar lands in `build/libs/Unbound-<version>.jar` (a sources jar is produced too).
Paper API is `compileOnly` — drop the jar into a Paper 26.2+ server's `plugins/` folder.

## Known limitations (honesty section)

* **Bow draw time cannot be shortened via the public Paper API** (it is client-side physics).
  Quick Charge therefore covers item cooldowns only; crossbow reload stays vanilla. An optional
  NMS layer could add draw-time scaling later.
* **Efficiency's attack-speed bonus is attribute-based**, not a swing-timer change: it shows in
  the attribute and applies to real attack cooldown mechanics.
* **Fortune/Looting bonuses are additive rolls** on top of vanilla, not reimplemented loot
  tables — vanilla already applies both enchantments to any qualifying held item.
* `PrepareItemEnchantEvent`/`EnchantItemEvent` cancellation blocks the vanilla table when
  `progression.enchanting-table.enabled: false`; anvils are never touched.
* Effect behaviors are unit tested at the math/config/resolver level; live server behavior
  (event ordering, Paper version drift) should be smoke-tested on a real server before trusting
  a release.

## License

MIT — see `LICENSE`.
