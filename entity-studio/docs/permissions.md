# Entity Studio — permissions

Every write path re-checks its node at click/execute time. `entitystudio.admin`
inherits everything; the configured owner (`settings.owner-name`) bypasses
permission checks transparently (logged at startup).

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

Activation: the plugin starts locked; `/entity start` (owner or console) unlocks
for everyone else. The owner never needs the lock or op.

Protected entities (`settings.security.protected-entities`, UUID list) can never
be edited, even by the owner. Other-player editing additionally requires
`entitystudio.player.edit` unless the target is yourself.
