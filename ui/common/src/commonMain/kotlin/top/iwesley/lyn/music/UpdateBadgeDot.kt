package top.iwesley.lyn.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun UpdateBadgeDot(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(9.dp)
            .background(MaterialTheme.colorScheme.error, RoundedCornerShape(50)),
    )
}

@Composable
fun BadgedIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    showBadge: Boolean,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Box(modifier = modifier) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = iconModifier.align(Alignment.Center),
        )
        if (showBadge) {
            UpdateBadgeDot(modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}
