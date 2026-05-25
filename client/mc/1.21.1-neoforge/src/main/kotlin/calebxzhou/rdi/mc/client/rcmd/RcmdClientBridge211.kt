package calebxzhou.rdi.mc.client.rcmd;

import calebxzhou.rdi.mc.common.RDI;
import calebxzhou.rdi.mc.common2.rcmd.client.RcmdClientBridge;
import calebxzhou.rdi.mc.common2.rcmd.client.RcmdLangSnapshot;
import calebxzhou.rdi.mc.common2.rcmd.client.RcmdRecipeSnapshot;
import calebxzhou.rdi.mc.common2.rcmd.client.RcmdRecipeView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RcmdClientBridge211 implements RcmdClientBridge {
    private final Minecraft minecraft;

    public RcmdClientBridge211(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public String name() {
        return minecraft.getUser().getName();
    }

    @Override
    public UUID playerId() {
        return minecraft.player == null ? NO_PLAYER_ID : minecraft.player.getUUID();
    }

    @Override
    public boolean hasPermission(String permission) {
        return true;
    }

    @Override
    public void sendFeedback(String message) {
        sendMessage(message);
    }

    @Override
    public void sendError(String message) {
        sendMessage("[rcmd] " + message);
    }

    @Override
    public Path gameDirectory() {
        return minecraft.gameDirectory.toPath();
    }

    @Override
    public void executeOnMainThread(Runnable task) {
        minecraft.execute(task);
    }

    @Override
    public void setFirmChunkVisible(boolean visible) {
        RDI.SHOW_FIRM_CHUNKS = visible;
    }

    @Override
    public RcmdRecipeSnapshot recipeExportSnapshot() {
        if (minecraft.level == null) {
            return null;
        }
        var registries = minecraft.level.registryAccess();
        var recipes = new ArrayList<RcmdRecipeView>();
        for (var holder : minecraft.level.getRecipeManager().getOrderedRecipes()) {
            var recipe = holder.value();
            var result = recipe.getResultItem(registries);
            recipes.add(RcmdRecipeCodec211.recipeView(
                    holder.id().toString(),
                    recipe,
                    result
            ));
        }
        return new RcmdRecipeSnapshot(List.copyOf(recipes));
    }

    @Override
    public RcmdLangSnapshot langExportSnapshot() {
        var resourceManager = minecraft.getResourceManager();
        var english = ClientLanguage.loadFrom(resourceManager, List.of("en_us"), false).getLanguageData();
        var chinese = ClientLanguage.loadFrom(resourceManager, List.of("zh_cn"), false).getLanguageData();
        return new RcmdLangSnapshot(english, chinese);
    }

    private void sendMessage(String message) {
        var component = Component.literal(message);
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(component, false);
            return;
        }
        minecraft.gui.getChat().addMessage(component);
    }
}
