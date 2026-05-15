package calebxzhou.rdi.mc.common2.mcp

open class RMcpEndpointException(val code: String?, val detail: String) : RuntimeException(code) {
    constructor(code: RErrorCode) : this(code.id,code.info)
    constructor(ex: RMcpEndpointException) : this(ex.javaClass.simpleName,ex.detail)
    constructor(detail: String) : this(null,detail)

    fun code(): String {
        return code?:""
    }
}
