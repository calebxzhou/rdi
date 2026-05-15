### `GET /nearby-resources?category=ore,fluid&chunkRadius=2&sectionRadius=1&limit=64`

Use to scan loaded sections around the player or a known position and summarize nearby useful resources like ores, fluids, wood, crops, containers, spawners, and block entities. This endpoint is a compact semantic scan for LLM planning. It does not force-load chunks.

`pos` is optional, uses `x,y,z` in the current dimension, and defaults to the local player's current block position. `chunkRadius` and `sectionRadius` are optional. Defaults are `chunkRadius=2` and `sectionRadius=1`; valid values are `0..4`.

Filters are optional:

- `category`: comma-separated semantic categories. Valid values: `ore`, `fluid`, `water`, `lava`, `wood`, `crop`, `container`, `block_entity`, `spawner`. `fluid` includes water and lava.
- `ids`: comma-separated exact resource IDs, such as `minecraft:iron_ore,minecraft:water`.
- `limit`: max returned resource summaries. Default is `64`; valid range is `1..256`.

Prefer omitting `pos` when the scan should be centered on the player.

Default response is compact JSON: each `resources[]` entry keeps counts, category, nearest position, and `detail` refs, but omits the full per-section list. Use `GET /nearby-resources/detail?ref=...` for Markdown, or `GET /nearby-resources/detail.json?ref=...` for exact JSON. `.detail.md` is a compatibility alias for Markdown. Use `view=full` only for debugging or extraction.

Always check `scan` before acting. It tells you the requested radius, how many chunks/sections were actually scanned, whether results were limited, and why data was skipped.

Compact returns:

```json
{
  "code": "ok",
  "data": {
    "dim": "minecraft:overworld",
    "center": {
      "block": {"x": 10, "y": 64, "z": -20},
      "chunkX": 0,
      "chunkZ": -2,
      "sectionY": 4
    },
    "range": {"chunkRadius": 2, "sectionRadius": 1},
    "filter": {
      "categories": ["ore", "fluid"],
      "ids": [],
      "limit": 64
    },
    "scan": {
      "chunkRadius": 2,
      "sectionRadius": 1,
      "requestedChunks": 25,
      "loadedChunks": 24,
      "skippedChunks": 1,
      "requestedSections": 75,
      "sections": 72,
      "blocks": 294912,
      "matchedResources": 8,
      "returnedResources": 8,
      "limited": false,
      "skippedReasons": [
        {"reason": "chunk_not_loaded", "count": 1}
      ]
    },
    "resources": [
      {
        "id": "minecraft:iron_ore",
        "category": "ore",
        "count": 18,
        "nearest": {"x": 12, "y": 63, "z": -31},
        "sectionCount": 3,
        "detail": "/nearby-resources/detail?...&ref=minecraft%3Airon_ore",
        "detailJson": "/nearby-resources/detail.json?...&ref=minecraft%3Airon_ore"
      },
      {
        "id": "minecraft:water",
        "category": "water",
        "count": 340,
        "nearest": {"x": 3, "y": 64, "z": -18},
        "sectionCount": 5,
        "detail": "/nearby-resources/detail?...&ref=minecraft%3Awater",
        "detailJson": "/nearby-resources/detail.json?...&ref=minecraft%3Awater"
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

Use `resources[].nearest` for the first target to inspect. Open `resources[].detail` when you need section counts in Markdown. `scan.skippedReasons` may include `chunk_not_loaded` or `section_out_of_world`. Use `/blockmap/slice` for horizontal layout when a resource section looks important, then `/blockstate/batch`, `/blockstate`, or `/blockentity` for exact cells.

Errors:

- `bad_pos`: provided `pos` is malformed.
- `bad_chunk_radius`: `chunkRadius` is not an integer or is outside `0..4`.
- `bad_section_radius`: `sectionRadius` is not an integer or is outside `0..4`.
- `bad_limit`: `limit` is not an integer or is outside `1..256`.
- `bad_request`: `category` or `ids` is malformed.
- `no_player`: the local player is not in a loaded world.
