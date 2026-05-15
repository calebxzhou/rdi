package calebxzhou.rdi.mc.common2.mcp

import java.time.chrono.JapaneseEra.values
import kotlin.jvm.JvmName

class RMcpMethodNotAllowedError : RMcpEndpointException("the endpoint does not support this HTTP method. Read APIs use GET; action APIs use POST.")
class RMcpNotFoundError : RMcpEndpointException("endpoint path is unknown. Read / for available APIs.")
class RMcpInternalError : RMcpEndpointException("the API failed internally.")
class RMcpModClassNotFoundError : RMcpEndpointException("mod version not match or not installed")
class RMcpBadRequestError : RMcpEndpointException("request query/body is missing, malformed, or has invalid JSON.")
class RMcpBadActionError : RMcpEndpointException("the server MCP bridge does not support the requested action.")
class RMcpActionFailedError : RMcpEndpointException("Minecraft action execution failed after the request was accepted.")
class RMcpPromptsNotFoundError : RMcpEndpointException("the API prompt summary resource was not found.")
class RMcpBadModIdError : RMcpEndpointException("/mods id query parameter is present but empty or invalid.")
class RMcpNoModError : RMcpEndpointException("no running mod has the requested mod id.")
class RMcpQuestDataNotLoadedError : RMcpEndpointException("FTB Quests data has not been received from the server yet.")
class RMcpMissingQuestChapterIdError : RMcpEndpointException("/quest/chapter/{id} did not include a chapter id.")
class RMcpBadQuestChapterIdError : RMcpEndpointException("/quest/chapter/{id} included an invalid FTB Quests chapter hex id.")
class RMcpNoQuestChapterError : RMcpEndpointException("no FTB Quests chapter has the requested id.")
class RMcpMissingQuestIdError : RMcpEndpointException("/quest/detail/{id} did not include a quest id.")
class RMcpBadQuestIdError : RMcpEndpointException("/quest/detail/{id} included an invalid FTB Quests quest hex id.")
class RMcpNoQuestError : RMcpEndpointException("no FTB Quests quest has the requested id.")
class RMcpMissingApidocError : RMcpEndpointException("/apidoc/{file} did not include a file segment.")
class RMcpBadApidocError : RMcpEndpointException("/apidoc/{file} included an invalid file segment.")
class RMcpUnknownApidocError : RMcpEndpointException("/apidoc/{file} was called for an API doc file that does not exist.")
class RMcpMissingErrcodeError : RMcpEndpointException("/errcode/{code} did not include a code segment.")
class RMcpBadErrcodeError : RMcpEndpointException("/errcode/{code} included an empty or invalid code segment.")
class RMcpUnknownErrcodeError : RMcpEndpointException("/errcode/{code} was called with a code that is not documented.")
class RMcpScreenshotFailedError : RMcpEndpointException("the screenshot could not be captured or encoded.")
class RMcpScreenshotTimeoutError : RMcpEndpointException("the screenshot capture did not complete in time.")
class RMcpServerMcpUnavailableError : RMcpEndpointException("the connected server does not expose the rdi:mcp bridge.")
class RMcpServerTimeoutError : RMcpEndpointException("the server did not answer the MCP bridge request in time.")
class RMcpNoPlayerError : RMcpEndpointException("the local player is not in a loaded world.")
class RMcpDimNotLoadedError : RMcpEndpointException("the requested data is not available in the current loaded dimension.")
class RMcpBusyContainerOpenError : RMcpEndpointException("another inventory/container screen is open, so inventory action is not safe.")
class RMcpCarriedItemNotEmptyError : RMcpEndpointException("the cursor is holding an item stack, so inventory action is not safe.")
class RMcpMissingIngredientsError : RMcpEndpointException("the player's inventory or selected source slots do not contain enough required items.")
class RMcpResultFullError : RMcpEndpointException("the crafted result or remaining container items cannot fit into the requested output slot/player inventory.")
class RMcpCraftFailedError : RMcpEndpointException("the craft action failed internally.")
class RMcpCraftTimeoutError : RMcpEndpointException("the craft action did not finish in time.")
class RMcpTooFarError : RMcpEndpointException("the requested target is outside the allowed interaction range.")
class RMcpNoItemHandlerError : RMcpEndpointException("the target block does not expose an item container or machine inventory.")
class RMcpTargetNotAirError : RMcpEndpointException("/place target position is already occupied.")
class RMcpNoPlaceItemError : RMcpEndpointException("the selected placement item is not a block item that can be placed.")
class RMcpNoPlaceFaceError : RMcpEndpointException("/place could not build a valid placement hit face.")
class RMcpNoUsableItemError : RMcpEndpointException("the requested item is absent, empty, not in the requested slot, or not usable from the requested hand.")
class RMcpItemUseFailedError : RMcpEndpointException("Minecraft right-click item-on-block logic refused the action.")
class RMcpBadBlockStateError : RMcpEndpointException("/place state contains an unknown, unsupported, unsafe, or invalid block state property/value.")
class RMcpPlaceFailedError : RMcpEndpointException("Minecraft placement logic refused the action.")
class RMcpBreakFailedError : RMcpEndpointException("Minecraft block breaking logic refused the action.")
class RMcpMoveTargetBlockedError : RMcpEndpointException("/move could not find a nearby safe standable target with empty feet/head space and a solid non-hazard floor.")
class RMcpProtectedError : RMcpEndpointException("the server says the player may not interact with the target position.")
class RMcpTooManyBlocksError : RMcpEndpointException("a batch or box block request included more than 512 target blocks.")
class RMcpMissingPosError : RMcpEndpointException("required pos or x/y/z query parameter is absent or blank.")
class RMcpBadPosError : RMcpEndpointException("pos is not x,y,z, coordinates are not integers, or x/y/z query values are invalid.")
class RMcpBadPositionsError : RMcpEndpointException("a batch block request body is missing, empty, malformed, or has no positions.")
class RMcpBadBoxError : RMcpEndpointException("a box block action body is missing, malformed, or does not include the required position fields.")
class RMcpBadBlockIdsError : RMcpEndpointException("/blocks/find ids is missing, empty, has more than 16 entries, includes a malformed ID, or includes an ID that is not a loaded block.")
class RMcpMissingBlockIdError : RMcpEndpointException("neither blockId nor pos was provided for a block-based query.")
class RMcpBadBlockIdError : RMcpEndpointException("blockId is not a valid loaded block ID.")
class RMcpMissingUuidError : RMcpEndpointException("required uuid query parameter is absent or blank.")
class RMcpBadUuidError : RMcpEndpointException("uuid is not a valid UUID.")
class RMcpMissingIdsError : RMcpEndpointException("ids is missing or empty.")
class RMcpBadIdsError : RMcpEndpointException("ids must be UUID strings and include at most 2048 entries.")
class RMcpMissingItemIdError : RMcpEndpointException("required itemId query parameter is absent or blank.")
class RMcpBadItemIdError : RMcpEndpointException("itemId is not a valid loaded item ID.")
class RMcpMissingRecipeRefError : RMcpEndpointException("required recipe ref query parameter is absent or blank.")
class RMcpBadRecipeRefError : RMcpEndpointException("recipe ref is malformed or does not match a recipe for the requested itemId.")
class RMcpMissingChunkXError : RMcpEndpointException("required chunk x query parameter is absent or blank.")
class RMcpMissingChunkZError : RMcpEndpointException("required chunk z query parameter is absent or blank.")
class RMcpMissingSectionYError : RMcpEndpointException("required section y query parameter is absent or blank.")
class RMcpBadChunkXError : RMcpEndpointException("chunk x is not an integer.")
class RMcpBadChunkZError : RMcpEndpointException("chunk z is not an integer.")
class RMcpBadSectionYError : RMcpEndpointException("section y is not an integer.")
class RMcpBadChunkRadiusError : RMcpEndpointException("chunkRadius is not an integer or is outside 0..4.")
class RMcpBadSectionRadiusError : RMcpEndpointException("sectionRadius is not an integer or is outside 0..4.")
class RMcpBadRadiusError : RMcpEndpointException("radius is not a number or is outside 0..128.")
class RMcpBadBlockmapRadiusError : RMcpEndpointException("radius for /blockmap/slice is not an integer or is outside 0..16.")
class RMcpBadLimitError : RMcpEndpointException("limit is not an integer or is outside the allowed range for that endpoint.")
class RMcpBadSlotError : RMcpEndpointException("an inventory slot reference is invalid.")
class RMcpBadSideError : RMcpEndpointException("container side or placement face is not one of up, down, north, south, west, or east.")
class RMcpSameSlotError : RMcpEndpointException("an action requested the same source/target slot, or /craft cannot safely reuse an input slot as the output slot.")
class RMcpUnsupportedMergeRiskError : RMcpEndpointException("the swap would merge two compatible stackable item stacks instead of purely swapping them.")
class RMcpBadCountError : RMcpEndpointException("an action count is missing, zero, negative, larger than a source stack where relevant, or /craft.times is outside 1..64.")
class RMcpEmptySourceError : RMcpEndpointException("an inventory move source slot is empty.")
class RMcpIncompatibleTargetError : RMcpEndpointException("an inventory move target slot cannot accept the source item.")
class RMcpTargetFullError : RMcpEndpointException("an inventory move target slot does not have enough remaining stack capacity.")
class RMcpBadShapeError : RMcpEndpointException("/craft shape or symbol-to-slot mapping is missing or invalid.")
class RMcpNoMatchingRecipeError : RMcpEndpointException("/craft shape and source items do not match any server crafting recipe.")
class RMcpMissingTextError : RMcpEndpointException("required text query parameter is absent or blank.")
class RMcpMissingKeyError : RMcpEndpointException("required key query parameter is absent or blank.")
class RMcpNoBlockError : RMcpEndpointException("player is not looking at a block, or /break target position is air.")
class RMcpNoBlockEntityError : RMcpEndpointException("no loaded block entity exists at the requested position.")
class RMcpNotSignError : RMcpEndpointException("the target block entity is not a sign.")
class RMcpSignWaxedError : RMcpEndpointException("the target sign is waxed and cannot be edited.")
class RMcpBadSignSideError : RMcpEndpointException("sign side is not valid for the endpoint; write accepts front, back, or auto, and read accepts front, back, or both.")
class RMcpBadSignTextError : RMcpEndpointException("sign text is missing, empty, or has more than 4 lines.")
class RMcpChunkNotLoadedError : RMcpEndpointException("the requested chunk is not loaded by the client/server.")
class RMcpSectionOutOfRangeError : RMcpEndpointException("section y is outside the current world's build height.")
class RMcpNoEntityError : RMcpEndpointException("no target or loaded entity is available for the request.")
class RMcpNotItemEntityError : RMcpEndpointException("the requested entity is not an item entity.")
class RMcpMovingItemEntityError : RMcpEndpointException("the requested item entity is moving, so direct pickup is refused.")
class RMcpInventoryFullError : RMcpEndpointException("the player's inventory cannot accept the requested item stack.")
class RMcpNoPlayerEntityError : RMcpEndpointException("requested player is not loaded.")
class RMcpNoLangkeyError : RMcpEndpointException("language key was not found in English or Chinese language data.")

