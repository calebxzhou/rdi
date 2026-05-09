# RDI Minecraft Client MCP API Prompt
Use these local HTTP APIs to read live data from the running Minecraft client. 
Prefer the smallest API that answers the question. Do not guess game state when an API can read it directly.

Read APIs use `GET`. Action APIs use `POST`. All responses are JSON except `/screenshot`, which returns raw PNG bytes on success:

```json
{
  "code": "ok",
  "data": {}
}
```
- Summary served by `GET /`
- Building index served by `GET /buildings`
- Endpoint docs served by `GET /apidoc/{file}`. The endpoint path has no leading `/` and `/` is replaced with `$`.

If `code` is not `ok`, `data` is `null`. Use the HTTP status and `code` to decide whether to retry, ask for a different input, or use another API.

## Decision Guide

- Need to know whether the API is alive or whether a world is loaded: call `/test`.
- Need to know which mods are currently loaded: call `/mods`; add `id=...` only for detailed metadata.
- Need to know available building templates and their IDs: call `/buildings`.
- Need to understand one building schematic by ID: call `/buildings/{id}`.
- User asks you to build a house, base, shelter, tower, farm, bridge, room, platform, or any structure: call `/buildings` first before choosing actions.
- Need the current visual game frame: call `/screenshot`.
- Need the exact meaning of an error code: call `/errcode/{code}`.
- Need the current player's exact location or current dimension: call `/pos`.
- Need a compact live context snapshot before planning an action: call `/situation`.
- Need the player's full inventory, hotbar, armor, and offhand: call `/inventory`.
- Need to change the currently selected hotbar slot or held item: call `POST /hotbar/select`.
- Need to swap two slots inside the player's inventory: call `POST /inventory/swap`.
- Need to move a specific item count between inventory slots: call `POST /inventory/move`.
- Need to inspect the currently open inventory/container/machine menu: call `/menu`.
- Need to throw an item stack or item count out of the currently open menu: call `POST /menu/drop` after `/menu`.
- Need to craft from chosen inventory slots: call `POST /craft`; it does not need a crafting table, open GUI, or recipe book state.
- Need the current player's main hand item stack: call `/mainhand`.
- Need recipes that produce a known item ID: call `/recipe?itemId=...`.
- Need a chunk overview before inspecting terrain/building layout: call `/chunk?x=...&z=...`.
- Need an LLM-readable 16x16x16 section layout: call `/section?x=...&y=...&z=...`.
- Need a horizontal top-down block map at one Y level: call `/blockmap/slice`; pass `x/y/z` only for a non-player center.
- Need a movement-friendly top-down map before `/move`: call `/blockmap/walkable`; use only `cells[].pos` entries with `symbol="."` as normal movement targets.
- Need to understand vertical terrain relationships, slopes, cliffs, pits, cave mouths, bridges, or stairs along a line: call `/terrain/profile`.
- Need nearby resources, fluids, containers, crops, wood, ores, or block entities: call `/nearby-resources`; add `pos=...` only for a known non-player center.
- Need exact nearby positions for a known block ID: call `POST /blocks/find`; use `scanMode:"chunk"` for mining or whole-chunk vertical scans.
- Need the block the player is currently looking at: call `/staring-block`.
- Need exact block states for multiple known positions: call `POST /blockstate/batch` first.
- Need exact block state for only one known position: call `/blockstate`.
- Need detailed block entity data, inventory, NBT, or runtime fields at a known position: call `/blockentity`.
- Need to read slots from a container or machine block: call `/container?pos=...`.
- Need to put items from the current player's inventory into a known container or machine block: call `POST /container/put`.
- Need to put multiple inventory slots into containers or machines: call `POST /container/put/batch`.
- Need to take items from a known container or machine block into the current player's inventory: call `POST /container/take`.
- Need to take multiple container slots into the current player's inventory: call `POST /container/take/batch`.
- Need to move items between two container or machine blocks: call `POST /container/move`.
- Need to move multiple slots between container or machine blocks: call `POST /container/move/batch`.
- Need to place the current main hand block item at a known air position: call `POST /place?x=...&y=...&z=...`; add `face=up/down/north/south/west/east` for direction-sensitive blocks.
- Need to break a known block using the current main hand item: call `POST /break?x=...&y=...&z=...`; the player does not need to face the block when it is within 32 blocks.
- Need to move the player to a nearby position in the current dimension: prefer a safe feet position from `/blockmap/walkable`, then call `POST /move` with JSON `{"x":...,"y":...,"z":...}` or query `?x=...&y=...&z=...`; `/move` searches within 4 blocks of that point for a safe standable target and returns an error if none exists.
- Need to recover after confirming the current player is dead: call `POST /respawn`, then re-read `/situation` or `/player` before continuing.
- Need to place or break several sparse positions: call `POST /place/batch` or `POST /break/batch`.
- Need to place or break every block inside a small cuboid: call `POST /place/box` or `POST /break/box`.
- Need to know which tool can harvest a block and what it drops with different tools: call `/harvest-tool?blockId=...` or `/harvest-tool?pos=...`.
- Need nearby monsters or animals: call `/nearby-entities`; add `pos=...` only for a known non-player center.
- Need nearby dropped items to pick up: call `POST /entity/pickup-item?radius=64&limit=256`; use `ids` only when selecting specific dropped item entities.
- Need the entity the player is currently looking at: call `/staring-entity`, then call `/entity?uuid=...` only if details are needed.
- Need details for a known entity UUID: call `/entity?uuid=...`.
- Need player info: call `/player`; add `detail=true` only when inventory, NBT, profile, or full entity details are needed.
- Need a translation text from a known language key: call `/langkey?key=...`.
- Need to discover possible language keys from English or Chinese text: call `/langkey-search?text=...`.

