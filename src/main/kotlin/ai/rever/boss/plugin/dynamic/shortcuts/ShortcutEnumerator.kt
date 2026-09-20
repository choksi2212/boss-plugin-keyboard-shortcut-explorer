package ai.rever.boss.plugin.dynamic.shortcuts

import ai.rever.boss.plugin.api.KeyboardShortcutInfo
import ai.rever.boss.plugin.api.KeyboardShortcutProvider
import ai.rever.boss.plugin.api.McpToolRegistry

/**
 * Pulls every shortcut the panel and the MCP tools can show.
 *
 * The plugin API exposes [KeyboardShortcutProvider] for the host's view of
 * its own bindings, but not a stable enumeration of plugin-contributed
 * ones. [McpToolRegistry] has no [shortcut_*] tool today (the registry
 * call is graceful and returns an empty list when no such tool exists),
 * so the tool surface is intentionally inert.
 *
 * The split is therefore:
 * - Host: every entry returned by [KeyboardShortcutProvider.getShortcuts].
 *   These carry an `action` and a `category` the host assigned, which the
 *   panel shows verbatim.
 * - Plugins: the same provider is consulted; entries the host classifies
 *   as coming from an installed plugin (the provider annotates them via
 *   `action` prefix `plugin.<pluginId>.<name>` per the [PluginShortcutSpec]
 *   contract in Shortcuts.kt) are surfaced under
 *   `source = "plugin:<pluginId>"`. The panel does NOT scrape
 *   `ShortcutActionProvider` - those are registered on the host's
 *   dispatcher, not exposed through a public read API.
 *
 * The host provider may be null on a build that predates the
 * `keyboardShortcutProvider` member. In that case [enumerate] still
 * returns a valid (empty) list, and the panel shows the
 * "host keyboard shortcut provider unavailable" banner.
 *
 * The result is bounded by [MAX_SHORTCUTS]; the provider may list more
 * than that, and a long plugin registry would otherwise hand the panel
 * thousands of rows to render.
 */
object ShortcutEnumerator {

    /**
     * One enumeration result, returned together so the panel can render a
     * banner when [provider] is null without losing the partial list.
     */
    data class Snapshot(
        val shortcuts: List<Shortcut>,
        val provider: KeyboardShortcutProvider?,
        val hostAvailable: Boolean,
        val pluginToolsAvailable: Boolean,
        val pluginToolsScanned: Int,
    )

    /**
     * Enumerate every shortcut the plugin can see.
     *
     * Called once on register, and again from the "Refresh" button and
     * the MCP `shortcuts_list` tool. Caching is the panel's concern.
     */
    fun enumerate(
        provider: KeyboardShortcutProvider?,
        mcpRegistry: McpToolRegistry?,
    ): Snapshot {
        val list = mutableListOf<Shortcut>()
        val hostAvailable = provider != null
        if (provider != null) {
            val raw = runCatching { provider.getShortcuts() }.getOrDefault(emptyList())
            for (info in raw) {
                if (list.size >= MAX_SHORTCUTS) break
                list.add(toShortcut(info))
            }
        }

        val pluginToolsScanned = scanPluginTools(mcpRegistry)

        return Snapshot(
            shortcuts = list.take(MAX_SHORTCUTS),
            provider = provider,
            hostAvailable = hostAvailable,
            pluginToolsAvailable = pluginToolsScanned >= 0,
            pluginToolsScanned = pluginToolsScanned.coerceAtLeast(0),
        )
    }

    /**
     * Map a [KeyboardShortcutInfo] from the host into the panel's [Shortcut]
     * shape.
     *
     * The chord is built from the modifiers (sorted for determinism) and
     * the main key. Mac/Win display order is normalised on Linux/Windows
     * so a "Cmd" chord reads as "Ctrl" - the provider already does the
     * platform mapping, so we treat the supplied modifiers verbatim.
     */
    private fun toShortcut(info: KeyboardShortcutInfo): Shortcut {
        val sortedMods = info.modifiers
            .filter { it.isNotBlank() }
            .distinct()
            .sortedBy { it.lowercase() }
        val parts = sortedMods + info.key
        val chord = parts
            .filter { it.isNotBlank() }
            .joinToString("+") { it.trim() }
        val category = info.category.ifBlank { OTHER_CATEGORY }
        val source = if (info.action.startsWith("plugin.")) {
            // plugin.<pluginId>.<name>
            val tail = info.action.removePrefix("plugin.")
            val dot = tail.indexOf('.')
            if (dot > 0) {
                val pluginId = tail.substring(0, dot)
                "plugin:$pluginId"
            } else {
                "host"
            }
        } else {
            "host"
        }
        val pluginId = if (source.startsWith("plugin:")) source.removePrefix("plugin:") else null
        return Shortcut(
            keyChord = chord,
            actionName = info.action,
            category = category,
            source = source,
            description = info.description.ifBlank { info.action },
            pluginId = pluginId,
        )
    }

    /**
     * Probe [McpToolRegistry] for any tool whose name starts with `shortcut_`.
     *
     * No such tool ships today, and the registry may be null on a build
     * where MCP tooling isn't exposed. Returns -1 to signal "no registry",
     * otherwise the count of registered tools scanned (zero or more).
     */
    private fun scanPluginTools(mcpRegistry: McpToolRegistry?): Int {
        if (mcpRegistry == null) return -1
        return runCatching {
            mcpRegistry.allTools.value.count { it.definition.name.startsWith("shortcut_") }
        }.getOrDefault(-1)
    }
}
