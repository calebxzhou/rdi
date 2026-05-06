# RDI Minecraft Client MCP API Prompt

Use these local HTTP APIs to read live data from the running Minecraft client. Prefer the smallest API that answers the question. Do not guess game state when an API can read it directly.

All APIs use `GET`. All responses are JSON except `/screenshot`, which returns raw PNG bytes on success:

```json
{
  "code": "ok",
  "data": {}
}
```

If `code` is not `ok`, `data` is `null`. Use the HTTP status and `code` to decide whether to retry, ask for a different input, or use another API.

## Decision Guide

- Need to know whether the API is alive or whether a world is loaded: call `/test`.
- Need the current visual game frame: call `/screenshot`.
- Need the current player's exact location or current dimension: call `/pos`.
- Need the current player's main hand item stack: call `/mainhand`.
- Need recipes that produce a known item ID: call `/recipe?itemId=...`.
- Need a chunk overview before inspecting terrain/building layout: call `/chunk?x=...&z=...`.
- Need an LLM-readable 16x16x16 section layout: call `/section?x=...&y=...&z=...`.
- Need nearby resources, fluids, containers, crops, wood, ores, or block entities around a position: call `/nearby-resources?pos=...`.
- Need the block the player is currently looking at: call `/staring-block`.
- Need a block at a known position: call `/blockstate`.
- Need detailed block entity data, inventory, NBT, or runtime fields at a known position: call `/blockentity`.
- Need to know which tool can harvest a block and what it drops with different tools: call `/harvest-tool?blockId=...` or `/harvest-tool?pos=...`.
- Need nearby monsters or animals around the player or a known position: call `/nearby-entities?category=monster,animal`.
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

## Endpoints

### `GET /test`

Use for a cheap health check.

Returns:

```json
{
  "code": "ok",
  "data": {
    "mod": "rdi",
    "mcVersion": "1.21.1",
    "side": "client",
    "worldLoaded": true
  }
}
```

### `GET /screenshot`

Use to capture the current Minecraft client frame. This endpoint returns raw PNG bytes, not JSON, on success.

Returns:

```http
HTTP/1.1 200 OK
Content-Type: image/png
Cache-Control: no-store

<PNG bytes>
```

If screenshot capture fails or times out, the response uses the normal JSON error shape.

### `GET /pos`

Use to get the local player's current dimension, position, yaw, and pitch.
`chunkX`, `chunkZ`, and `sectionY` are derived from the block position using floor division, so negative coordinates match Minecraft chunk math.

Returns `RMcpPosData`:

```json
{
  "code": "ok",
  "data": {
    "dim": "minecraft:overworld",
    "x": 10.5,
    "y": 64.0,
    "z": -20.5,
    "yaw": 90.0,
    "pitch": 15.0,
    "chunkX": 0,
    "chunkZ": -2,
    "sectionY": 4
  }
}
```

### `GET /mainhand`

Use to get the local player's current main hand item stack. The response uses a compact ItemStack format for LLM reading.

Returns:

```json
{
  "code": "ok",
  "data": {
    "id": "minecraft:diamond_pickaxe",
    "count": 1,
    "snbt": "{count:1,id:\"minecraft:diamond_pickaxe\"}"
  }
}
```

If the main hand is empty, `data` is `null`.

### `GET /recipe?itemId=namespace:path`

Use to fetch recipes whose result item exactly matches a known item registry ID. The client must be in a loaded world because recipes are read from the live synced recipe manager.

Example:

```text
/recipe?itemId=minecraft:crafting_table
```

Returns:

```json
{
  "code": "ok",
  "data": [
    {
      "id": "minecraft:crafting_table",
      "type": "minecraft:crafting_shaped",
      "group": "",
      "special": false,
      "result": {
        "id": "minecraft:crafting_table",
        "langKey": "block.minecraft.crafting_table",
        "count": 1
      },
      "category": "building",
      "key": {
        "A": {
          "items": [
            {"id": "minecraft:oak_planks", "langKey": "block.minecraft.oak_planks", "count": 1}
          ]
        }
      },
      "pattern": ["AA", "AA"]
    }
  ]
}
```

If no synced recipe produces the item, `data` is an empty array.

### `GET /chunk?x=chunkX&z=chunkZ`

Use to understand the loaded chunk at a high level before fetching any section layout. This endpoint reads only the current client dimension and does not force-load chunks.

