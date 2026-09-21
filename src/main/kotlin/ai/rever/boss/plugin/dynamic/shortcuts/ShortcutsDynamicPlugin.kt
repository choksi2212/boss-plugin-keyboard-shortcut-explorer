package ai.rever.boss.plugin.dynamic.shortcuts

import ai.rever.boss.plugin.api.DynamicPlugin
import ai.rever.boss.plugin.api.PluginContext

/**
 * Keyboard Shortcut Explorer dynamic plugin.
 *
 * The plugin exposes every keyboard shortcut available in BOSS - the
 * host's own bindings plus any registered by installed plugins - in one
 * searchable, conflict-aware sidebar panel, and to in-terminal agents
 * through four `shortcuts_*` MCP tools.
 *
 * The panel is registered on the left sidebar (bottom slot, priority 80,
 * so it sorts below the existing helper panels).
 */
class ShortcutsDynamicPlugin : DynamicPlugin {
    override val pluginId: String = "ai.rever.boss.plugin.dynamic.shortcuts"
    override val displayName: String = "Shortcuts"
    override val version: String = manifestVersion()
    override val description: String =
        "Discover every keyboard shortcut available in BOSS - host + plugins, search by action or key, detect conflicts, export a markdown cheatsheet"
    override val author: String = "choksi2212"
    override val url: String = "https://github.com/choksi2212/boss-plugin-keyboard-shortcut-explorer"

    private var mcpProvider: ShortcutsMcpToolProvider? = null

    override fun register(context: PluginContext) {
        val provider = context.keyboardShortcutProvider
        val mcpRegistry = context.mcpToolRegistry
        val clipboard = context.clipboardProvider

        // The sidebar panel for human use.
        context.panelRegistry.registerPanel(ShortcutsInfo) { ctx, panelInfo ->
            ShortcutsComponent(
                ctx = ctx,
                panelInfo = panelInfo,
                provider = provider,
                mcpRegistry = mcpRegistry,
                clipboardProvider = clipboard,
            )
        }

        // The MCP tool surface for in-terminal agents.
        val toolProvider = ShortcutsMcpToolProvider(
            providerId = pluginId,
            keyboardShortcutProvider = provider,
            mcpRegistry = mcpRegistry,
            clipboardProvider = clipboard,
        )
        mcpProvider = toolProvider
        context.registerMcpToolProvider(toolProvider)
    }

    override fun dispose() {
        mcpProvider = null
    }

    /**
     * The version from this plugin's own manifest.
     *
     * Every BOSS plugin ships `/META-INF/boss-plugin/plugin.json` at the
     * same resource path, so a `getResourceAsStream` that returns the
     * first hit could read someone else's manifest if the host ever loads
     * plugins through a parent-first classloader. Only the entry that
     * names this plugin id is accepted.
     */
    private fun manifestVersion(): String =
        runCatching {
            javaClass.classLoader
                ?.getResources("META-INF/boss-plugin/plugin.json")
                ?.asSequence()
                ?.mapNotNull { url -> runCatching { url.readText() }.getOrNull() }
                ?.firstOrNull { text -> field(text, "pluginId") == pluginId }
                ?.let { text -> field(text, "version") }
        }.getOrNull() ?: "unknown"

    private fun field(manifest: String, name: String): String? =
        Regex(""""$name"\s*:\s*"([^"]+)"""").find(manifest)?.groupValues?.get(1)
}
