# Entity Studio — visual entity editor for Paper 1.21.x

Shift + right-click an entity, change its properties in a GUI, and watch the
real entity change. No `/data` memorization.

> **Status: v0.2.0 — Paper 1.21.11 target; security hardening, friendly-fire
> guard, universal inventories, read-only advanced data browser.**
> Roadmap stages 01–04 (core) are implemented; stages 05–06 (rich presets UI,
> isolated internal-data module) are partially present. See `docs/`.

## Quick start

1. Requires Paper **1.21–1.21.11** and Java **21**.
2. Drop `releases/EntityStudio-latest.jar` into `plugins/`, restart.
3. The plugin starts **locked**. Run `/entity start` (owner account or console),
   then ops can use `/entity`, `/entitystudio`, and shift + right-click editing.
4. Shift + right-click any entity to open its dashboard.

## What works in v0.2.0

- **Dashboard** — name/type/UUID/coords, health, AI + target state, capabilities,
  warnings, property search, entity removal (confirm, recorded).
- **Properties** — ~90 typed properties: vitals/combat windows (health, max
  health, absorption, no-damage ticks, last damage, pickup/collide/glide/swim
  flags), age/taming/breeding, villagers, creepers, armor stands, wolves/cats/
  foxes/parrots/axolotls/mooshrooms/goats/bees/endermen, steerable saddles,
  dropped items, boats, primed TNT, falling blocks, explosives, projectiles —
  with validation, risk badges and undo. Click booleans to toggle; type
  values in chat. See `docs/property-support.md` for the exact list and
  deliberate exclusions.
- **Equipment** — live swap editing of all 6 slots, item editor (rename, lore,
  amount, damage, enchants), drop-chance cycler for mobs, clear-all with
  snapshot + confirm.
- **Attributes** — base values, final values, modifiers, reset-to-default.
- **Effects** — add (`TYPE:ticks:amp`), remove, clear-all.
- **Behavior** — AI/aware toggles, target clear, 6 behavior profiles, soft/hard
  anchors, teleport, owner/trusted/enemies, defend/retaliate rules, native
  vs plugin-managed taming display. Vanilla targeting conflicts are mediated
  deterministically (see `docs/architecture.md`).
- **Trades** — view/reset/remove offers, add offer from held item, profession
  and level via properties.
- **Advanced data (read-only)** — PDC entries, derived facts, filter +
  paging; click a row for the full value in chat. No write path by design.
- **History** — every edit records before/after; undo for reversible ops;
  honest "cannot be undone" for the rest.
- **Snapshots & presets** — save, restore, preview exact field changes, apply
  properties/equipment/behavior selectively.
- **Inventories** — player inventory + ender chest open **live** (viewing the
  hub needs `inventory.view`, opening the live views needs `inventory.edit` —
  the vanilla view has no undo, so view-only staff can't reach it) and
  mount/container inventories open through a size-safe paged mirror (llama
  8-slots and friends can't crash it). Entities with no inventory of their
  own — cows, pigs, creepers — get a persistent 27-slot plugin **stash**
  (`stashes.yml`, kept across restarts, dropped if Studio removes the
  entity). Withdraw-all previews the stack count and needs confirm, with
  undo; snapshot/preset equipment + behavior restores record undo entries.
  Offline player editing is explicitly unsupported.

## Activation & owner override (read this)

- Commands stay **locked** until `/entity start` after each restart.
- Only the account in `settings.owner-name` (`config.yml`, default
  `"ImNotAllocate"`) — or console — can run `/entity start`.
- That owner can always use the editor, even while locked and even without op.
- This override is **declared in config.yml and logged at startup on purpose**:
  it is not hidden anywhere in the code. Change it to your name, or set it to
  `""` to disable it.

## Permissions & security model

Full table in `docs/permissions.md`. The rules below are enforced in code
and covered by unit tests (`GatekeeperTest`, `SessionManagerTest`,
`HistoryServiceTest`, `AllegianceServiceTest`):

- **Locked by default.** Every entry point (commands, shift + right-click,
  inspect mode, tab completion) refuses while locked. No permission node
  implies access before `/entity start`.
- **Single enforcement point.** `plugin.yml` declares no blanket permission
  on purpose, so Bukkit can't pre-empt the gate: the lock + owner check in
  `StudioCommand` is the only door. Console is always trusted.
- **Owner override, in the open.** The `settings.owner-name` account bypasses
  permission *nodes* (including while locked) but never the lock decision
  for others, and never protected entities. Declared in `config.yml`, logged
  at every startup, `""` disables it.
- **Granular nodes.** View vs edit are separate: `inventory.view` /
  `data.view` open pages, `inventory.edit` / `data.edit` mutate; `behavior`
  and `allegiance` cover AI vs ownership; `trades`, `snapshot`, `undo`,
  `presets`, `player.edit`, `admin` cover the rest. Defaults are all `op` —
  ordinary players get nothing.
- **Sessions re-validate every mutation.** GUI clicks, chat input, preset/
  snapshot applies, confirmations, and undos re-resolve the entity by UUID
  and re-check lock, permissions, protected list, and concurrent editors.
  Stale targets, despawned entities, and revoked permissions fail closed.
- **Target protections.** `settings.security.protected-entities` (UUIDs,
  bad entries logged + ignored) can never be edited, even by the owner.
  Other players need `player.edit` (honors `protect-other-players`).
  Sessions close on quit, so disconnects can't leave stale edit locks.
- **Destructive actions need their node twice.** Remove-entity, clear-
  equipment, and every confirm dialog re-check its node at *execution* time,
  not just when the dialog opened — plus session + target checks.
- **No secret leakage.** `/entity version` (reveals activation state) and
  tab completion (subcommands, player names) are gated; player-facing errors
  are generic while details go to the console log. Snapshot/preset names are
  restricted to `[A-Za-z0-9 _-]{1,32}`.

## Build

```bash
cd entity-studio
gradle build   # or: ~/.gradle-dist/gradle-8.10.2/bin/gradle build
gradle test
```

Jar: `build/libs/EntityStudio-<version>.jar`, copied to `releases/`.