Returns a lossy semantic overview:

```json
{
  "code": "ok",
  "data": {
    "format": "chunk-semantic-v1",
    "dim": "minecraft:overworld",
    "chunk": {"x": 0, "z": 0},
    "summary": {
      "sections": 24,
      "nonEmptySections": 8,
      "nonAir": 21033,
      "topBlocks": [
        {"id": "minecraft:stone", "count": 12000},
        {"id": "minecraft:air", "count": 8000}
      ],
      "heightRangeNonAir": {"min": -20, "max": 91}
    },
    "sections": [
      {
        "sectionY": 4,
        "blockY": {"min": 64, "max": 79},
        "empty": false,
        "nonAir": 812,
        "topBlocks": [{"id": "minecraft:air", "count": 3284}],
        "sectionApi": "/section?x=0&y=4&z=0"
      }
    ]
  }
}
```

### `GET /section?x=chunkX&y=sectionY&z=chunkZ`

Use to understand the structure, layout, and content of one loaded 16x16x16 chunk section. This is not a full deserializable blockstate snapshot. It is a lossy semantic view for LLM reasoning.

Returns:

```json
{
  "code": "ok",
  "data": {
    "format": "section-semantic-v1",
    "dim": "minecraft:overworld",
    "chunk": {"x": 0, "z": 0},
    "sectionY": 4,
    "blockY": {"min": 64, "max": 79},
    "summary": {
      "empty": false,
      "nonAir": 812,
      "paletteSize": 9,
      "topBlocks": [
        {"id": "minecraft:air", "count": 3284},
        {"id": "minecraft:stone", "count": 520}
      ],
      "bboxNonAir": {
        "min": {"x": 0, "y": 64, "z": 0},
        "max": {"x": 15, "y": 78, "z": 15}
      }
    },
    "layers": [
      {
        "y": 0,
        "grid": [
          "................",
          "....AAAA........"
        ],
        "topBlocks": [{"id": "minecraft:air", "count": 240}]
      }
    ],
    "legend": {
      ".": "minecraft:air",
      "A": "minecraft:stone",
      "?": "other"
    },
    "features": {
      "hasFluids": false,
      "hasBlockEntities": true,
      "solidRegions": 3,
      "airRegions": 2
    }
  }
}
```

Layer grid rules:

- `layers[].y` is local section Y `0..15`; world block Y is `blockY.min + layers[].y`.
- Each grid line is local Z, and each character in the line is local X.
- `.` always means air.
- Symbols are explained by `legend`.
- `?` means low-priority blocks grouped as `other`; use `/blockstate` for exact cells when needed.

### `GET /nearby-resources?pos=dim,x,y,z&chunkRadius=2&sectionRadius=1`

Use to scan loaded sections around a known position and summarize nearby useful resources like ores, fluids, wood, crops, containers, spawners, and block entities. This endpoint is a compact semantic scan for LLM planning. It does not force-load chunks.

`chunkRadius` and `sectionRadius` are optional. Defaults are `chunkRadius=2` and `sectionRadius=1`; valid values are `0..4`.

Returns:

```json
{
  "code": "ok",
  "data": {
    "format": "nearby-resources-v1",
    "dim": "minecraft:overworld",
    "center": {
      "block": {"x": 10, "y": 64, "z": -20},
      "chunkX": 0,
      "chunkZ": -2,
      "sectionY": 4
    },
    "range": {"chunkRadius": 2, "sectionRadius": 1},
    "scan": {"loadedChunks": 25, "skippedChunks": 0, "sections": 75, "blocks": 307200},
    "resources": [
      {
        "id": "minecraft:iron_ore",
        "category": "ore",
        "count": 18,
        "nearest": {"x": 12, "y": 63, "z": -31},
        "sections": [
          {"chunkX": 0, "chunkZ": -2, "sectionY": 3, "count": 7}
        ]
      },
      {
        "id": "minecraft:water",
        "category": "water",
        "count": 340,
        "nearest": {"x": 3, "y": 64, "z": -18},
        "sections": [
          {"chunkX": 0, "chunkZ": -2, "sectionY": 4, "count": 120}
        ]
      }
    ],
    "topBlocks": [
      {"id": "minecraft:stone", "count": 12000},
      {"id": "minecraft:air", "count": 8000}
    ],
    "features": {
      "hasWater": true,
      "hasLava": false,
      "hasOre": true,
      "hasWood": true,
      "hasCrops": false,
      "hasBlockEntities": true
    }
  }
}
```

