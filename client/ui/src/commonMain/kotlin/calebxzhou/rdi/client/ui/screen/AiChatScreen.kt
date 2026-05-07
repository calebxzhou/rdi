package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme as MaterialTheme3
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import calebxzhou.rdi.client.AppConfig
import calebxzhou.rdi.client.UIFontFamily
import calebxzhou.rdi.client.service.AiChatHistoryService
import calebxzhou.rdi.client.service.AiChatRecord
import calebxzhou.rdi.client.service.AiChatRecordSummary
import calebxzhou.rdi.client.service.AiChatSavedMessage
import calebxzhou.rdi.client.service.AiChatSavedToolStatus
import calebxzhou.rdi.client.ui.CircleIconButton
import calebxzhou.rdi.client.ui.MainBox
import calebxzhou.rdi.client.ui.MainColumn
import calebxzhou.rdi.client.ui.MaterialColor
import calebxzhou.rdi.client.ui.Space8h
import calebxzhou.rdi.client.ui.TitleRow
import calebxzhou.rdi.client.ui.asIconText
import calebxzhou.rdi.client.ui.comp.PlatformVerticalScrollbar
import calebxzhou.rdi.client.ui.copyToClipboard
import calebxzhou.rdi.client.ui.loadResourceStream
import calebxzhou.rdi.common.DEBUG
import calebxzhou.rdi.common.service.OpenaiChatEvent
import calebxzhou.rdi.common.service.OpenaiChatMessage
import calebxzhou.rdi.common.service.OpenaiService
import calebxzhou.rdi.common.util.humanDateTimeNow
import calebxzhou.rdi.common.util.millisToHumanDateTime
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class AiChatBubble(
    val role: String,
    val content: String,
    val contextMessageCount: Int = 1,
    val contextCompressed: Boolean = false,
    val reasoningContent: String = "",
    val reasoningExpanded: Boolean = true,
    val toolStatuses: List<AiToolStatus> = emptyList(),
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val billablePromptTokens: Int? = null,
    val billableCompletionTokens: Int? = null,
    val startedAtMillis: Long? = null,
    val reasoningFinishedAtMillis: Long? = null,
    val finishedAtMillis: Long? = null,
    val receivedChars: Int = 0
)

