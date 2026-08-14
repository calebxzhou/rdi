package calebxzhou.rdi.earlydisplay;

import java.util.List;

/** Loader-neutral snapshot of the progress data rendered by the early window. */
public interface RdiProgressSource {
    List<AgeMessage> messages();

    List<ProgressBar> progressBars();

    record AgeMessage(int ageMillis, String text) {}

    record ProgressBar(String label, int steps, float progress) {}
}