Use `resources[].nearest` for the first target to inspect. Use `/section` for layout when a resource section looks important, then `/blockstate` or `/blockentity` for exact cells.

### `GET /staring-block?fluid=true`

Use to inspect the block the local player is looking at. The trace range is long enough for normal inspection. Add `fluid=true` only when fluid information is needed.

Returns:

```json
{
  "code": "ok",
  "data": {
    "dim": "minecraft:overworld",
    "pos": {"x": 10, "y": 64, "z": -20},
    "id": "minecraft:water",
    "state": "minecraft:water[level=0]",
    "fluid": {
      "id": "minecraft:water",
      "state": "minecraft:water[level=0]"
    }
  }
}
```

If `fluid` is omitted or false, `fluid` is `null`.

### `GET /blockstate?pos=dim,x,y,z`

Use when you already know the block position and only need block ID/state. This is cheaper than `/blockentity`.

Returns:

```json
{
  "code": "ok",
  "data": {
    "dim": "minecraft:overworld",
    "pos": {"x": 10, "y": 64, "z": -20},
    "id": "minecraft:oak_log",
    "state": "minecraft:oak_log[axis=y]"
  }
}
```

Air is valid data and returns an air block state, not an error.

### `GET /blockentity?pos=dim,x,y,z`

Use for detailed block entity data at a known position. This is for chests, machines, signs, spawners, and modded blocks that store extra data. This endpoint uses the `rdi:mcp` server bridge, so the returned data is read from the server rather than from the client's partial block entity copy.

Returns:

```json
{
  "code": "ok",
  "data": {
    "dim": "minecraft:overworld",
    "pos": {"x": 10, "y": 64, "z": -20},
    "blockId": "minecraft:chest",
    "blockState": "minecraft:chest[facing=north,type=single,waterlogged=false]",
    "type": "minecraft:chest",
    "runtimeClass": "net.minecraft.world.level.block.entity.ChestBlockEntity",
    "snbt": "{...}"
  }
}
```

Use `blockId`, `blockState`, `type`, and `runtimeClass` for concise answers. Use `snbt` only when exact stored data is needed.

### `GET /harvest-tool?blockId=namespace:path`

Use to ask the server which tools can harvest a block and what drops each tool can produce. This endpoint uses the server `rdi:mcp` bridge and active server loot tables.

You can also use a world position:

```text
/harvest-tool?pos=minecraft:overworld,10,64,-20
```

Use `pos` when block state or block entity data may matter. Use `blockId` for a generic default-state answer.

Returns:

```json
{
  "code": "ok",
  "data": {
    "format": "harvest-tool-v1",
    "stateSource": "default",
    "block": {
      "id": "minecraft:diamond_ore",
      "state": "minecraft:diamond_ore",
      "pos": null,
      "requiresCorrectToolForDrops": true,
      "mineableWith": ["pickaxe"],
      "minimumTier": "iron"
    },
    "scenarios": [
      {
        "tool": {
          "id": "minecraft:air",
          "category": "hand",
          "tier": null,
          "enchantments": {}
        },
        "correctToolForDrops": false,
        "harvestable": false,
        "drops": []
      },
      {
        "tool": {
          "id": "minecraft:iron_pickaxe",
          "category": "pickaxe",
          "tier": "iron",
          "enchantments": {}
        },
        "correctToolForDrops": true,
        "harvestable": true,
        "drops": [
          {"id": "minecraft:diamond", "countMin": 1, "countMax": 1, "snbt": "{count:1,id:\"minecraft:diamond\"}"}
        ]
      },
      {
        "tool": {
          "id": "minecraft:diamond_pickaxe",
          "category": "pickaxe",
          "tier": "diamond",
          "enchantments": {"minecraft:silk_touch": 1}
        },
        "correctToolForDrops": true,
        "harvestable": true,
        "drops": [
          {"id": "minecraft:diamond_ore", "countMin": 1, "countMax": 1, "snbt": "{count:1,id:\"minecraft:diamond_ore\"}"}
        ]
      },
      {
        "tool": {
          "id": "minecraft:diamond_pickaxe",
          "category": "pickaxe",
          "tier": "diamond",
          "enchantments": {"minecraft:fortune": 3}
        },
        "correctToolForDrops": true,
        "harvestable": true,
        "drops": [
          {"id": "minecraft:diamond", "countMin": 1, "countMax": 4, "snbt": "{count:1,id:\"minecraft:diamond\"}"}
        ]
      }
    ],
    "notes": [
      "Drops are simulated on the server from the active loot table.",
      "Wrong tools return no drops when the block requires a correct tool.",
      "Random loot is sampled 16 times and summarized as countMin/countMax."
    ]
  }
}
```

