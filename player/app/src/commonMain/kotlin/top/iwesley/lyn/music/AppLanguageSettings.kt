package top.iwesley.lyn.music

import top.iwesley.lyn.music.resources.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.ui.mainShellColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLanguageSettings(language: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val shellColors = mainShellColors
    val shape = RoundedCornerShape(28.dp)
    MainShellElevatedCard(
        modifier = Modifier.fillMaxWidth().clip(shape)
            .clickable(role = Role.Button, onClick = { showPicker = true }),
        shape = shape,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = uiString(Res.string.language_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = appLanguageLabel(language),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = shellColors.secondaryText,
                textAlign = TextAlign.End,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = shellColors.secondaryText,
                modifier = Modifier.size(20.dp),
            )
        }
    }

    if (showPicker) {
        val selectLanguage: (AppLanguage) -> Unit = { option ->
            showPicker = false
            if (option != language) onSelect(option)
        }
        val appDensity = LocalDensity.current
        if (currentPlatformDescriptor.isMobilePlatform()) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val scope = rememberCoroutineScope()
            var isClosing by remember { mutableStateOf(false) }
            ModalBottomSheet(
                onDismissRequest = { showPicker = false },
                sheetState = sheetState,
                containerColor = shellColors.navContainer,
            ) {
                CompositionLocalProvider(LocalDensity provides appDensity) {
                    LanguagePickerContent(language) { option ->
                        if (!isClosing) {
                            isClosing = true
                            if (option != language) onSelect(option)
                            scope.launch {
                                try {
                                    sheetState.hide()
                                } finally {
                                    if (!sheetState.isVisible) showPicker = false
                                    isClosing = false
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Dialog(onDismissRequest = { showPicker = false }) {
                CompositionLocalProvider(LocalDensity provides appDensity) {
                    Surface(shape = shape, color = shellColors.navContainer) {
                        LanguagePickerContent(language, selectLanguage)
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguagePickerContent(language: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = uiString(Res.string.language_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = uiString(Res.string.language_description),
            style = MaterialTheme.typography.bodyMedium,
            color = mainShellColors.secondaryText,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Column(Modifier.fillMaxWidth().selectableGroup()) {
            AppLanguage.entries.forEach { option ->
                val selected = language == option
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onSelect(option) },
                    ).heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = appLanguageLabel(option),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    if (selected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}
