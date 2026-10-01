package top.iwesley.lyn.music

import top.iwesley.lyn.music.resources.*
import org.jetbrains.compose.resources.StringResource

import top.iwesley.lyn.music.core.model.sourceNameUiText
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.uiPlural
import top.iwesley.lyn.music.core.model.LyricsSearchApplyMode
import top.iwesley.lyn.music.core.model.LyricsSearchCandidate
import top.iwesley.lyn.music.core.model.WorkflowSongCandidate
import top.iwesley.lyn.music.core.model.normalizeArtworkLocator
import top.iwesley.lyn.music.domain.parseEnhancedLyricsPresentation
import top.iwesley.lyn.music.platform.PlatformBackHandler
import top.iwesley.lyn.music.ui.mainShellColors

internal data class LyricsSearchDialogState(
    val headerTitle: String,
    val headerSubtitle: String,
    val title: String,
    val artistName: String,
    val albumTitle: String,
    val isLoading: Boolean,
    val hasResult: Boolean,
    val directResults: List<LyricsSearchCandidate>,
    val workflowResults: List<WorkflowSongCandidate>,
    val error: String? = null,
)

internal data class LyricsSearchDialogStrings(
    val formSubtitle: UiText,
    val resultsAppliedSubtitle: UiText,
    val idleBody: UiText = uiText(Res.string.lyrics_search_ready_hint),
    val emptyBody: UiText = uiText(Res.string.lyrics_search_sources_empty_hint),
    val resultsPlaceholderSubtitle: UiText = uiText(Res.string.lyrics_search_results_placeholder),
    val backLabel: UiText = uiText(Res.string.common_back),
    val cancelLabel: UiText = uiText(Res.string.common_cancel),
    val dismissLabel: UiText = uiText(Res.string.common_close),
    val searchIdleLabel: UiText = uiText(Res.string.common_search),
    val searchLoadingLabel: UiText = uiText(Res.string.common_searching),
)

@Composable
internal fun LyricsSearchOverlayDialog(
    state: LyricsSearchDialogState,
    strings: LyricsSearchDialogStrings,
    onDismiss: () -> Unit,
    onTitleChanged: (String) -> Unit,
    onArtistChanged: (String) -> Unit,
    onAlbumChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onApplyDirectCandidate: (LyricsSearchCandidate, LyricsSearchApplyMode) -> Unit,
    onApplyWorkflowCandidate: (WorkflowSongCandidate, LyricsSearchApplyMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = shellColors.secondaryText
    var pendingConfirmation by remember { mutableStateOf<LyricsSearchApplyConfirmation?>(null) }
    var mobileScreen by remember { mutableStateOf(LyricsSearchMobileScreen.FORM) }
    PlatformBackHandler(
        onBack = {
            when {
                pendingConfirmation != null -> pendingConfirmation = null
                mobileScreen == LyricsSearchMobileScreen.RESULTS -> mobileScreen = LyricsSearchMobileScreen.FORM
                else -> onDismiss()
            }
        },
    )
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.68f))
                .clickable { onDismiss() },
        )
        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .widthIn(max = 1040.dp)
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
            ) { },
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = shellColors.cardContainer),
            border = BorderStroke(1.dp, shellColors.cardBorder),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(22.dp),
            ) {
                val density = LocalDensity.current
                val layoutProfile = buildLayoutProfile(
                    maxWidth = maxWidth,
                    maxHeight = maxHeight,
                    platform = currentPlatformDescriptor,
                    density = density,
                )
                val wideLayout = layoutProfile.isExpandedLayout
                if (wideLayout) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    state.headerTitle,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = primaryTextColor,
                                )
                                Text(
                                    state.headerSubtitle,
                                    color = secondaryTextColor,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TextButton(onClick = onDismiss) {
                                Text(strings.dismissLabel.displayText())
                            }
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            LyricsSearchFormPane(
                                title = state.title,
                                artistName = state.artistName,
                                albumTitle = state.albumTitle,
                                isLoading = state.isLoading,
                                error = state.error,
                                strings = strings,
                                onDismiss = onDismiss,
                                onTitleChanged = onTitleChanged,
                                onArtistChanged = onArtistChanged,
                                onAlbumChanged = onAlbumChanged,
                                onSearch = onSearch,
                                modifier = Modifier
                                    .weight(0.42f)
                                    .fillMaxHeight(),
                            )
                            LyricsSearchResultsPane(
                                state = state,
                                strings = strings,
                                onApplyDirectCandidate = { pendingConfirmation = LyricsSearchApplyConfirmation.Direct(it) },
                                onApplyWorkflowCandidate = { pendingConfirmation = LyricsSearchApplyConfirmation.Workflow(it) },
                                modifier = Modifier
                                    .weight(0.58f)
                                    .fillMaxHeight(),
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        if (mobileScreen == LyricsSearchMobileScreen.FORM) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        state.headerTitle,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = primaryTextColor,
                                    )
                                    Text(
                                        state.headerSubtitle,
                                        color = secondaryTextColor,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(onClick = onDismiss) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = strings.dismissLabel.displayText(),
                                        tint = primaryTextColor,
                                    )
                                }
                            }
                            LyricsSearchFormPane(
                                title = state.title,
                                artistName = state.artistName,
                                albumTitle = state.albumTitle,
                                isLoading = state.isLoading,
                                error = state.error,
                                strings = strings,
                                onDismiss = onDismiss,
                                onTitleChanged = onTitleChanged,
                                onArtistChanged = onArtistChanged,
                                onAlbumChanged = onAlbumChanged,
                                onSearch = {
                                    mobileScreen = LyricsSearchMobileScreen.RESULTS
                                    onSearch()
                                },
                                showSectionTitle = false,
                                showDismissButton = false,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(onClick = { mobileScreen = LyricsSearchMobileScreen.FORM }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = strings.backLabel.displayText(),
                                        tint = primaryTextColor,
                                    )
                                }
                                IconButton(onClick = onDismiss) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = strings.dismissLabel.displayText(),
                                        tint = primaryTextColor,
                                    )
                                }
                            }
                            LyricsSearchResultsPane(
                                state = state,
                                strings = strings,
                                onApplyDirectCandidate = { pendingConfirmation = LyricsSearchApplyConfirmation.Direct(it) },
                                onApplyWorkflowCandidate = { pendingConfirmation = LyricsSearchApplyConfirmation.Workflow(it) },
                                showSectionTitle = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
        pendingConfirmation?.let { confirmation ->
            LyricsSearchApplyConfirmationOverlay(
                confirmation = confirmation,
                onDismiss = { pendingConfirmation = null },
                onApply = { mode ->
                    pendingConfirmation = null
                    when (confirmation) {
                        is LyricsSearchApplyConfirmation.Direct -> onApplyDirectCandidate(confirmation.candidate, mode)
                        is LyricsSearchApplyConfirmation.Workflow -> onApplyWorkflowCandidate(confirmation.candidate, mode)
                    }
                },
            )
        }
    }
}