Use `mineableWith` and `minimumTier` for the short answer. Use `scenarios` when the user asks what changes with silk touch, fortune, shears, or wrong tools.

### `GET /nearby-entities?pos=dim,x,y,z&radius=64&category=monster,animal&limit=64`

Use to discover nearby monsters and animals from the client-loaded entity set. This is a brief semantic scan. Use `/entity?uuid=...` afterwards only when full runtime or NBT details are needed.

`pos` is optional and defaults to the local player's current block position. `radius` defaults to `64` and must be `0..128`. `limit` defaults to `64` and must be `1..128`. `category` is comma-separated; common values are `monster`, `animal`, or `all`.

Returns:

```json
{
  "code": "ok",
  "data": {
    "format": "nearby-entities-v1",
    "dim": "minecraft:overworld",
    "center": {
      "block": {"x": 10, "y": 64, "z": -20},
      "chunkX": 0,
      "chunkZ": -2,
      "sectionY": 4
    },
    "radius": 64.0,
    "summary": {
      "total": 8,
      "monsters": 3,
      "animals": 5,
      "nearestMonster": {"type": "minecraft:zombie", "distance": 12.4},
      "nearestAnimal": {"type": "minecraft:cow", "distance": 7.1}
    },
    "entities": [
      {
        "dim": "minecraft:overworld",
        "uuid": "00000000-0000-0000-0000-000000000000",
        "type": "minecraft:zombie",
        "name": "Zombie",
        "category": "monster",
        "pos": {},
        "distance": 12.4,
        "health": 20.0,
        "maxHealth": 20.0,
        "hostile": true,
        "baby": false
      }
    ]
  }
}
```

Use `summary` for quick threat or animal availability answers. Use `entities[].uuid` with `/entity` only for a specific entity that needs detail.

### `GET /staring-entity`

Use to identify the entity the local player is looking at. The trace range is 64 blocks.

Returns:

```json
{
  "code": "ok",
  "data": {
    "dim": "minecraft:overworld",
    "uuid": "00000000-0000-0000-0000-000000000000",
    "type": "minecraft:zombie",
    "name": "Zombie",
    "pos": {
      "dim": "minecraft:overworld",
      "x": 12.0,
      "y": 64.0,
      "z": -20.0,
      "yaw": 0.0,
      "pitch": 0.0
    },
    "distance": 4.2
  }
}
```

Call `/entity?uuid=...` after this only if full details are needed.

### `GET /entity?uuid=uuid`

Use to fetch detailed data for a loaded entity by UUID.

Returns:

```json
{
  "code": "ok",
  "data": {
    "dim": "minecraft:overworld",
    "uuid": "00000000-0000-0000-0000-000000000000",
    "type": "minecraft:zombie",
    "name": "Zombie",
    "pos": {},
    "runtime": {},
    "nbt": {},
    "snbt": "{...}"
  }
}
```

Use `type`, `name`, and `pos` for concise answers. Use `nbt` or `snbt` only for exact entity state.

### `GET /player?uuid=uuid&detail=true`

Use to fetch player data. If `uuid` is absent, the local player is used. If `detail=true` is absent, a brief response is returned.

Brief response:

```json
{
  "code": "ok",
  "data": {
    "dim": "minecraft:overworld",
    "uuid": "00000000-0000-0000-0000-000000000000",
    "name": "Player",
    "pos": {},
    "health": 20.0,
    "maxHealth": 20.0,
    "food": 20,
    "gameMode": "survival",
    "latency": 50
  }
}
```

Detailed response:

```json
{
  "code": "ok",
  "data": {
    "brief": {},
    "entity": {},
    "profile": {},
    "player": {},
    "inventory": {}
  }
}
```

Use brief mode by default. Use `detail=true` only when inventory, profile, NBT, or extra player runtime fields are needed.

### `GET /langkey?key=language.key`

Use when you already know a language key and need English/Chinese display text.

