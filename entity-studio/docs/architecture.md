# Entity Studio — architecture

```
Player action → selection/session → capability check → GUI describes
            → provider/property mutates → history records
```

## Layers

| Layer | Classes | Job |
|---|---|---|
| Selection | `selection.EntitySelector` | Shift-right-click / inspect mode → session. Resolves by UUID. |
| Session | `session.SessionManager`, `EditorSession` | Tracks editor, entity UUID, page. Re-validates before every mutation: entity exists, editor authorized, not protected, no concurrent editor. |
| Capability | `capability.Capability`, `CapabilityRegistry` | Detects per-entity features. The GUI only offers what is detected. |
| Property | `property.*` | Typed `EntityProperty` definitions (id, display, type, risk, support, read/validate/write/parse). `PropertyRegistry.apply` enforces permission → validation → snapshot → write → history. |
| GUI | `gui.StudioGUI` | Describes settings; never mutates directly except inventory swaps, which go through history. |
| Behavior | `behavior.AllegianceService`, `AllegianceRules`, `ManagedRecord` | Plugin-managed ownership/trust/enemies, retaliation + protect listeners, anchor reconciler (only anchored entities, throttled). |
| Persistence | `persistence.ManagedStore` | UUID-keyed `managed.yml`, validated on load, corrupt entries quarantined with logs. |
| Snapshot | `snapshot.SnapshotService` | Schema-versioned snapshots + presets with preview and selective apply. Supported state only — never raw NBT rewrites. |
| History | `history.*` | Bounded per-player undo stacks; reversible flag is honest. |
| Gate | `permission.Gatekeeper` | Locked-until-`/entity start` + transparent owner override. Pure logic, unit tested. |

## Key decisions

- **Edit the real entity.** Properties write through the public Bukkit/Paper API.
  Inventories open live where the API allows (`openInventory` on the real
  holder). Equipment uses a swap GUI that writes slots immediately.
- **Capabilities, not instanceof chains.** The dashboard renders buttons from
  `CapabilityRegistry.detect(entity)`.
- **Deterministic targeting.** `AllegianceRules.evaluate` order: validity →
  exclusions (owner/allies/protected) → type filters → explicit enemies.
  Protected players always win over enemy rules.
- **No per-tick scans.** Allegiance uses damage/target events; anchors use one
  throttled task that only visits anchored entities.
- **Main thread only.** All entity mutation runs on the server thread
  (events, commands, scheduler tasks). The plugin performs no async entity I/O.
- **Version boundary.** `compatibility` is reserved for internal adapters;
  v0.1.0 uses public API only and labels unsupported controls honestly.
