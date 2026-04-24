package top.iwesley.lyn.music

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

@Composable
internal fun ImeAwareOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    resetKey: Any? = null,
) {
    var textFieldValueState by remember(resetKey) {
        mutableStateOf(textFieldValueFor(value))
    }
    LaunchedEffect(value, resetKey) {
        if (value != textFieldValueState.text) {
            textFieldValueState = textFieldValueFor(value)
        }
    }

    OutlinedTextField(
        value = textFieldValueState,
        onValueChange = { updatedValue ->
            textFieldValueState = updatedValue
            // Avoid leaking composing pinyin/IME placeholder text into store-backed state.
            if (updatedValue.composition == null && updatedValue.text != value) {
                onValueChange(updatedValue.text)
            }
        },
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        trailingIcon = trailingIcon,
        shape = shape,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        readOnly = readOnly,
        keyboardOptions = keyboardOptions,
        colors = colors,
    )
}

private fun textFieldValueFor(value: String): TextFieldValue {
    return TextFieldValue(
        text = value,
        selection = TextRange(value.length),
    )
}
