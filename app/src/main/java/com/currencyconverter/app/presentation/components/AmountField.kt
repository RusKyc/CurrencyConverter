package com.currencyconverter.app.presentation.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Numeric input. The cursor state lives here so typing never fights with the ViewModel
 * round-trip; [text] is only pushed into the field when it differs from what is displayed.
 */
@Composable
fun AmountField(
    text: String,
    onTextChange: (String) -> Unit,
    sanitize: (String) -> String,
    label: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
) {
    var value by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }

    LaunchedEffect(text) {
        if (value.text != text) value = TextFieldValue(text, TextRange(text.length))
    }

    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            val clean = sanitize(input.text)
            value = if (clean == input.text) input else TextFieldValue(clean, TextRange(clean.length))
            if (clean != text) onTextChange(clean)
        },
        modifier = modifier,
        label = { Text(label) },
        textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.End),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
    )
}
