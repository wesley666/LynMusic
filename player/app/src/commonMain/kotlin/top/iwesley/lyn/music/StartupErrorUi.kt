package top.iwesley.lyn.music

import top.iwesley.lyn.music.resources.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.iwesley.lyn.music.ui.LynMusicTheme
import top.iwesley.lyn.music.ui.mainShellColors
import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.uiFailureTextOrNull

internal fun startupDataLocationErrorText(error: Throwable): UiText =
    error.uiFailureTextOrNull() ?: UiText.Raw(error.message ?: error.toString())

internal val STARTUP_DATABASE_COMPATIBILITY_ERROR_TITLE: String @Composable get() = uiString(Res.string.startup_database_version_recovery_hint)

internal val STARTUP_DATABASE_COMPATIBILITY_ERROR_BODY: String @Composable get() = uiString(Res.string.startup_database_version_data_preserved)

@Composable
@Suppress("DEPRECATION")
fun StartupDatabaseErrorScreen(
    error: Throwable?,
    showDetails: Boolean,
    modifier: Modifier = Modifier,
) {
    top.iwesley.lyn.music.core.model.ProvideUiLanguage {
        LynMusicTheme {
            val shellColors = mainShellColors
            val clipboardManager = LocalClipboardManager.current
            var detailsCopied by remember(error) { mutableStateOf(false) }
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(shellColors.appGradientTop)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(shellColors.navContainer.copy(alpha = 0.94f))
                        .padding(horizontal = 24.dp, vertical = 26.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = STARTUP_DATABASE_COMPATIBILITY_ERROR_TITLE,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = STARTUP_DATABASE_COMPATIBILITY_ERROR_BODY,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    startupDatabaseErrorDetails(error)
                        ?.takeIf { showDetails }
                        ?.let { details ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = details,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 180.dp)
                                        .verticalScroll(rememberScrollState())
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(shellColors.cardContainer.copy(alpha = 0.72f))
                                        .padding(14.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(details))
                                        detailsCopied = true
                                    },
                                ) {
                                    Text(if (detailsCopied) uiString(Res.string.startup_logs_copied) else uiString(Res.string.startup_copy_error_logs))
                                }
                            }
                        }
                }
            }
        }


    }
}

@Composable
fun StartupDataLocationProgressScreen(
    message: String,
    fraction: Float?,
    modifier: Modifier = Modifier,
) {
    top.iwesley.lyn.music.core.model.ProvideUiLanguage {
        StartupDataLocationSurface(modifier) {
            CircularProgressIndicator()
            Text(message, style = MaterialTheme.typography.titleMedium)
            fraction?.let {
                LinearProgressIndicator(
                    progress = { it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                uiString(Res.string.startup_preparation_wait_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }


    }
}

@Composable
fun StartupDataLocationErrorScreen(
    error: Throwable,
    canCancelChange: Boolean,
    onRetry: () -> Unit,
    onCancelChange: () -> Unit,
    onExitApplication: () -> Unit,
    modifier: Modifier = Modifier,
) {
    top.iwesley.lyn.music.core.model.ProvideUiLanguage {
        StartupDataLocationSurface(modifier) {
            Text(uiString(Res.string.startup_location_change_failed), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                startupDataLocationErrorText(error).displayText(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (canCancelChange) {
                    uiString(Res.string.startup_location_change_retry_hint)
                } else {
                    uiString(Res.string.startup_active_folder_recovery_hint)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRetry) { Text(uiString(Res.string.common_retry)) }
                if (canCancelChange) {
                    OutlinedButton(onClick = onCancelChange) { Text(uiString(Res.string.startup_cancel_location_change)) }
                } else {
                    OutlinedButton(onClick = onExitApplication) { Text(uiString(Res.string.common_exit_app)) }
                }
            }
        }


    }
}

@Composable
private fun StartupDataLocationSurface(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    LynMusicTheme {
        val shellColors = mainShellColors
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(shellColors.appGradientTop)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(shellColors.navContainer.copy(alpha = 0.94f))
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
        }
    }
}

internal fun startupDatabaseErrorDetails(error: Throwable?): String? {
    return error
        ?.stackTraceToString()
        ?.takeIf { it.isNotBlank() }
}
