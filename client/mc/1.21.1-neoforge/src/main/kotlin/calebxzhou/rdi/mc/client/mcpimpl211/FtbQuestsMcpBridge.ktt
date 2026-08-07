package calebxzhou.rdi.mc.client.mcpimpl211

import calebxzhou.rdi.mc.common2.mcp.McpBadRequestError
import calebxzhou.rdi.mc.common2.mcp.model.Quest
import calebxzhou.rdi.mc.common2.mcp.model.QuestChapter
import dev.ftb.mods.ftbquests.client.ClientQuestFile
import dev.ftb.mods.ftbquests.quest.Chapter
import dev.ftb.mods.ftbquests.quest.QuestObject
import dev.ftb.mods.ftbquests.quest.QuestObjectBase
import dev.ftb.mods.ftbquests.quest.TeamData
import dev.ftb.mods.ftbquests.quest.reward.Reward
import dev.ftb.mods.ftbquests.quest.task.Task
import net.minecraft.nbt.CompoundTag
import java.util.UUID
import kotlin.streams.asSequence

internal object FtbQuestsMcpBridge {
    fun questChapterList(): List<QuestChapter> {
        if (!ClientQuestFile.exists()) {
            throw McpBadRequestError("quest data not loaded")
        }
        val file = ClientQuestFile.INSTANCE
        val data = file.selfTeamData
        return file.allChapters.map { it.questChapter(data) }
    }

    fun questsOfChapter(chapterId: String, playerId: UUID): List<Quest> {
        if (!ClientQuestFile.exists()) {
            throw McpBadRequestError("quest data not loaded")
        }
        val parsedChapterId = QuestObjectBase.parseHexId(chapterId).orElseThrow {
            McpBadRequestError("invalid quest chapter id $chapterId")
        }
        val file = ClientQuestFile.INSTANCE
        val chapter = file.getChapter(parsedChapterId) ?: throw McpBadRequestError("unknown quest chapter $chapterId")
        val data = file.selfTeamData
        return chapter.quests.map { it.questData(data, playerId) }
    }

    private fun Chapter.questChapter(data: TeamData): QuestChapter {
        val chapterQuests = this.quests
        return QuestChapter(
            id = codeString,
            title = title.string,
            groupId = group.codeString,
            groupTitle = group.title.string,
            completed = data.isCompleted(this),
            questCount = chapterQuests.size,
            completedQuestCount = chapterQuests.count { data.isCompleted(it) },
        )
    }

    private fun dev.ftb.mods.ftbquests.quest.Quest.questData(data: TeamData, playerId: UUID): Quest {
        val questChapter = this.chapter
        val group = questChapter.group
        return Quest(
            id = codeString,
            title = title.string,
            subtitle = rawSubtitle,
            chapterId = questChapter.codeString,
            chapterTitle = questChapter.title.string,
            groupId = group.codeString,
            groupTitle = group.title.string,
            state = Quest.State(
                completed = data.isCompleted(this),
            ),
            rules = Quest.Rules(
                dependencyRequirement = dependencyRequirement(),
                optional = isOptional,
                repeatable = canBeRepeated(),
            ),
            description = description.map { it.string },
            dependencies = streamDependencies()
                .asSequence()
                .map { it.questDependency(data) }
                .toList(),
            tasks = tasks.map { it.questTask(data) },
            rewards = rewards.map { it.questReward(data, playerId) },
        )
    }

    private fun QuestObject.questDependency(data: TeamData): Quest.Dependency {
        return Quest.Dependency(
            id = codeString,
            type = objectType.id,
            title = title.string,
            started = data.isStarted(this),
            completed = data.isCompleted(this),
        )
    }

    private fun Task.questTask(data: TeamData): Quest.Task {
        return Quest.Task(
            id = codeString,
            type = type.typeId.toString(),
            title = title.string,
            completed = data.isCompleted(this),
        )
    }

    private fun Reward.questReward(data: TeamData, playerId: UUID): Quest.Reward {
        return Quest.Reward(
            id = codeString,
            type = type.typeId.toString(),
            title = title.string,
            claimed = data.isRewardClaimed(playerId, this),
        )
    }

    private fun dev.ftb.mods.ftbquests.quest.Quest.dependencyRequirement(): String {
        val tag = CompoundTag()
        writeData(tag, holderLookup())
        return tag.getString("dependency_requirement").ifEmpty { "all_completed" }
    }
}
