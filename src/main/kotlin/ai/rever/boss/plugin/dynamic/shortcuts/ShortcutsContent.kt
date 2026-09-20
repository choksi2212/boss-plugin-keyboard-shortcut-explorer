package ai.rever.boss.plugin.dynamic.shortcuts

import ai.rever.boss.plugin.ui.BossTheme
import ai.rever.boss.plugin.ui.BossThemeColors
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * The composable surface for the Shortcuts panel.
 *
 * Top-to-bottom:
 *
 * - Header (title + refresh + count).
 * - Search bar.
 * - Category chips (Navigation, Editor, Panel, MCP, Tab, Window, Plugin, Other).
 * - Source chips (host, plugin:*, etc.).
 * - Press to find / Press to record row (disabled with banner today).
 * - Conflict section (collapsible).
 * - Action list.
 * - Export markdown / Copy rebinds footer.
 *
 * The list is hoisted out of the LazyColumn so it sees plain values: a
 * LazyListScope is not a @Composable context, and reading
 * `viewModel.filtered()` inside one would run it for every recompose.
 */
@Composable
fun ShortcutsContent(component: ShortcutsComponent) {
    BossTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                HeaderRow(component)

                Divider(color = BossThemeColors.TextPrimary.copy(alpha = 0.1f))

                val status by component.viewModel.status.collectAsState()
                val error by component.viewModel.error.collectAsState()
                Toast(status = status, error = error, onDismiss = { component.clearMessages() })

                val snap by component.viewModel.snapshot.collectAsState()
                if (!snap.hostAvailable) {
                    HostUnavailableBanner()
                }
                if (snap.pluginToolsScanned > 0) {
                    PluginToolsBanner(snap.pluginToolsScanned)
                }

                SearchBar(component)
                CategoryChips(component)
                SourceChips(component)
                ListenRow(component)

                ConflictSection(component)

                val items = component.viewModel.filtered()
                if (items.isEmpty()) {
                    EmptyState(query = component.viewModel.search.collectAsState().value)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(items.size) { idx ->
                            ShortcutRow(items[idx])
                        }
                    }
                }

                FooterRow(component)
            }
        }
    }
}

