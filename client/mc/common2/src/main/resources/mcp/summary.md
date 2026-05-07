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
- Endpoint docs served by `GET /apidoc/{file}`. The endpoint path has no leading `/` and `/` is replaced with `$`.

If `code` is not `ok`, `data` is `null`. Use the HTTP status and `code` to decide whether to retry, ask for a different input, or use another API.

## Decision Guide

- Need to know whether the API is alive or whether a world is loaded: call `/test`.
- Need the current visual game frame: call `/screenshot`.
- Need the exact meaning of an error code: call `/errcode/{code}`.
- Need the current player's exact location or current dimension: call `/pos`.
- Need a compact live context snapshot before planning an action: call `/situation`.
- Need the player's full inventory, hotbar, armor, and offhand: call `/inventory`.
- Need to swap two slots inside the player's inventory: call `POST /inventory/swap`.
- Need to move a specific item count between inventory slots: call `POST /inventory/move`.
- Need to open a nearby crafting table GUI for the user: call `POST /crafting/open`.
- Need to craft from chosen inventory slots: call `POST /craft`.
- Need the current player's main hand item stack: call `/mainhand`.
- Need recipes that produce a known item ID: call `/recipe?itemId=...`.
- Need a chunk overview before inspecting terrain/building layout: call `/chunk?x=...&z=...`.
- Need an LLM-readable 16x16x16 section layout: call `/section?x=...&y=...&z=...`.
- Need nearby resources, fluids, containers, crops, wood, ores, or block entities: call `/nearby-resources`; add `pos=...` only for a known non-player center.
- Need the block the player is currently looking at: call `/staring-block`.
- Need exact block states for multiple known positions: call `POST /blockstate/batch` first.
- Need exact block state for only one known position: call `/blockstate`.
- Need detailed block entity data, inventory, NBT, or runtime fields at a known position: call `/blockentity`.
- Need to read slots from a container or machine block: call `/container?pos=...`.
- Need to move items between two container or machine blocks: call `POST /container/move`.
- Need to place the current main hand block item at a known air position: call `POST /place?x=...&y=...&z=...`.
- Need to break a known block using the current main hand item: call `POST /break?x=...&y=...&z=...`.
- Need to place or break several sparse positions: call `POST /place/batch` or `POST /break/batch`.
- Need to place or break every block inside a small cuboid: call `POST /place/box` or `POST /break/box`.
- Need to know which tool can harvest a block and what it drops with different tools: call `/harvest-tool?blockId=...` or `/harvest-tool?pos=...`.
- Need nearby monsters or animals: call `/nearby-entities`; add `pos=...` only for a known non-player center.
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
- Call `/situation` first when deciding the next action from current game state.
- Call `/inventory` when exact item stacks or slots are needed; do not use `/player?detail=true` only for inventory.
- Use `POST /inventory/swap` only after checking `/inventory`; use `dryRun=true` when the slot choice is uncertain.
- Use `POST /inventory/move` instead of `/inventory/swap` when only part of a stack should move or when merging into an existing compatible stack is intended.
- Use `POST /crafting/open` only when the user needs the crafting table GUI opened; `/craft` itself does not require a GUI.
- To craft an item, call `/recipe?itemId=...` and `/inventory`, choose source inventory slots, build a `shape`, then call `POST /craft` with `dryRun=true` before the real action when the slot plan is uncertain.
- Use `POST /blockstate/batch` before `/place/batch`, `/break/batch`, `/place/box`, or `/break/box` when target cells are not already known.
- Use `/blockstate` before single `/place` or `/break` only when checking exactly one target cell; `/place` only accepts air targets and `/break` only accepts non-air targets.
- Use `/container` before `/container/move`; never infer container slot numbers from `/blockentity` SNBT when an item handler view is available.
- Prefer brief APIs first, then detail APIs only when the brief result is insufficient.
- For terrain or build understanding, call `/chunk` first, then call `/section` only for sections that matter.
- Treat `/section` as a visual semantic map, not as exact block data. Use `POST /blockstate/batch` for exact cells unless there is only one cell.
- For nearby resource discovery around the player, call `/nearby-resources` without `pos`; use `pos` only when the center is not the player.
- For nearby monsters or animals around the player, call `/nearby-entities` without `pos`; use `/entity` only after choosing a specific UUID.
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
- `GET /apidoc/{file}`: `GET /apidoc/apidoc${file}.md`
- `GET /errcode/{code}`: `GET /apidoc/errcode${code}.md`

- `GET /test`: `GET /apidoc/test.md`
- `GET /screenshot`: `GET /apidoc/screenshot.md`
- `GET /pos`: `GET /apidoc/pos.md`
- `GET /situation?entityRadius=32&resourceChunkRadius=1&resourceSectionRadius=1`: `GET /apidoc/situation.md`
- `GET /inventory`: `GET /apidoc/inventory.md`
- `POST /inventory/swap`: `GET /apidoc/inventory$swap.md`
- `POST /inventory/move`: `GET /apidoc/inventory$move.md`
- `POST /crafting/open`: `GET /apidoc/crafting$open.md`
- `POST /craft`: `GET /apidoc/craft.md`
- `GET /mainhand`: `GET /apidoc/mainhand.md`
- `GET /recipe?itemId=namespace:path`: `GET /apidoc/recipe.md`
- `GET /chunk?x=chunkX&z=chunkZ`: `GET /apidoc/chunk.md`
- `GET /section?x=chunkX&y=sectionY&z=chunkZ`: `GET /apidoc/section.md`
- `GET /nearby-resources?chunkRadius=2&sectionRadius=1`: `GET /apidoc/nearby-resources.md`
- `GET /staring-block?fluid=true`: `GET /apidoc/staring-block.md`
- `GET /blockstate?pos=dim,x,y,z`: `GET /apidoc/blockstate.md`
- `POST /blockstate/batch`: `GET /apidoc/blockstate$batch.md`
- `GET /blockentity?pos=dim,x,y,z`: `GET /apidoc/blockentity.md`
- `GET /container?pos=dim,x,y,z&side=north`: `GET /apidoc/container.md`
- `POST /container/move`: `GET /apidoc/container$move.md`
- `POST /place?x=10&y=64&z=-20`: `GET /apidoc/place.md`
- `POST /break?x=10&y=64&z=-20`: `GET /apidoc/break.md`
- `POST /place/batch`: `GET /apidoc/place$batch.md`
- `POST /break/batch`: `GET /apidoc/break$batch.md`
- `POST /place/box`: `GET /apidoc/place$box.md`
- `POST /break/box`: `GET /apidoc/break$box.md`
- `GET /harvest-tool?blockId=namespace:path`: `GET /apidoc/harvest-tool.md`
- `GET /nearby-entities?radius=64&category=monster,animal&limit=64`: `GET /apidoc/nearby-entities.md`
- `GET /staring-entity`: `GET /apidoc/staring-entity.md`
- `GET /entity?uuid=uuid`: `GET /apidoc/entity.md`
- `GET /player?uuid=uuid&detail=true`: `GET /apidoc/player.md`
- `GET /langkey?key=language.key`: `GET /apidoc/langkey.md`
- `GET /langkey-search?text=query`: `GET /apidoc/langkey-search.md`

## Error Codes
Use `/errcode/{code}` to fetch one error description without reading the full list.
