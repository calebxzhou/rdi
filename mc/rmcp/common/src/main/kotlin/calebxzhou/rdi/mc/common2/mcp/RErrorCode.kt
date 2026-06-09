package calebxzhou.rdi.mc.common2.mcp

class McpBadArgsError(detail: String) : McpError(detail)
class McpMethodNotAllowedError : McpError("endpoint method not support read / for correct method")
class McpNotFoundError : McpError("unknown endpoint read / for all api")
class McpInternalError(detail: String = "") : McpError("api internal failed $detail")
class McpBadRequestError(detail: String = "") : McpError("malform request $detail")
class McpNoPlayerError : McpError("the local player is not in a loaded world.")



class McpItemUseFailedError : McpError("Minecraft right-click item-on-block logic refused the action.")
class McpBadBlockStateError :
    McpError("/place state contains an unknown, unsupported, unsafe, or invalid block state property/value.")

class McpBreakFailedError : McpError("Minecraft block breaking logic refused the action.")
class McpMoveTargetBlockedError :
    McpError("/move could not find a nearby safe standable target with empty feet/head space and a solid non-hazard floor.")

class McpBlockError(detail: String) : McpError(detail)
class McpContainerError(detail: String) : McpError(detail)
class McpCraftError(detail: String) : McpError(detail)



class McpBadSlotError : McpError("an inventory slot reference is invalid.")

class McpBadShapeError : McpError("/craft shape or symbol-to-slot mapping is missing or invalid.")

open class McpError(val detail: String, override var cause: Throwable?= null) : RuntimeException(cause) {
}
