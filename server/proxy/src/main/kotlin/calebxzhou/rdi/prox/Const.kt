package calebxzhou.rdi.prox

object Const {

    //是否为调试模式,本地用
    @JvmStatic
    val DEBUG = System.getProperty("rdi.debug").toBoolean()

    @JvmField
    val SERVER_PORT = System.getProperty("rdi.port")?.toIntOrNull() ?: 65230

    @JvmField
    val BACKEND_HOST = System.getProperty("rdi.backend.host") ?: "127.0.0.1"
}
