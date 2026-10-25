package top.iwesley.lyn.music

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.resources.*
import top.iwesley.lyn.music.ui.mainShellColors

data class ListItemMenuAction(
    val id: String,
    val label: UiText,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

enum class ListItemMenuPresentation { BottomSheet, Dropdown }

@Composable
fun ListItemMoreButton(description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(Icons.Rounded.MoreVert, description, modifier = Modifier.size(24.dp))
    }
}

/** Place next to the trigger inside its Box so desktop menus remain anchored to it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListItemActionMenu(
    title: String,
    actions: List<ListItemMenuAction>,
    presentation: ListItemMenuPresentation,
    expanded: Boolean,
    onDismiss: () -> Unit,
) {
    if (!expanded) return
    val colors = mainShellColors
    if (presentation == ListItemMenuPresentation.Dropdown) {
        DropdownMenu(expanded = true, onDismissRequest = onDismiss, containerColor = colors.navContainer) {
            ListItemActionMenuItems(actions, onDismiss)
        }
    } else {
        val density = LocalDensity.current
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.navContainer,
        ) {
            CompositionLocalProvider(LocalDensity provides density) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
                    Text(title, Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                    ListItemActionMenuItems(actions, onDismiss)
                }
            }
        }
    }
}

@Composable
private fun ListItemActionMenuItems(actions: List<ListItemMenuAction>, onDismiss: () -> Unit) {
    actions.forEach { action ->
        key(action.id) {
            val color = if (action.destructive && action.enabled) MaterialTheme.colorScheme.error else Color.Unspecified
            DropdownMenuItem(
                text = { Text(action.label.displayText(), color = color) },
                leadingIcon = { Icon(action.icon, null, tint = if (action.destructive && action.enabled) color else LocalContentColor.current) },
                enabled = action.enabled,
                onClick = {
                    onDismiss()
                    action.onClick()
                },
            )
        }
    }
}