@Composable
private fun LyricsSearchFormPane(
    title: String,
    artistName: String,
    albumTitle: String,
    isLoading: Boolean,
    error: String?,
    strings: LyricsSearchDialogStrings,
    onDismiss: () -> Unit,
    onTitleChanged: (String) -> Unit,
    onArtistChanged: (String) -> Unit,
    onAlbumChanged: (String) -> Unit,
    onSearch: () -> Unit,
    showSectionTitle: Boolean = true,
    showDismissButton: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = shellColors.secondaryText
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = primaryTextColor,
        unfocusedTextColor = primaryTextColor,
        focusedLabelColor = secondaryTextColor,
        unfocusedLabelColor = secondaryTextColor,
        cursorColor = MaterialTheme.colorScheme.primary,
        focusedBorderColor = shellColors.selectedBorder,
        unfocusedBorderColor = shellColors.cardBorder,
        focusedPlaceholderColor = secondaryTextColor,
        unfocusedPlaceholderColor = secondaryTextColor,
    )
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showSectionTitle) {
            LyricsSearchSectionTitle(
                title = uiString(Res.string.lyrics_search_fields_title),
                subtitle = strings.formSubtitle.displayText(),
            )
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = shellColors.navContainer),
            border = BorderStroke(1.dp, shellColors.cardBorder),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
            ) {
                val density = LocalDensity.current
                val layoutProfile = buildLayoutProfile(
                    maxWidth = maxWidth,
                    maxHeight = maxHeight,
                    platform = currentPlatformDescriptor,
                    density = density,
                )
                val stackedFields = layoutProfile.isCompactLayout
                val buttonSpacing = if (stackedFields) 8.dp else 10.dp
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ImeAwareOutlinedTextField(
                        value = title,
                        onValueChange = onTitleChanged,
                        label = { Text(uiString(Res.string.common_title)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        singleLine = true,
                        colors = textFieldColors,
                    )
                    if (stackedFields) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ImeAwareOutlinedTextField(
                                value = artistName,
                                onValueChange = onArtistChanged,
                                label = { Text(uiString(Res.string.common_artist)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                singleLine = true,
                                colors = textFieldColors,
                            )
                            ImeAwareOutlinedTextField(
                                value = albumTitle,
                                onValueChange = onAlbumChanged,
                                label = { Text(uiString(Res.string.library_albums_title)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                singleLine = true,
                                colors = textFieldColors,
                            )
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ImeAwareOutlinedTextField(
                                value = artistName,
                                onValueChange = onArtistChanged,
                                label = { Text(uiString(Res.string.common_artist)) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(18.dp),
                                singleLine = true,
                                colors = textFieldColors,
                            )
                            ImeAwareOutlinedTextField(
                                value = albumTitle,
                                onValueChange = onAlbumChanged,
                                label = { Text(uiString(Res.string.library_albums_title)) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(18.dp),
                                singleLine = true,
                                colors = textFieldColors,
                            )
                        }
                    }
                    if (showDismissButton) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(buttonSpacing),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(strings.cancelLabel.displayText(), maxLines = 1)
                            }
                            Button(
                                onClick = onSearch,
                                enabled = !isLoading && title.isNotBlank(),
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(),
                            ) {
                                Text(if (isLoading) strings.searchLoadingLabel.displayText() else strings.searchIdleLabel.displayText(), maxLines = 1)
                            }
                        }
                    } else {
                        Button(
                            onClick = onSearch,
                            enabled = !isLoading && title.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(),
                        ) {
                            Text(if (isLoading) strings.searchLoadingLabel.displayText() else strings.searchIdleLabel.displayText(), maxLines = 1)
                        }
                    }
                    error?.let {
                        ElevatedCard(
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
                            ),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Text(
                                text = it,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsSearchResultsPane(
    state: LyricsSearchDialogState,
    strings: LyricsSearchDialogStrings,
    onApplyDirectCandidate: (LyricsSearchCandidate) -> Unit,
    onApplyWorkflowCandidate: (WorkflowSongCandidate) -> Unit,
    showSectionTitle: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = shellColors.secondaryText
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showSectionTitle) {
            LyricsSearchSectionTitle(
                title = uiString(Res.string.search_results_title),
                subtitle = when {
                    state.isLoading -> uiString(Res.string.lyrics_querying_sources_label)
                    state.directResults.isNotEmpty() || state.workflowResults.isNotEmpty() -> strings.resultsAppliedSubtitle.displayText()
                    state.hasResult -> uiString(Res.string.lyrics_search_results_empty_hint)
                    else -> strings.resultsPlaceholderSubtitle.displayText()
                },
            )
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = shellColors.navContainer),
            border = BorderStroke(1.dp, shellColors.cardBorder),
        ) {
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            uiString(Res.string.lyrics_querying_sources_progress),
                            color = secondaryTextColor,
                        )
                    }
                }

                state.directResults.isNotEmpty() || state.workflowResults.isNotEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (state.directResults.isNotEmpty()) {
                            Text(uiString(Res.string.lyrics_search_direct_results), color = primaryTextColor, fontWeight = FontWeight.SemiBold)
                            state.directResults.forEach { candidate ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable { onApplyDirectCandidate(candidate) }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    LyricsSearchArtworkThumbnail(
                                        artworkLocator = candidate.artworkLocator,
                                        modifier = Modifier.size(56.dp),
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                candidate.sourceNameUiText().displayText(),
                                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = primaryTextColor,
                                            )
                                            Text(
                                                resolveLyricsSearchContentType(candidate.document)
                                                    .summaryText(candidate.document.lines.size).displayText(),
                                                color = secondaryTextColor,
                                            )
                                        }
                                        candidate.title?.takeIf { it.isNotBlank() }?.let { resultTitle ->
                                            Text(
                                                resultTitle,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = primaryTextColor,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        }
                                        lyricsSearchCandidateMetadata(candidate)?.let { metadata ->
                                            Text(
                                                metadata,
                                                color = secondaryTextColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Text(
                                            lyricsSearchPreview(candidate).displayText(),
                                            color = secondaryTextColor,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                        if (state.workflowResults.isNotEmpty()) {
                            Text(uiString(Res.string.lyrics_workflow_candidates_title), color = primaryTextColor, fontWeight = FontWeight.SemiBold)
                            state.workflowResults.forEach { candidate ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable { onApplyWorkflowCandidate(candidate) }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    LyricsSearchArtworkThumbnail(
                                        artworkLocator = candidate.imageUrl,
                                        modifier = Modifier.size(56.dp),
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                candidate.sourceName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = primaryTextColor,
                                            )
                                            candidate.durationSeconds?.let { seconds ->
                                                Text(
                                                    formatLyricsSearchDuration(seconds),
                                                    color = secondaryTextColor,
                                                )
                                            }
                                        }
                                        Text(
                                            candidate.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = primaryTextColor,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            workflowSearchPreview(candidate).displayText(),
                                            color = secondaryTextColor,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                state.hasResult -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LyricsSearchEmptyStateCard(
                            title = uiString(Res.string.lyrics_not_found),
                            body = strings.emptyBody.displayText(),
                        )
                    }
                }

                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LyricsSearchEmptyStateCard(
                            title = uiString(Res.string.lyrics_search_ready_title),
                            body = strings.idleBody.displayText(),
                        )
                    }
                }
            }
        }
    }
}

private enum class LyricsSearchMobileScreen {
    FORM,
    RESULTS,
}

@Composable
private fun LyricsSearchApplyConfirmationOverlay(
    confirmation: LyricsSearchApplyConfirmation,
    onDismiss: () -> Unit,
    onApply: (LyricsSearchApplyMode) -> Unit,
) {
    val shellColors = mainShellColors
    val applyModes = lyricsSearchApplyModes(confirmation.artworkLocator)
    PlatformBackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.34f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(min = 280.dp, max = 440.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { },
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = shellColors.cardContainer),
            border = BorderStroke(1.dp, shellColors.cardBorder),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = uiString(Res.string.lyrics_apply_mode_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LyricsSearchArtworkThumbnail(
                        artworkLocator = confirmation.artworkLocator,
                        modifier = Modifier.size(72.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = confirmation.sourceName.displayText(),
                            color = shellColors.secondaryText,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = confirmation.title.displayText(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        confirmation.metadata?.let { metadata ->
                            Text(
                                text = metadata.displayText(),
                                style = MaterialTheme.typography.labelMedium,
                                color = shellColors.secondaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Text(
                    text = confirmation.preview.displayText(),
                    color = shellColors.secondaryText,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                val actions = buildList<@Composable (Modifier) -> Unit> {
                    add { modifier ->
                        Button(
                            onClick = { onApply(LyricsSearchApplyMode.FULL) },
                            modifier = modifier,
                        ) {
                            Text(uiString(Res.string.common_apply))
                        }
                    }
                    add { modifier ->
                        OutlinedButton(
                            onClick = { onApply(LyricsSearchApplyMode.LYRICS_ONLY) },
                            modifier = modifier,
                        ) {
                            Text(uiString(Res.string.lyrics_apply_lyrics_only))
                        }
                    }
                    if (LyricsSearchApplyMode.ARTWORK_ONLY in applyModes) {
                        add { modifier ->
                            OutlinedButton(
                                onClick = { onApply(LyricsSearchApplyMode.ARTWORK_ONLY) },
                                modifier = modifier,
                            ) {
                                Text(uiString(Res.string.lyrics_apply_artwork_only))
                            }
                        }
                    }
                    add { modifier ->
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = modifier,
                        ) {
                            Text(uiString(Res.string.common_cancel))
                        }
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    actions.chunked(2).forEach { rowActions ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            rowActions.forEach { action ->
                                action(Modifier.weight(1f))
                            }
                            repeat((2 - rowActions.size).coerceAtLeast(0)) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

fun lyricsSearchApplyModes(artworkLocator: String?): List<LyricsSearchApplyMode> {
    return buildList {
        add(LyricsSearchApplyMode.FULL)
        add(LyricsSearchApplyMode.LYRICS_ONLY)
        if (!normalizeArtworkLocator(artworkLocator).isNullOrBlank()) {
            add(LyricsSearchApplyMode.ARTWORK_ONLY)
        }
    }
}

private sealed interface LyricsSearchApplyConfirmation {
    val sourceName: UiText
    val title: UiText
    val metadata: UiText?
    val preview: UiText
    val artworkLocator: String?

    data class Direct(val candidate: LyricsSearchCandidate) : LyricsSearchApplyConfirmation {
        override val sourceName get() = candidate.sourceNameUiText()
        override val title get() = candidate.title?.takeIf { it.isNotBlank() }?.let(UiText::Raw)
            ?: uiText(Res.string.lyrics_search_result_label)
        override val metadata get(): UiText = UiText.Joined(listOfNotNull(
            resolveLyricsSearchContentType(candidate.document).summaryText(candidate.document.lines.size, describeType = true),
            lyricsSearchCandidateMetadata(candidate)?.let(UiText::Raw),
        ))
        override val preview get() = lyricsSearchPreview(candidate)
        override val artworkLocator = candidate.artworkLocator
    }

    data class Workflow(val candidate: WorkflowSongCandidate) : LyricsSearchApplyConfirmation {
        override val sourceName = UiText.Raw(candidate.sourceName)
        override val title = UiText.Raw(candidate.title)
        override val metadata get() = workflowSearchPreview(candidate)
        override val preview = uiText(Res.string.lyrics_workflow_candidate_apply_hint)
        override val artworkLocator = candidate.imageUrl
    }
}

internal enum class LyricsSearchContentType(
    private val shortLabelResource: StringResource,
    private val descriptionLabelResource: StringResource,
) {
    WORD(Res.string.lyrics_sync_mode_word, Res.string.lyrics_word_synced_label),
    LINE(Res.string.lyrics_sync_mode_line, Res.string.lyrics_line_synced_label),
    PLAIN(Res.string.lyrics_sync_mode_plain_text, Res.string.lyrics_plain_text_label);

    val shortLabel: UiText get() = uiText(shortLabelResource)
    val descriptionLabel: UiText get() = uiText(descriptionLabelResource)

    fun summaryText(lineCount: Int, describeType: Boolean = false): UiText = lyricsSearchLineCountSummary(
        uiText(if (describeType) descriptionLabelResource else shortLabelResource), lineCount,
    )
}

internal fun lyricsSearchLineCountSummary(typeLabel: UiText, lineCount: Int): UiText =
    uiText(Res.string.lyrics_result_type_and_line_count, typeLabel, uiPlural(Res.plurals.common_line_count, lineCount, lineCount))

internal fun resolveLyricsSearchContentType(document: LyricsDocument): LyricsSearchContentType {
    return when {
        parseEnhancedLyricsPresentation(
            rawPayload = document.rawPayload,
            fallbackDocument = document,
        ) != null -> LyricsSearchContentType.WORD

        document.isSynced -> LyricsSearchContentType.LINE
        else -> LyricsSearchContentType.PLAIN
    }
}

private fun lyricsSearchPreview(candidate: LyricsSearchCandidate): UiText {
    return candidate.document.lines
        .map { it.text.trim() }
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString(" / ")
        .takeIf { it.isNotBlank() }?.let(UiText::Raw) ?: uiText(Res.string.lyrics_empty_message)
}

private fun lyricsSearchCandidateMetadata(candidate: LyricsSearchCandidate): String? {
    return buildString {
        candidate.artistName?.takeIf { it.isNotBlank() }?.let { append(it) }
        candidate.albumTitle?.takeIf { it.isNotBlank() }?.let {
            if (isNotEmpty()) append(" · ")
            append(it)
        }
        candidate.durationSeconds?.takeIf { it > 0 }?.let {
            if (isNotEmpty()) append(" · ")
            append(formatLyricsSearchDuration(it))
        }
    }.takeIf { it.isNotBlank() }
}

private fun workflowSearchPreview(candidate: WorkflowSongCandidate): UiText = UiText.Joined(listOfNotNull(
    candidate.artists.joinToString(" / ").takeIf { it.isNotBlank() }?.let(UiText::Raw)
        ?: uiText(Res.string.common_unknown_singer),
    candidate.album?.takeIf { it.isNotBlank() }?.let(UiText::Raw),
))

@Composable
private fun LyricsSearchSectionTitle(
    title: String,
    subtitle: String,
) {
    val shellColors = mainShellColors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, color = shellColors.secondaryText)
    }
}

@Composable
private fun LyricsSearchEmptyStateCard(
    title: String,
    body: String,
) {
    val shellColors = mainShellColors
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = shellColors.cardContainer),
        border = BorderStroke(1.dp, shellColors.cardBorder),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LyricsSearchArtworkThumbnail(
    artworkLocator: String?,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(shellColors.cardContainer)
            .border(
                border = BorderStroke(1.dp, shellColors.cardBorder),
                shape = RoundedCornerShape(1.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        LynArtworkImage(
            artworkLocator = artworkLocator,
            contentDescription = null,
            cacheRemote = false,
            maxDecodeSizePx = ArtworkDecodeSize.Thumbnail,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

private fun formatLyricsSearchDuration(durationSeconds: Int): String {
    val safeSeconds = durationSeconds.coerceAtLeast(0)
    val minutes = safeSeconds / 60
    val seconds = safeSeconds % 60
    return buildString {
        append(minutes)
        append(':')
        if (seconds < 10) append('0')
        append(seconds)
    }
}
