package calebxzhou.rdi.mc.client.mcp

import betterquesting.api.api.QuestingAPI
import betterquesting.api.properties.NativeProps
import betterquesting.api.questing.IQuest
import betterquesting.api.questing.IQuestLine
import betterquesting.api2.utils.QuestTranslation
import betterquesting.questing.QuestDatabase
import betterquesting.questing.QuestLineDatabase
import calebxzhou.rdi.mc.common2.mcp.McpBadRequestError
import calebxzhou.rdi.mc.common2.mcp.model.Quest
import calebxzhou.rdi.mc.common2.mcp.model.QuestChapter
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.StatCollector
import java.util.UUID

internal object BetterQuestingMcpBridge1710 {
    private const val GROUP_ID = "betterquesting"
    private const val GROUP_TITLE = "BetterQuesting"

    fun questChapterList(player: EntityPlayer): List<QuestChapter> {
        val playerId = questingUuid(player)
        return QuestLineDatabase.INSTANCE.getOrderedEntries().map { lineEntry ->
            val lineId = lineEntry.key
            val line = lineEntry.value
            val questEntries = lineQuestEntries(line)
            QuestChapter(
                id = lineId.toString(),
                title = QuestTranslation.translateQuestLineName(lineId, line),
                groupId = GROUP_ID,
                groupTitle = GROUP_TITLE,
                completed = questEntries.isNotEmpty() && questEntries.all { it.value.isComplete(playerId) },
                questCount = questEntries.size,
                completedQuestCount = questEntries.count { it.value.isComplete(playerId) },
            )
        }
    }

    fun questsOfChapter(chapterId: String, player: EntityPlayer): List<Quest> {
        val lineId = runCatching { UUID.fromString(chapterId) }
            .getOrElse { throw McpBadRequestError("invalid quest chapter id $chapterId") }
        val line = QuestLineDatabase.INSTANCE[lineId]
            ?: throw McpBadRequestError("unknown quest chapter $chapterId")
        val playerId = questingUuid(player)
        val chapterTitle = QuestTranslation.translateQuestLineName(lineId, line)
        return lineQuestEntries(line).map { questEntry ->
            val questId = questEntry.key
            val quest = questEntry.value
            Quest(
                id = questId.toString(),
                title = QuestTranslation.translateQuestName(questId, quest),
                subtitle = "",
                chapterId = lineId.toString(),
                chapterTitle = chapterTitle,
                groupId = GROUP_ID,
                groupTitle = GROUP_TITLE,
                state = Quest.State(
                    completed = quest.isComplete(playerId),
                ),
                rules = Quest.Rules(
                    dependencyRequirement = quest.getProperty(NativeProps.LOGIC_QUEST).name.lowercase(),
                    optional = false,
                    repeatable = quest.getProperty(NativeProps.REPEAT_TIME) >= 0,
                ),
                description = QuestTranslation.translateQuestDescription(questId, quest)
                    .lines()
                    .map(String::trim)
                    .filter(String::isNotEmpty),
                dependencies = questDependencies(quest, playerId),
                tasks = questTasks(quest, playerId),
                rewards = questRewards(quest, playerId),
            )
        }
    }

    private fun questingUuid(player: EntityPlayer): UUID {
        return QuestingAPI.getQuestingUUID(player)
            ?: throw McpBadRequestError("BetterQuesting player data is not loaded")
    }

    private fun lineQuestEntries(line: IQuestLine): List<Map.Entry<UUID, IQuest>> {
        return line.entries.mapNotNull { entry ->
            val quest = QuestDatabase.INSTANCE[entry.key] ?: return@mapNotNull null
            SimpleEntry(entry.key, quest)
        }
    }

    private fun questDependencies(quest: IQuest, playerId: UUID): List<Quest.Dependency> {
        return quest.requirements.mapNotNull { reqId ->
            val reqQuest = QuestDatabase.INSTANCE[reqId] ?: return@mapNotNull null
            Quest.Dependency(
                id = reqId.toString(),
                type = quest.getRequirementType(reqId).name.lowercase(),
                title = QuestTranslation.translateQuestName(reqId, reqQuest),
                started = reqQuest.isUnlocked(playerId),
                completed = reqQuest.isComplete(playerId),
            )
        }
    }

    private fun questTasks(quest: IQuest, playerId: UUID): List<Quest.Task> {
        return quest.tasks.entries.map { taskEntry ->
            val task = taskEntry.value
            Quest.Task(
                id = taskEntry.id.toString(),
                type = task.factoryID.toString(),
                title = translateKeyOrText(task.unlocalisedName),
                completed = task.isComplete(playerId),
            )
        }
    }

    private fun questRewards(quest: IQuest, playerId: UUID): List<Quest.Reward> {
        val claimed = quest.hasClaimed(playerId)
        return quest.rewards.entries.map { rewardEntry ->
            val reward = rewardEntry.value
            Quest.Reward(
                id = rewardEntry.id.toString(),
                type = reward.factoryID.toString(),
                title = translateKeyOrText(reward.unlocalisedName),
                claimed = claimed,
            )
        }
    }

    private fun translateKeyOrText(text: String): String {
        return if (text.isNotBlank() && StatCollector.canTranslate(text)) {
            StatCollector.translateToLocal(text)
        } else {
            text
        }
    }

    private data class SimpleEntry<K, V>(
        private val k: K,
        private val v: V,
    ) : Map.Entry<K, V> {
        override val key: K get() = k
        override val value: V get() = v
    }
}
