package ai.rever.boss.plugin.dynamic.shortcuts

/**
 * Pure grouping over a list of [Shortcut]s.
 *
 * A "conflict" is two or more actions bound to the same key chord. The
 * chord comparison is case-insensitive and whitespace-trimmed so a
 * "Cmd+Shift+P" from one source and a "cmd+shift+p" from another match.
 *
 * The result is bounded: an empty list returns no conflicts; a list of
 * one matches a single chord with no conflict. Every chord seen is
 * included exactly once.
 */
object ConflictDetector {

    /**
     * The conflict panel's row: one chord shared by two or more actions.
     */
    data class ConflictGroup(
        val keyChord: String,
        val actions: List<Shortcut>,
    )

    /**
     * Group [shortcuts] by their chord and return only groups with more
     * than one action. Chords with no collision are dropped - they are
     * already in the main list, and listing them again would double the
     * panel's row count for nothing.
     */
    fun detect(shortcuts: List<Shortcut>): List<ConflictGroup> {
        if (shortcuts.isEmpty()) return emptyList()
        val grouped = HashMap<String, MutableList<Shortcut>>()
        for (s in shortcuts) {
            val key = s.keyChord.trim().lowercase()
            if (key.isEmpty()) continue
            grouped.getOrPut(key) { mutableListOf() }.add(s)
        }
        return grouped
            .filterValues { it.size > 1 }
            .map { (key, items) ->
                val canonical = items.first().keyChord
                ConflictGroup(
                    keyChord = canonical,
                    actions = items.sortedBy { it.actionName.lowercase() },
                )
            }
            .sortedBy { it.keyChord.lowercase() }
    }
}
