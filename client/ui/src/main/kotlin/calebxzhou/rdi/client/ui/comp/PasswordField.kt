package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calebxzau.rdi.client.ui.CodeFontFamily
import calebxzau.rdi.client.ui.asIconText
import calebxzau.rdi.client.ui.baseShapeRadius

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
    shape: Shape = RoundedCornerShape(baseShapeRadius.dp),
    showPassword: Boolean = false,
    onToggleVisibility: () -> Unit={},
    onEnter: () -> Unit={}
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        shape = shape,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
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
                style = MaterialTheme.typography.titleLarge.copy(
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
