# Entity Studio — permissions

Every view page checks its node on open, and every write path re-checks its
node at click/execute time. `entitystudio.admin` inherits everything; the
configured owner (`settings.owner-name`) bypasses permission *nodes*
transparently (logged at startup) but never the activation lock for others
and never protected entities.

| Node | Grants |
|---|---|
| `entitystudio.use` | Open the editor (after activation) |
| `entitystudio.inspect` | Inspect mode + summaries |
| `entitystudio.inventory.view` | Open live inventories |
| `entitystudio.inventory.edit` | Equipment swaps, item edits, slot writes |
| `entitystudio.data.view` | Read properties/advanced data |
| `entitystudio.data.edit` | Property writes, effects, attributes, entity removal |
| `entitystudio.behavior` | AI/aware/target/profile/anchor/teleport edits |
| `entitystudio.allegiance` | Owner/trusted/enemy edits |
| `entitystudio.trades` | Offer reset/remove/add, clear-all |
| `entitystudio.snapshot` | Save/restore snapshots |
| `entitystudio.undo` | Undo pages + undo command |
| `entitystudio.presets` | Save/apply presets |
| `entitystudio.player.edit` | Edit other players (with `security.protect-other-players`) |
| `entitystudio.admin` | Reload, all of the above |

## View vs edit

Opening a page requires the view node; changing anything requires the edit
node. Dashboard/summary needs only `use`.

| Page | View node | Edit node(s) |
|---|---|---|
| Properties, Attributes, Effects | `data.view` | `data.edit` |
| Equipment, player/container inventories, item editor | `inventory.view` | `inventory.edit` |
| Trades | `trades` | `trades` |
| Behavior (AI, profiles, anchors, teleports) | `behavior` | `behavior` / `allegiance` |
| History | `undo` | `undo` |
| Snapshots | `snapshot` | `snapshot` |
| Presets | `presets` | `presets` (+ `data.edit` / `inventory.edit` / `behavior` for the sections applied) |

## Execution-time rules

- **Confirmations** (entity removal, clear equipment) store their required
  node and re-check it when CONFIRM is pressed, together with a fresh session
  validation and target-access check.
- **Preset section picker, snapshot restore, full preset apply** re-validate
  the session and target (stale pickers can't hit a swapped entity).
- **Chat input** (values, names, teleports, owner/trust edits) re-validates
  the session and target when the text arrives — up to 60s after the click.
- **Undo** re-checks `undo` plus current target protections for the record
  being undone, via a peek-before-pop.
- **`/entity version`** (reveals activation state) and **tab completion**
  (subcommands, player names) are gated; completion fails silently.
- Player-facing errors are generic; details go to the console log.
- Snapshot/preset names must match `[A-Za-z0-9 _-]{1,32}`.
- Sessions close on quit; inspect mode exits on quit.

Activation: the plugin starts locked; `/entity start` (owner or console) unlocks
for everyone else. The owner never needs the lock or op.

Protected entities (`settings.security.protected-entities`, UUID list) can never
be edited, even by the owner. Other-player editing additionally requires
`entitystudio.player.edit` unless the target is yourself.
