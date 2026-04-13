package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calebxzhou.rdi.client.CodeFontFamily
import calebxzhou.rdi.client.ui.asIconText

/**
 * calebxzhou @ 2026-01-20 23:20
 */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "密码",
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    showPassword: Boolean = false,
    onToggleVisibility: () -> Unit,
    onEnter: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        textStyle = MaterialTheme.typography.body2,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().onKeyEvent { event ->
            if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                onEnter()
                true
            } else {
                false
            }
        },
        visualTransformation = if (showPassword) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            Text(
                text = "\uDB80\uDE08".asIconText,
                style = MaterialTheme.typography.h6.copy(
                    fontFamily = CodeFontFamily,
                    fontSize = 20.sp
                ),
                modifier = Modifier
                    .padding(end = 8.dp)
                    .clickable { onToggleVisibility() }
            )
        },
    )
}
