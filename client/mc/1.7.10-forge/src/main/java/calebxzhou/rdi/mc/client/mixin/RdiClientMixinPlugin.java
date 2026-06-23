package calebxzhou.rdi.mc.client.mixin;

import net.minecraft.launchwrapper.Launch;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class RdiClientMixinPlugin implements IMixinConfigPlugin {
    private static final String MCLIB_MIXIN_PREFIX = "calebxzhou.rdi.mc.client.mixin.mMCLib";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.startsWith(MCLIB_MIXIN_PREFIX)) {
            return true;
        }
        return classResourceExists(targetClassName);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String s, org.spongepowered.asm.lib.tree.ClassNode classNode, String s1, IMixinInfo iMixinInfo) {

    }

    @Override
    public void postApply(String s, org.spongepowered.asm.lib.tree.ClassNode classNode, String s1, IMixinInfo iMixinInfo) {

    }


    private static boolean classResourceExists(String className) {
        return Launch.classLoader.getResource(className.replace('.', '/') + ".class") != null;
    }
}
