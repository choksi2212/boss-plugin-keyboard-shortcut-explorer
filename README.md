# Shortcuts Plugin for BOSS Console

A BOSS Console plugin that lists every keyboard shortcut available in the app - the host's own bindings plus those contributed by every installed plugin - in one searchable, conflict-aware sidebar panel.

Two surfaces:

- **A sidebar panel** that lets a person search by action name, key chord or category, filter by category (Navigation, Editor, Panel, MCP, Tab, Window, Plugin) and source (host, plugin:*), inspect every chord that collides with another, and copy a markdown cheatsheet or a rebind snippet to the clipboard.
- **Four MCP tools** for in-terminal agents: `shortcuts_list`, `shortcuts_for_key`, `shortcuts_conflicts`, `shortcuts_markdown`.

## Why it exists

The BOSS host already registers hundreds of internal shortcuts across its window, tab, editor, panel and MCP surfaces, and every installed plugin can register its own on top. None of those were visible in one place - a user had to read each plugin's docs to discover its bindings, and conflicts between two plugins (or a plugin and the host) only surfaced at runtime, when a binding fired and the wrong action triggered.

This plugin provides one canonical answer for the common case: *what shortcuts are currently bound, and which of them collide?*

## Install

1. Download `boss-plugin-keyboard-shortcut-explorer-0.1.0.jar` from a release.
2. Open BOSS Console.
3. Open the **Toolbox** (Plugin Manager).
4. Install from local jar and enable.

`minBossVersion` is 9.4.2; `apiVersion` and `minApiVersion` are 1.0.93.

## Panel

The panel renders, top-to-bottom:

- A search bar (filters by action name, key chord, category or description).
- Category chips (Navigation, Editor, Panel, MCP, Tab, Window, Plugin, Other) - toggle any chip to narrow the list.
- Source chips (`host`, `plugin:<pluginId>` for every loaded plugin) - toggle any chip to narrow the list.
- A "Press to find" / "Press to record" row. These render disabled with a banner today - the plugin SDK does not expose a global key listener, so capturing a chord in-band is reserved for a future host release.
- A collapsible Conflicts section listing every chord bound to two or more actions.
- The filtered shortcut list - one row per action, with the chord rendered left, the action name, the category and source as a sub-line, and the description below.
- A footer with "Copy markdown" (puts the full cheatsheet on the clipboard) and "Copy rebinds" (puts the recorded chord list as a JSON snippet for pasting into `plugin.json`).

A short-lived toast confirms each action; an error toast surfaces a clipboard failure or a "host keyboard shortcut provider unavailable" banner when the host build pre-dates `keyboardShortcutProvider`.

## MCP tools

| Name | What it does |
|---|---|
| `shortcuts_list` | Returns the filtered list of shortcuts. Filters: `query` (substring on action/chord/category/description), `category`, `source`. Bounded at 2,000 rows. |
| `shortcuts_for_key` | Returns every action bound to a single chord. Required: `key_chord`. Case-insensitive. |
| `shortcuts_conflicts` | Returns every chord bound to two or more actions, with the actions it conflicts with. |
| `shortcuts_markdown` | Returns a markdown cheatsheet of every shortcut, grouped by category, with a conflicts section at the end. Also copies the same string to the clipboard when a `clipboardProvider` is available. |

All four are read-only: this plugin exposes a search-and-inspect surface, and the rebind API lives in the host, not in this toolset.

## Compatibility

- BOSS API: 1.0.93
- BOSS host: 9.4.2 or newer
- Platforms: out-of-process with in-process fallback
- Host dependency: reads `keyboardShortcutProvider` and `mcpToolRegistry` from the plugin context; degrades gracefully when either is null

## License

Apache 2.0. See `LICENSE` if present in the release artifact, or the upstream BOSS Console plugin SDK licensing terms.
