package calebxzhou.rdi.mc.client.mcp

import calebxzhou.rdi.mc.common2.mcp.RErrorCode
import calebxzhou.rdi.mc.common2.mcp.RMcpEndpointException
import calebxzhou.rdi.mc.common2.mcp.RQuest
import calebxzhou.rdi.mc.common2.mcp.RQuestChapter
import calebxzhou.rdi.mc.common2.mcp.RQuestChapterList
import calebxzhou.rdi.mc.common2.mcp.RReachableQuestList
import dev.ftb.mods.ftbquests.client.ClientQuestFile
import dev.ftb.mods.ftbquests.quest.Chapter
import dev.ftb.mods.ftbquests.quest.Quest
import dev.ftb.mods.ftbquests.quest.QuestObject
import dev.ftb.mods.ftbquests.quest.QuestObjectBase
import dev.ftb.mods.ftbquests.quest.TeamData
import dev.ftb.mods.ftbquests.quest.reward.Reward
import dev.ftb.mods.ftbquests.quest.task.Task
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import java.util.UUID
import kotlin.streams.asSequence

internal object FtbQuestsMcpBridge {
    fun requireQuestData(){
        if (!ClientQuestFile.exists())
            throw RMcpEndpointException(RErrorCode.QUEST_DATA_NOT_LOADED)
    }
    @JvmStatic
    fun questChapterList(): RQuestChapterList {
        requireQuestData()
        val file = ClientQuestFile.INSTANCE
        val data = file.selfTeamData
        val chapters = file.allChapters
            .mapTo(arrayListOf()) { questChapterData(it, data) }
        val visible = chapters.count { it.visible }
        return RQuestChapterList(chapters.size, visible, chapters)
    }

    private fun questChapterData(chapter: Chapter, data: TeamData): RQuestChapterList.Chapter {
        val quests = chapter.quests
        var visibleQuestCount = 0
        var startableQuestCount = 0
        var completedQuestCount = 0
        for (quest in quests) {
            val visible = quest.isVisible(data)
            val completed = data.isCompleted(quest)
            if (visible) {
                visibleQuestCount++
            }
            if (completed) {
                completedQuestCount++
            }
            if (!completed && visible && data.canStartTasks(quest)) {
                startableQuestCount++
            }
        }
        val group = chapter.group
        return RQuestChapterList.Chapter(
            chapter.codeString,
            chapter.title.string,
            group.codeString,
            group.title.string,
            chapter.isVisible(data),
            data.isStarted(chapter),
            data.isCompleted(chapter),
            data.getRelativeProgress(chapter),
            quests.size,
            visibleQuestCount,
            startableQuestCount,
            completedQuestCount
        )
    }

    @JvmStatic
    fun questChapter(id: String): RQuestChapter {
        requireQuestData()
        val chapterId = QuestObjectBase.parseHexId(id)
            .orElseThrow { RMcpEndpointException(RErrorCode.BAD_QUEST_CHAPTER_ID) }
        val file = ClientQuestFile.INSTANCE
        val chapter = file.getChapter(chapterId) ?: throw RMcpEndpointException(RErrorCode.NO_QUEST_CHAPTER)
        val data = file.selfTeamData
        val quests = chapter.quests
            .mapTo(arrayListOf()) { questBriefData(it, data) }
        val group = chapter.group
        return RQuestChapter(
            chapter.codeString,
            chapter.title.string,
            group.codeString,
            group.title.string,
            chapter.isVisible(data),
            data.isStarted(chapter),
            data.isCompleted(chapter),
            data.getRelativeProgress(chapter),
            quests.size,
            quests
        )
    }

    @JvmStatic
    fun reachableQuests(): RReachableQuestList {
        requireQuestData()
        val file = ClientQuestFile.INSTANCE
        val data = file.selfTeamData
        val quests = arrayListOf<RReachableQuestList.Quest>()
        for (chapter in file.allChapters) {
            val group = chapter.group
            for (quest in chapter.quests) {
                val visible = quest.isVisible(data)
                val completed = data.isCompleted(quest)
                if (completed || !visible || !data.canStartTasks(quest)) {
                    continue
                }
                val dependencyIds = quest.streamDependencies()
                    .asSequence()
                    .mapTo(arrayListOf<String>()) { QuestObjectBase.getCodeString(it) }
                quests.add(
                    RReachableQuestList.Quest(
                        quest.codeString,
                        quest.title.string,
                        quest.rawSubtitle,
                        chapter.codeString,
                        chapter.title.string,
                        group.codeString,
                        group.title.string,
                        quest.x,
                        quest.y,
                        visible,
                        data.isStarted(quest),
                        completed,
                        true,
                        data.getRelativeProgress(quest),
                        quest.tasks.size,
                        quest.rewards.size,
                        dependencyIds.size,
                        dependencyIds
                    )
                )
            }
        }
        return RReachableQuestList(quests.size, quests)
    }