enum class RErrorCode(
    @get:JvmName("id") val id: String,
    @get:JvmName("info") val info: String
) {
    METHOD_NOT_ALLOWED(
        "method_not_allowed",
        "the endpoint does not support this HTTP method. Read APIs use GET; action APIs use POST."
    ),

    NOT_FOUND("not_found", "endpoint path is unknown. Read / for available APIs."),

    INTERNAL_ERROR("internal_error", "the API failed internally."),

    MOD_CLASS_NOT_FOUND("mod_class_not_found", "mod version not match or not installed"),

    BAD_REQUEST("bad_request", "request query/body is missing, malformed, or has invalid JSON."),

    BAD_ACTION("bad_action", "the server MCP bridge does not support the requested action."),

    ACTION_FAILED("action_failed", "Minecraft action execution failed after the request was accepted."),

    PROMPTS_NOT_FOUND("prompts_not_found", "the API prompt summary resource was not found."),

    BAD_MOD_ID("bad_mod_id", "/mods id query parameter is present but empty or invalid."),

    NO_MOD("no_mod", "no running mod has the requested mod id."),

    QUEST_DATA_NOT_LOADED("quest_data_not_loaded", "FTB Quests data has not been received from the server yet."),

    MISSING_QUEST_CHAPTER_ID("missing_quest_chapter_id", "/quest/chapter/{id} did not include a chapter id."),

    BAD_QUEST_CHAPTER_ID("bad_quest_chapter_id", "/quest/chapter/{id} included an invalid FTB Quests chapter hex id."),

    NO_QUEST_CHAPTER("no_quest_chapter", "no FTB Quests chapter has the requested id."),

    MISSING_QUEST_ID("missing_quest_id", "/quest/detail/{id} did not include a quest id."),

    BAD_QUEST_ID("bad_quest_id", "/quest/detail/{id} included an invalid FTB Quests quest hex id."),

    NO_QUEST("no_quest", "no FTB Quests quest has the requested id."),

    MISSING_APIDOC("missing_apidoc", "/apidoc/{file} did not include a file segment."),

    BAD_APIDOC("bad_apidoc", "/apidoc/{file} included an invalid file segment."),

    UNKNOWN_APIDOC("unknown_apidoc", "/apidoc/{file} was called for an API doc file that does not exist."),

    MISSING_ERRCODE("missing_errcode", "/errcode/{code} did not include a code segment."),

    BAD_ERRCODE("bad_errcode", "/errcode/{code} included an empty or invalid code segment."),

    UNKNOWN_ERRCODE("unknown_errcode", "/errcode/{code} was called with a code that is not documented."),

    SCREENSHOT_FAILED("screenshot_failed", "the screenshot could not be captured or encoded."),

    SCREENSHOT_TIMEOUT("screenshot_timeout", "the screenshot capture did not complete in time."),

    SERVER_MCP_UNAVAILABLE("server_mcp_unavailable", "the connected server does not expose the rdi:mcp bridge."),

    SERVER_TIMEOUT("server_timeout", "the server did not answer the MCP bridge request in time."),

    NO_PLAYER("no_player", "the local player is not in a loaded world."),

    DIM_NOT_LOADED("dim_not_loaded", "the requested data is not available in the current loaded dimension."),

    BUSY_CONTAINER_OPEN("busy_container_open", "another inventory/container screen is open, so inventory action is not safe."),

    CARRIED_ITEM_NOT_EMPTY("carried_item_not_empty", "the cursor is holding an item stack, so inventory action is not safe."),

    MISSING_INGREDIENTS(
        "missing_ingredients",
        "the player's inventory or selected source slots do not contain enough required items."
    ),

    RESULT_FULL(
        "result_full",
        "the crafted result or remaining container items cannot fit into the requested output slot/player inventory."
    ),

    CRAFT_FAILED("craft_failed", "the craft action failed internally."),

    CRAFT_TIMEOUT("craft_timeout", "the craft action did not finish in time."),

    TOO_FAR("too_far", "the requested target is outside the allowed interaction range."),

    NO_ITEM_HANDLER("no_item_handler", "the target block does not expose an item container or machine inventory."),

    TARGET_NOT_AIR("target_not_air", "/place target position is already occupied."),

    NO_PLACE_ITEM("no_place_item", "the selected placement item is not a block item that can be placed."),

    NO_PLACE_FACE("no_place_face", "/place could not build a valid placement hit face."),

    NO_USABLE_ITEM(
        "no_usable_item",
        "the requested item is absent, empty, not in the requested slot, or not usable from the requested hand."
    ),

    ITEM_USE_FAILED("item_use_failed", "Minecraft right-click item-on-block logic refused the action."),

    BAD_BLOCK_STATE(
        "bad_block_state",
        "/place state contains an unknown, unsupported, unsafe, or invalid block state property/value."
    ),

    PLACE_FAILED("place_failed", "Minecraft placement logic refused the action."),

    BREAK_FAILED("break_failed", "Minecraft block breaking logic refused the action."),

    MOVE_TARGET_BLOCKED(
        "move_target_blocked",
        "/move could not find a nearby safe standable target with empty feet/head space and a solid non-hazard floor."
    ),

    PROTECTED("protected", "the server says the player may not interact with the target position."),

    TOO_MANY_BLOCKS("too_many_blocks", "a batch or box block request included more than 512 target blocks."),

    MISSING_POS("missing_pos", "required pos or x/y/z query parameter is absent or blank."),

    BAD_POS("bad_pos", "pos is not x,y,z, coordinates are not integers, or x/y/z query values are invalid."),

    BAD_POSITIONS("bad_positions", "a batch block request body is missing, empty, malformed, or has no positions."),

    BAD_BOX(
        "bad_box",
        "a box block action body is missing, malformed, or does not include the required position fields."
    ),

    BAD_BLOCK_IDS(
        "bad_block_ids",
        "/blocks/find ids is missing, empty, has more than 16 entries, includes a malformed ID, or includes an ID that is not a loaded block."
    ),

    MISSING_BLOCK_ID("missing_block_id", "neither blockId nor pos was provided for a block-based query."),

    BAD_BLOCK_ID("bad_block_id", "blockId is not a valid loaded block ID."),

    MISSING_UUID("missing_uuid", "required uuid query parameter is absent or blank."),

    BAD_UUID("bad_uuid", "uuid is not a valid UUID."),

    MISSING_IDS("missing_ids", "ids is missing or empty."),

    BAD_IDS("bad_ids", "ids must be UUID strings and include at most 2048 entries."),

    MISSING_ITEM_ID("missing_item_id", "required itemId query parameter is absent or blank."),

    BAD_ITEM_ID("bad_item_id", "itemId is not a valid loaded item ID."),

    MISSING_RECIPE_REF("missing_recipe_ref", "required recipe ref query parameter is absent or blank."),

    BAD_RECIPE_REF("bad_recipe_ref", "recipe ref is malformed or does not match a recipe for the requested itemId."),

    MISSING_CHUNK_X("missing_chunk_x", "required chunk x query parameter is absent or blank."),

    MISSING_CHUNK_Z("missing_chunk_z", "required chunk z query parameter is absent or blank."),

    MISSING_SECTION_Y("missing_section_y", "required section y query parameter is absent or blank."),

    BAD_CHUNK_X("bad_chunk_x", "chunk x is not an integer."),

    BAD_CHUNK_Z("bad_chunk_z", "chunk z is not an integer."),

    BAD_SECTION_Y("bad_section_y", "section y is not an integer."),

    BAD_CHUNK_RADIUS("bad_chunk_radius", "chunkRadius is not an integer or is outside 0..4."),

    BAD_SECTION_RADIUS("bad_section_radius", "sectionRadius is not an integer or is outside 0..4."),

    BAD_RADIUS("bad_radius", "radius is not a number or is outside 0..128."),

    BAD_BLOCKMAP_RADIUS(
        "bad_blockmap_radius",
        "radius for /blockmap/slice is not an integer or is outside 0..16."
    ),

    BAD_LIMIT("bad_limit", "limit is not an integer or is outside the allowed range for that endpoint."),

    BAD_SLOT("bad_slot", "an inventory slot reference is invalid."),

    BAD_SIDE("bad_side", "container side or placement face is not one of up, down, north, south, west, or east."),

    SAME_SLOT(
        "same_slot",
        "an action requested the same source/target slot, or /craft cannot safely reuse an input slot as the output slot."
    ),

    UNSUPPORTED_MERGE_RISK(
        "unsupported_merge_risk",
        "the swap would merge two compatible stackable item stacks instead of purely swapping them."
    ),

    BAD_COUNT(
        "bad_count",
        "an action count is missing, zero, negative, larger than a source stack where relevant, or /craft.times is outside 1..64."
    ),

    EMPTY_SOURCE("empty_source", "an inventory move source slot is empty."),

    INCOMPATIBLE_TARGET("incompatible_target", "an inventory move target slot cannot accept the source item."),

    TARGET_FULL("target_full", "an inventory move target slot does not have enough remaining stack capacity."),

    BAD_SHAPE("bad_shape", "/craft shape or symbol-to-slot mapping is missing or invalid."),

    NO_MATCHING_RECIPE("no_matching_recipe", "/craft shape and source items do not match any server crafting recipe."),

    MISSING_TEXT("missing_text", "required text query parameter is absent or blank."),

    MISSING_KEY("missing_key", "required key query parameter is absent or blank."),

    NO_BLOCK("no_block", "player is not looking at a block, or /break target position is air."),

    NO_BLOCK_ENTITY("no_block_entity", "no loaded block entity exists at the requested position."),

    NOT_SIGN("not_sign", "the target block entity is not a sign."),

    SIGN_WAXED("sign_waxed", "the target sign is waxed and cannot be edited."),

    BAD_SIGN_SIDE(
        "bad_sign_side",
        "sign side is not valid for the endpoint; write accepts front, back, or auto, and read accepts front, back, or both."
    ),

    BAD_SIGN_TEXT("bad_sign_text", "sign text is missing, empty, or has more than 4 lines."),

    CHUNK_NOT_LOADED("chunk_not_loaded", "the requested chunk is not loaded by the client/server."),

    SECTION_OUT_OF_RANGE("section_out_of_range", "section y is outside the current world's build height."),

    NO_ENTITY("no_entity", "no target or loaded entity is available for the request."),

    NOT_ITEM_ENTITY("not_item_entity", "the requested entity is not an item entity."),

    MOVING_ITEM_ENTITY("moving_item_entity", "the requested item entity is moving, so direct pickup is refused."),

    INVENTORY_FULL("inventory_full", "the player's inventory cannot accept the requested item stack."),

    NO_PLAYER_ENTITY("no_player_entity", "requested player is not loaded."),

    NO_LANGKEY("no_langkey", "language key was not found in English or Chinese language data.");

    companion object {
        val MAP: Map<String, RErrorCode> = values().associateBy(RErrorCode::id)

        @JvmStatic
        fun get(id: String?): RErrorCode? = MAP[id]
    }
}
