### `GET /nearby-resources?chunkRadius=2&sectionRadius=1`

Use to scan loaded sections around the player or a known position and summarize nearby useful resources like ores, fluids, wood, crops, containers, spawners, and block entities. This endpoint is a compact semantic scan for LLM planning. It does not force-load chunks.

`pos` is optional and defaults to the local player's current block position. `chunkRadius` and `sectionRadius` are optional. Defaults are `chunkRadius=2` and `sectionRadius=1`; valid values are `0..4`.

Prefer omitting `pos` when the scan should be centered on the player.

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

Use `resources[].nearest` for the first target to inspect. Use `/section` for layout when a resource section looks important, then `/blockstate/batch`, `/blockstate`, or `/blockentity` for exact cells.

Errors:

- `bad_pos`: provided `pos` is malformed.
- `bad_chunk_radius`: `chunkRadius` is not an integer or is outside `0..4`.
- `bad_section_radius`: `sectionRadius` is not an integer or is outside `0..4`.
- `no_player`: the local player is not in a loaded world.
- `dim_not_loaded`: provided `pos` dimension is not the client's current loaded dimension.

