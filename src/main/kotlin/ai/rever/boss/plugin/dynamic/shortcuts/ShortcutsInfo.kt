package ai.rever.boss.plugin.dynamic.shortcuts

import ai.rever.boss.plugin.api.Panel
import ai.rever.boss.plugin.api.Panel.Companion.bottom
import ai.rever.boss.plugin.api.Panel.Companion.left
import ai.rever.boss.plugin.api.PanelId
import ai.rever.boss.plugin.api.PanelInfo
import compose.icons.FeatherIcons
import compose.icons.feathericons.Command

/**
 * Describes the Shortcuts panel: id, sidebar icon, default slot.
 *
 * Lives in the left bottom slot at priority 80 - below the existing
 * helper panels (Plugin X-Ray is 71, Plugin Deps is 72, Page Memory is
 * 68) so it sorts to the bottom of that column on the sidebar.
 */
object ShortcutsInfo : PanelInfo {
    override val id = PanelId("shortcuts-explorer", 80)
    override val displayName = "Shortcuts"
    override val icon = FeatherIcons.Command
    override val defaultSlotPosition: Panel = left.bottom
}