    @JvmStatic
    fun questDetail(id: String): RQuest {
        requireQuestData()
        val questId = QuestObjectBase.parseHexId(id)
            .orElseThrow { RMcpEndpointException(RErrorCode.BAD_QUEST_ID) }
        val file = ClientQuestFile.INSTANCE
        val quest = file.getQuest(questId) ?: throw RMcpEndpointException(RErrorCode.NO_QUEST_CHAPTER)
        val playerId = Minecraft.getInstance().player?.uuid
        return questDetailData(quest, file.selfTeamData, playerId)
    }

    private fun questDetailData(quest: Quest, data: TeamData, playerId: UUID?): RQuest {
        val chapter = quest.chapter
        val group = chapter.group
        val visible = quest.isVisible(data)
        val completed = data.isCompleted(quest)
        val startable = !completed && visible && data.canStartTasks(quest)
        val dependencies = quest.streamDependencies()
            .asSequence()
            .mapTo(arrayListOf()) { questDependencyData(it, data) }
        val tasks = quest.tasks
            .mapTo(arrayListOf()) { questTaskData(it, data) }
        val rewards = quest.rewards
            .mapTo(arrayListOf()) { questRewardData(it, data, playerId) }
        val description = quest.description
            .mapTo(arrayListOf<String>()) { it.string }
        return RQuest(
            quest.codeString,
            quest.title.string,
            quest.rawSubtitle,
            chapter.codeString,
            chapter.title.string,
            group.codeString,
            group.title.string,
            RQuest.Position(quest.x, quest.y),
            RQuest.State(
                visible,
                data.isStarted(quest),
                completed,
                startable,
                data.getRelativeProgress(quest),
                questCannotStartReason(visible, completed, startable)
            ),
            RQuest.Rules(
                quest.progressionMode.id,
                questDependencyRequirement(quest),
                quest.minRequiredDependencies,
                quest.isOptional,
                quest.canBeRepeated(),
                quest.hideDetailsUntilStartable(),
                quest.requireSequentialTasks
            ),
            description,
            dependencies,
            tasks,
            rewards
        )
    }

    private fun questDependencyData(dependency: QuestObject, data: TeamData): RQuest.Dependency {
        return RQuest.Dependency(
            dependency.codeString,
            dependency.objectType.id,
            dependency.title.string,
            dependency.isVisible(data),
            data.isStarted(dependency),
            data.isCompleted(dependency)
        )
    }

    private fun questTaskData(task: Task, data: TeamData): RQuest.Task {
        return RQuest.Task(
            task.codeString,
            task.type.typeId.toString(),
            task.title.string,
            data.isCompleted(task),
            data.getProgress(task),
            task.maxProgress
        )
    }

    private fun questRewardData(reward: Reward, data: TeamData, playerId: UUID?): RQuest.Reward {
        return RQuest.Reward(
            reward.codeString,
            reward.type.typeId.toString(),
            reward.title.string,
            playerId != null && data.isRewardClaimed(playerId, reward)
        )
    }

    private fun questCannotStartReason(visible: Boolean, completed: Boolean, startable: Boolean): String? {
        return when {
            startable -> null
            completed -> "completed"
            !visible -> "not_visible"
            else -> "dependencies_or_repeat_blocked"
        }
    }

    private fun questDependencyRequirement(quest: Quest): String {
        val tag = CompoundTag()
        quest.writeData(tag, quest.holderLookup())
        val dependencyRequirement = tag.getString("dependency_requirement")
        return if (dependencyRequirement.isEmpty()) "all_completed" else dependencyRequirement
    }

    private fun questBriefData(quest: Quest, data: TeamData): RQuestChapter.Quest {
        val visible = quest.isVisible(data)
        val completed = data.isCompleted(quest)
        val dependencyIds = quest.streamDependencies()
            .asSequence()
            .mapTo(arrayListOf<String>()) { QuestObjectBase.getCodeString(it) }
        return RQuestChapter.Quest(
            quest.codeString,
            quest.title.string,
            quest.rawSubtitle,
            quest.x,
            quest.y,
            visible,
            data.isStarted(quest),
            completed,
            !completed && visible && data.canStartTasks(quest),
            data.getRelativeProgress(quest),
            quest.tasks.size,
            quest.rewards.size,
            dependencyIds.size,
            dependencyIds
        )
    }
}
