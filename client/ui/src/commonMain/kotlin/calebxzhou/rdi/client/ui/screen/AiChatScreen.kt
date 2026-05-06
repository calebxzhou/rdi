package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import kotlinx.coroutines.launch

private data class AiChatBubble(
    val role: String,
    val content: String,
    val reasoningContent: String = "",
    val reasoningExpanded: Boolean = true,
    val toolStatuses: List<AiToolStatus> = emptyList(),
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
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
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<AiChatBubble>() }
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val aiConfig = remember { AppConfig.load().aiConfig }
    val missingConfig = aiConfig.apiKey.isBlank() || aiConfig.model.isBlank()
    val contextLimitTokens = aiConfig.contextLimitTokens.coerceIn(64_000, 1_000_000)
    val contextUsedTokens = messages.asReversed()
        .firstOrNull { it.role == "assistant" && it.promptTokens != null }
        ?.promptTokens ?: 0
    val basicPromptResult = remember { loadAiBasicPrompt() }
    val basicPromptLoadError = basicPromptResult.exceptionOrNull()
    val basicPrompt = basicPromptResult.getOrNull().orEmpty()
    val systemPrompt = remember(basicPrompt, mcpPort, versionDir) {
        buildAiSystemPrompt(
            basicPrompt = basicPrompt,
            mcpPort = mcpPort,
            versionDir = versionDir
        )
    }

    fun buildRequestMessages(untilIndex: Int): List<OpenaiChatMessage> {
        return listOf(OpenaiChatMessage(role = "system", content = systemPrompt)) +
            messages
                .take(untilIndex)
                .map { it.toOpenaiMessage() }
    }

    fun startAssistantResponse(assistantIndex: Int, requestMessages: List<OpenaiChatMessage>) {
        sending = true
        messages[assistantIndex] = messages[assistantIndex].copy(
            startedAtMillis = System.currentTimeMillis(),
            finishedAtMillis = null,
            promptTokens = null,
            completionTokens = null,
            reasoningContent = "",
            reasoningExpanded = true,
            reasoningFinishedAtMillis = null,
            receivedChars = 0
        )
        scope.launch {
            var failed = false
            runCatching {
                OpenaiService.chat(
                    aiBaseUrl = aiConfig.baseUrl,
                    apiKey = aiConfig.apiKey,
                    model = aiConfig.model,
                    messages = requestMessages,
                    versionDir = versionDir
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
                                completionTokens = event.completionTokens
                            )
                        }
                    }
                }
            }.onFailure {
                failed = true
                errorMessage = it.message ?: "AI聊天失败"
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
        }
    }

    fun regenerateAssistant(assistantIndex: Int) {
        if (sending || assistantIndex !in messages.indices) return
        if (messages.take(assistantIndex).none { it.role == "user" }) return
        val requestMessages = buildRequestMessages(assistantIndex)
        messages[assistantIndex] = AiChatBubble("assistant", "")
        errorMessage = null
        startAssistantResponse(assistantIndex, requestMessages)
    }

    fun sendMessage() {
        val userText = input.trim()
        if (userText.isBlank() || sending) return
        input = ""
        errorMessage = null
        messages += AiChatBubble("user", userText)
        messages += AiChatBubble("assistant", "")
        val assistantIndex = messages.lastIndex
        startAssistantResponse(
            assistantIndex = assistantIndex,
            requestMessages = buildRequestMessages(assistantIndex)
        )
    }

    LaunchedEffect(
        messages.size,
        messages.lastOrNull()?.content,
        messages.lastOrNull()?.reasoningContent?.length,
        messages.lastOrNull()?.toolStatuses?.size
    ) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
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

    MainBox {
        MainColumn {
            TitleRow("AI陪玩 w/ R-MCP", onBack) {
                AiContextUsageProgress(
                    usedTokens = contextUsedTokens,
                    limitTokens = contextLimitTokens
                )
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
                                    scope.launch { listState.animateScrollToItem(messages.lastIndex) }
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
                        CircleIconButton(
                            icon = "\uF1D8",
                            tooltip = if (sending) "发送中" else "发送",
                            bgColor = MaterialColor.GREEN_900.color,
                            enabled = !sending && input.isNotBlank(),
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
            text = "上下文${formatTokenCount(usedTokens)}/${formatTokenCount(limitTokens)}",
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
                .fillMaxWidth(0.75f)
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
    val speedText = message.completionTokens?.let { tokens ->
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
            text = "\uDB81\uDD52 ${message.promptTokens?.toString() ?: "--"}tokens".asIconText,
            color = MaterialColor.GRAY_700.color
        )
        Text(
            text = "\uDB80\uDDDA ${message.completionTokens?.toString() ?: "--"}tokens".asIconText,
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

private fun AiChatBubble.toOpenaiMessage(): OpenaiChatMessage {
    return OpenaiChatMessage(
        role = role,
        content = content,
        reasoningContent = reasoningContent.takeIf(String::isNotBlank)
    )
}
