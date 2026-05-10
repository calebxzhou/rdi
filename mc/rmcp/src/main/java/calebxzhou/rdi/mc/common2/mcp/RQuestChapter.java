package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;

public record RQuestChapter(
        String id,
        String title,
        String groupId,
        String groupTitle,
        boolean visible,
        boolean started,
        boolean completed,
        int progress,
        int questCount,
        List<Quest> quests
) {
    public record Quest(
            String id,
            String title,
            String subtitle,
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
