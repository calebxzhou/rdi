package calebxzau.rdi.client.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSearch: (() -> Unit)?,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        disabledContainerColor = MaterialTheme.colorScheme.surface
    )
    val textColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier
                .onPreviewKeyEvent { event ->
                    if (onSearch != null && event.key == Key.Enter && event.type == KeyEventType.KeyUp) {
                        onSearch()
                        true
                    } else {
                        false
                    }
                }
                .height(42.dp),
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value,
                    innerTextField = innerTextField,
                    enabled = enabled,
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = interactionSource,
                    placeholder = { Text(placeholder) },
                    leadingIcon = {
                        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                            when {
                                loading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)

                                else -> IconButton(
                                    onClick = { onSearch?.invoke() },
                                    modifier = Modifier.size(32.dp).padding(start = 10.dp),
                                    enabled = enabled
                                ) {
                                    Text("\uF002", fontFamily = IconFontFamily)
                                }
                            }
                        }
                    },
                    colors = colors,
                    contentPadding = PaddingValues(2.dp)
                )
            }
        )
    }
}
