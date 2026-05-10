### `GET /blockmap/slice?x=10&y=64&z=-20&radius=8`

Use to read a top-down horizontal block map at one Y level. This is easier for spatial reasoning than a vertical 16x16x16 section.

`x`, `y`, and `z` are optional as a group. If absent, the map is centered on the current player block position. `radius` defaults to `8` and must be `0..16`.

Rows are ordered by increasing Z. Columns are ordered by increasing X. The top-left cell is `center.x-radius, center.z-radius`.

Returns:

```json
{
  "code": "ok",
  "data": {
    "dim": "minecraft:overworld",
    "mode": "slice",
    "center": {"x": 10, "y": 64, "z": -20},
    "radius": 8,
    "axes": {
      "rows": "z increases downward",
      "cols": "x increases rightward",
      "origin": "top-left is center minus radius on x and z"
    },
    "y": 64,
    "scanY": null,
    "summary": {
      "width": 17,
      "height": 17,
      "loadedChunks": 4,
      "topBlocks": [{"id": "minecraft:air", "count": 180}]
    },
    "legend": {
      ".": "minecraft:air",
      "P": "player xz",
      "C": "center xz",
      "A": "minecraft:stone"
    },
    "rows": [
      "....AAAA.........",
      "....A..C........"
    ],
    "cells": []
  }
}
```

Use this API when the user asks what blocks are arranged around a level, room, floor, platform, farm, wall, or terrain surface at a known Y.

Errors:

- `bad_pos`: only some of x/y/z were provided, or one coordinate is not an integer.
- `bad_blockmap_radius`: radius is not an integer or is outside `0..16`.
- `no_player`: no local player is loaded and x/y/z were omitted.
- `chunk_not_loaded`: part of the requested map is not loaded.
- `section_out_of_range`: y is outside world build height.
