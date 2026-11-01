package top.iwesley.lyn.music.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun rememberListIndexColumnWidth(itemCount: Int): Dp {
    val style = listIndexTextStyle()
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 10)
    val digitCount = itemCount.coerceAtLeast(1).toString().length.coerceAtLeast(2)
    // TextMeasurer caches these glyphs and invalidates layouts when resolved fonts change.
    val widestDigitWidthPx = (0..9).maxOf { digit ->
        textMeasurer.measure(
            text = digit.toString(),
            style = style,
            softWrap = false,
            maxLines = 1,
        ).size.width
    }
    return with(density) { (widestDigitWidthPx * digitCount).toDp().coerceAtLeast(32.dp) }
}

@Composable
internal fun ListIndexColumn(
    index: Int,
    width: Dp,
    color: Color = Color.Unspecified,
    content: (@Composable BoxScope.() -> Unit)? = null,
) {
    val style = listIndexTextStyle()
    Box(modifier = Modifier.width(width), contentAlignment = Alignment.Center) {
        if (content != null) {
            content()
        } else {
            Text(
                text = (index + 1).toString().padStart(2, '0'),
                modifier = Modifier.fillMaxWidth(),
                color = color,
                style = style,
                textAlign = TextAlign.Center,
                softWrap = false,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun listIndexTextStyle(): TextStyle = LocalTextStyle.current.copy(
    fontWeight = FontWeight.Bold,
    fontFeatureSettings = "tnum",
)
