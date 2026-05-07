package calebxzhou.rdi.client.service

import calebxzhou.rdi.client.AiProvider
import calebxzhou.rdi.client.platformAppConfigFile
import calebxzhou.rdi.common.serdesJson
import calebxzhou.rdi.common.service.OpenaiChatMessage
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.random.Random

@Serializable
data class AiChatRecord(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val mcpPort: Int? = null,
    val versionDir: String? = null,
    val provider: AiProvider = AiProvider.OPENAI,
    val baseUrl: String = "",
    val model: String = "",
    val messages: List<AiChatSavedMessage> = emptyList(),
    val contextMessages: List<OpenaiChatMessage> = emptyList()
)

@Serializable
data class AiChatSavedMessage(
    val role: String,
    val content: String,
    val contextMessageCount: Int = 1,
    val contextCompressed: Boolean = false,
    val reasoningContent: String = "",
    val reasoningExpanded: Boolean = false,
    val toolStatuses: List<AiChatSavedToolStatus> = emptyList(),
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val billablePromptTokens: Int? = null,
    val billableCompletionTokens: Int? = null,
    val startedAtMillis: Long? = null,
    val reasoningFinishedAtMillis: Long? = null,
    val finishedAtMillis: Long? = null,
    val receivedChars: Int = 0
)

@Serializable
data class AiChatSavedToolStatus(
    val action: String,
    val target: String
)

data class AiChatRecordSummary(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val model: String,
    val messageCount: Int
)

object AiChatHistoryService {
    private val historyDir: File
        get() {
            val configDir = platformAppConfigFile().absoluteFile.parentFile ?: File(".")
            return configDir.resolve("ai-chats").also { it.mkdirs() }
        }

    fun newRecordId(): String {
        return "${System.currentTimeMillis().toString(36)}-${Random.nextInt(0x100000, 0xFFFFFF).toString(36)}"
    }

    fun listRecords(): Result<List<AiChatRecordSummary>> = runCatching {
        historyDir
            .listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) }
            ?.mapNotNull { file ->
                runCatching { serdesJson.decodeFromString<AiChatRecord>(file.readText()).toSummary() }.getOrNull()
            }
            ?.sortedByDescending(AiChatRecordSummary::updatedAt)
            .orEmpty()
    }

    fun loadRecord(id: String): Result<AiChatRecord> = runCatching {
        serdesJson.decodeFromString(recordFile(id).readText())
    }

    fun saveRecord(record: AiChatRecord): Result<Unit> = runCatching {
        val file = recordFile(record.id)
        val tempFile = file.resolveSibling("${file.name}.tmp")
        tempFile.writeText(serdesJson.encodeToString(record))
        Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    fun deleteRecord(id: String): Result<Unit> = runCatching {
        val file = recordFile(id)
        if (!file.exists()) return@runCatching
        Files.delete(file.toPath())
    }

    private fun recordFile(id: String): File {
        require(id.matches(Regex("[A-Za-z0-9_-]+"))) { "聊天记录id无效" }
        return historyDir.resolve("$id.json")
    }

    private fun AiChatRecord.toSummary() = AiChatRecordSummary(
        id = id,
        title = title,
        updatedAt = updatedAt,
        model = model,
        messageCount = messages.size
    )
}
