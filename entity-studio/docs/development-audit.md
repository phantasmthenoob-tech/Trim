# Entity Studio — development audit (2026-10-26, branch `11-hr`)

Verified by inspection + build. Reported facts were re-checked; only what
passed below is claimed.

## 1. Build configuration (verified)

- Gradle 8.10.2 (local dist), JDK 21 toolchain (`jdk-21.0.13+9`), UTF-8,
  `-Xlint:deprecation -Xlint:unchecked`, JUnit 5.11.4 (jupiter, launcher).
- Repos: Maven Central + `repo.papermc.io` (reachable from this environment).
- Paper API **pinned, not floating** — correct, keep it that way.
- `plugin.yml`: `api-version: '1.21'`, main class, command/permission tree all
  present and consistent with `StudioCommand` + `StudioPermissions`.
- Source: 27 main + 9 test files, `com.entitystudio.{behavior,capability,
  command,gui,history,permission,persistence,property,selection,session,
  snapshot}` + plugin root. Matches `docs/architecture.md`.
- Working tree clean at audit start; nothing outside `entity-studio/` touched.

## 2. Version discrepancy — RESOLVED: target is Paper 1.21.11

- Evidence: `paper-api/1.21.11-R0.1-SNAPSHOT` confirmed present upstream
  (maven metadata lists it; the jar downloaded and compiled here).
- The static `-SNAPSHOT.pom` URL 404s — expected; Gradle resolves
  timestamped snapshots via maven-metadata, which works.
- Migration cost was exactly 3 lines: `Attribute.GENERIC_MAX_HEALTH` no
  longer exists (verified via `javap` on the real 1.21.11 artifact — the
  whole `Attribute` enum dropped the `GENERIC_` prefix) → `MAX_HEALTH`.
- Pin is now `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT`.
- Remaining deprecation warnings (`getCustomName/setCustomName`,
  `getDescription().getVersion()`) are benign.

## 3. Baseline (after corrections): `gradle build` green, 60/60 tests pass

Suites: Gatekeeper 9, SessionManager 12, AllegianceRules 7,
AllegianceService 8, History 6, Validators 4, ContainerPages 6, DataRows 6,
SnapshotService 2. No failures, no errors.

## 4. JAR staleness — checked, fresh at audit start

`releases/EntityStudio-0.1.0.jar` == `releases/EntityStudio-latest.jar` ==
`build/libs/EntityStudio-0.1.0.jar` (identical md5, built from current
sources, embedded `plugin.yml` version matches). Not stale — but the
**version label was**: jars said 0.1.0 while containing post-0.1.0 stages.
Corrected to **0.2.0** (build file, README, property-support doc, fresh
release jars; old 0.1.0 artifacts removed from `releases/`).

## 5. Findings

**Broken:** none (build + tests green; the 1.21.11 migration broke 3 lines,
fixed and re-verified in the same session).

**Incomplete (acknowledged, scheduled):** deep preset merging (selective
apply exists); isolated internal-NBT writes (deliberately out — public API
only); offline player editing (explicitly unsupported, documented).

**Unsafe (remaining gaps, not yet fixed):**
1. Snapshot/preset equipment restore writes slots with no history record —
   silent and non-undoable. Should record or label.
2. `warnings()` takes an unused `caps` param — dead weight, tiny cleanup.
3. Retaliation handler scans all managed records per damage event — fine
   now, revisit if managed sets grow large.

**Duplicated (tolerable):** session-validation logic exists in both
`SessionManager.validate` and `StudioCommand.validatedListEntity`; container
undo lambdas repeat the backing-inventory lookup. No action — small and
tested.

**Misleading docs (fixed):** README/`property-support.md` said v0.1.0;
`build.gradle.kts` comment implied 1.21.1→1.21.11 coverage without pinning
1.21.11. All corrected.

## 6. Changes made in this prompt (no features added)

- `paper-api` 1.21.1 → 1.21.11; `Attribute.GENERIC_MAX_HEALTH` →
  `Attribute.MAX_HEALTH` (3 sites).
- Project version 0.1.0 → 0.2.0; release jars refreshed; stale 0.1.0
  artifacts removed from `releases/`.
- This audit file. Docs/README version labels corrected.

## 7. Prioritized blockers for next stages

1. **P1 — live-server verification.** Headless green is not proof on a
   server: shift-click entry, container mirror on llamas/horses/hoppers,
   guard retaliation vs arrows, anchors across restarts, two-admin
   concurrency. Needs a Paper 1.21.11 test server (forbidden here).
2. **P2 — snapshot equipment restore without history.** See §5.1.
3. **P3 — deep preset merging** (still the major incomplete feature).
4. **P4 — dependency drift.** Re-check the pin when targeting newer 1.21.x;
   never float it.
