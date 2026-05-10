### `POST /blocks/find`

Use when you already know one or more block IDs and need nearby exact block positions quickly. This endpoint scans loaded chunks around the current player. It does not force-load chunks.

Request body:

Single ID:

```json
{
  "id": "minecraft:oak_log",
  "chunkRadius": 2,
  "sectionRadius": 1,
  "scanMode": "nearby_sections",
  "limit": 64,
  "includeState": false
}
```

Multiple IDs:

```json
{
  "ids": ["minecraft:oak_log", "minecraft:chest"],
  "chunkRadius": 2,
  "sectionRadius": 1,
  "scanMode": "nearby_sections",
  "limit": 64,
  "includeState": false
}
```

Mining / whole chunk mode:

```json
{
  "ids": ["minecraft:diamond_ore", "minecraft:deepslate_diamond_ore"],
  "chunkRadius": 1,
  "scanMode": "chunk",
  "limit": 128,
  "includeState": false
}
```

Fields:

- `id`: optional single block ID.
- `ids`: optional block IDs.
- At least one valid block ID must be provided through `id`, `ids`, or both. Duplicate IDs are ignored. Total unique IDs must be `1..16`.
- `chunkRadius`: optional, defaults to `2`, valid `0..4`.
- `scanMode`: optional, defaults to `nearby_sections`. Valid values are `nearby_sections` and `chunk`.
- `sectionRadius`: optional, defaults to `1`, valid `0..4`. Used only when `scanMode="nearby_sections"`.
- `limit`: optional, defaults to `64`, valid `1..512`.
- `includeState`: optional, defaults to `false`. If true, each match group includes `states` in the same order as `positions`.

Scan modes:

- `nearby_sections`: scans only sections near the player's current Y. Use this for nearby blocks, trees, containers, or visible structures.
- `chunk`: scans every vertical section in each loaded chunk selected by `chunkRadius`. Use this for mining and underground resources. This can scan many blocks; start with `chunkRadius=0` or `1`.

Returns:

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
    "range": {
      "chunkRadius": 2,
      "sectionRadius": 1,
      "scanMode": "nearby_sections",
      "minSectionY": 3,
      "maxSectionY": 5
    },
    "scan": {
      "loadedChunks": 25,
      "skippedChunks": 0,
      "sections": 75,
      "blocks": 307200,
      "matched": 18,
      "returned": 18,
      "truncated": false
    },
    "matches": [
      {
        "id": "minecraft:oak_log",
        "count": 12,
        "nearest": {"x": 14, "y": 64, "z": -18},
        "positions": [
          {"x": 14, "y": 64, "z": -18}
        ]
      }
    ]
  }
}
```

Use `matches[].nearest` for the first target to inspect. Use `positions` for exact actions like `/blockstate/batch`, `/break/batch`, or movement planning.

Use `/nearby-resources` instead when you do not know the exact block ID yet.

Errors:

- `bad_request`: request body is missing, empty, malformed, invalid JSON, or `scanMode` is not `nearby_sections` or `chunk`.
- `bad_block_ids`: `id`/`ids` is missing, empty, has more than 16 unique entries, includes a malformed ID, or includes an ID that is not a loaded block.
- `bad_chunk_radius`: `chunkRadius` is outside `0..4`.
- `bad_section_radius`: `sectionRadius` is outside `0..4`.
- `bad_limit`: `limit` is outside `1..512`.
- `no_player`: the local player is not in a loaded world.
- `section_out_of_range`: requested section scan range is outside world build height.
