package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.Space8h
import calebxzau.rdi.client.ui.Space8w
import calebxzau.rdi.client.ui.TitleRow
import calebxzhou.rdi.common.model.AllGameRules
import calebxzhou.rdi.common.model.GameRuleValueType
import java.text.Collator
import java.util.Locale


@Composable
fun GameRuleModal(
    show: Boolean,
    overrideRules: MutableMap<String, String>,
    onClose: () -> Unit,
    onBack: (() -> Unit)? = null,
    zIndex: Float = 2f
) {
    if (!show) return

    val groupedRules = remember {
        val collator = Collator.getInstance(Locale.SIMPLIFIED_CHINESE)
        AllGameRules.groupBy { it.category }
            .toSortedMap(compareBy(collator) { it })
    }
    val baseRuleById = remember { AllGameRules.associateBy { it.id } }
    val changedCount = overrideRules.size

    fun updateOverride(ruleId: String, newValue: String) {
        val baseRule = baseRuleById[ruleId] ?: return
        val normalized = newValue.trim()
        if (normalized.equals(baseRule.value, ignoreCase = true) || normalized.isEmpty()) {
            overrideRules.remove(ruleId)
        } else {
            overrideRules[ruleId] = normalized
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .zIndex(zIndex)
            .pointerInput(Unit) { detectTapGestures(onPress = { tryAwaitRelease() }) }
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.9f)
                .height(560.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val title = if (changedCount > 0) {
                    "游戏规则设定（${changedCount}项已更改）"
                } else {
                    "游戏规则设定"
                }
                TitleRow(title, { (onBack ?: onClose).invoke() }) {
                    CircleIconButton("\uDB81\uDC50", "重置") { overrideRules.clear() }
                }
                Space8h()
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(320.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    groupedRules.forEach { (category, rules) ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = category,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        items(rules.sortedBy { it.name }, key = { it.id }) { rule ->
                            val currentValue = overrideRules[rule.id] ?: rule.value
                            val isChanged = overrideRules.containsKey(rule.id)
                            val ruleContentColor = if (isChanged) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = if (isChanged) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                contentColor = ruleContentColor,
                                tonalElevation = if (isChanged) 2.dp else 0.dp
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        when (rule.valueType) {
                                            GameRuleValueType.BOOLEAN -> {
                                                Checkbox(
                                                    checked = currentValue.equals("true", true),
                                                    onCheckedChange = { checked ->
                                                        updateOverride(rule.id, checked.toString())
                                                    }
                                                )
                                            }
                                            GameRuleValueType.INTEGER -> {
                                                var text by remember(rule.id, currentValue) {
                                                    mutableStateOf(currentValue)
                                                }
                                                OutlinedTextField(
                                                    value = text,
                                                    onValueChange = { input ->
                                                        text = input
                                                        val parsed = input.trim().toIntOrNull()
                                                        if (parsed != null) {
                                                            updateOverride(rule.id, parsed.toString())
                                                        } else if (input.isBlank()) {
                                                            updateOverride(rule.id, "")
                                                        }
                                                    },
                                                    label = { Text("数值") },
                                                    singleLine = true,
                                                    modifier = Modifier.width(96.dp)
                                                )
                                            }
                                        }
                                        Space8w()
                                        Text(
                                            text = rule.name,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    Text(
                                        text = rule.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ruleContentColor.copy(alpha = 0.78f)
                                    )
                                    rule.effect?.takeIf { it.isNotBlank() }?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ruleContentColor.copy(alpha = 0.78f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
