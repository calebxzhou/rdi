package calebxzhou.rdi.mc.common2.mcp

class McpBadArgsError(detail: String) : McpError(detail)
class McpMethodNotAllowedError : McpError("endpoint method not support read / for correct method")
class McpNotFoundError : McpError("unknown endpoint read / for all api")
class McpInternalError(detail: String = "") : McpError("api internal failed $detail")
class McpModClassNotFoundError : McpError("mod version not match or not installed")
class McpBadRequestError(detail: String = "") : McpError("malform request $detail")
class McpActionFailedError : McpError("Minecraft action execution failed after the request was accepted.")
class McpPromptsNotFoundError : McpError("the API prompt summary resource was not found.")
class McpBadModIdError : McpError("/mods id query parameter is present but empty or invalid.")
class McpNoModError : McpError("no running mod has the requested mod id.")
class McpQuestDataNotLoadedError : McpError("FTB Quests data has not been received from the server yet.")
class McpMissingQuestChapterIdError : McpError("/quest/chapter/{id} did not include a chapter id.")
class McpBadQuestChapterIdError : McpError("/quest/chapter/{id} included an invalid FTB Quests chapter hex id.")
class McpNoQuestChapterError : McpError("no FTB Quests chapter has the requested id.")
class McpMissingQuestIdError : McpError("/quest/detail/{id} did not include a quest id.")
class McpBadQuestIdError : McpError("/quest/detail/{id} included an invalid FTB Quests quest hex id.")
class McpNoQuestError : McpError("no FTB Quests quest has the requested id.")
class McpMissingApidocError : McpError("/apidoc/{file} did not include a file segment.")
class McpBadApidocError : McpError("/apidoc/{file} included an invalid file segment.")
class McpUnknownApidocError : McpError("/apidoc/{file} was called for an API doc file that does not exist.")
class McpMissingErrcodeError : McpError("/errcode/{code} did not include a code segment.")
class McpBadErrcodeError : McpError("/errcode/{code} included an empty or invalid code segment.")
class McpUnknownErrcodeError : McpError("/errcode/{code} was called with a code that is not documented.")
class McpScreenshotFailedError : McpError("the screenshot could not be captured or encoded.")
class McpScreenshotTimeoutError : McpError("the screenshot capture did not complete in time.")
class McpServerMcpUnavailableError : McpError("the connected server does not expose the rdi:mcp bridge.")
class McpServerTimeoutError : McpError("the server did not answer the MCP bridge request in time.")
class McpNoPlayerError : McpError("the local player is not in a loaded world.")
class McpDimNotLoadedError : McpError("the requested data is not available in the current loaded dimension.")
class McpBusyContainerOpenError :
    McpError("another inventory/container screen is open, so inventory action is not safe.")

class McpCarriedItemNotEmptyError : McpError("the cursor is holding an item stack, so inventory action is not safe.")

class McpResultFullError :
    McpError("the crafted result or remaining container items cannot fit into the requested output slot/player inventory.")

class McpCraftFailedError : McpError("the craft action failed internally.")
class McpCraftTimeoutError : McpError("the craft action did not finish in time.")
class McpTooFarError : McpError("the requested target is outside the allowed interaction range.")
class McpNoItemHandlerError : McpError("the target block does not expose an item container or machine inventory.")
class McpTargetNotAirError : McpError("/place target position is already occupied.")
class McpNoPlaceItemError : McpError("the selected placement item is not a block item that can be placed.")
class McpNoPlaceFaceError : McpError("/place could not build a valid placement hit face.")
class McpNoUsableItemError :
    McpError("the requested item is absent, empty, not in the requested slot, or not usable from the requested hand.")

class McpItemUseFailedError : McpError("Minecraft right-click item-on-block logic refused the action.")
class McpBadBlockStateError :
    McpError("/place state contains an unknown, unsupported, unsafe, or invalid block state property/value.")

class McpBreakFailedError : McpError("Minecraft block breaking logic refused the action.")
class McpMoveTargetBlockedError :
    McpError("/move could not find a nearby safe standable target with empty feet/head space and a solid non-hazard floor.")

