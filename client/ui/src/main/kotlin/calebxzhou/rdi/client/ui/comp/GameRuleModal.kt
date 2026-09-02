package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.RVerticalScrollbar
import calebxzau.rdi.client.ui.Space8h
import calebxzau.rdi.client.ui.TitleRow
import calebxzhou.rdi.common.model.AllGameRules
import calebxzhou.rdi.common.model.GameRule
import calebxzhou.rdi.common.model.GameRuleValueType
import java.text.Collator
import java.util.*

internal data class GameRuleChange(
    val ruleId: String,
    val oldValue: String?,
    val newValue: String?,
)

internal fun findChangedGameRules(
    openingRules: Map<String, String>,
    draft: Map<String, String>,
    baseRuleById: Map<String, GameRule>,
): List<GameRuleChange> {
    fun effectiveValue(ruleId: String, rules: Map<String, String>): String? =
        rules[ruleId] ?: baseRuleById[ruleId]?.value

    fun valuesEqual(ruleId: String, oldValue: String?, newValue: String?): Boolean {
        if (oldValue == null || newValue == null) return oldValue == newValue
        return if (baseRuleById[ruleId]?.valueType == GameRuleValueType.BOOLEAN) {
            oldValue.equals(newValue, ignoreCase = true)
        } else {
            oldValue.trim() == newValue.trim()
        }
    }

    return (openingRules.keys + draft.keys).distinct().mapNotNull { ruleId ->
        val oldValue = effectiveValue(ruleId, openingRules)
        val newValue = effectiveValue(ruleId, draft)
        if (valuesEqual(ruleId, oldValue, newValue)) {
            null
        } else {
            GameRuleChange(ruleId, oldValue, newValue)
        }
    }
}


@Composable
fun GameRuleModal(
    show: Boolean,
    initialOverrideRules: Map<String, String>,
    onSave: (Map<String, String>) -> Unit,
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
    val openingRules = remember { initialOverrideRules.toMap() }
    val draft = remember {
        mutableStateMapOf<String, String>().also { it.putAll(initialOverrideRules) }
    }
    val listState = rememberLazyListState()
    val invalidIntegerRules = remember { mutableStateSetOf<String>() }
    var resetGeneration by remember { mutableStateOf(0) }
    var showSaveConfirmation by remember { mutableStateOf(false) }
    val changedCount = draft.size

    fun updateOverride(ruleId: String, newValue: String) {
        val baseRule = baseRuleById[ruleId] ?: return
        val normalized = newValue.trim()
        if (normalized.equals(baseRule.value, ignoreCase = true) || normalized.isEmpty()) {
            draft.remove(ruleId)
        } else {
            draft[ruleId] = normalized
        }
    }

    fun displayValue(ruleId: String, value: String?): String {
        val rule = baseRuleById[ruleId]
        if (value == null) return if (rule == null) "默认值（未知）" else rule.value
        return if (rule?.valueType == GameRuleValueType.BOOLEAN) {
            if (value.equals("true", ignoreCase = true)) "开启" else "关闭"
        } else {
            value
        }
    }

    val changedRules = findChangedGameRules(openingRules, draft, baseRuleById).map {
        Triple(
            it.ruleId,
            displayValue(it.ruleId, it.oldValue),
            displayValue(it.ruleId, it.newValue),
        )
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
            val title = if (changedCount > 0) {
                "游戏规则设定（${changedCount}项未保存）"
            } else {
                "游戏规则设定"
            }

            Column(modifier = Modifier.padding(0.dp)) {
                TitleRow(title, { (onBack ?: onClose).invoke() }) {
                    CircleIconButton("\uDB81\uDC50", "重置") {
                        draft.clear()
                        invalidIntegerRules.clear()
                        resetGeneration++
                    }
                    CircleIconButton(
                        icon = "\uF0C7",
                        label = "保存",
                        enabled = invalidIntegerRules.isEmpty(),
                    ) { showSaveConfirmation = true }
                }
                Space8h()
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp)
                    ) {
                        groupedRules.forEach { (category, rules) ->
                            item(key = "category:${category}") {
                                Text(
                                    text = category,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }
                            items(rules.sortedBy { it.name }, key = { "rule:${it.id}" }) { rule ->
                                val currentValue = draft[rule.id] ?: rule.value
                                val isChanged = draft.containsKey(rule.id)
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier.width(104.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
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
                                                    var text by remember(rule.id, currentValue, resetGeneration) {
                                                        mutableStateOf(currentValue)
                                                    }
                                                    val isInvalid = invalidIntegerRules.contains(rule.id)
                                                    val interactionSource = remember(rule.id) { MutableInteractionSource() }
                                                    val colors = OutlinedTextFieldDefaults.colors()
                                                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                                                        BasicTextField(
                                                            value = text,
                                                            onValueChange = { input ->
                                                                val parsed = input.trim().toIntOrNull()
                                                                when {
                                                                    parsed != null -> {
                                                                        text = input
                                                                        invalidIntegerRules.remove(rule.id)
                                                                        updateOverride(rule.id, parsed.toString())
                                                                    }
                                                                    input.isBlank() -> {
                                                                        invalidIntegerRules.remove(rule.id)
                                                                        updateOverride(rule.id, "")
                                                                        text = rule.value
                                                                    }
                                                                    else -> {
                                                                        text = input
                                                                        invalidIntegerRules.add(rule.id)
                                                                    }
                                                                }
                                                            },
                                                            modifier = Modifier.width(96.dp),
                                                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            ),
                                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                                            singleLine = true,
                                                            interactionSource = interactionSource,
                                                            decorationBox = { innerTextField ->
                                                                OutlinedTextFieldDefaults.DecorationBox(
                                                                    value = text,
                                                                    innerTextField = innerTextField,
                                                                    enabled = true,
                                                                    singleLine = true,
                                                                    visualTransformation = VisualTransformation.None,
                                                                    interactionSource = interactionSource,
                                                                    label = { Text("数值") },
                                                                    isError = isInvalid,
                                                                    supportingText = if (isInvalid) {
                                                                        { Text("请输入整数") }
                                                                    } else {
                                                                        null
                                                                    },
                                                                    colors = colors,
                                                                    contentPadding = PaddingValues(
                                                                        horizontal = 16.dp,
                                                                        vertical = 8.dp
                                                                    )
                                                                )
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        Text(
                                            text = rule.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isChanged) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            modifier = Modifier
                                                .weight(0.22f)
                                                .padding(end = 8.dp),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = rule.id,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .weight(0.23f)
                                                .padding(end = 8.dp),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = rule.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(0.55f)
                                        )
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                    RVerticalScrollbar(
                        listState = listState,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
        }
    }

    if (showSaveConfirmation) {
        AlertDialog(
            onDismissRequest = { showSaveConfirmation = false },
            title = { Text("确认保存游戏规则") },
            text = {
                if (changedRules.isEmpty()) {
                    Text("没有游戏规则发生变化。")
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        changedRules.forEach { (ruleId, oldValue, newValue) ->
                            val ruleName = baseRuleById[ruleId]?.name ?: "未知规则"
                            Text("$ruleName（$ruleId）：$oldValue→$newValue")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSaveConfirmation = false
                        onSave(draft.toMap())
                        onClose()
                    }
                ) {
                    Text("确认保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveConfirmation = false }) {
                    Text("继续修改")
                }
            }
        )
    }
}
