package calebxzhou.rdi.mc.client;

import calebxzhou.rdi.mc.common2.mcp.RErrorCode;
import calebxzhou.rdi.mc.common2.mcp.RMcpEndpointException;
import calebxzhou.rdi.mc.common2.mcp.RQuest;
import calebxzhou.rdi.mc.common2.mcp.RQuestChapter;
import calebxzhou.rdi.mc.common2.mcp.RQuestChapterList;
import calebxzhou.rdi.mc.common2.mcp.RReachableQuestList;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObject;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.task.Task;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.UUID;

final class FtbQuestsMcpBridge {
    private FtbQuestsMcpBridge() {
    }

    static RQuestChapterList questChapterList() {
        if (!ClientQuestFile.exists()) {
            return null;
        }
        var file = ClientQuestFile.INSTANCE;
        var data = file.selfTeamData;
        var chapters = file.getAllChapters().stream()
                .map(chapter -> questChapterData(chapter, data))
                .toList();
        var visible = (int) chapters.stream().filter(RQuestChapterList.Chapter::visible).count();
        return new RQuestChapterList(chapters.size(), visible, chapters);
    }

    private static RQuestChapterList.Chapter questChapterData(Chapter chapter, TeamData data) {
        var quests = chapter.getQuests();
        var visibleQuestCount = 0;
        var startableQuestCount = 0;
        var completedQuestCount = 0;
        for (var quest : quests) {
            var visible = quest.isVisible(data);
            var completed = data.isCompleted(quest);
            if (visible) {
                visibleQuestCount++;
            }
            if (completed) {
                completedQuestCount++;
            }
            if (!completed && visible && data.canStartTasks(quest)) {
                startableQuestCount++;
            }
        }
        var group = chapter.getGroup();
        return new RQuestChapterList.Chapter(
                chapter.getCodeString(),
                chapter.getTitle().getString(),
                group.getCodeString(),
                group.getTitle().getString(),
                chapter.isVisible(data),
                data.isStarted(chapter),
                data.isCompleted(chapter),
                data.getRelativeProgress(chapter),
                quests.size(),
                visibleQuestCount,
                startableQuestCount,
                completedQuestCount
        );
    }

    static RQuestChapter questChapter(String id) {
        if (!ClientQuestFile.exists()) {
            throw new RMcpEndpointException(RErrorCode.QUEST_DATA_NOT_LOADED);
        }
        var chapterId = QuestObjectBase.parseHexId(id)
                .orElseThrow(() -> new RMcpEndpointException(RErrorCode.BAD_QUEST_CHAPTER_ID));
        var file = ClientQuestFile.INSTANCE;
        var chapter = file.getChapter(chapterId);
        if (chapter == null) {
            return null;
        }
        var data = file.selfTeamData;
        var quests = chapter.getQuests().stream()
                .map(quest -> questBriefData(quest, data))
                .toList();
        var group = chapter.getGroup();
        return new RQuestChapter(
                chapter.getCodeString(),
                chapter.getTitle().getString(),
                group.getCodeString(),
                group.getTitle().getString(),
                chapter.isVisible(data),
                data.isStarted(chapter),
                data.isCompleted(chapter),
                data.getRelativeProgress(chapter),
                quests.size(),
                quests
        );
    }

    static RReachableQuestList reachableQuests() {
        if (!ClientQuestFile.exists()) {
            return null;
        }
        var file = ClientQuestFile.INSTANCE;
        var data = file.selfTeamData;
        var quests = new ArrayList<RReachableQuestList.Quest>();
        for (var chapter : file.getAllChapters()) {
            var group = chapter.getGroup();
            for (var quest : chapter.getQuests()) {
                var visible = quest.isVisible(data);
                var completed = data.isCompleted(quest);
                if (completed || !visible || !data.canStartTasks(quest)) {
                    continue;
                }
                var dependencyIds = quest.streamDependencies()
                        .map((questObj) -> QuestObjectBase.getCodeString(questObj))
                        .toList();
                quests.add(new RReachableQuestList.Quest(
                        quest.getCodeString(),
                        quest.getTitle().getString(),
                        quest.getRawSubtitle(),
                        chapter.getCodeString(),
                        chapter.getTitle().getString(),
                        group.getCodeString(),
                        group.getTitle().getString(),
                        quest.getX(),
                        quest.getY(),
                        visible,
                        data.isStarted(quest),
                        completed,
                        true,
                        data.getRelativeProgress(quest),
                        quest.getTasks().size(),
                        quest.getRewards().size(),
                        dependencyIds.size(),
                        dependencyIds
                ));
            }
        }
        return new RReachableQuestList(quests.size(), quests);
    }

