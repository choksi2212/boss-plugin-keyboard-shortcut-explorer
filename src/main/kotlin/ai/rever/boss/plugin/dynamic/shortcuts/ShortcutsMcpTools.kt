package ai.rever.boss.plugin.dynamic.shortcuts

import ai.rever.boss.plugin.api.ClipboardProvider
import ai.rever.boss.plugin.api.KeyboardShortcutProvider
import ai.rever.boss.plugin.api.McpToolArgs
import ai.rever.boss.plugin.api.McpToolDefinition
import ai.rever.boss.plugin.api.McpToolHandler
import ai.rever.boss.plugin.api.McpToolProvider
import ai.rever.boss.plugin.api.McpToolResult
import ai.rever.boss.plugin.api.McpToolRegistry
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The MCP tools contributed by the Keyboard Shortcut Explorer.
 *
 * Four tools, each a focused slice over the same enumerator the panel
 * uses:
 *  - `shortcuts_list`     - filtered list of every shortcut.
 *  - `shortcuts_for_key`  - shortcuts bound to a single chord.
 *  - `shortcuts_conflicts`- groups of actions sharing one chord.
 *  - `shortcuts_markdown` - the full markdown cheatsheet, suitable for
 *    saving or piping into another tool.
 *
 * All four are read-only: the plugin exposes a search-and-inspect surface
 * and the rebind API lives in the host, not in this toolset.
 */
internal class ShortcutsMcpToolProvider(
    override val providerId: String,
    private val keyboardShortcutProvider: KeyboardShortcutProvider?,
    private val mcpRegistry: McpToolRegistry?,
    private val clipboardProvider: ClipboardProvider?,
) : McpToolProvider {

    override fun tools(): List<McpToolDefinition> = listOf(
        list(),
        forKey(),
        conflicts(),
        markdown(),
    )

    private fun list(): McpToolDefinition = McpToolDefinition(
        name = "shortcuts_list",
        description =
            "List every keyboard shortcut currently enumerable in BOSS, filtered by category and source. " +
                "Returns one shortcut per row with its chord, action name, category, source and description. " +
                "The result is bounded at 2,000 rows.",
        inputSchema = LIST_SCHEMA,
        handler = McpToolHandler { args -> handleList(args) },
    )

    private fun forKey(): McpToolDefinition = McpToolDefinition(
        name = "shortcuts_for_key",
        description =
            "Return every shortcut bound to a single chord (case-insensitive). " +
                "Use this to answer 'what does this key do' or 'is this chord taken'.",
        inputSchema = FOR_KEY_SCHEMA,
        handler = McpToolHandler { args -> handleForKey(args) },
    )

    private fun conflicts(): McpToolDefinition = McpToolDefinition(
        name = "shortcuts_conflicts",
        description =
            "Return every key chord bound to two or more actions, with the " +
                "actions it conflicts with. Useful for rebind planning.",
        inputSchema = "{}",
        handler = McpToolHandler { _ -> handleConflicts() },
    )

    private fun markdown(): McpToolDefinition = McpToolDefinition(
        name = "shortcuts_markdown",
        description =
            "Return a markdown cheatsheet of every shortcut, grouped by " +
                "category, with a conflicts section at the end. Suitable for " +
                "saving to a file or pasting into docs.",
        inputSchema = "{}",
        handler = McpToolHandler { _ -> handleMarkdown() },
    )

    private suspend fun handleList(args: McpToolArgs): McpToolResult {
        val snap = ShortcutEnumerator.enumerate(keyboardShortcutProvider, mcpRegistry)
        if (!snap.hostAvailable) {
            return McpToolResult(
                "Host keyboard shortcut provider unavailable.",
                isError = true,
            )
        }
        val filterCategory = args.string("category")?.trim()?.takeIf { it.isNotEmpty() }
        val filterSource = args.string("source")?.trim()?.takeIf { it.isNotEmpty() }
        val query = args.string("query")?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

        val rows = snap.shortcuts.filter { s ->
            (filterCategory == null || s.category.equals(filterCategory, ignoreCase = true)) &&
                (filterSource == null || s.source.equals(filterSource, ignoreCase = true)) &&
                (query == null ||
                    s.actionName.lowercase().contains(query) ||
                    s.keyChord.lowercase().contains(query) ||
                    s.category.lowercase().contains(query) ||
                    s.description.lowercase().contains(query))
        }

        val array = buildJsonArray {
            for (s in rows) add(shortcutJson(s))
        }
        val obj = buildJsonObject {
            put("total", snap.shortcuts.size)
            put("matched", rows.size)
            put("shortcuts", array)
        }
        return McpToolResult(text = obj.toString())
    }

    private suspend fun handleForKey(args: McpToolArgs): McpToolResult {
        val key = args.string("key_chord")?.trim()
            ?: return McpToolResult(
                "Missing required argument: key_chord",
                isError = true,
            )
        val snap = ShortcutEnumerator.enumerate(keyboardShortcutProvider, mcpRegistry)
        val rows = snap.shortcuts.filter { it.keyChord.trim().equals(key, ignoreCase = true) }
        val array = buildJsonArray { for (s in rows) add(shortcutJson(s)) }
        val obj = buildJsonObject {
            put("key_chord", key)
            put("count", rows.size)
            put("shortcuts", array)
        }
        return McpToolResult(text = obj.toString())
    }

    private suspend fun handleConflicts(): McpToolResult {
        val snap = ShortcutEnumerator.enumerate(keyboardShortcutProvider, mcpRegistry)
        val groups = ConflictDetector.detect(snap.shortcuts)
        val groupsArray = buildJsonArray {
            for (g in groups) {
                add(buildJsonObject {
                    put("key_chord", g.keyChord)
                    put("actions", buildJsonArray {
                        for (a in g.actions) add(shortcutJson(a))
                    })
                })
            }
        }
        val obj = buildJsonObject {
            put("count", groups.size)
            put("groups", groupsArray)
        }
        return McpToolResult(text = obj.toString())
    }

    private suspend fun handleMarkdown(): McpToolResult {
        val snap = ShortcutEnumerator.enumerate(keyboardShortcutProvider, mcpRegistry)
        val groups = ConflictDetector.detect(snap.shortcuts)
        val markdown = buildMarkdown(snap.shortcuts, groups)
        val obj = buildJsonObject {
            put("markdown", markdown)
            put("shortcuts", snap.shortcuts.size)
            put("conflicts", groups.size)
        }
        val cp = clipboardProvider
        if (cp != null) {
            runCatching { cp.setText(markdown) }
        }
        return McpToolResult(text = obj.toString())
    }

    private fun shortcutJson(s: Shortcut): JsonObject = buildJsonObject {
        put("key_chord", s.keyChord)
        put("action", s.actionName)
        put("category", s.category)
        put("source", s.source)
        put("description", s.description)
        if (s.pluginId != null) put("plugin_id", s.pluginId)
    }

    private companion object {
        const val LIST_SCHEMA = """
            {"type":"object","properties":{
              "query":{"type":"string","description":"Case-insensitive substring filter on action, chord, category or description."},
              "category":{"type":"string","description":"Restrict to a single category (e.g. Navigation, Editor)."},
              "source":{"type":"string","description":"Restrict to a single source ('host' or 'plugin:<pluginId>')."}
            }}
        """

        const val FOR_KEY_SCHEMA = """
            {"type":"object","properties":{
              "key_chord":{"type":"string","description":"Chord to look up, e.g. 'Cmd+Shift+P' (case-insensitive)."}
            },"required":["key_chord"]}
        """
    }
}
