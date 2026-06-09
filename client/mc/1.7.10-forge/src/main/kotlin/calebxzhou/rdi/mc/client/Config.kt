package calebxzhou.rdi.mc.client

import net.minecraftforge.common.config.Configuration
import java.io.File

object Config {
    @JvmField
    var greeting: String? = "Hello World"

    @JvmStatic
    fun synchronizeConfiguration(configFile: File) {
        val configuration = Configuration(configFile)

        greeting = configuration.getString("greeting", Configuration.CATEGORY_GENERAL, greeting, "How shall I greet?")

        if (configuration.hasChanged()) {
            configuration.save()
        }
    }
}
