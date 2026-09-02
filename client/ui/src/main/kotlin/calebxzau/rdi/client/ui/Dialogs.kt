package calebxzau.rdi.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * calebxzhou @ 2026-08-06 13:11
 */

@Composable
fun AlertWarn(msg: String, onClose: (() -> Unit)? = null) {
    AlertDialog(
        title = "警告",
        icon = "\uEA6C",
        msg = msg,
        accentColor = Color(0xFFE0A800),
        onClose = onClose
    )
}

@Composable
fun AlertErr(msg: String, onClose: (() -> Unit)? = null) {
    AlertDialog(
        title = "错误",
        icon = "\uEA87",
        msg = msg,
        accentColor = Color(0xFFD64545),
        onClose = onClose
    )
}

@Composable
fun AlertOk(msg: String, onClose: (() -> Unit)? = null) {
    AlertDialog(
        title = "成功",
        icon = "\uF00C",
        msg = msg,
        accentColor = Color(0xFF3A9D5D),
        onClose = onClose
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确定", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}


@Composable
private fun AlertDialog(
    title: String,
    icon: String,
    msg: String,
    accentColor: Color,
    onClose: (() -> Unit)? = null
) {
    var visible by remember(msg) { mutableStateOf(true) }
    if (!visible && onClose == null) return

    fun close() {
        if (onClose == null) visible = false
        onClose?.invoke()
    }

    AlertDialog(
        onDismissRequest = ::close,
        title = {
            val titleStyle = MaterialTheme.typography.titleMedium
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = icon,
                    color = Color.White,
                    style = titleStyle,
                    fontFamily = IconFontFamily,
                    modifier = Modifier
                        .background(accentColor, CircleShape)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                        .alignByBaseline()
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = titleStyle,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.alignByBaseline()
                )
            }
        },
        text = {
            Text(
                text = msg,
                textAlign = TextAlign.Left,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = ::close) {
                Text("明白", color = accentColor)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp)
    )
}
