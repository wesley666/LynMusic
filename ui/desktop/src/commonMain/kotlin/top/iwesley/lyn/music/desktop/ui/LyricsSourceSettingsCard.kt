package top.iwesley.lyn.music.desktop.ui

import top.iwesley.lyn.music.*

import top.iwesley.lyn.music.resources.*
import top.iwesley.lyn.music.core.model.plus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.iwesley.lyn.music.core.model.LyricsSourceConfig
import top.iwesley.lyn.music.ui.mainShellColors

@Composable
internal fun LyricsSourceCard(
    source: top.iwesley.lyn.music.core.model.LyricsSourceDefinition,
    onClick: () -> Unit,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    source.name,
                    modifier = Modifier.weight(1f, fill = false),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                PriorityBadge(priority = source.priority)
            }
            Text(
                if (source.enabled) uiString(Res.string.common_enabled) else uiString(Res.string.common_inactive),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            when (source) {
                is LyricsSourceConfig -> source.urlTemplate
                is top.iwesley.lyn.music.core.model.WorkflowLyricsSourceConfig -> "Workflow JSON · ${source.search.request.url}"
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (source) {
                is LyricsSourceConfig -> {
                    MainShellAssistChip(
                        onClick = {},
                        label = { Text(source.method.name) },
                        leadingIcon = { Icon(Icons.Rounded.CloudSync, null) })
                    MainShellAssistChip(
                        onClick = {},
                        label = { Text(source.responseFormat.name) },
                        leadingIcon = { Icon(Icons.Rounded.GraphicEq, null) })
                }

                is top.iwesley.lyn.music.core.model.WorkflowLyricsSourceConfig -> {
                    MainShellAssistChip(
                        onClick = {},
                        label = { Text(uiString(Res.string.settings_workflow_source)) },
                        leadingIcon = { Icon(Icons.Rounded.CloudSync, null) })
                    MainShellAssistChip(
                        onClick = {},
                        label = { Text(uiString(Res.plurals.workflow_step_count, (source.lyrics.steps.size).toInt(), source.lyrics.steps.size)) },
                        leadingIcon = { Icon(Icons.Rounded.GraphicEq, null) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onToggleEnabled) {
                Text(if (source.enabled) uiString(Res.string.common_deactivate) else uiString(Res.string.common_enable))
            }
            TextButton(onClick = onDelete) {
                Text(uiString(Res.string.common_delete))
            }
        }
    }
}

@Composable
internal fun PriorityBadge(
    priority: Int,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        colors = CardDefaults.cardColors(
            containerColor = shellColors.selectedContainer,
        ),
        border = BorderStroke(1.dp, shellColors.selectedBorder),
    ) {
        Text(
            text = "P$priority",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
