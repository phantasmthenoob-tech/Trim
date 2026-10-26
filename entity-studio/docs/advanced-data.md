# Advanced data inspector (read-only)

The Advanced page (`/entitystudio` → dashboard → Advanced, or the data
section) is a **diagnostic viewer, never an editor**. It has no write path:
rows render values that were already read; nothing is written back. (Writing a
partial snapshot could erase unknown fields, so no such path exists.)

## What is shown, and which public API provides it

Every row carries a `source` tag. Today every row is `public-api`: no NMS,
no reflection, and no version-specific internals are used anywhere in the
inspector. Verified against the pinned `paper-api:1.21.11-R0.1-SNAPSHOT`
artifact (`javap` on the real jar):

| Section | Contents | Public API used |
|---|---|---|
| `identity` | class, entity type, entity id, UUID, world, position, ticks-lived, velocity, passenger count, vehicle, portal cooldown | `Entity#getClass/#getType/#getEntityId/#getUniqueId/#getWorld/#getLocation/#getTicksLived/#getVelocity/#getPassengers/#getVehicle/#getPortalCooldown` |
| `tags` | scoreboard tags, one row each | `Entity#getScoreboardTags` |
| `pdc` | plugin/custom data, per namespaced key | `PersistentDataHolder#getPersistentDataContainer`, typed reads (`STRING`, `INTEGER`, `LONG`, `DOUBLE`, `FLOAT`, `SHORT`, `BYTE`, `BOOLEAN`, primitive arrays, `LIST.*`, nested `TAG_CONTAINER` one level deep) |
| `snbt` | full entity data, parsed into typed rows | `Entity#getAsString` (SNBT text), with a `createSnapshot().getAsString()` fallback for entity types whose direct read refuses |

Each identity getter is isolated: one failing call becomes a single
`<unavailable>` row, never a blank page. The same applies per section —
if the snapshot string cannot be read or parsed, the `snbt` section reports
the reason instead of inventing rows.

## What is deliberately NOT available

- **Raw NBT bytes / internal fields.** Paper's public API exposes entity data
  as the SNBT string above, not as a writable NBT tree. Anything that has no
  safe public read path is shown as `unsupported`/`unavailable`, never faked.
- **Version-specific internals.** No `net.minecraft` classes and no
  reflection probes ship with the plugin. Reads go through the
  `com.entitystudio.data.DataInspector` seam, so a future
  version-gated probe could be added behind that interface — and fail closed
  — without touching the GUI. It was not needed: `getAsString()` already
  covers full-entity reads through the public contract.
- The config key `settings.advanced-data.allow-internal-access` remains
  reserved and defaults to `false`. Enabling it changes nothing today; it
  exists so any future internal probe is opt-in, logged, and isolated.

Treat all of this as diagnostic information, not a stable contract: Mojang
field names and snapshot contents can change between versions.

## Safety limits

- SNBT input is refused above 64 KiB, parsed with a 4,000-node budget and a
  nesting cap — runaway data becomes an `<unavailable: …>` row, never a
  server-thread freeze.
- PDC is capped at 250 keys/section, tags at 120, SNBT at 500 rows; the GUI
  paginates (45 rows/page) and the row count is shown in the help item.
- Row values truncate at 200 characters in the GUI, 400 in chat. Nested
  values deeper than 5 levels render inline (`{a=1, b=[…]}`).

## Redaction and player data

- PDC keys that look credential-like (`token`, `secret`, `password`, `api-key`,
  `auth`, `session`, `bearer`, private keys — case-insensitive) show their
  existence and type, but the value is replaced with `<redacted>` **at build
  time**: renderers and chat output never receive the secret.
- When the target is another player and the viewer lacks
  `entitystudio.player.edit`, the whole `tags`/`pdc`/`snbt` sections arrive
  pre-redacted (a player's snapshot can contain inventory and other private
  data). Identity facts stay visible, matching what the dashboard already
  shows. Redacted rows say so in chat instead of leaking the value.

## Permissions and config

- Opening the page requires `entitystudio.data.view` (re-checked on every
  open and refresh, like every other page).
- `settings.advanced-data.enabled: false` disables the page entirely.

## Verification

- Unit tests (no server needed): `SnbtParserTest` (compounds, lists, typed
  arrays, strings/escapes, malformed input, node/depth/size caps),
  `DataBrowserTest` (flattening, sorting, type labels, caps, search,
  redaction), `PublicApiInspectorTest` (null/failing getters become
  UNAVAILABLE, other-player data REDACTED, PDC typed/unknown reads).
- Live-server checks still required: open the page for a zombie, a horse
  with chest, a chest boat, and a villager; confirm sections render, search
  filters, pagination works, row click echoes the full value in chat, and no
  errors appear in the console.