    static RQuest questDetail(String id) {
        if (!ClientQuestFile.exists()) {
            throw new RMcpEndpointException(RErrorCode.QUEST_DATA_NOT_LOADED);
        }
        var questId = QuestObjectBase.parseHexId(id)
                .orElseThrow(() -> new RMcpEndpointException(RErrorCode.BAD_QUEST_ID));
        var file = ClientQuestFile.INSTANCE;
        var quest = file.getQuest(questId);
        if (quest == null) {
            return null;
        }
        var player = Minecraft.getInstance().player;
        return questDetailData(quest, file.selfTeamData, player == null ? null : player.getUUID());
    }

    private static RQuest questDetailData(Quest quest, TeamData data, UUID playerId) {
        var chapter = quest.getChapter();
        var group = chapter.getGroup();
        var visible = quest.isVisible(data);
        var completed = data.isCompleted(quest);
        var startable = !completed && visible && data.canStartTasks(quest);
        var dependencies = quest.streamDependencies()
                .map(dep -> questDependencyData(dep, data))
                .toList();
        var tasks = quest.getTasks().stream()
                .map(task -> questTaskData(task, data))
                .toList();
        var rewards = quest.getRewards().stream()
                .map(reward -> questRewardData(reward, data, playerId))
                .toList();
        var description = quest.getDescription().stream()
                .map(Component::getString)
                .toList();
        return new RQuest(
                quest.getCodeString(),
                quest.getTitle().getString(),
                quest.getRawSubtitle(),
                chapter.getCodeString(),
                chapter.getTitle().getString(),
                group.getCodeString(),
                group.getTitle().getString(),
                new RQuest.Position(quest.getX(), quest.getY()),
                new RQuest.State(
                        visible,
                        data.isStarted(quest),
                        completed,
                        startable,
                        data.getRelativeProgress(quest),
                        questCannotStartReason(visible, completed, startable)
                ),
                new RQuest.Rules(
                        quest.getProgressionMode().getId(),
                        questDependencyRequirement(quest),
                        quest.getMinRequiredDependencies(),
                        quest.isOptional(),
                        quest.canBeRepeated(),
                        quest.hideDetailsUntilStartable(),
                        quest.getRequireSequentialTasks()
                ),
                description,
                dependencies,
                tasks,
                rewards
        );
    }

    private static RQuest.Dependency questDependencyData(QuestObject dependency, TeamData data) {
        return new RQuest.Dependency(
                dependency.getCodeString(),
                dependency.getObjectType().getId(),
                dependency.getTitle().getString(),
                dependency.isVisible(data),
                data.isStarted(dependency),
                data.isCompleted(dependency)
        );
    }

    private static RQuest.Task questTaskData(Task task, TeamData data) {
        return new RQuest.Task(
                task.getCodeString(),
                task.getType().getTypeId().toString(),
                task.getTitle().getString(),
                data.isCompleted(task),
                data.getProgress(task),
                task.getMaxProgress()
        );
    }

    private static RQuest.Reward questRewardData(Reward reward, TeamData data, UUID playerId) {
        return new RQuest.Reward(
                reward.getCodeString(),
                reward.getType().getTypeId().toString(),
                reward.getTitle().getString(),
                playerId != null && data.isRewardClaimed(playerId, reward)
        );
    }

    private static String questCannotStartReason(boolean visible, boolean completed, boolean startable) {
        if (startable) {
            return null;
        }
        if (completed) {
            return "completed";
        }
        if (!visible) {
            return "not_visible";
        }
        return "dependencies_or_repeat_blocked";
    }

    private static String questDependencyRequirement(Quest quest) {
        var tag = new CompoundTag();
        quest.writeData(tag, quest.holderLookup());
        var dependencyRequirement = tag.getString("dependency_requirement");
        return dependencyRequirement.isEmpty() ? "all_completed" : dependencyRequirement;
    }

    private static RQuestChapter.Quest questBriefData(Quest quest, TeamData data) {
        var visible = quest.isVisible(data);
        var completed = data.isCompleted(quest);
        var dependencyIds = quest.streamDependencies()
                .map((questObj) -> QuestObjectBase.getCodeString(questObj))
                .toList();
        return new RQuestChapter.Quest(
                quest.getCodeString(),
                quest.getTitle().getString(),
                quest.getRawSubtitle(),
                quest.getX(),
                quest.getY(),
                visible,
                data.isStarted(quest),
                completed,
                !completed && visible && data.canStartTasks(quest),
                data.getRelativeProgress(quest),
                quest.getTasks().size(),
                quest.getRewards().size(),
                dependencyIds.size(),
                dependencyIds
        );
    }
}
