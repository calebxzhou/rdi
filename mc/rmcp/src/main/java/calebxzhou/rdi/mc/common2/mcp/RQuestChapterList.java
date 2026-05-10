package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;

public record RQuestChapterList(
        int total,
        int visible,
        List<Chapter> chapters
) {
    public record Chapter(
            String id,
            String title,
            String groupId,
            String groupTitle,
            boolean visible,
            boolean started,
            boolean completed,
            int progress,
            int questCount,
            int visibleQuestCount,
            int startableQuestCount,
            int completedQuestCount
    ) {
    }
}
