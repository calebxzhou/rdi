package calebxzhou.rdi.mc.common2.rcmd.client;

import calebxzhou.rdi.mc.rcmd.RcmdSource;

import java.nio.file.Path;

public interface RcmdClientBridge extends RcmdSource {
    Path gameDirectory();

    void executeOnMainThread(Runnable task);

    void setFirmChunkVisible(boolean visible);

    RcmdRecipeSnapshot recipeExportSnapshot();

    RcmdLangSnapshot langExportSnapshot();
}
