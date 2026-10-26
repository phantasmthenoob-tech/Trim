# MineTranslate — Google Translate for Minecraft (Paper 1.21.x)

A fully working in-game Google Translate for Paper **1.21 – 1.21.11**.
No API key needed — translations use Google's free endpoint straight from your server.

## Features

| Command | What it does |
|---|---|
| `/translate <target> <text...>` | Translate any text. Auto-detects the source language. Click the result to copy it. |
| `/translate <source> <target> <text...>` | Translate with an explicit source language. |
| `/translate <source>><target> <text...>` | Same thing, compact form (`es>en Hola`). |
| `/mylang <language>` | Set your own default language. |
| `/trchat <language\|off\|status>` | Auto-translate all incoming chat into your language. |
| `/languages [search]` | List all ~100 supported language codes. |
| `/trsign <target> [--apply]` | Translate the sign you're looking at. `--apply` writes it back. |
| `/trbook <target> [--copy]` | Read a translated book in chat, or `--copy` to get a translated copy. |
| `/translate-reload` | Reload `config.yml`. |
| `/translate` (no text) or `/trgui` | Open the MineTranslate **GUI** — translate, languages & auto-translate in menus. |

Auto-translated chat shows a `[en➜es]` prefix with the original message on hover.
Aliases: `/tr`, `/t`, `/setlang`, `/langs`, `/autotranslate`.

## GUI

Run `/translate` with no arguments (or `/trgui`) to open the chest-menu GUI:

* **Translate text** — click, type the text in chat (or `cancel`), and the
  translated result appears in a GUI with the original beside it. Click
  **Send to chat** to post it.
* **Target language** — paginated browser with all ~100 languages.
* **Auto-translate toggle** — turn chat auto-translation ON/OFF right in the
  menu, no command needed.
* **My language / Chat language** — pick your language and the
  auto-translate target from the same browser.

## Requirements

* Paper (or Purpur / Pufferfish / Folia-compatible fork) **1.21.x** — the jar targets `api-version: 1.21`, so it loads on 1.21 through 1.21.11.
* Java **21** on the server.
* Outbound HTTPS access to `translate.googleapis.com` (no key required).

## Install

1. Download `MineTranslate-<version>.jar` (or `releases/MineTranslate-latest.jar`) from this repo.
2. Drop it into your server's `plugins/` folder.
3. Restart the server — `config.yml` and `playerdata.yml` are created automatically.
4. Run `/languages`, then `/mylang es` and `/trchat es` to try it.

## Build

Requires JDK 21.

```bash
gradle build   # or: ./gradle-8.10.2/bin/gradle build
gradle test    # unit tests (response parsing, chunking, language codes)
```

The plugin jar lands in `build/libs/MineTranslate-<version>.jar` and is copied to
`releases/MineTranslate-latest.jar`.

## Permissions

`minetranslate.use`, `minetranslate.setlang`, `minetranslate.auto`,
`minetranslate.list`, `minetranslate.sign`, `minetranslate.book` (default: true);
`minetranslate.apply` (write signs/books, default: op);
`minetranslate.admin` (reload, default: op).

## How chat translation works

With `chat.mode: per-viewer` (default), players who enabled `/trchat` only see the
translated message (original on hover) — everyone else sees the normal message.
If your chat plugin conflicts, set `chat.mode: copy` so subscribers get an extra
translated line after the original instead.

## License

MIT — see `LICENSE`.
