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
| `Unbound-latest.jar` | 1.0.0 | Initial implementation: 11 universal enchantments (Sharpness delta rebase, Power/Punch extension, Multishot melee+bow, Infinity preservation, Unbreaking, Mending, Efficiency attack-speed, Quick Charge cooldowns, Fortune/Looting additive rolls), 28 pass-through placeholders, `/unbound` command suite, guard-based recursion/budget safety, config-driven behavior, 61 unit tests green. |

> Note: `Unbound-latest.jar` is a copy of the jar produced by
> `build/libs/Unbound-<version>.jar`; rebuilding always overwrites it, so it
> is never stale after a change.
