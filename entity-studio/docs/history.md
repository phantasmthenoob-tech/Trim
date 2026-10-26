# History & undo — what can and cannot be restored

Every successful edit appends one `EditRecord`: actor UUID, entity UUID,
entity type, operation label, wall-clock timestamp (captured at creation),
before-value, after-value, risk level, and whether recovery is supported.
Records are per-player, newest-first, bounded by
`settings.history.max-entries-per-player` (default 500, floor 10, applied on
enable and on `/entitystudio reload`); the oldest entries are dropped first.
Invalid entries (null record, missing actor/entity identity) are dropped,
never stored.

## Guarantees

- **No live object references.** Every undo closure re-resolves its entity by
  UUID at fire time (`Bukkit.getEntity`) and refuses dead, invalid, or
  type-changed targets with a reason instead of editing the wrong entity.
  Item snapshots are deep clones taken before the mutation.
- **Authorization is re-checked.** Undoing runs the same lock, permission
  (`entitystudio.undo`), protected-entity, and concurrent-editor checks as
  any other mutation, against the *current* state — not the state when the
  menu was opened. A peek at the newest record pre-validates target access;
  failures report `undo-empty` / `undo-irreversible` / `Undo failed` in chat
  with details in the console, never a stack trace to the player.
- **Newest-first, one pop per undo.** An irreversible record on top is popped
  and reported as such; the older reversible edit beneath it stays undoable
  (partial recovery — nothing is skipped or merged).
- **The history page is honest.** Each row shows entity type + short UUID,
  before → after, risk level, timestamp, and either `Click to undo (newest
  first)` or `Cannot be undone`. Clicking a row undoes in order until it is
  reached; single-step undo is the "Undo last" button.

## Reversible (undo restores the exact prior value)

| Area | Restored on undo |
|---|---|
| Typed properties | Prior value via the same setter (health, max health, age, names, colors, sizes, fuse, gravity, AI-adjacent flags, etc.) |
| Equipment slots (6) | Prior `ItemStack` clone per slot; swaps, item-editor changes, bulk restore |
| Drop chances | Prior float per slot |
| Attributes | Prior base value (chat entry and reset-to-default) |
| Effects | Removed effect re-added with same type/duration/amplifier; clear-all restores the full list |
| AI / awareness toggles | Prior boolean on the live entity |
| Allegiance flags | Exact prior combination (defend owner/allies, retaliate, join owner attacks, friendly-fire guard) |
| Owner / trusted / enemies | Prior UUID or prior set membership |
| Allowed / excluded type filters | Prior set contents (including cleared → restored) |
| Anchors | Prior anchored/hard state **and** coordinates |
| Target clear | Previous target re-acquired by UUID — only if that entity still exists, is valid, and is alive |
| Behavior profile apply | Prior profile + all rule flags + prior AI/awareness booleans (two records: plugin-side, then brain state) |
| Teleport (both paths) | Prior location; fails safely if the original world is unloaded |
| Snapshot / preset equipment + behavior restores | The loadout / profile that was overwritten |

## NOT reversible (recorded with `Cannot be undone`)

- **Remove entity** (`DESTRUCTIVE`, shift-click + confirm): the UUID never
  returns. The managed record, session lock, and stash are also dropped.
- **External changes after the fact**: another plugin, a command, or the
  player's own hand changing the same slot/property between the edit and the
  undo means the undo restores the *recorded* prior value, not a merge with
  the intervening change. Undo is last-writer-wins, same as the editor.
- **Consumed or transformed state**: items eaten/burned/crafted away, mobs
  that died, unloaded-then-garbage-collected targets, worlds that no longer
  load — the undo fails with a reason (`Entity is gone`, `Previous target is
  gone`, `Original world is gone`) instead of fabricating state.
- **Profile apply does not re-acquire the old attack target.** Flags, profile,
  and brain booleans come back; the mob picks its next target by its own
  rules. The record label says so.
- **Live player inventories have no undo.** Player inventory / ender chest
  views write straight through to the player's live inventory (no
  snapshotting — that would be a dupe vector); only equipment-slot and item
  edits on non-player entities, plus stash withdraw/deposit, record undo.

## What still needs a live server to verify

Unit tests (`HistoryServiceTest`: sequential edits, repeated undo order,
irreversible-then-older recovery, invalid-entry drops, null-actor safety;
service-level failure propagation) cover the decision and storage logic.
These behaviors cannot be proven without a running Paper server and are
**not** claimed as verified: undo against a despawned entity, undo after a
chunk unload / restart, target re-acquisition of a logged-out player's
attacker, teleport-back across a world unload, and any interaction with
other plugins fighting over the same entity.