private data class AiToolStatus(
    val action: String,
    val target: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    mcpPort: Int? = null,
    versionDir: String? = null,
    chatId: String? = null,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<AiChatBubble>() }
    val contextMessages = remember { mutableStateListOf<OpenaiChatMessage>() }
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var responseJob by remember { mutableStateOf<Job?>(null) }
    var saveJob by remember { mutableStateOf<Job?>(null) }
    var activeRecordId by remember { mutableStateOf(chatId) }
    var activeCreatedAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var effectiveMcpPort by remember { mutableStateOf(mcpPort) }
    var effectiveVersionDir by remember { mutableStateOf(versionDir) }
    var historyDialogOpen by remember { mutableStateOf(false) }
    var historyRecords by remember { mutableStateOf<List<AiChatRecordSummary>>(emptyList()) }
    val aiConfig = remember { AppConfig.load().aiConfig }
    val missingConfig = aiConfig.apiKey.isBlank() || aiConfig.model.isBlank()
    val contextLimitTokens = aiConfig.contextLimitTokens.coerceIn(64_000, 1_000_000)
    val basicPromptResult = remember { loadAiBasicPrompt() }
    val basicPromptLoadError = basicPromptResult.exceptionOrNull()
    val basicPrompt = basicPromptResult.getOrNull().orEmpty()
    val systemPrompt = remember(basicPrompt, effectiveMcpPort, effectiveVersionDir) {
        buildAiSystemPrompt(
            basicPrompt = basicPrompt,
            mcpPort = effectiveMcpPort,
            versionDir = effectiveVersionDir
        )
    }
    var contextWasCompressed by remember { mutableStateOf(false) }
    val measuredContextTokens = messages.asReversed()
        .firstOrNull { it.role == "assistant" && it.promptTokens != null }
        ?.promptTokens ?: 0
    val estimatedContextTokens = estimateContextTokens(systemPrompt, contextMessages)
    val contextUsedTokens = if (contextWasCompressed || measuredContextTokens == 0) {
        estimatedContextTokens
    } else {
        measuredContextTokens
    }

    fun buildRequestMessages(untilIndex: Int): List<OpenaiChatMessage> {
        return listOf(OpenaiChatMessage(role = "system", content = systemPrompt)) +
            contextMessages.take(untilIndex.coerceAtMost(contextMessages.size))
    }

    fun compressContextIfNeeded(): Boolean {
        val triggerTokens = contextLimitTokens * 3 / 5
        if (measuredContextTokens < triggerTokens && estimatedContextTokens < triggerTokens) return false

        var contextOffset = 0
        val rebuiltContext = mutableListOf<OpenaiChatMessage>()
        val keepFullToolAssistantIndexes = messages.indices
            .filter { index ->
                val message = messages[index]
                message.role == "assistant" && message.contextMessageCount > 1 && !message.contextCompressed
            }
            .takeLast(2)
            .toSet()
        var compressed = false
        messages.indices.forEach { index ->
            val message = messages[index]
            val count = message.contextMessageCount.coerceAtLeast(0)
            val slice = contextMessages.drop(contextOffset).take(count)
            contextOffset += count
            if (
                message.role == "assistant" &&
                count > 1 &&
                !message.contextCompressed &&
                index !in keepFullToolAssistantIndexes &&
                message.finishedAtMillis != null
            ) {
                rebuiltContext += message.toCompressedContextMessage()
                messages[index] = message.copy(
                    contextMessageCount = 1,
                    contextCompressed = true
                )
                compressed = true
            } else {
                rebuiltContext += slice
            }
        }
        if (!compressed) return false
        contextMessages.clear()
        contextMessages.addAll(rebuiltContext)
        contextWasCompressed = true
        return true
    }

    fun buildChatTitle(): String {
        return messages.firstOrNull { it.role == "user" }
            ?.content
            ?.trim()
            ?.take(20)
            ?.takeIf(String::isNotBlank)
            ?: "AI聊天 $humanDateTimeNow"
    }

    fun buildCurrentRecord(id: String, now: Long): AiChatRecord {
        return AiChatRecord(
            id = id,
            title = buildChatTitle(),
            createdAt = activeCreatedAt,
            updatedAt = now,
            mcpPort = effectiveMcpPort,
            versionDir = effectiveVersionDir,
            provider = aiConfig.provider,
            baseUrl = aiConfig.baseUrl,
            model = aiConfig.model,
            messages = messages.map { it.toSavedMessage() },
            contextMessages = contextMessages.toList()
        )
    }

    fun ensureActiveRecordId(): String {
        return activeRecordId ?: AiChatHistoryService.newRecordId().also {
            activeRecordId = it
            activeCreatedAt = System.currentTimeMillis()
        }
    }

    fun saveCurrentChat(debounce: Boolean = true) {
        if (messages.isEmpty()) return
        ensureActiveRecordId()
        saveJob?.cancel()
        saveJob = scope.launch {
            if (debounce) delay(800)
            val id = activeRecordId ?: return@launch
            val now = System.currentTimeMillis()
            val record = buildCurrentRecord(id, now)
            withContext(Dispatchers.IO) {
                AiChatHistoryService.saveRecord(record)
            }.onFailure {
                errorMessage = "保存聊天记录失败: ${it.message ?: "未知错误"}"
            }
        }
    }

    fun loadChatRecord(record: AiChatRecord) {
        responseJob?.cancel()
        saveJob?.cancel()
        sending = false
        errorMessage = null
        activeRecordId = record.id
        activeCreatedAt = record.createdAt
        effectiveMcpPort = record.mcpPort
        effectiveVersionDir = record.versionDir
        messages.clear()
        contextMessages.clear()
        val loadedMessages = record.messages.map { it.toBubble() }
        if (record.contextMessages.isEmpty()) {
            messages.addAll(loadedMessages.map { it.copy(contextMessageCount = 1, contextCompressed = true) })
            contextMessages.addAll(record.messages.map { it.toContextMessageForLegacyRecord() })
            contextWasCompressed = true
        } else if (loadedMessages.sumOf { it.contextMessageCount.coerceAtLeast(0) } != record.contextMessages.size) {
            messages.addAll(loadedMessages.map { it.copy(contextMessageCount = 1, contextCompressed = true) })
            contextMessages.addAll(record.messages.map { it.toContextMessageForLegacyRecord() })
            contextWasCompressed = true
        } else {
            messages.addAll(loadedMessages)
            contextMessages.addAll(record.contextMessages)
            contextWasCompressed = false
        }
    }

    fun refreshHistoryRecords() {
        scope.launch {
            withContext(Dispatchers.IO) {
                AiChatHistoryService.listRecords()
            }.onSuccess {
                historyRecords = it
            }.onFailure {
                errorMessage = "读取聊天记录失败: ${it.message ?: "未知错误"}"
            }
        }
    }

    fun startAssistantResponse(assistantIndex: Int, requestMessages: List<OpenaiChatMessage>) {
        sending = true
        messages[assistantIndex] = messages[assistantIndex].copy(
            startedAtMillis = System.currentTimeMillis(),
            finishedAtMillis = null,
            promptTokens = null,
            completionTokens = null,
            billablePromptTokens = null,
            billableCompletionTokens = null,
            contextCompressed = false,
            reasoningContent = "",
            reasoningExpanded = true,
            reasoningFinishedAtMillis = null,
            receivedChars = 0
        )
        responseJob = scope.launch {
            var failed = false
            try {
                OpenaiService.chat(
                    aiBaseUrl = aiConfig.baseUrl,
                    apiKey = aiConfig.apiKey,
                    model = aiConfig.model,
                    messages = requestMessages,
                    versionDir = effectiveVersionDir
                ).collect { event ->
                    val current = messages.getOrNull(assistantIndex) ?: return@collect
                    when (event) {
                        is OpenaiChatEvent.Delta -> {
                            val reasoningFinishedAt = current.reasoningFinishedAtMillis
                                ?: System.currentTimeMillis().takeIf { current.reasoningContent.isNotBlank() }
                            messages[assistantIndex] = current.copy(
                                content = current.content + event.content,
                                reasoningExpanded = current.reasoningExpanded && reasoningFinishedAt == null,
                                reasoningFinishedAtMillis = reasoningFinishedAt,
                                receivedChars = current.receivedChars + event.content.length
                            )
                        }

                        is OpenaiChatEvent.ReasoningDelta -> {
                            messages[assistantIndex] = current.copy(
                                reasoningContent = current.reasoningContent + event.content,
                                reasoningExpanded = true
                            )
                        }

                        is OpenaiChatEvent.ToolAccess -> {
                            messages[assistantIndex] = current.copy(
                                toolStatuses = (current.toolStatuses + AiToolStatus(event.action, event.target)).distinct()
                            )
                        }

                        is OpenaiChatEvent.ToolFailed -> {
                            errorMessage = event.message
                        }

                        is OpenaiChatEvent.Usage -> {
                            messages[assistantIndex] = current.copy(
                                promptTokens = event.promptTokens,
                                completionTokens = event.completionTokens,
                                billablePromptTokens = event.billablePromptTokens,
                                billableCompletionTokens = event.billableCompletionTokens
                            )
                            contextWasCompressed = false
                        }

                        is OpenaiChatEvent.ContextMessages -> {
                            contextMessages += event.messages
                            messages[assistantIndex] = current.copy(
                                contextMessageCount = event.messages.size,
                                contextCompressed = false
                            )
                        }
                    }
                    saveCurrentChat()
                }
            } catch (_: CancellationException) {
            } catch (err: Throwable) {
                failed = true
                errorMessage = err.message ?: "AI聊天失败"
                if (assistantIndex in messages.indices) {
                    messages.removeAt(assistantIndex)
                }
            }
            if (!failed && assistantIndex in messages.indices) {
                messages[assistantIndex] = messages[assistantIndex].copy(
                    reasoningExpanded = false,
                    finishedAtMillis = System.currentTimeMillis()
                )
            }
            sending = false
            responseJob = null
            saveCurrentChat(debounce = false)
        }
    }

    fun stopAssistantResponse() {
        responseJob?.cancel()
    }

    fun regenerateAssistant(assistantIndex: Int) {
        if (sending || assistantIndex !in messages.indices) return
        if (messages.take(assistantIndex).none { it.role == "user" }) return
        val contextIndex = messages.take(assistantIndex).sumOf { it.contextMessageCount }
        while (messages.size > assistantIndex) {
            messages.removeAt(messages.lastIndex)
        }
        while (contextMessages.size > contextIndex) {
            contextMessages.removeAt(contextMessages.lastIndex)
        }
        messages += AiChatBubble("assistant", "", contextMessageCount = 0)
        val newAssistantIndex = messages.lastIndex
        val requestMessages = buildRequestMessages(contextIndex)
        errorMessage = null
        startAssistantResponse(newAssistantIndex, requestMessages)
    }

    fun sendMessage() {
        val userText = input.trim()
        if (userText.isBlank() || sending) return
        input = ""
        errorMessage = null
        compressContextIfNeeded()
        val userBubble = AiChatBubble("user", userText)
        messages += userBubble
        contextMessages += userBubble.toContextMessage()
        messages += AiChatBubble("assistant", "", contextMessageCount = 0)
        val assistantIndex = messages.lastIndex
        saveCurrentChat(debounce = false)
        startAssistantResponse(
            assistantIndex = assistantIndex,
            requestMessages = buildRequestMessages(contextMessages.size)
        )
    }

    LaunchedEffect(
        messages.size,
        messages.lastOrNull()?.content,
        messages.lastOrNull()?.reasoningContent?.length,
        messages.lastOrNull()?.toolStatuses?.size
    ) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size)
        }
    }

    LaunchedEffect(chatId) {
        if (chatId.isNullOrBlank()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            AiChatHistoryService.loadRecord(chatId)
        }.onSuccess {
            loadChatRecord(it)
        }.onFailure {
            errorMessage = "加载聊天记录失败: ${it.message ?: "未知错误"}"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            responseJob?.cancel()
            saveJob?.cancel()
            if (messages.isNotEmpty()) {
                val id = ensureActiveRecordId()
                AiChatHistoryService.saveRecord(buildCurrentRecord(id, System.currentTimeMillis()))
                    .onFailure { it.printStackTrace() }
            }
        }
    }

    if (basicPromptLoadError != null) {
        MainBox {}
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text("AI聊天初始化失败") },
            text = { Text("读取基础提示词失败：${basicPromptLoadError.message ?: "未知错误"}") },
            confirmButton = {
                TextButton(onClick = onBack) {
                    Text("确定")
                }
            }
        )
        return
    }

    if (historyDialogOpen) {
        AiChatHistoryDialog(
            records = historyRecords,
            activeRecordId = activeRecordId,
            onDismiss = { historyDialogOpen = false },
            onLoad = { summary ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        AiChatHistoryService.loadRecord(summary.id)
                    }.onSuccess {
                        loadChatRecord(it)
                        historyDialogOpen = false
                    }.onFailure {
                        errorMessage = "加载聊天记录失败: ${it.message ?: "未知错误"}"
                    }
                }
            },
            onDelete = { summary ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        AiChatHistoryService.deleteRecord(summary.id)
                    }.onSuccess {
                        if (summary.id == activeRecordId) activeRecordId = null
                        refreshHistoryRecords()
                    }.onFailure {
                        errorMessage = "删除聊天记录失败: ${it.message ?: "未知错误"}"
                    }
                }
            }
        )
    }

    MainBox {
        MainColumn {
            TitleRow("AI陪玩 w/ R-MCP", onBack) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiContextUsageProgress(
                        usedTokens = contextUsedTokens,
                        limitTokens = contextLimitTokens
                    )
                    CircleIconButton(
                        icon = "\uF1DA",
                        tooltip = "聊天记录",
                        bgColor = MaterialColor.PURPLE_700.color,
                        showText = false
                    ) {
                        historyDialogOpen = true
                        refreshHistoryRecords()
                    }
                }
            }
            Space8h()
            if (missingConfig) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("请先在设置中配置AI")
                        CircleIconButton(
                            icon = "\uEB51",
                            tooltip = "去设置",
                            bgColor = MaterialColor.PURPLE_700.color
                        ) {
                            onOpenSettings()
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(end = 14.dp),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(messages.size) { index ->
                                AiChatBubbleView(
                                    message = messages[index],
                                    sending = sending,
                                    active = sending && index == messages.lastIndex,
                                    onCopy = { copyToClipboard(messages[index].content) },
                                    onRegenerate = { regenerateAssistant(index) },
                                    onToggleReasoning = {
                                        messages[index] = messages[index].copy(
                                            reasoningExpanded = !messages[index].reasoningExpanded
                                        )
                                    }
                                )
                            }
                            item {
                                Spacer(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(14.dp)
                                .background(MaterialColor.GRAY_100.color)
                        ) {
                            PlatformVerticalScrollbar(
                                listState = listState,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(2.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 30.dp, bottom = 12.dp)
                        ) {
                            CircleIconButton(
                                icon = "\uF103",
                                tooltip = "到底部",
                                size = 42,
                                showText = false,
                                bgColor = MaterialColor.GRAY_200.color,
                                iconColor = MaterialColor.GRAY_900.color,
                                enabled = messages.isNotEmpty()
                            ) {
                                if (messages.isNotEmpty()) {
                                    scope.launch { listState.animateScrollToItem(messages.size) }
                                }
                            }
                        }
                    }
                    errorMessage?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colors.error,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            label = { Text("输入消息") },
                            enabled = !sending,
                            modifier = Modifier
                                .weight(1f)
                                .onPreviewKeyEvent {
                                    if (it.type == KeyEventType.KeyDown && it.key == Key.Enter && !it.isShiftPressed) {
                                        sendMessage()
                                        true
                                    } else {
                                        false
                                    }
                                },
                            maxLines = 4
                        )
                        if (sending) {
                            CircleIconButton(
                                icon = "\uF04D",
                                tooltip = "停止生成",
                                bgColor = MaterialColor.RED_700.color,
                                showText = false
                            ) {
                                stopAssistantResponse()
                            }
                        } else {
                            CircleIconButton(
                                icon = "\uF1D8",
                                tooltip = "发送",
                                bgColor = MaterialColor.GREEN_900.color,
                                enabled = input.isNotBlank(),
                                showText = false
                            ) {
                                sendMessage()
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiChatHistoryDialog(
    records: List<AiChatRecordSummary>,
    activeRecordId: String?,
    onDismiss: () -> Unit,
    onLoad: (AiChatRecordSummary) -> Unit,
    onDelete: (AiChatRecordSummary) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("聊天记录") },
        text = {
            if (records.isEmpty()) {
                Text("暂无聊天记录", color = MaterialColor.GRAY_700.color)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(records) { record ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialColor.GRAY_100.color, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (record.id == activeRecordId) "${record.title}（当前）" else record.title,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialColor.GRAY_900.color
                                )
                                Text(
                                    text = "${record.updatedAt.millisToHumanDateTime} · ${record.model.ifBlank { "未知模型" }} · ${record.messageCount}条消息",
                                    color = MaterialColor.GRAY_700.color
                                )
                            }
                            CircleIconButton(
                                icon = "\uF07C",
                                tooltip = "载入",
                                bgColor = MaterialColor.PURPLE_700.color,
                                showText = false
                            ) {
                                onLoad(record)
                            }
                            CircleIconButton(
                                icon = "\uF1F8",
                                tooltip = "删除",
                                bgColor = MaterialColor.RED_700.color,
                                showText = false
                            ) {
                                onDelete(record)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun AiContextUsageProgress(
    usedTokens: Int,
    limitTokens: Int
) {
    val progress = (usedTokens.toFloat() / limitTokens).coerceIn(0f, 1f)
    val progressColor = when {
        progress >= 0.9f -> MaterialColor.RED_700.color
        progress >= 0.7f -> MaterialColor.ORANGE_700.color
        else -> MaterialColor.GREEN_900.color
    }
    Column(
        modifier = Modifier.width(220.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "上下文${usedTokens}/${limitTokens}",
            color = MaterialColor.GRAY_800.color,
            style = MaterialTheme.typography.body2
        )
        LinearProgressIndicator(
            progress = progress,
            color = progressColor,
            backgroundColor = MaterialColor.GRAY_200.color,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        )
    }
}

@Composable
private fun AiChatBubbleView(
    message: AiChatBubble,
    sending: Boolean,
    active: Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onToggleReasoning: () -> Unit
) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (isUser) MaterialColor.GREEN_100.color else MaterialColor.GRAY_100.color,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(12.dp)
        ) {
            Text(
                text = if (isUser) "我" else "AI",
                fontWeight = FontWeight.Bold,
                color = if (isUser) MaterialColor.GREEN_900.color else MaterialColor.PURPLE_700.color
            )
            if (!isUser && message.toolStatuses.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                message.toolStatuses.forEach { status ->
                    val target = if(!DEBUG && (status.target.contains("localhost") || status.target.contains("127.0.0.1") || status.target.contains("[::1]"))) "R-MCP" else status.target
                    Text(
                        text = "${if (active) "正在${status.action}" else "已${status.action}"}: $target",
                        color = MaterialColor.BLUE_700.color,
                        modifier = Modifier.fillMaxWidth(),
                        softWrap = true
                    )
                }
            }
            if (!isUser && message.reasoningContent.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                AiReasoningCard(
                    message = message,
                    onToggle = onToggleReasoning
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (isUser) {
                Text(
                    text = message.content.ifBlank { "..." },
                    color = Color.Black
                )
            } else {
                AiMarkdownText(message.content.ifBlank { "..." })
            }
            if (!isUser) {
                Spacer(modifier = Modifier.height(8.dp))
                AiChatResponseFooter(
                    message = message,
                    sending = sending,
                    onCopy = onCopy,
                    onRegenerate = onRegenerate
                )
            }
        }
        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
private fun AiReasoningCard(
    message: AiChatBubble,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialColor.BLUE_GRAY_100.color, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "\uF0EB 思考了${String.format("%.1f", message.reasoningSeconds())}秒".asIconText,
                color = MaterialColor.BLUE_GRAY_900.color,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = (if (message.reasoningExpanded) "\uF077" else "\uF078").asIconText,
                color = MaterialColor.BLUE_GRAY_900.color
            )
        }
        if (message.reasoningExpanded) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialColor.BLUE_GRAY_50.color, RoundedCornerShape(6.dp))
                    .padding(10.dp)
            ) {
                AiMarkdownText(message.reasoningContent)
            }
        }
    }
}

