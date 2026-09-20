package ai.rever.boss.plugin.dynamic.shortcuts

/**
 * The canonical form for a single keyboard shortcut surfaced by the panel
 * and the `shortcuts_*` MCP tools.
 *
 * - [keyChord] is the display form ("Cmd+Shift+P"). It is also the conflict
 *   key, so two shortcuts collide exactly when their chords match
 *   case-insensitively.
 * - [category] is a short label ("Navigation", "Editor", "Panel", "MCP",
 *   "Tab", "Window", "Plugin", or the host's own group names) used by the
 *   category chips in the panel and the [category] MCP filter.
 * - [source] is "host" for an action built into BOSS, or
 *   `"plugin:<pluginId>"` for an action contributed by an installed plugin.
 *   The plugin id is also kept on its own in [pluginId] for filtering and
 *   for the conflict panel.
 */
data class Shortcut(
    val keyChord: String,
    val actionName: String,
    val category: String,
    val source: String,
    val description: String,
    val pluginId: String? = null,
)

/**
 * The set of category chips rendered at the top of the panel.
 *
 * These are the seven filter buttons shown to the user. Categories coming
 * back from the host outside this set are still listed - they fall through
 * into "Other" rather than being dropped - so an unknown group is visible
 * without the user having to add it.
 */
val DEFAULT_CATEGORIES: List<String> = listOf(
    "Navigation",
    "Editor",
    "Panel",
    "MCP",
    "Tab",
    "Window",
    "Plugin",
)

/** The label given to categories outside [DEFAULT_CATEGORIES] in the chips row. */
const val OTHER_CATEGORY: String = "Other"

/**
 * Bound everything. The host registers several hundred shortcuts at
 * startup, plus every installed plugin can contribute a few, and a
 * pathological filter pass should still terminate.
 */
internal const val MAX_SHORTCUTS: Int = 2_000
internal const val MAX_CATEGORIES: Int = 50
internal const val MAX_CHORD_LENGTH: Int = 8
internal const val MAX_PLUGIN_SOURCES: Int = 50
