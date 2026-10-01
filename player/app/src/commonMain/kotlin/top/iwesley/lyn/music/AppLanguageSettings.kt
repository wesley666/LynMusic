package top.iwesley.lyn.music

import top.iwesley.lyn.music.resources.*

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import top.iwesley.lyn.music.core.model.AppLanguage

@Composable
fun AppLanguageSettings(language: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(uiString(Res.string.language_title), style = MaterialTheme.typography.titleMedium)
        Text(uiString(Res.string.language_description), style = MaterialTheme.typography.bodyMedium)
        AppLanguage.entries.forEach { option ->
            Row(
                Modifier.fillMaxWidth().selectable(
                    selected = language == option,
                    role = Role.RadioButton,
                    onClick = { onSelect(option) },
                ).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = language == option, onClick = null)
                Spacer(Modifier.width(8.dp))
                Text(appLanguageLabel(option))
            }
        }
    }
}
