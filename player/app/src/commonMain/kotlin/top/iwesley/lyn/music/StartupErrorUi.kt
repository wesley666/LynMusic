package top.iwesley.lyn.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.iwesley.lyn.music.ui.LynMusicTheme
import top.iwesley.lyn.music.ui.mainShellColors

internal const val STARTUP_DATABASE_COMPATIBILITY_ERROR_TITLE =
    "数据库版本不兼容，请升级到最新版本或恢复备份"

internal const val STARTUP_DATABASE_COMPATIBILITY_ERROR_BODY =
    "当前版本无法打开本地数据库。为避免数据丢失，应用没有清空数据库。"

@Composable
fun StartupDatabaseErrorScreen(
    error: Throwable?,
    showDetails: Boolean,
    modifier: Modifier = Modifier,
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
                    }
            }
        }
    }
}

internal fun startupDatabaseErrorDetails(error: Throwable?): String? {
    return error
        ?.toString()
        ?.takeIf { it.isNotBlank() }
}
