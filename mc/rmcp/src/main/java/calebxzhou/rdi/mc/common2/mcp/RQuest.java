package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;

/**
 * calebxzhou @ 2026-05-10 11:23
 */
public record RQuest(
        String id,
        String title,
        String subtitle,
        String chapterId,
        String chapterTitle,
        Position position,
        State state,
        Rules rules,
        List<String> description,
        List<Dependency> dependencies,
        List<Task> tasks,
        List<Reward> rewards
) {
    public record Position(double x, double y) {
    }

    public record State(
            boolean visible,
            boolean started,
            boolean completed,
            boolean startable,
            int progress,
            String cannotStartReason
    ) {
    }

    public record Rules(
            String progressionMode,
            String dependencyRequirement,
            int minRequiredDependencies,
            boolean optional,
            boolean repeatable,
            boolean hideDetailsUntilStartable,
            boolean requireSequentialTasks
    ) {
    }

    public record Dependency(
            String id,
            String type,
            String title,
            boolean visible,
            boolean started,
            boolean completed
    ) {
    }

    public record Task(
            String id,
            String type,
            String title,
            boolean completed,
            long progress,
            long maxProgress
    ) {
    }

    public record Reward(
            String id,
            String type,
            String title,
            boolean claimed
    ) {
    }
}
