package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;

public record RReachableQuestList(
        int total,
        List<Quest> quests
) {
    public record Quest(
            String id,
            String title,
            String subtitle,
            String chapterId,
            String chapterTitle,
            String groupId,
            String groupTitle,
            double x,
            double y,
            boolean visible,
            boolean started,
            boolean completed,
            boolean startable,
            int progress,
            int taskCount,
            int rewardCount,
            int dependencyCount,
            List<String> dependencyIds
    ) {
    }
}
