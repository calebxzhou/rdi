package calebxzhou.rdi.mc.client.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public final class RdiJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("rdi", "jei_recipe_source");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    /*@Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        RJeiRecipeSource.setRuntime(jeiRuntime);
    }

    @Override
    public void onRuntimeUnavailable() {
        RJeiRecipeSource.clearRuntime();
    }*/
}