class McpBlockError(detail: String) : McpError(detail)
class McpContainerError(detail: String) : McpError(detail)
class McpMissingPosError : McpError("required pos or x/y/z query parameter is absent or blank.")
class McpBadPosError : McpError("pos is not x,y,z, coordinates are not integers, or x/y/z query values are invalid.")
class McpBadPositionsError : McpError("a batch block request body is missing, empty, malformed, or has no positions.")
class McpBadBoxError :
    McpError("a box block action body is missing, malformed, or does not include the required position fields.")


class McpMissingBlockIdError : McpError("neither blockId nor pos was provided for a block-based query.")
class McpMissingUuidError : McpError("required uuid query parameter is absent or blank.")
class McpBadUuidError : McpError("uuid is not a valid UUID.")
class McpMissingIdsError : McpError("ids is missing or empty.")
class McpBadIdsError : McpError("ids must be UUID strings and include at most 2048 entries.")
class McpMissingItemIdError : McpError("required itemId query parameter is absent or blank.")
class McpBadItemIdError : McpError("itemId is not a valid loaded item ID.")
class McpMissingRecipeRefError : McpError("required recipe ref query parameter is absent or blank.")
class McpBadRecipeRefError : McpError("recipe ref is malformed or does not match a recipe for the requested itemId.")
class McpMissingChunkXError : McpError("required chunk x query parameter is absent or blank.")
class McpMissingChunkZError : McpError("required chunk z query parameter is absent or blank.")
class McpMissingSectionYError : McpError("required section y query parameter is absent or blank.")
class McpBadChunkXError : McpError("chunk x is not an integer.")
class McpBadChunkZError : McpError("chunk z is not an integer.")
class McpBadSectionYError : McpError("section y is not an integer.")
class McpBadChunkRadiusError : McpError("chunkRadius is not an integer or is outside 0..4.")
class McpBadSectionRadiusError : McpError("sectionRadius is not an integer or is outside 0..4.")
class McpBadRadiusError : McpError("radius is not a number or is outside 0..128.")
class McpBadBlockmapRadiusError : McpError("radius for /blockmap/slice is not an integer or is outside 0..16.")
class McpBadLimitError : McpError("limit is not an integer or is outside the allowed range for that endpoint.")
class McpBadSlotError : McpError("an inventory slot reference is invalid.")
class McpBadSideError :
    McpError("container side or placement face is not one of up, down, north, south, west, or east.")

class McpSameSlotError :
    McpError("an action requested the same source/target slot, or /craft cannot safely reuse an input slot as the output slot.")

class McpUnsupportedMergeRiskError :
    McpError("the swap would merge two compatible stackable item stacks instead of purely swapping them.")

class McpBadCountError :
    McpError("an action count is missing, zero, negative, larger than a source stack where relevant, or /craft.times is outside 1..64.")

class McpEmptySourceError : McpError("an inventory move source slot is empty.")
class McpIncompatibleTargetError : McpError("an inventory move target slot cannot accept the source item.")
class McpTargetFullError : McpError("an inventory move target slot does not have enough remaining stack capacity.")
class McpBadShapeError : McpError("/craft shape or symbol-to-slot mapping is missing or invalid.")
class McpNoMatchingRecipeError : McpError("/craft shape and source items do not match any server crafting recipe.")
class McpMissingTextError : McpError("required text query parameter is absent or blank.")
class McpMissingKeyError : McpError("required key query parameter is absent or blank.")
class McpNoBlockError : McpError("player is not looking at a block, or /break target position is air.")
class McpNoBlockEntityError : McpError("no loaded block entity exists at the requested position.")
class McpNotSignError : McpError("the target block entity is not a sign.")
class McpSignWaxedError : McpError("the target sign is waxed and cannot be edited.")
class McpBadSignSideError :
    McpError("sign side is not valid for the endpoint; write accepts front, back, or auto, and read accepts front, back, or both.")

class McpNoEntityError : McpError("no target or loaded entity is available for the request.")
class McpNotItemEntityError : McpError("the requested entity is not an item entity.")
class McpMovingItemEntityError : McpError("the requested item entity is moving, so direct pickup is refused.")
class McpInventoryFullError : McpError("the player's inventory cannot accept the requested item stack.")
class McpNoPlayerEntityError : McpError("requested player is not loaded.")
class McpNoLangkeyError : McpError("language key was not found in English or Chinese language data.")

open class McpError(val code: String?, val detail: String) : RuntimeException(code) {

    constructor(detail: String) : this(null, detail)

    fun code(): String {
        return code ?: ""
    }
}
