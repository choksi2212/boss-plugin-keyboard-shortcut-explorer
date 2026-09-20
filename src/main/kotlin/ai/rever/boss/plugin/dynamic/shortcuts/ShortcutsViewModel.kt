package ai.rever.boss.plugin.dynamic.shortcuts

import ai.rever.boss.plugin.api.KeyboardShortcutProvider
import ai.rever.boss.plugin.api.McpToolRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The state the panel reads.
 *
 * The view model owns the in-memory snapshot, the filter selections, the
 * "candidate rebind" list a user builds up with "Press to record", and
 * the conflicts derived from the snapshot. It does NOT own any Compose
 * state - that lives in the composable - so reloading the panel does not
 * lose the user's selection.
 *
 * The candidate rebind list is process-local: the host does not expose a
 * rebind API, so a "remap" intent is captured as a snippet the user
 * copies into their `plugin.json` (`dependencies` block, or a settings
 * file the host reads). The list is offered through the clipboard and
 * survives only for the running session.
 */
class ShortcutsViewModel(
    private val provider: KeyboardShortcutProvider?,
    private val mcpRegistry: McpToolRegistry?,
) {

    private val _snapshot = MutableStateFlow(
        ShortcutEnumerator.enumerate(provider, mcpRegistry),
    )
    val snapshot: StateFlow<ShortcutEnumerator.Snapshot> = _snapshot.asStateFlow()

    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search.asStateFlow()

    private val _enabledCategories = MutableStateFlow<Set<String>>(DEFAULT_CATEGORIES.toSet())
    val enabledCategories: StateFlow<Set<String>> = _enabledCategories.asStateFlow()

    private val _enabledSources = MutableStateFlow<Set<String>>(setOf("host"))
    val enabledSources: StateFlow<Set<String>> = _enabledSources.asStateFlow()

    private val _showConflicts = MutableStateFlow(true)
    val showConflicts: StateFlow<Boolean> = _showConflicts.asStateFlow()

    private val _candidateRebinds = MutableStateFlow<List<String>>(emptyList())
    val candidateRebinds: StateFlow<List<String>> = _candidateRebinds.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * The full source set the snapshot exposes. `host` is always present;
     * one `plugin:<id>` entry is added for every plugin the snapshot saw.
     */
    val allSources: List<String>
        get() {
            val hosts = mutableSetOf("host")
            for (s in _snapshot.value.shortcuts) hosts.add(s.source)
            return hosts.sorted().take(MAX_PLUGIN_SOURCES)
        }

    /** The distinct categories the snapshot exposes, with [OTHER_CATEGORY] appended last. */
    val allCategories: List<String>
        get() {
            val seen = LinkedHashSet<String>()
            for (s in _snapshot.value.shortcuts) {
                if (DEFAULT_CATEGORIES.contains(s.category)) {
                    seen.add(s.category)
                } else {
                    seen.add(OTHER_CATEGORY)
                }
            }
            // Walk DEFAULT_CATEGORIES in the canonical order; the rest pile up.
            val ordered = DEFAULT_CATEGORIES.filter { seen.contains(it) }
            return ordered.take(MAX_CATEGORIES) +
                if (seen.contains(OTHER_CATEGORY)) listOf(OTHER_CATEGORY) else emptyList()
        }

    /** Re-run the enumerator. Cheap; the host provider is in-memory. */
    fun refresh() {
        _snapshot.value = ShortcutEnumerator.enumerate(provider, mcpRegistry)
        _status.value = "Refreshed - ${_snapshot.value.shortcuts.size} shortcuts"
        _error.value = null
    }

    fun setSearch(text: String) {
        _search.value = text.take(256)
    }

    fun toggleCategory(name: String) {
        val next = _enabledCategories.value.toMutableSet().also {
            if (!it.add(name)) it.remove(name)
        }
        _enabledCategories.value = if (next.isEmpty()) {
            // An empty filter is a "show everything" reset; otherwise the
            // user can land in a state where the panel renders nothing
            // after toggling the last chip.
            DEFAULT_CATEGORIES.toSet()
        } else {
            next
        }
    }

    fun toggleSource(name: String) {
        val next = _enabledSources.value.toMutableSet().also {
            if (!it.add(name)) it.remove(name)
        }
        _enabledSources.value = if (next.isEmpty()) {
            setOf("host")
        } else {
            next
        }
    }

    fun setShowConflicts(value: Boolean) {
        _showConflicts.value = value
    }

    fun clearMessages() {
        _status.value = null
        _error.value = null
    }

    fun setStatus(text: String) {
        _status.value = text.take(256)
    }

    fun setError(text: String) {
        _error.value = text.take(256)
    }

    /** Filtered list for the action panel. */
    fun filtered(): List<Shortcut> {
        val needle = _search.value.trim().lowercase()
        val snap = _snapshot.value.shortcuts
        val cats = _enabledCategories.value
        val sources = _enabledSources.value
        val filtered = snap.filter { s ->
            (cats.isEmpty() || cats.contains(s.category) ||
                (!DEFAULT_CATEGORIES.contains(s.category) && cats.contains(OTHER_CATEGORY))) &&
                (sources.isEmpty() || sources.contains(s.source)) &&
                (needle.isEmpty() ||
                    s.actionName.lowercase().contains(needle) ||
                    s.keyChord.lowercase().contains(needle) ||
                    s.category.lowercase().contains(needle) ||
                    s.description.lowercase().contains(needle))
        }
        return filtered.sortedWith(
            compareBy(
                { it.category.lowercase() },
                { it.actionName.lowercase() },
            ),
        )
    }

    /** Conflict groups for the collapsible list. */
    fun conflicts(): List<ConflictDetector.ConflictGroup> =
        ConflictDetector.detect(_snapshot.value.shortcuts)

    /** Items matching a single chord. */
    fun forKey(chord: String): List<Shortcut> {
        val needle = chord.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        return _snapshot.value.shortcuts.filter { it.keyChord.trim().lowercase() == needle }
    }

    /** Markdown cheatsheet for the export. */
    fun markdown(): String = buildMarkdown(
        shortcuts = _snapshot.value.shortcuts,
        conflicts = conflicts(),
    )

    /** Add a candidate rebind the user typed. */
    fun recordRebind(chord: String) {
        val trimmed = chord.trim().take(64)
        if (trimmed.isEmpty()) return
        val current = _candidateRebinds.value
        if (current.contains(trimmed)) return
        if (current.size >= MAX_CHORD_LENGTH * 4) {
            _error.value = "Too many recorded chords - clear some first"
            return
        }
        _candidateRebinds.value = current + trimmed
        _status.value = "Recorded $trimmed"
    }

    fun clearRebinds() {
        _candidateRebinds.value = emptyList()
        _status.value = "Cleared rebind candidates"
    }

    /** Render a markdown cheatsheet suitable for pasting into the clipboard. */
    fun cheatsheet(): String = markdown()

    /**
     * Reset all filters to the canonical defaults. The candidate rebind
     * list survives - the user can clear it explicitly.
     */
    fun resetFilters() {
        _search.value = ""
        _enabledCategories.value = DEFAULT_CATEGORIES.toSet()
        _enabledSources.value = setOf("host")
    }
}