## Common Formats

Position query format:

```text
pos=dimension,x,y,z
```

Example:

```text
pos=minecraft:overworld,10,64,-20
```

Dimension values are resource IDs such as `minecraft:overworld`, `minecraft:the_nether`, or `minecraft:the_end`. Position based APIs only read the client currently loaded dimension. If the requested dimension is not the current client dimension, expect `code=dim_not_loaded`.

`/blockstate` and `POST /blockstate/batch` are simpler: they always read the current loaded dimension, so pass only `x/y/z` coordinates and do not include `dim`.

Block state strings are normalized as:

```text
namespace:block_id[property=value,...]
```

Example: `minecraft:oak_log[axis=y]`.

Chunk APIs use chunk coordinates, not block coordinates. Section APIs use world section Y, not array index:

```text
chunkX = blockX >> 4
chunkZ = blockZ >> 4
sectionY = blockY >> 4
```

In a normal 1.21 overworld, section Y can be negative, such as `-4` for blocks `-64..-49`.


## LLM Usage Rules

- Always check `code`; only trust `data` when `code` is `ok`.
- `/screenshot` is the only success response that is not JSON; treat it as an image.
- When the player asks you to build something, read `GET /buildings` first. Use bundled building templates when one matches the request instead of inventing a structure from scratch.
- For a template build, first call `GET /buildings/{id}` without `layer` to read size, palette, material IDs, and valid layer range before placing blocks.
- `GET /buildings/{id}?layer=Y` returns only that layer map, without repeating summary or palette. Keep the palette from the summary response.
- Do not request every building layer at once. Call `GET /buildings/{id}?layer=Y` only for the next single layer you are about to build, and read that layer carefully before placing blocks.
- Build templates from low y to high y. For each layer, use the stored palette symbols to choose block items, skip `.` air cells, and use `POST /place/batch` or `POST /place/box` only after checking target cells are air.
- If the requested structure does not exist in `/buildings`, explain the available IDs from the index and ask the player whether to use the closest template or build a custom structure.
- Call `/situation` first when deciding the next action from current game state.
- Call `/inventory` when exact item stacks or slots are needed; do not use `/player?detail=true` only for inventory.
- Use `POST /hotbar/select` to change the held hotbar slot. The slot must be `0..8`. If the desired item is in main inventory, move or swap it into hotbar first.
- Use `POST /inventory/swap` only after checking `/inventory`; use `dryRun=true` when the slot choice is uncertain.
- Use `POST /inventory/move` instead of `/inventory/swap` when only part of a stack should move or when merging into an existing compatible stack is intended.
- Use `/menu` for currently open GUI slots. `/menu` slot numbers are menu slot indexes and are not the same as `/inventory` aliases.
- Use `POST /menu/drop` only with a slot returned by `/menu`; use `dryRun=true` before throwing valuable items.
- `POST /craft` runs server-side from selected inventory slots; it does not require a nearby crafting table, an open menu, or recipe book state.
- To craft an item, call `/recipe?itemId=...` and `/inventory`, choose source inventory slots, build a `shape`, then call `POST /craft` with `dryRun=true` before the real action when the slot plan is uncertain.
- Use `POST /blockstate/batch` before `/place/batch`, `/break/batch`, `/place/box`, or `/break/box` when target cells are not already known.
- Use `/blockstate` before single `/place` or `/break` only when checking exactly one target cell; `/place` only accepts air targets and can place ordinary blocks floating when Minecraft rules allow it; `/break` only accepts non-air targets.
- `/break`, `/break/batch`, and `/break/box` are server-side remote break actions within 32 blocks. Do not move or rotate the player just to face the block. If a break action returns `too_far`, then use `/move` to get closer and retry.
- Unless the player explicitly asks for it, do not break any player container, storage block, chest, barrel, shulker box, machine inventory, or modded container. If breaking such a block is necessary, ask the player for permission before calling `/break`, `/break/batch`, or `/break/box`.
- After successful `/break`, `/break/batch`, or `/break/box`, pick up harvested drops by default unless the user explicitly says not to. Use `POST /entity/pickup-item?radius=64&limit=256`.
- Prefer `/blockmap/walkable` positions for `/move`; guessed `x/y/z`, `/pos`, room centers, and structure centers are only search centers and may resolve to a nearby different safe block or fail.
- Use `/move` only for server-side movement within 128 blocks. The API searches within 4 blocks of the requested point for empty/non-colliding feet and head spaces, a solid floor, and no obvious hazard. It returns `move_target_blocked` if no safe standable target exists.
- After successful `/move`, the player receives `Slow Falling` for 3 seconds. Trust `data.to` as the actual moved position, even when it differs from requested `x/y/z`.
- If `/move` returns `data.moved=false` or `data.to` has the same `dim/x/y/z` as `data.from`, immediately call `GET /player`; if `data.health<=0`, the player is dead. Do not continue the original task until `POST /respawn` succeeds and fresh `/situation` or `/player` data is read.
- Use `/blockmap/walkable` before `/move`; its `cells[].pos` values are exact feet positions. Prefer entries with `symbol="."`; avoid `#`, `_`, `~`, and `!` unless the user explicitly wants that risk.
- If a movement target is manually constructed, verify the target feet position, head position, and floor with `POST /blockstate/batch` before `/move` when precision matters; feet and head should be air, and floor should not be air.
- Use `/terrain/profile` before vertical-sensitive movement, digging, bridge building, stair planning, cliff handling, or cave entrance reasoning. Prefer it over `/section` when a single X or Z line is enough.
- Use `/container` before `/container/move`; never infer container slot numbers from `/blockentity` SNBT when an item handler view is available.
- Use `POST /container/move/batch` instead of repeated `/container/move` when moving multiple known container slots. Inspect every `results[].code`; top-level `code=ok` only means the batch request was accepted. Use `dryRun=true` before reorganizing important containers.
- Use `POST /container/put` when the source item is in the current player's inventory and the destination is a known block position. It does not require opening the container GUI. Check `/inventory` first for `fromInventorySlot`; check `/container` first when choosing a specific `to.slot`.
- Use `POST /container/take` when the source item is in a known block container and the destination is the current player's inventory. It does not require opening the container GUI. Check `/container` first for `from.slot`; use `toInventorySlot=null` unless an exact hotbar/main inventory slot is required.
- Use `POST /container/put/batch` or `POST /container/take/batch` instead of repeated single operations when moving multiple known inventory/container slots. Inspect every `results[].code`; top-level `code=ok` only means the batch request was accepted. Use `dryRun=true` before moving valuable items.
- Prefer brief APIs first, then detail APIs only when the brief result is insufficient.
- Use `/mods` without `id` when checking loaded mods; use `/mods?id=...` only after choosing one mod ID.
- For terrain or build understanding, call `/chunk` first, then call `/section` only for sections that matter.
- Prefer `/blockmap/slice` over `/section` when the question is horizontal layout at one Y level.
- Prefer `/terrain/profile` over `/section` when the question is about height changes along one direction.
- Treat `/section` as a visual semantic map, not as exact block data. Use `POST /blockstate/batch` for exact cells unless there is only one cell.
- Use `POST /blocks/find` when the target block ID is already known. Use `scanMode:"nearby_sections"` for nearby surface/structure targets, and `scanMode:"chunk"` for mining because it scans every vertical section in the selected loaded chunks. Start mining scans with `chunkRadius=0` or `1`; avoid defaulting to `chunkRadius=4`.
- For ore searches, include both normal and deepslate IDs when relevant, such as `minecraft:diamond_ore` and `minecraft:deepslate_diamond_ore`.
- Use `/nearby-resources` first when the target is semantic, such as ores, wood, containers, fluids, or crops.
- For nearby resource discovery around the player, call `/nearby-resources` without `pos`; use `pos` only when the center is not the player.
- For nearby monsters or animals around the player, call `/nearby-entities` without `pos`; use `/entity` only after choosing a specific UUID.
- For dropped items, call `POST /entity/pickup-item?radius=64&limit=256` directly. Call `/nearby-entities?category=item` first only when you need to inspect item IDs/counts before selecting specific `ids`.
- Prefer `POST /blockstate/batch` over repeated `/blockstate` calls whenever there are 2 or more known positions.
- Prefer `/blockstate` over `/blockentity` when only the block ID/state is needed.
- Prefer `/harvest-tool?pos=...` over `/harvest-tool?blockId=...` when the exact block state or block entity may affect drops.
- Prefer `/staring-entity` before `/entity` when the user refers to "the entity I am looking at".
- Prefer omitting `pos` for `/nearby-resources` and `/nearby-entities` when the center should be the current player; call `/pos` only when another API needs explicit coordinates.
- Do not call position based APIs for unloaded dimensions; the client can only inspect its current loaded dimension.
- Use `langkey-search` to translate user-visible names into language keys, then `langkey` for exact display text if needed.

