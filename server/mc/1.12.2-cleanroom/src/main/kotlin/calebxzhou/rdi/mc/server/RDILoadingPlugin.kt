package calebxzhou.rdi.mc.server

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.MCVersion
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.TransformerExclusions

@IFMLLoadingPlugin.Name("rdi")
@MCVersion("1.12.2")
@TransformerExclusions("calebxzhou.rdi.mc.server")
class RDILoadingPlugin : IFMLLoadingPlugin {
    override fun getASMTransformerClass(): Array<String?>? {
        return null
    }

    override fun getModContainerClass(): String? {
        return null
    }

    override fun getSetupClass(): String? {
        return null
    }

    override fun injectData(data: MutableMap<String?, Any?>?) {
    }

    override fun getAccessTransformerClass(): String? {
        return null
    }
}
