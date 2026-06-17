package calebxzhou.rdi.mc.client

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.MCVersion
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.TransformerExclusions

@IFMLLoadingPlugin.Name("rdi")
@MCVersion("1.12.2")
@TransformerExclusions("calebxzhou.rdi.mc.client")
class RDILoadingPlugin : IFMLLoadingPlugin {
    override fun getASMTransformerClass(): Array<String?>? = null

    override fun getModContainerClass(): String? = null

    override fun getSetupClass(): String? = null

    override fun injectData(data: MutableMap<String?, Any?>?) {
    }

    override fun getAccessTransformerClass(): String? = null
}
