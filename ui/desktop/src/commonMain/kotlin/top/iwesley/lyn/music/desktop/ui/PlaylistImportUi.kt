package top.iwesley.lyn.music.desktop.ui

import top.iwesley.lyn.music.*

import top.iwesley.lyn.music.resources.*
import top.iwesley.lyn.music.core.model.uiPlural
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.UiText

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.iwesley.lyn.music.data.repository.PlaylistImportReport
import top.iwesley.lyn.music.data.repository.PlaylistImportFailedLineIssue
import top.iwesley.lyn.music.data.repository.messageUiText
import top.iwesley.lyn.music.ui.mainShellColors

@Composable
fun PlaylistTextImportDialog(
    playlistName: String,
    isOnlineMode: Boolean = false,
    isImporting: Boolean,
    report: PlaylistImportReport?,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    val shellColors = mainShellColors
    val appDensity = LocalDensity.current
    val uriHandler = LocalUriHandler.current
    var text by rememberSaveable(playlistName) { mutableStateOf("") }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = shellColors.cardBorder,
        unfocusedBorderColor = shellColors.cardBorder,
        disabledBorderColor = shellColors.cardBorder,
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        CompositionLocalProvider(LocalDensity provides appDensity) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .navigationBarsPadding(),
            ) {
                val dialogLayout = playlistImportDialogLayout(maxWidth = maxWidth, maxHeight = maxHeight)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            horizontal = dialogLayout.outerHorizontalPadding,
                            vertical = dialogLayout.outerVerticalPadding,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.94f)
                            .widthIn(max = 460.dp)
                            .heightIn(max = dialogLayout.maxHeight),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = shellColors.navContainer),
                        border = BorderStroke(1.dp, shellColors.cardBorder),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = dialogLayout.maxHeight)
                                .padding(
                                    horizontal = dialogLayout.contentHorizontalPadding,
                                    vertical = dialogLayout.contentVerticalPadding,
                                ),
                            verticalArrangement = Arrangement.spacedBy(dialogLayout.verticalSpacing),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = uiString(Res.string.playlist_import_title),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = if (isOnlineMode) {
                                        uiString(Res.string.playlist_import_remote_hint, playlistName)
                                    } else {
                                        uiString(Res.string.playlist_import_local_hint, playlistName)
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                ImeAwareOutlinedTextField(
                                    value = text,
                                    onValueChange = { text = it },
                                    label = { Text(uiString(Res.string.playlist_import_text_label)) },
                                    placeholder = { Text(uiString(Res.string.playlist_import_text_example)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
                                    minLines = dialogLayout.textFieldLines,
                                    maxLines = dialogLayout.textFieldLines,
                                    colors = fieldColors,
                                )
                                report?.let { PlaylistImportReportContent(it) }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = onDismiss) {
                                    Text(uiString(Res.string.common_close), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.width(8.dp))
                                TextButton(
                                    onClick = { uriHandler.openUri(PlaylistImportAssistantUrl) },
                                ) {
                                    Text(uiString(Res.string.playlist_import_assistant_title), color = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(Modifier.width(8.dp))
                                TextButton(
                                    onClick = { onImport(text) },
                                    enabled = canConfirmPlaylistImport(text, isImporting),
                                ) {
                                    Text(
                                        text = if (isImporting) uiString(Res.string.playlist_importing_status) else uiString(Res.string.playlist_import_action),
                                        color = if (canConfirmPlaylistImport(text, isImporting)) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistImportReportContent(report: PlaylistImportReport) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(mainShellColors.cardContainer.copy(alpha = 0.65f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = playlistImportReportSummary(report),
            fontWeight = FontWeight.SemiBold,
        )
        if (report.hasIssues) {
            PlaylistImportIssueGroup(uiString(Res.string.playlist_import_invalid_format_status), report.malformedLines.map { formatPlaylistImportLineIssue(it) })
            PlaylistImportIssueGroup(uiString(Res.string.playlist_import_no_match_status), report.notMatchedLines.map { formatPlaylistImportLineIssue(it) })
            PlaylistImportIssueGroup(
                title = uiString(Res.string.playlist_import_multiple_matches_status),
                lines = report.ambiguousLines.map { issue ->
                    uiString(Res.string.playlist_import_line_with_match_count, issue.lineNumber, issue.rawText, issue.matchCount)
                },
            )
            PlaylistImportIssueGroup(
                title = uiString(Res.string.playlist_import_failed_status),
                lines = report.failedLines.map { formatPlaylistImportFailedLineIssue(it) },
            )
            if (report.duplicateInputCount > 0) {
                Text(
                    text = uiString(Res.plurals.playlist_import_input_duplicate_count, (report.duplicateInputCount).toInt(), report.duplicateInputCount),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun PlaylistImportIssueGroup(
    title: String,
    lines: List<String>,
) {
    if (lines.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodySmall,
        )
        lines.forEach { line ->
            Text(
                text = line,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

internal fun canConfirmPlaylistImport(
    text: String,
    isImporting: Boolean,
): Boolean = text.trim().isNotEmpty() && !isImporting

internal const val PlaylistImportAssistantUrl = "https://music.unmeta.cn/"

internal data class PlaylistImportDialogLayout(
    val outerHorizontalPadding: Dp,
    val outerVerticalPadding: Dp,
    val contentHorizontalPadding: Dp,
    val contentVerticalPadding: Dp,
    val verticalSpacing: Dp,
    val maxHeight: Dp,
    val textFieldLines: Int,
)

internal fun playlistImportDialogLayout(
    maxWidth: Dp,
    maxHeight: Dp,
): PlaylistImportDialogLayout {
    val outerHorizontalPadding = when {
        maxWidth < 360.dp -> 12.dp
        maxWidth < 430.dp -> 16.dp
        else -> 20.dp
    }
    val outerVerticalPadding = when {
        maxHeight < 460.dp -> 8.dp
        maxHeight < 620.dp -> 16.dp
        else -> 24.dp
    }
    val contentHorizontalPadding = when {
        maxWidth < 360.dp || maxHeight < 460.dp -> 16.dp
        maxHeight < 620.dp -> 20.dp
        else -> 24.dp
    }
    val contentVerticalPadding = when {
        maxHeight < 460.dp -> 14.dp
        maxHeight < 620.dp -> 18.dp
        else -> 22.dp
    }
    val availableHeight = maxDp(280.dp, maxHeight - outerVerticalPadding - outerVerticalPadding)
    val scaledHeightCap = maxDp(320.dp, maxHeight * 0.78f)
    val dialogMaxHeight = minDp(playlistImportDialogMaxHeight(), minDp(availableHeight, scaledHeightCap))
    return PlaylistImportDialogLayout(
        outerHorizontalPadding = outerHorizontalPadding,
        outerVerticalPadding = outerVerticalPadding,
        contentHorizontalPadding = contentHorizontalPadding,
        contentVerticalPadding = contentVerticalPadding,
        verticalSpacing = if (dialogMaxHeight < 420.dp) 10.dp else 14.dp,
        maxHeight = dialogMaxHeight,
        textFieldLines = playlistImportTextFieldLines(dialogMaxHeight),
    )
}

internal fun playlistImportTextFieldLines(dialogHeight: Dp): Int {
    return when {
        dialogHeight < 360.dp -> 2
        dialogHeight < 440.dp -> 3
        dialogHeight < 520.dp -> 4
        else -> 6
    }
}

internal fun playlistImportDialogMaxHeight(): Dp = 560.dp

private fun minDp(first: Dp, second: Dp): Dp = if (first < second) first else second

private fun maxDp(first: Dp, second: Dp): Dp = if (first > second) first else second

internal fun playlistImportReportSummaryText(report: PlaylistImportReport): UiText {
    val parts = mutableListOf(uiPlural(Res.plurals.playlist_import_added_track_count, (report.addedCount).toInt(), report.addedCount))
    if (report.alreadyExistsCount > 0) {
        parts += uiPlural(Res.plurals.playlist_import_existing_track_count, (report.alreadyExistsCount).toInt(), report.alreadyExistsCount)
    }
    if (report.duplicateInputCount > 0) {
        parts += uiPlural(Res.plurals.playlist_import_duplicate_track_count, (report.duplicateInputCount).toInt(), report.duplicateInputCount)
    }
    val issueCount = report.malformedLines.size +
        report.notMatchedLines.size +
        report.ambiguousLines.size +
        report.failedLines.size
    if (issueCount > 0) {
        parts += uiPlural(Res.plurals.playlist_import_failed_line_count, (issueCount).toInt(), issueCount)
    }
    return UiText.Joined(parts, separatorText = uiText(Res.string.ui_list_separator))
}

@Composable
internal fun playlistImportReportSummary(report: PlaylistImportReport): String = playlistImportReportSummaryText(report).displayText()

internal fun formatPlaylistImportFailedLineIssueText(issue: PlaylistImportFailedLineIssue): UiText =
    uiText(Res.string.playlist_import_line_with_reason, issue.lineNumber, issue.rawText, issue.messageUiText())

@Composable
internal fun formatPlaylistImportFailedLineIssue(issue: PlaylistImportFailedLineIssue): String = formatPlaylistImportFailedLineIssueText(issue).displayText()

@Composable
private fun formatPlaylistImportLineIssue(
    issue: top.iwesley.lyn.music.data.repository.PlaylistImportLineIssue,
): String {
    return uiString(Res.string.playlist_import_line_label, issue.lineNumber, issue.rawText)
}
