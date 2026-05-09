package calebxzhou.rdi.mc.common2.mcp;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RErrorCode(String id, String info) {
    public static final RErrorCode METHOD_NOT_ALLOWED = code("method_not_allowed", "the endpoint does not support this HTTP method. Read APIs use GET; action APIs use POST.");
    public static final RErrorCode NOT_FOUND = code("not_found", "endpoint path is unknown. Read / for available APIs.");
    public static final RErrorCode INTERNAL_ERROR = code("internal_error", "the API failed internally.");
    public static final RErrorCode BAD_REQUEST = code("bad_request", "request query/body is missing, malformed, or has invalid JSON.");
    public static final RErrorCode BAD_ACTION = code("bad_action", "the server MCP bridge does not support the requested action.");
    public static final RErrorCode ACTION_FAILED = code("action_failed", "Minecraft action execution failed after the request was accepted.");
    public static final RErrorCode PROMPTS_NOT_FOUND = code("prompts_not_found", "the API prompt summary resource was not found.");
    public static final RErrorCode BUILDINGS_NOT_FOUND = code("buildings_not_found", "the building index resource was not found.");
    public static final RErrorCode BAD_BUILDING_ID = code("bad_building_id", "/buildings/{id} included an empty or invalid building id.");
    public static final RErrorCode BAD_BUILDING_LAYER = code("bad_building_layer", "/buildings/{id}?layer=Y included a missing, non-integer, or out-of-range layer index.");
    public static final RErrorCode UNKNOWN_BUILDING = code("unknown_building", "/buildings/{id} was called for a building schematic that does not exist.");
    public static final RErrorCode BAD_BUILDING_SCHEMATIC = code("bad_building_schematic", "the requested building schematic could not be parsed.");
    public static final RErrorCode BAD_MOD_ID = code("bad_mod_id", "/mods id query parameter is present but empty or invalid.");
    public static final RErrorCode NO_MOD = code("no_mod", "no running mod has the requested mod id.");

    public static final RErrorCode MISSING_APIDOC = code("missing_apidoc", "/apidoc/{file} did not include a file segment.");
    public static final RErrorCode BAD_APIDOC = code("bad_apidoc", "/apidoc/{file} included an invalid file segment.");
    public static final RErrorCode UNKNOWN_APIDOC = code("unknown_apidoc", "/apidoc/{file} was called for an API doc file that does not exist.");
    public static final RErrorCode MISSING_ERRCODE = code("missing_errcode", "/errcode/{code} did not include a code segment.");
    public static final RErrorCode BAD_ERRCODE = code("bad_errcode", "/errcode/{code} included an empty or invalid code segment.");
    public static final RErrorCode UNKNOWN_ERRCODE = code("unknown_errcode", "/errcode/{code} was called with a code that is not documented.");

    public static final RErrorCode SCREENSHOT_FAILED = code("screenshot_failed", "the screenshot could not be captured or encoded.");
    public static final RErrorCode SCREENSHOT_TIMEOUT = code("screenshot_timeout", "the screenshot capture did not complete in time.");
    public static final RErrorCode SERVER_MCP_UNAVAILABLE = code("server_mcp_unavailable", "the connected server does not expose the rdi:mcp bridge.");
    public static final RErrorCode SERVER_TIMEOUT = code("server_timeout", "the server did not answer the MCP bridge request in time.");

    public static final RErrorCode NO_PLAYER = code("no_player", "the local player is not in a loaded world.");
    public static final RErrorCode DIM_NOT_LOADED = code("dim_not_loaded", "the requested dimension is not the client's current loaded dimension.");
    public static final RErrorCode BUSY_CONTAINER_OPEN = code("busy_container_open", "another inventory/container screen is open, so inventory action is not safe.");
    public static final RErrorCode CARRIED_ITEM_NOT_EMPTY = code("carried_item_not_empty", "the cursor is holding an item stack, so inventory action is not safe.");
    public static final RErrorCode MISSING_INGREDIENTS = code("missing_ingredients", "the selected source slots do not contain enough ingredients.");
    public static final RErrorCode RESULT_FULL = code("result_full", "the crafted result or remaining container items cannot fit into the requested output slot/player inventory.");
    public static final RErrorCode CRAFT_FAILED = code("craft_failed", "the craft action failed internally.");
    public static final RErrorCode CRAFT_TIMEOUT = code("craft_timeout", "the craft action did not finish in time.");
    public static final RErrorCode TOO_FAR = code("too_far", "the requested target is outside the allowed interaction range.");
    public static final RErrorCode NO_ITEM_HANDLER = code("no_item_handler", "the target block does not expose an item container or machine inventory.");
    public static final RErrorCode TARGET_NOT_AIR = code("target_not_air", "/place target position is already occupied.");
    public static final RErrorCode NO_PLACE_ITEM = code("no_place_item", "the player's main hand item is not a block item that can be placed.");
    public static final RErrorCode NO_PLACE_FACE = code("no_place_face", "/place could not build a valid placement hit face.");
    public static final RErrorCode PLACE_FAILED = code("place_failed", "Minecraft placement logic refused the action.");
    public static final RErrorCode BREAK_FAILED = code("break_failed", "Minecraft block breaking logic refused the action.");
    public static final RErrorCode MOVE_TARGET_BLOCKED = code("move_target_blocked", "/move could not find a nearby safe standable target with empty feet/head space and a solid non-hazard floor.");
    public static final RErrorCode PROTECTED = code("protected", "the server says the player may not interact with the target position.");
    public static final RErrorCode TOO_MANY_BLOCKS = code("too_many_blocks", "a batch or box block request included more than 512 target blocks.");

    public static final RErrorCode MISSING_POS = code("missing_pos", "required pos or x/y/z query parameter is absent or blank.");
    public static final RErrorCode BAD_POS = code("bad_pos", "pos is not dim,x,y,z, dimension is empty, coordinates are not integers, or x/y/z query values are invalid.");
    public static final RErrorCode BAD_POSITIONS = code("bad_positions", "a batch block request body is missing, empty, malformed, or has no positions.");
    public static final RErrorCode BAD_BOX = code("bad_box", "a box block action body is missing, malformed, or does not include both from and to.");
    public static final RErrorCode BAD_BLOCK_IDS = code("bad_block_ids", "/blocks/find ids is missing, empty, has more than 16 entries, includes a malformed ID, or includes an ID that is not a loaded block.");
    public static final RErrorCode MISSING_BLOCK_ID = code("missing_block_id", "neither blockId nor pos was provided for a block-based query.");
    public static final RErrorCode BAD_BLOCK_ID = code("bad_block_id", "blockId is not a valid loaded block ID.");
    public static final RErrorCode MISSING_UUID = code("missing_uuid", "required uuid query parameter is absent or blank.");
    public static final RErrorCode BAD_UUID = code("bad_uuid", "uuid is not a valid UUID.");
    public static final RErrorCode MISSING_IDS = code("missing_ids", "ids is missing or empty.");
    public static final RErrorCode BAD_IDS = code("bad_ids", "ids must be UUID strings and include at most 2048 entries.");
    public static final RErrorCode MISSING_ITEM_ID = code("missing_item_id", "required itemId query parameter is absent or blank.");
    public static final RErrorCode MISSING_CHUNK_X = code("missing_chunk_x", "required chunk x query parameter is absent or blank.");
    public static final RErrorCode MISSING_CHUNK_Z = code("missing_chunk_z", "required chunk z query parameter is absent or blank.");
    public static final RErrorCode MISSING_SECTION_Y = code("missing_section_y", "required section y query parameter is absent or blank.");
    public static final RErrorCode BAD_CHUNK_X = code("bad_chunk_x", "chunk x is not an integer.");
    public static final RErrorCode BAD_CHUNK_Z = code("bad_chunk_z", "chunk z is not an integer.");
    public static final RErrorCode BAD_SECTION_Y = code("bad_section_y", "section y is not an integer.");
    public static final RErrorCode BAD_CHUNK_RADIUS = code("bad_chunk_radius", "chunkRadius is not an integer or is outside 0..4.");
    public static final RErrorCode BAD_SECTION_RADIUS = code("bad_section_radius", "sectionRadius is not an integer or is outside 0..4.");
    public static final RErrorCode BAD_RADIUS = code("bad_radius", "radius is not a number or is outside 0..128.");
    public static final RErrorCode BAD_BLOCKMAP_RADIUS = code("bad_blockmap_radius", "radius for /blockmap/slice or /blockmap/walkable is not an integer or is outside 0..16.");
    public static final RErrorCode BAD_AXIS = code("bad_axis", "axis is not x or z.");
    public static final RErrorCode BAD_TERRAIN_LENGTH = code("bad_terrain_length", "length for /terrain/profile is not an odd integer in 1..33.");
    public static final RErrorCode BAD_VERTICAL_RADIUS = code("bad_vertical_radius", "verticalRadius for /terrain/profile is not an integer in 1..64.");
    public static final RErrorCode BAD_LIMIT = code("bad_limit", "limit is not an integer or is outside the allowed range for that endpoint.");
    public static final RErrorCode BAD_SLOT = code("bad_slot", "an inventory slot reference is invalid.");
    public static final RErrorCode BAD_SIDE = code("bad_side", "container side or placement face is not one of up, down, north, south, west, or east.");
    public static final RErrorCode SAME_SLOT = code("same_slot", "an action requested the same source/target slot, or /craft cannot safely reuse an input slot as the output slot.");
    public static final RErrorCode UNSUPPORTED_MERGE_RISK = code("unsupported_merge_risk", "the swap would merge two compatible stackable item stacks instead of purely swapping them.");
    public static final RErrorCode BAD_COUNT = code("bad_count", "an action count is missing, zero, negative, larger than a source stack where relevant, or /craft.times is outside 1..64.");
    public static final RErrorCode EMPTY_SOURCE = code("empty_source", "an inventory move source slot is empty.");
    public static final RErrorCode INCOMPATIBLE_TARGET = code("incompatible_target", "an inventory move target slot cannot accept the source item.");
    public static final RErrorCode TARGET_FULL = code("target_full", "an inventory move target slot does not have enough remaining stack capacity.");
    public static final RErrorCode BAD_SHAPE = code("bad_shape", "/craft shape or symbol-to-slot mapping is missing or invalid.");
    public static final RErrorCode NO_MATCHING_RECIPE = code("no_matching_recipe", "/craft shape and source items do not match any server crafting recipe.");
    public static final RErrorCode MISSING_TEXT = code("missing_text", "required text query parameter is absent or blank.");
    public static final RErrorCode MISSING_KEY = code("missing_key", "required key query parameter is absent or blank.");
    public static final RErrorCode NO_BLOCK = code("no_block", "player is not looking at a block, or /break target position is air.");
    public static final RErrorCode NO_BLOCK_ENTITY = code("no_block_entity", "no loaded block entity exists at the requested position.");
    public static final RErrorCode CHUNK_NOT_LOADED = code("chunk_not_loaded", "the requested chunk is not loaded by the client/server.");
    public static final RErrorCode SECTION_OUT_OF_RANGE = code("section_out_of_range", "section y is outside the current world's build height.");
    public static final RErrorCode NO_ENTITY = code("no_entity", "no target or loaded entity is available for the request.");
    public static final RErrorCode NOT_ITEM_ENTITY = code("not_item_entity", "the requested entity is not an item entity.");
    public static final RErrorCode MOVING_ITEM_ENTITY = code("moving_item_entity", "the requested item entity is moving, so direct pickup is refused.");
    public static final RErrorCode INVENTORY_FULL = code("inventory_full", "the player's inventory cannot accept the requested item stack.");
    public static final RErrorCode NO_PLAYER_ENTITY = code("no_player_entity", "requested player is not loaded.");
    public static final RErrorCode NO_LANGKEY = code("no_langkey", "language key was not found in English or Chinese language data.");

    public static final Map<String, RErrorCode> MAP = mapOf(List.of(
            METHOD_NOT_ALLOWED, NOT_FOUND, INTERNAL_ERROR, BAD_REQUEST, BAD_ACTION, ACTION_FAILED, PROMPTS_NOT_FOUND, BUILDINGS_NOT_FOUND,
            BAD_BUILDING_ID, BAD_BUILDING_LAYER, UNKNOWN_BUILDING, BAD_BUILDING_SCHEMATIC,
            BAD_MOD_ID, NO_MOD,
            MISSING_APIDOC, BAD_APIDOC, UNKNOWN_APIDOC, MISSING_ERRCODE, BAD_ERRCODE, UNKNOWN_ERRCODE,
            SCREENSHOT_FAILED, SCREENSHOT_TIMEOUT, SERVER_MCP_UNAVAILABLE, SERVER_TIMEOUT,
            NO_PLAYER, DIM_NOT_LOADED, BUSY_CONTAINER_OPEN, CARRIED_ITEM_NOT_EMPTY,
            MISSING_INGREDIENTS, RESULT_FULL, CRAFT_FAILED, CRAFT_TIMEOUT,
            TOO_FAR, NO_ITEM_HANDLER, TARGET_NOT_AIR, NO_PLACE_ITEM, NO_PLACE_FACE, PLACE_FAILED, BREAK_FAILED, MOVE_TARGET_BLOCKED,
            PROTECTED, TOO_MANY_BLOCKS, MISSING_POS, BAD_POS, BAD_POSITIONS, BAD_BOX, BAD_BLOCK_IDS, MISSING_BLOCK_ID,
            BAD_BLOCK_ID, MISSING_UUID, BAD_UUID, MISSING_ITEM_ID, MISSING_CHUNK_X, MISSING_CHUNK_Z,
            MISSING_IDS, BAD_IDS,
            MISSING_SECTION_Y, BAD_CHUNK_X, BAD_CHUNK_Z, BAD_SECTION_Y, BAD_CHUNK_RADIUS, BAD_SECTION_RADIUS,
            BAD_RADIUS, BAD_BLOCKMAP_RADIUS, BAD_AXIS, BAD_TERRAIN_LENGTH, BAD_VERTICAL_RADIUS, BAD_LIMIT, BAD_SLOT, BAD_SIDE, SAME_SLOT, UNSUPPORTED_MERGE_RISK, BAD_COUNT,
            EMPTY_SOURCE, INCOMPATIBLE_TARGET, TARGET_FULL, BAD_SHAPE, NO_MATCHING_RECIPE,
            MISSING_TEXT, MISSING_KEY, NO_BLOCK, NO_BLOCK_ENTITY, CHUNK_NOT_LOADED, SECTION_OUT_OF_RANGE,
            NO_ENTITY, NOT_ITEM_ENTITY, MOVING_ITEM_ENTITY, INVENTORY_FULL, NO_PLAYER_ENTITY, NO_LANGKEY
    ));

    public static RErrorCode get(String id) {
        return MAP.get(id);
    }

    private static RErrorCode code(String id, String info) {
        return new RErrorCode(id, info);
    }

    private static Map<String, RErrorCode> mapOf(List<RErrorCode> codes) {
        var map = new LinkedHashMap<String, RErrorCode>();
        for (var code : codes) {
            if (map.put(code.id(), code) != null) {
                throw new IllegalStateException("duplicate error code: " + code.id());
            }
        }
        return Collections.unmodifiableMap(map);
    }
}
