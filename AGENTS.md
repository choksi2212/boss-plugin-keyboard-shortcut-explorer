# AGENTS.md

Guidance for coding agents working on this repository.

## What this plugin is

A BOSS Console dynamic plugin that exposes every keyboard shortcut available in BOSS - host + plugins - in one searchable, conflict-aware sidebar panel, and to in-terminal agents through four `shortcuts_*` MCP tools.

## Layout

```
src/main/kotlin/ai/rever/boss/plugin/dynamic/shortcuts/
  Shortcut.kt                 - data model (Shortcut, DEFAULT_CATEGORIES, bounds)
  ShortcutEnumerator.kt       - combines the host provider and the MCP tool scan
  ConflictDetector.kt         - groups by chord and returns collisions
  PressListener.kt            - stub bridge for "press to find/record"; disabled today
  ShortcutsViewModel.kt       - panel state, filtering, candidate rebinds, markdown
  ShortcutsComponent.kt       - Decompose component owning the panel UI state
  ShortcutsContent.kt         - composable surface (header, search, chips, list)
  ShortcutsInfo.kt            - panel id and slot
  ShortcutsMcpTools.kt        - four MCP tool definitions + handlers
  ShortcutsDynamicPlugin.kt   - entry point; registers panel + MCP tools
src/main/resources/META-INF/boss-plugin/
  plugin.json                 - manifest; type "mixed"; out-of-process + fallback
```

## Three sources, one canonical list

`ShortcutEnumerator.enumerate` is the single door every panel render and every MCP call walks through:

- **Host shortcuts** - everything `KeyboardShortcutProvider.getShortcuts()` returns. The provider tags plugin-contributed actions with the `plugin.<pluginId>.<name>` action id prefix (per the [PluginShortcutSpec] contract in Shortcuts.kt); the enumerator decodes that into `source = "plugin:<pluginId>"` so the source chips render sensibly.
- **Plugin-to-plugin** - `McpToolRegistry.allTools` is scanned for any tool whose name starts with `shortcut_`. None ships today; the scan is graceful (returns -1 when the registry is absent, otherwise the count) so the panel can show a "N tools detected" banner without ever throwing.
- **Host-internal beyond the provider** - the host does not expose its full keymap through a stable read API. The panel surfaces "host keyboard shortcut provider unavailable" when the provider is null; it deliberately does not try to scrape the host's internal classes (the spec warned against this).

## Bounding everything

The bounds in `Shortcut.kt` exist because a long plugin registry could hand the panel thousands of rows otherwise:

- `MAX_SHORTCUTS = 2,000` - the enumerator stops appending past this count.
- `MAX_CATEGORIES = 50` - the chips row caps here.
- `MAX_CHORD_LENGTH = 8` (key tokens) - reject longer chords from the host provider, even though no host build produces them today.
- `MAX_PLUGIN_SOURCES = 50` - the source chip set is capped so a hostile provider can't add a thousand `plugin:*` chips.

An empty filter set ("no categories selected", "no sources selected") is treated as "show everything"; otherwise the user could land in a state where the panel renders nothing after toggling the last chip. `ShortcutsViewModel.toggleCategory` / `toggleSource` reset to the canonical default on an empty toggle.

## The "Press to find" / "Press to record" buttons

`PressListener` is an explicit "no" surface today: `available = false`, `unavailableReason` is set, `awaitNextPress()` returns null. The buttons in the composable are rendered disabled with the reason under them. A future host release that exposes `registerKeyListener(...)` would flip `available` to true and the buttons would activate; nothing in the rest of the plugin depends on the listener being implemented.

The candidate rebind list (`ShortcutsViewModel.candidateRebinds`) is therefore populated only by `recordRebind(chord)` callers, never by the listener - the "Press to record" button is a placeholder until the host bridge ships.

## Markdown and rebind snippet

The panel's "Copy markdown" button and the `shortcuts_markdown` MCP tool both call the same `buildMarkdown` helper in `ShortcutsViewModel`. The "Copy rebinds" button formats the candidate chord list as a JSON-ish snippet (`"Cmd+Shift+P"` per line) so a user can paste it into a plugin's `keymap` block.

## Build

```bash
./gradlew buildPluginJar -x test --no-daemon
```

Local builds read the api jar from `../boss-plugin-api/build/libs/boss-plugin-api-1.0.93.jar`. CI sets `CI=true` and reads it from `build/downloaded-deps/boss-plugin-api.jar` after downloading a matching release.

## CI

`.github/workflows/build.yml` runs on push to `main` and delegates to the upstream release workflow. The `permissions: contents: write` block is required - the upstream workflow pushes a version bump commit back to main, and without write access `github-actions[bot]` returns 403 and the whole release run fails before the build even runs.

`.github/workflows/test.yml` runs `./gradlew build` on every pull request; `build` depends on `buildPluginJar`, so a packaging break is caught here, not at release.

## Style

- Kotlin files end with a newline.
- Prose uses spaced hyphens (` - `), never em-dashes (U+2014).
- ktlint and detekt are not yet wired into this repo; rely on the same style as `boss-plugin-page-content`.

## What deliberately stays out of scope

- A global key listener. The plugin SDK does not expose one today; the buttons render disabled with a banner explaining why. A future host release that adds one would flip a single boolean in `PressListener`.
- A rebind API. The host owns keymap storage; this plugin surfaces "candidate rebinds" as a copyable snippet, not as a persisted change.
- Host-internal shortcut enumeration beyond what `KeyboardShortcutProvider` returns. The panel does not scrape internal classes - it surfaces a banner when the provider is null and the user updates the host if they want full coverage.