## Endpoint Docs
At runtime, fetch one detailed doc with `GET /apidoc/{file}`.
File names are endpoint paths without the leading slash, with `/` replaced by `$`.
- `GET /prompts`: `GET /apidoc/prompts.md`
- `GET /buildings`: `GET /apidoc/buildings.md`
- `GET /buildings/{id}`: `GET /apidoc/buildings${id}.md`
- `GET /apidoc/{file}`: `GET /apidoc/apidoc${file}.md`
- `GET /errcode/{code}`: `GET /apidoc/errcode${code}.md`
- `GET /mods`: `GET /apidoc/mods.md`

- `GET /test`: `GET /apidoc/test.md`
- `GET /screenshot`: `GET /apidoc/screenshot.md`
- `GET /pos`: `GET /apidoc/pos.md`
- `GET /situation?entityRadius=32&resourceChunkRadius=1&resourceSectionRadius=1`: `GET /apidoc/situation.md`
- `GET /inventory`: `GET /apidoc/inventory.md`
- `POST /hotbar/select`: `GET /apidoc/hotbar$select.md`
- `POST /inventory/swap`: `GET /apidoc/inventory$swap.md`
- `POST /inventory/move`: `GET /apidoc/inventory$move.md`
- `GET /menu`: `GET /apidoc/menu.md`
- `POST /menu/drop`: `GET /apidoc/menu$drop.md`
- `POST /move`: `GET /apidoc/move.md`
- `POST /respawn`: `GET /apidoc/respawn.md`
- `POST /craft`: `GET /apidoc/craft.md`
- `GET /mainhand`: `GET /apidoc/mainhand.md`
- `GET /recipe?itemId=namespace:path`: `GET /apidoc/recipe.md`
- `GET /chunk?x=chunkX&z=chunkZ`: `GET /apidoc/chunk.md`
- `GET /section?x=chunkX&y=sectionY&z=chunkZ`: `GET /apidoc/section.md`
- `GET /blockmap/slice`: `GET /apidoc/blockmap$slice.md`
- `GET /blockmap/walkable`: `GET /apidoc/blockmap$walkable.md`
- `GET /terrain/profile`: `GET /apidoc/terrain$profile.md`
- `POST /blocks/find`: `GET /apidoc/blocks$find.md`
- `GET /nearby-resources?chunkRadius=2&sectionRadius=1`: `GET /apidoc/nearby-resources.md`
- `GET /staring-block?fluid=true`: `GET /apidoc/staring-block.md`
- `GET /blockstate?x=10&y=64&z=-20`: `GET /apidoc/blockstate.md`
- `POST /blockstate/batch`: `GET /apidoc/blockstate$batch.md`
- `GET /blockentity?pos=dim,x,y,z`: `GET /apidoc/blockentity.md`
- `GET /container?pos=dim,x,y,z&side=north`: `GET /apidoc/container.md`
- `POST /container/put`: `GET /apidoc/container$put.md`
- `POST /container/put/batch`: `GET /apidoc/container$put$batch.md`
- `POST /container/take`: `GET /apidoc/container$take.md`
- `POST /container/take/batch`: `GET /apidoc/container$take$batch.md`
- `POST /container/move`: `GET /apidoc/container$move.md`
- `POST /container/move/batch`: `GET /apidoc/container$move$batch.md`
- `POST /place?x=10&y=64&z=-20&face=up`: `GET /apidoc/place.md`
- `POST /break?x=10&y=64&z=-20`: `GET /apidoc/break.md`
- `POST /place/batch`: `GET /apidoc/place$batch.md`
- `POST /break/batch`: `GET /apidoc/break$batch.md`
- `POST /place/box`: `GET /apidoc/place$box.md`
- `POST /break/box`: `GET /apidoc/break$box.md`
- `GET /harvest-tool?blockId=namespace:path`: `GET /apidoc/harvest-tool.md`
- `GET /nearby-entities?radius=64&category=monster,animal&limit=64`: `GET /apidoc/nearby-entities.md`
- `POST /entity/pickup-item`: `GET /apidoc/entity$pickup-item.md`
- `GET /staring-entity`: `GET /apidoc/staring-entity.md`
- `GET /entity?uuid=uuid`: `GET /apidoc/entity.md`
- `GET /player?uuid=uuid&detail=true`: `GET /apidoc/player.md`
- `GET /langkey?key=language.key`: `GET /apidoc/langkey.md`
- `GET /langkey-search?text=query`: `GET /apidoc/langkey-search.md`

## Error Codes
Use `/errcode/{code}` to fetch one error description without reading the full list.