@Composable
private fun AiMarkdownText(content: String) {
    Markdown(
        content = content,
        typography = markdownTypography(
            h1 = MaterialTheme3.typography.headlineLarge.withUiFontFamily(),
            h2 = MaterialTheme3.typography.headlineMedium.withUiFontFamily(),
            h3 = MaterialTheme3.typography.headlineSmall.withUiFontFamily(),
            h4 = MaterialTheme3.typography.titleLarge.withUiFontFamily(),
            h5 = MaterialTheme3.typography.titleMedium.withUiFontFamily(),
            h6 = MaterialTheme3.typography.titleSmall.withUiFontFamily(),
            text = MaterialTheme3.typography.bodyLarge.withUiFontFamily(),
            code = MaterialTheme3.typography.bodyMedium.withUiFontFamily(),
            quote = MaterialTheme3.typography.bodyMedium.withUiFontFamily(),
            paragraph = MaterialTheme3.typography.bodyLarge.withUiFontFamily(),
            ordered = MaterialTheme3.typography.bodyLarge.withUiFontFamily(),
            bullet = MaterialTheme3.typography.bodyLarge.withUiFontFamily(),
            list = MaterialTheme3.typography.bodyLarge.withUiFontFamily()
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

private fun TextStyle.withUiFontFamily() = copy(fontFamily = UIFontFamily)

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable
private fun AiChatResponseFooter(
    message: AiChatBubble,
    sending: Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit
) {
    val elapsedSeconds = message.elapsedSeconds()
    val sentTokens = message.billablePromptTokens ?: message.promptTokens
    val receivedTokens = message.billableCompletionTokens ?: message.completionTokens
    val speedText = receivedTokens?.let { tokens ->
        val seconds = elapsedSeconds.takeIf { it > 0.0 } ?: return@let null
        String.format("%.1f", tokens / seconds)
    } ?: "--"
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CircleIconButton(
            icon = "\uF4BB",
            tooltip = "复制",
            size = 24,
            showText = false,
            bgColor = MaterialColor.GRAY_200.color,
            iconColor = MaterialColor.GRAY_900.color,
            enabled = message.content.isNotBlank(),
            onClick = onCopy
        )
        CircleIconButton(
            icon = "\uF021",
            tooltip = "重新生成",
            size = 24,
            showText = false,
            bgColor = MaterialColor.GRAY_200.color,
            iconColor = MaterialColor.GRAY_900.color,
            enabled = !sending,
            onClick = onRegenerate
        )
        Text(
            text = "\uDB81\uDD52 ${sentTokens?.toString() ?: "--"}tokens".asIconText,
            color = MaterialColor.GRAY_700.color
        )
        Text(
            text = "\uDB80\uDDDA ${receivedTokens?.toString() ?: "--"}tokens".asIconText,
            color = MaterialColor.GRAY_700.color
        )
        Text(
            text = "\uDB81\uDCC5 ${speedText}tok/s".asIconText,
            color = MaterialColor.GRAY_700.color
        )
        Text(
            text = "\uDB86\uDED1 ${String.format("%.1f", elapsedSeconds)}s".asIconText,
            color = MaterialColor.GRAY_700.color
        )
    }
}

private fun AiChatBubble.elapsedSeconds(): Double {
    val startedAt = startedAtMillis ?: return 0.0
    val endedAt = finishedAtMillis ?: System.currentTimeMillis()
    return ((endedAt - startedAt).coerceAtLeast(0L) / 1000.0)
}

private fun AiChatBubble.reasoningSeconds(): Double {
    val startedAt = startedAtMillis ?: return 0.0
    val endedAt = reasoningFinishedAtMillis ?: finishedAtMillis ?: System.currentTimeMillis()
    return ((endedAt - startedAt).coerceAtLeast(0L) / 1000.0)
}

private fun loadAiBasicPrompt(): Result<String> = runCatching {
    loadResourceStream("rmcp/basic-prompts.md").bufferedReader().use { it.readText() }
        .trim()
        .takeIf(String::isNotBlank)
        ?: error("rmcp/basic-prompts.md为空")
}

private fun buildAiSystemPrompt(
    basicPrompt: String,
    mcpPort: Int?,
    versionDir: String?
): String = buildString {
    appendLine(basicPrompt.trim())
    appendLine()
    appendLine("Current RDI runtime context:")
    appendLine("- DEBUG=$DEBUG")
    if (mcpPort != null) {
        appendLine("- The current RMCP connection number/port is $mcpPort. Use localhost:$mcpPort for RMCP tool calls and do not ask the user for the port.")
    } else {
        appendLine("- No RMCP port was provided. Ask the user for the connection number before using RMCP.")
    }
    val normalizedVersionDir = versionDir?.trim().takeIf { !it.isNullOrBlank() }
    if (normalizedVersionDir != null) {
        appendLine("- The current modpack versionDir is: $normalizedVersionDir")
        appendLine("- Use local file and Java bytecode tools to inspect mods, configs, scripts, logs, and dependencies inside this versionDir when gameplay analysis needs local pack data.")
    } else {
        appendLine("- No versionDir was provided. Do not assume a local modpack directory.")
    }
}

private fun formatTokenCount(tokens: Int): String = when {
    tokens >= 1_000_000 -> "${tokens / 1_000_000.0}".take(3).trimEnd('.') + "M"
    tokens >= 1_000 -> "${tokens / 1_000.0}".take(5).trimEnd('.') + "K"
    else -> tokens.toString()
}

private fun AiChatBubble.toContextMessage(): OpenaiChatMessage {
    return OpenaiChatMessage(
        role = role,
        content = content
    )
}

private fun AiChatBubble.toCompressedContextMessage(): OpenaiChatMessage {
    val compressedContent = buildString {
        append(content.ifBlank { "已完成一次工具辅助回答。" })
        if (toolStatuses.isNotEmpty()) {
            appendLine()
            appendLine()
            append("旧工具调用记录已压缩：")
            append(toolStatuses.joinToString("；") { "${it.action}${it.target}" })
        }
    }
    return OpenaiChatMessage(
        role = "assistant",
        content = compressedContent
    )
}

private fun AiChatBubble.toSavedMessage(): AiChatSavedMessage {
    return AiChatSavedMessage(
        role = role,
        content = content,
        contextMessageCount = contextMessageCount,
        contextCompressed = contextCompressed,
        reasoningContent = reasoningContent,
        reasoningExpanded = false,
        toolStatuses = toolStatuses.map { AiChatSavedToolStatus(it.action, it.target) },
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        billablePromptTokens = billablePromptTokens,
        billableCompletionTokens = billableCompletionTokens,
        startedAtMillis = startedAtMillis,
        reasoningFinishedAtMillis = reasoningFinishedAtMillis,
        finishedAtMillis = finishedAtMillis,
        receivedChars = receivedChars
    )
}

private fun AiChatSavedMessage.toBubble(): AiChatBubble {
    return AiChatBubble(
        role = role,
        content = content,
        contextMessageCount = contextMessageCount,
        contextCompressed = contextCompressed,
        reasoningContent = reasoningContent,
        reasoningExpanded = reasoningExpanded,
        toolStatuses = toolStatuses.map { AiToolStatus(it.action, it.target) },
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        billablePromptTokens = billablePromptTokens,
        billableCompletionTokens = billableCompletionTokens,
        startedAtMillis = startedAtMillis,
        reasoningFinishedAtMillis = reasoningFinishedAtMillis,
        finishedAtMillis = finishedAtMillis,
        receivedChars = receivedChars
    )
}

private fun AiChatSavedMessage.toContextMessageForLegacyRecord(): OpenaiChatMessage {
    return OpenaiChatMessage(
        role = role,
        content = content
    )
}

private fun estimateContextTokens(systemPrompt: String, messages: List<OpenaiChatMessage>): Int {
    val chars = systemPrompt.length + messages.sumOf { message ->
        message.role.length +
            (message.content?.length ?: 0) +
            (message.reasoningContent?.length ?: 0) +
            (message.toolCallId?.length ?: 0) +
            message.toolCalls.orEmpty().sumOf { toolCall ->
                toolCall.id.length +
                    toolCall.type.length +
                    toolCall.function.name.length +
                    toolCall.function.arguments.length
            }
    }
    return chars / 4 + messages.size * 4
}