internal fun buildMarkdown(
    shortcuts: List<Shortcut>,
    conflicts: List<ConflictDetector.ConflictGroup>,
): String {
    val sb = StringBuilder()
    sb.append("# BOSS Keyboard Shortcuts").append('\n')
    sb.append("Total: ").append(shortcuts.size)
        .append(" - Conflicts: ").append(conflicts.size)
        .append('\n').append('\n')
    if (shortcuts.isEmpty()) {
        sb.append("No shortcuts are currently enumerable.").append('\n')
        return sb.toString()
    }
    val byCategory = shortcuts
        .groupBy { it.category.ifBlank { OTHER_CATEGORY } }
        .toSortedMap()
    for ((category, items) in byCategory) {
        sb.append("## ").append(category).append('\n')
        items
            .sortedBy { it.actionName.lowercase() }
            .forEach { s ->
                sb.append("- `").append(s.keyChord).append("` - ")
                    .append(s.actionName)
                if (s.description.isNotBlank() && s.description != s.actionName) {
                    sb.append(" - ").append(s.description)
                }
                sb.append("  _(source: ").append(s.source).append(")_")
                    .append('\n')
            }
        sb.append('\n')
    }
    if (conflicts.isNotEmpty()) {
        sb.append("## Conflicts").append('\n')
        for (c in conflicts) {
            sb.append("- `").append(c.keyChord).append("`: ")
                .append(c.actions.joinToString(" vs ") { it.actionName })
                .append('\n')
        }
    }
    return sb.toString()
}
