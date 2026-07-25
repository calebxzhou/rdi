package calebxzhou.rdi.client

import calebxzhou.rdi.common.DEBUG
import org.bson.types.ObjectId

object Const {

    const val MODID = "rdi"
    var USE_MOCK_DATA = System.getProperty("rdi.mockData").toBoolean()
    var NO_UPDATE = System.getProperty("rdi.noUpdate").toBoolean()
    val WINDOW_TRANSPARENT = System.getProperty("rdi.window.transparent")?.toBooleanStrictOrNull() ?: true
    val AI_TEST get() = System.getProperty("rdi.aiTest").toBoolean() || DEBUG
    val SEED = 1145141919810L
    val DEFAULT_MODPACK_ID = ObjectId("abcdefabcdefabcdefabcdef")
    //显示版本
    val VERSION_NUMBER: String = loadVersionNumber()


    private fun loadVersionNumber(): String {
        val manifestVersion = Const::class.java.`package`?.implementationVersion?.takeIf { it.isNotBlank() }
        return manifestVersion
            ?: System.getProperty("rdi.version")
            ?: "dev"
    }



}