@Composable
private fun HeaderRow(component: ShortcutsComponent) {
    val snap by component.viewModel.snapshot.collectAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colors.surface)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Shortcuts",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colors.onSurface,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${snap.shortcuts.size}",
            fontSize = 11.sp,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
        )
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = { component.resetFilters() }) {
            Text(text = "Reset filters", fontSize = 10.sp)
        }
        IconButton(onClick = { component.refresh() }, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun Toast(status: String?, error: String?, onDismiss: () -> Unit) {
    if (status == null && error == null) return
    LaunchedEffect(status, error) {
        delay(4000)
        onDismiss()
    }
    val message = error ?: status ?: return
    val isError = error != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isError) BossThemeColors.ErrorColor else BossThemeColors.SuccessColor,
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isError) Icons.Default.Error else Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = BossThemeColors.TextPrimary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = message,
            fontSize = 11.sp,
            color = BossThemeColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(20.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss",
                modifier = Modifier.size(12.dp),
                tint = BossThemeColors.TextPrimary.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun HostUnavailableBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .background(
                BossThemeColors.ErrorColor.copy(alpha = 0.18f),
                shape = RoundedCornerShape(4.dp),
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colors.onSurface,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Host keyboard shortcut provider unavailable - showing zero shortcuts.",
            fontSize = 11.sp,
            color = MaterialTheme.colors.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PluginToolsBanner(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(
                MaterialTheme.colors.surface,
                shape = RoundedCornerShape(4.dp),
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$count plugin-contributed shortcut tool(s) detected.",
            fontSize = 10.sp,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun SearchBar(component: ShortcutsComponent) {
    val search by component.viewModel.search.collectAsState()
    OutlinedTextField(
        value = search,
        onValueChange = { component.setSearch(it) },
        placeholder = {
            Text(
                text = "Search action, key, or category",
                fontSize = 11.sp,
            )
        },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = TextFieldDefaults.outlinedTextFieldColors(
            textColor = MaterialTheme.colors.onSurface,
            cursorColor = BossThemeColors.AccentColor,
        ),
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
        },
    )
}

@Composable
private fun CategoryChips(component: ShortcutsComponent) {
    val enabled by component.viewModel.enabledCategories.collectAsState()
    val categories = component.viewModel.allCategories
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(categories.size) { idx ->
            Chip(
                label = categories[idx],
                selected = enabled.contains(categories[idx]),
                onToggle = { component.toggleCategory(categories[idx]) },
            )
        }
    }
}

@Composable
private fun SourceChips(component: ShortcutsComponent) {
    val enabled by component.viewModel.enabledSources.collectAsState()
    val sources = component.viewModel.allSources
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(sources.size) { idx ->
            Chip(
                label = sources[idx],
                selected = enabled.contains(sources[idx]),
                onToggle = { component.toggleSource(sources[idx]) },
            )
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onToggle: () -> Unit) {
    val background = if (selected) BossThemeColors.AccentColor.copy(alpha = 0.85f)
    else MaterialTheme.colors.surface
    val foreground = if (selected) BossThemeColors.TextPrimary
    else MaterialTheme.colors.onSurface.copy(alpha = 0.85f)
    Box(
        modifier = Modifier
            .background(background, shape = RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = BossThemeColors.TextPrimary.copy(alpha = if (selected) 0.0f else 0.15f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable { onToggle() }
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ListenRow(component: ShortcutsComponent) {
    val listening by component.listening.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = { component.startListenForKey(record = false) },
                enabled = PressListener.available && !listening,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = BossThemeColors.AccentColor,
                    contentColor = BossThemeColors.TextPrimary,
                    disabledBackgroundColor = MaterialTheme.colors.surface,
                    disabledContentColor = MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
                ),
            ) {
                Text(
                    text = if (listening) "Listening..." else "Press to find",
                    fontSize = 11.sp,
                )
            }
            Button(
                onClick = { component.startListenForKey(record = true) },
                enabled = PressListener.available && !listening,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = BossThemeColors.AccentColor,
                    contentColor = BossThemeColors.TextPrimary,
                    disabledBackgroundColor = MaterialTheme.colors.surface,
                    disabledContentColor = MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
                ),
            ) {
                Text(
                    text = if (listening) "Listening..." else "Press to record",
                    fontSize = 11.sp,
                )
            }
        }
        if (!PressListener.available) {
            Text(
                text = PressListener.unavailableReason,
                fontSize = 10.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        val rebinds by component.viewModel.candidateRebinds.collectAsState()
        if (rebinds.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Recorded (${rebinds.size}): " + rebinds.joinToString(", "),
                fontSize = 10.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ConflictSection(component: ShortcutsComponent) {
    val showConflicts by component.viewModel.showConflicts.collectAsState()
    val conflicts = component.viewModel.conflicts()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { component.setShowConflicts(!showConflicts) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (showConflicts) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Conflicts (${conflicts.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colors.onSurface,
            )
        }
        if (showConflicts) {
            if (conflicts.isEmpty()) {
                Text(
                    text = "No conflicts detected.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            BossThemeColors.ErrorColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    conflicts.take(MAX_VISIBLE_CONFLICTS).forEach { group ->
                        ConflictRow(group)
                    }
                    if (conflicts.size > MAX_VISIBLE_CONFLICTS) {
                        Text(
                            text = "... +${conflicts.size - MAX_VISIBLE_CONFLICTS} more",
                            fontSize = 10.sp,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConflictRow(group: ConflictDetector.ConflictGroup) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = group.keyChord,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colors.onSurface,
        )
        group.actions.forEach { s ->
            Text(
                text = "- ${s.actionName} (${s.source})",
                fontSize = 10.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ShortcutRow(item: Shortcut) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.surface, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.keyChord,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colors.onSurface,
            modifier = Modifier.width(110.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.actionName,
                fontSize = 11.sp,
                color = MaterialTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.category + " - " + item.source,
                fontSize = 10.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.description.isNotBlank() && item.description != item.actionName) {
                Text(
                    text = item.description,
                    fontSize = 10.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(query: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (query.isBlank()) {
                    "No shortcuts match the current filters."
                } else {
                    "No shortcuts match \"$query\"."
                },
                fontSize = 12.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun FooterRow(component: ShortcutsComponent) {
    val rebinds by component.viewModel.candidateRebinds.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { component.copyMarkdown() }) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Copy markdown", fontSize = 11.sp)
            }
            TextButton(
                onClick = { component.copyRebinds() },
                enabled = rebinds.isNotEmpty(),
            ) {
                Text(text = "Copy rebinds (${rebinds.size})", fontSize = 11.sp)
            }
            if (rebinds.isNotEmpty()) {
                TextButton(onClick = { component.clearRebinds() }) {
                    Text(text = "Clear", fontSize = 11.sp)
                }
            }
        }
    }
}

private const val MAX_VISIBLE_CONFLICTS: Int = 8
