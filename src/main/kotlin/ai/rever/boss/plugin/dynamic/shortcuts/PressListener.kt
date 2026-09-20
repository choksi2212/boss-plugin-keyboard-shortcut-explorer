package ai.rever.boss.plugin.dynamic.shortcuts

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Bridge for "Press to find" and "Press to record" gestures.
 *
 * Plugins cannot access `java.awt.KeyboardFocusManager` directly: the host
 * sandbox strips AWT access and the plugin classloader runs in a way that
 * does not see focus state. The plugin API does not currently expose a
 * global key listener either - the closest surface is the
 * [ai.rever.boss.plugin.api.KeyboardShortcutProvider] read API, which is
 * for queries, not for input.
 *
 * This class is therefore an explicit "no" surface. The panel reads
 * [available] to decide whether to render the "Press to find" /
 * "Press to record" buttons enabled or greyed-out with a banner, and
 * calls [awaitNextPress] which suspends briefly and returns null so
 * callers do not have to null-check the API. A future host release that
 * exposes `registerKeyListener(...)` would flip [available] to true and
 * [awaitNextPress] to forward the captured chord.
 *
 * The await is bounded: callers never wait forever, and the suspend
 * function cooperates with the host's tool timeouts.
 */
internal object PressListener {

    /**
     * `false` for every build today. The panel renders the corresponding
     * buttons disabled with the banner from [unavailableReason].
     */
    const val available: Boolean = false

    /**
     * One-line reason shown under the disabled buttons.
     */
    const val unavailableReason: String =
        "Host input listener not available - update the host or use the search bar to find a binding."

    /**
     * Suspend until the user presses a key chord, then return it.
     *
     * Returns null immediately when [available] is false. The host's
     * eventual input bridge would replace this with a real implementation
     * that returns the chord string the user pressed (e.g. "Cmd+Shift+P").
     *
     * The suspend signature means a caller can be cancelled by a parent
     * scope, and the bounded [timeoutMs] ensures a hung host cannot keep
     * a button stuck in a "listening" state.
     */
    suspend fun awaitNextPress(timeoutMs: Long = 30_000): String? {
        if (!available) return null
        return try {
            withTimeoutOrNull(timeoutMs) {
                // Reserved for a future host input bridge.
                null
            }
        } catch (_: TimeoutCancellationException) {
            null
        }
    }
}
