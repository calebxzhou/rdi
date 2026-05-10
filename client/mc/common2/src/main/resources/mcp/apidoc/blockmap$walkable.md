### `GET /blockmap/walkable?x=10&y=64&z=-20&radius=8`

Use to read a top-down movement map around a position. This is the preferred map before calling `/move`.

`x`, `y`, and `z` are optional as a group. If absent, the map is centered on the current player block position. `radius` defaults to `8` and must be `0..16`.

The API scans near the center Y to find a standable feet position for each XZ cell. Rows are ordered by increasing Z. Columns are ordered by increasing X. `cells` lists exact feet positions for walkable, fluid, and hazard cells so the LLM does not need to infer Y from the grid.

Legend:

- `P`: player XZ
- `C`: center XZ
- `.`: walkable
- `#`: blocked
- `~`: fluid
- `!`: hazard
- `_`: drop/no floor in scan range

Returns:

```json
{
  "code": "ok",
  "data": {
    "dim": "minecraft:overworld",
    "mode": "walkable",
    "center": {"x": 10, "y": 64, "z": -20},
    "radius": 8,
    "scanY": {"min": 60, "max": 68},
    "summary": {
      "width": 17,
      "height": 17,
      "loadedChunks": 4,
      "walkable": 120,
      "blocked": 40,
      "hazards": 0,
      "fluids": 2,
      "drops": 1
    },
    "rows": [
      "....###..",
      "...P..#..",
      "...~..#.."
    ],
    "cells": [
      {
        "pos": {"x": 10, "y": 64, "z": -20},
        "symbol": ".",
        "blockId": "minecraft:air",
        "floorBlockId": "minecraft:grass_block",
        "note": "walkable"
      }
    ]
  }
}
```

Use `cells[].pos` as the movement target for `/move`. For normal movement, use only entries whose `symbol` is `"."`. Avoid `#`, `_`, `~`, and `!` unless the user explicitly wants blocked/drop/fluid/hazard movement. Do not use the map `center` as a move target unless it also appears as a safe `cells[].pos` entry.

Errors:

- `bad_pos`: only some of x/y/z were provided, or one coordinate is not an integer.
- `bad_blockmap_radius`: radius is not an integer or is outside `0..16`.
- `no_player`: no local player is loaded and x/y/z were omitted.
- `chunk_not_loaded`: part of the requested map is not loaded.
- `section_out_of_range`: y is outside world build height.
