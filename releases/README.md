# Releases

This folder always carries the **current** MineTranslate plugin jar, refreshed
automatically on every `gradle build` (the `releaseJar` task in
`build.gradle.kts` copies the fresh jar here as `MineTranslate-latest.jar`).

- **`MineTranslate-1.0.0.jar`** — the official finished plugin jar (v1.0.0).
  Drop into a Paper 1.21–1.21.11 server's `plugins/` folder (Java 21 required).
- **`MineTranslate-latest.jar`** — same jar, rolling copy always matching the
  latest successful build.

See the [README](../README.md) for install steps, commands, permissions and
the GUI guide. No API key needed — translations use Google's free endpoint
straight from your server (outbound HTTPS to `translate.googleapis.com`).

| Jar version | Plugin version | Changes |
|---|---|---|
| `MineTranslate-1.0.0.jar` | 1.0.0 | Initial release: Google Translate in-game (`/translate`, `/tr`, `/t`), `/mylang`, `/trchat` chat auto-translation (per-viewer + copy modes), `/languages`, `/trsign` + `/trbook` with `--apply`/`--copy`, full chest-menu GUI (`/trgui`) with text input, language browser and auto-translate toggle, in-memory cache, 9 unit tests green. |