Returns:

```json
{
  "code": "ok",
  "data": {
    "en": "Acacia Sign",
    "cn": "金合欢木告示牌"
  }
}
```

If only one language exists, the other field may be an empty string.

### `GET /langkey-search?text=query`

Use to discover possible language keys from English or Chinese text. Chinese matching is prioritized over English. Fuzzy matching is supported. At most 10 candidates are returned.

Example:

```text
/langkey-search?text=金合欢牌子
```

Returns:

```json
{
  "code": "ok",
  "data": {
    "text": "金合欢牌子",
    "results": [
      {
        "langkey": "block.minecraft.acacia_sign",
        "englishName": "Acacia Sign",
        "chineseName": "金合欢木告示牌"
      }
    ]
  }
}
```

## Error Codes

General:

- `method_not_allowed`: only `GET` is supported.
- `not_found`: endpoint path is unknown.
- `internal_error`: the API failed internally.
- `screenshot_failed`: the screenshot could not be captured or encoded.
- `screenshot_timeout`: the screenshot capture did not complete in time.
- `server_mcp_unavailable`: the connected server does not expose the `rdi:mcp` bridge.
- `server_timeout`: the server did not answer the MCP bridge request in time.

Game state:

- `no_player`: the local player is not in a loaded world.
- `dim_not_loaded`: the requested dimension is not the client's current loaded dimension.

Input:

- `missing_pos`: required `pos` query parameter is absent or blank.
- `bad_pos`: `pos` is not `dim,x,y,z`, dimension is empty, or coordinates are not integers.
- `missing_block_id`: neither `blockId` nor `pos` was provided for a block-based query.
- `bad_block_id`: `blockId` is not a valid loaded block ID.
- `missing_uuid`: required `uuid` query parameter is absent or blank.
- `bad_uuid`: `uuid` is not a valid UUID.
- `missing_item_id`: required `itemId` query parameter is absent or blank.
- `missing_chunk_x`: required chunk `x` query parameter is absent or blank.
- `missing_chunk_z`: required chunk `z` query parameter is absent or blank.
- `missing_section_y`: required section `y` query parameter is absent or blank.
- `bad_chunk_x`: chunk `x` is not an integer.
- `bad_chunk_z`: chunk `z` is not an integer.
- `bad_section_y`: section `y` is not an integer.
- `bad_chunk_radius`: `chunkRadius` is not an integer or is outside `0..4`.
- `bad_section_radius`: `sectionRadius` is not an integer or is outside `0..4`.
- `bad_radius`: `radius` is not a number or is outside `0..128`.
- `bad_limit`: `limit` is not an integer or is outside `1..128`.
- `missing_text`: required `text` query parameter is absent or blank.
- `missing_key`: required `key` query parameter is absent or blank.

Not loaded or not found:

- `no_block`: player is not looking at a block.
- `no_block_entity`: no loaded block entity exists at the requested position.
- `chunk_not_loaded`: the requested chunk is not loaded by the client.
- `section_out_of_range`: section `y` is outside the current world's build height.
- `no_entity`: no target or loaded entity is available for the request.
- `no_player_entity`: requested player is not loaded.
- `no_langkey`: language key was not found in English or Chinese language data.

## LLM Usage Rules

- Always check `code`; only trust `data` when `code` is `ok`.
- `/screenshot` is the only success response that is not JSON; treat it as an image.
- Prefer brief APIs first, then detail APIs only when the brief result is insufficient.
- For terrain or build understanding, call `/chunk` first, then call `/section` only for sections that matter.
- Treat `/section` as a visual semantic map, not as exact block data. Use `/blockstate` for exact cells.
- For nearby resource discovery, call `/nearby-resources` before scanning many chunks or sections manually.
- For nearby monsters or animals, call `/nearby-entities` before using `/entity`.
- Prefer `/blockstate` over `/blockentity` when only the block ID/state is needed.
- Prefer `/harvest-tool?pos=...` over `/harvest-tool?blockId=...` when the exact block state or block entity may affect drops.
- Prefer `/staring-entity` before `/entity` when the user refers to "the entity I am looking at".
- Prefer `/pos` before position based APIs if you need the current dimension.
- Do not call position based APIs for unloaded dimensions; the client can only inspect its current loaded dimension.
- Use `langkey-search` to translate user-visible names into language keys, then `langkey` for exact display text if needed.
