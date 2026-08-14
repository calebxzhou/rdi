package calebxzhou.rdi.earlydisplay;

import java.util.List;

/**
 * The small loader-specific seam used by the shared early-display renderer.
 *
 * <p>This interface deliberately contains no Forge or NeoForge types.  The
 * service-layer adapters provide the values from the loader that discovered
 * them, while the window, video decoder, and compositor remain usable from
 * either loader.</p>
 */
public interface RdiDisplayPlatform {
    Settings parse(String[] arguments);

    RdiProgressSource progressSource();

    void reportLoaderMessage(String message);

    String loaderDisplayName();

    void updateProgress(String message);

    String loaderModuleName();

    String fallbackLoadingOverlayClassName();

    default void crash(String message) {
        throw new IllegalStateException(message);
    }

    record Settings(
            String minecraftVersion,
            String loaderVersion,
            int windowWidth,
            int windowHeight,
            int framebufferScale,
            boolean maximized,
            boolean dark,
            List<String> skippedGlVersions) {
        public Settings {
            skippedGlVersions = List.copyOf(skippedGlVersions);
        }
    }
}
