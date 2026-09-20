package ai.rever.boss.plugin.dynamic.shortcuts

import ai.rever.boss.plugin.api.ClipboardProvider
import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Decompose panel component for the Keyboard Shortcut Explorer.
 *
 * The component is a thin wrapper over [ShortcutsViewModel] that exposes
 * the state flows a Compose composable needs, and three user actions:
 *
 * - [startListenForKey] - one shot "Press to find" or "Press to record".
 *   Calls [PressListener.awaitNextPress] which always returns null today
 *   because the host input bridge is not exposed; the listener object's
 *   `available` flag gates the buttons in the composable.
 * - [copyMarkdown] - copies a markdown cheatsheet onto the clipboard
 *   through [ClipboardProvider].
 * - [copyRebinds] - copies the recorded chord list as a JSON-ish snippet.
 *
 * The view model does the filtering and the conflict detection - the
 * component itself holds no derived state.
 */
class ShortcutsComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo,
    private val provider: ai.rever.boss.plugin.api.KeyboardShortcutProvider?,
    private val mcpRegistry: ai.rever.boss.plugin.api.McpToolRegistry?,
    private val clipboardProvider: ClipboardProvider?,
) : PanelComponentWithUI, ComponentContext by ctx {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val viewModel: ShortcutsViewModel = ShortcutsViewModel(provider, mcpRegistry)

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    fun setSearch(text: String) = viewModel.setSearch(text)
    fun toggleCategory(name: String) = viewModel.toggleCategory(name)
    fun toggleSource(name: String) = viewModel.toggleSource(name)
    fun setShowConflicts(value: Boolean) = viewModel.setShowConflicts(value)
    fun clearMessages() = viewModel.clearMessages()
    fun resetFilters() = viewModel.resetFilters()
    fun refresh() = viewModel.refresh()
    fun clearRebinds() = viewModel.clearRebinds()

    fun recordRebind(chord: String) = viewModel.recordRebind(chord)

    /**
     * One shot "Press to find" / "Press to record".
     *
     * Sets [listening] to true while the listener is suspended; flips it
     * back when the result lands or the listener times out. The host
     * bridge is not available today, so this resolves to null and the
     * composable stays in "press" mode for at most a heartbeat before
     * rendering the "host input listener not available" banner.
     */
    fun startListenForKey(record: Boolean) {
        if (!PressListener.available) {
            viewModel.setError(PressListener.unavailableReason)
            return
        }
        _listening.value = true
        scope.launch {
            try {
                val chord = PressListener.awaitNextPress()
                if (chord != null) {
                    if (record) {
                        viewModel.recordRebind(chord)
                    } else {
                        viewModel.setSearch(chord)
                        viewModel.setStatus("Filter set to $chord")
                    }
                } else {
                    viewModel.setError(PressListener.unavailableReason)
                }
            } finally {
                _listening.value = false
            }
        }
    }

    fun copyMarkdown() {
        val text = viewModel.cheatsheet()
        if (text.isBlank()) {
            viewModel.setError("Nothing to copy")
            return
        }
        val cp = clipboardProvider
        if (cp == null) {
            viewModel.setError("Clipboard provider unavailable")
            return
        }
        scope.launch {
            if (cp.setText(text)) {
                viewModel.setStatus("Markdown cheatsheet copied")
            } else {
                viewModel.setError("Clipboard write failed")
            }
        }
    }

    fun copyRebinds() {
        val list = viewModel.candidateRebinds.value
        if (list.isEmpty()) {
            viewModel.setError("No recorded chords to copy")
            return
        }
        val cp = clipboardProvider
        if (cp == null) {
            viewModel.setError("Clipboard provider unavailable")
            return
        }
        val snippet = list.joinToString("\n") { "\"$it\"" }
        scope.launch {
            if (cp.setText(snippet)) {
                viewModel.setStatus("Rebind candidates copied")
            } else {
                viewModel.setError("Clipboard write failed")
            }
        }
    }

    @Composable
    override fun Content() {
        ShortcutsContent(component = this)
    }
}
