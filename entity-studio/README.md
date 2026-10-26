# Entity Studio — visual entity editor for Paper 1.21.x

Shift + right-click an entity, change its properties in a GUI, and watch the
real entity change. No `/data` memorization.

> **Status: v0.1.0 — foundation + equipment + data + behavior core.**
> Roadmap stages 01–04 (core) are implemented; stages 05–06 (rich presets UI,
> isolated internal-data module) are partially present. See `docs/`.

## Quick start

1. Requires Paper **1.21–1.21.11** and Java **21**.
2. Drop `releases/EntityStudio-latest.jar` into `plugins/`, restart.
3. The plugin starts **locked**. Run `/entity start` (owner account or console),
   then ops can use `/entity`, `/entitystudio`, and shift + right-click editing.
4. Shift + right-click any entity to open its dashboard.

## What works in v0.1.0

- **Dashboard** — name/type/UUID/coords, health, AI + target state, capabilities,
  warnings, property search, entity removal (confirm, recorded).
- **Properties** — ~25 typed properties (health, max health, fire/freeze/air,
  invulnerable, gravity, silent, persistent, AI, awareness, name/visibility,
  glow, age, taming, villager profession/level/XP, creeper fuse, colors,
  slime size, horse jump/chest, wolf anger) with validation, risk badges and
  undo. Click booleans to toggle; type values in chat.
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
- **History** — every edit records before/after; undo for reversible ops;
  honest "cannot be undone" for the rest.
- **Snapshots & presets** — save, restore, preview exact field changes, apply
  properties/equipment/behavior selectively.
- **Inventories** — player inventory + ender chest and mount/container
  inventories open **live**. Offline player editing is explicitly unsupported.

## Activation & owner override (read this)

- Commands stay **locked** until `/entity start` after each restart.
- Only the account in `settings.owner-name` (`config.yml`, default
  `"ImNotAllocate"`) — or console — can run `/entity start`.
- That owner can always use the editor, even while locked and even without op.
- This override is **declared in config.yml and logged at startup on purpose**:
  it is not hidden anywhere in the code. Change it to your name, or set it to
  `""` to disable it.

## Permissions

See `docs/permissions.md` — every page re-checks its node at click time; hidden
buttons are never the security boundary.

## Build

```bash
cd entity-studio
gradle build   # or: ~/.gradle-dist/gradle-8.10.2/bin/gradle build
gradle test
```

Jar: `build/libs/EntityStudio-<version>.jar`, copied to `releases/`.
