### `GET /terrain/profile?axis=x&x=10&y=64&z=-20&length=17&verticalRadius=16`

Use to understand terrain height relationships along one horizontal line. This is the preferred compact API for slopes, cliffs, stairs, cave mouths, pits, bridges, and short route planning when vertical relationships matter.

`axis` is required and must be `x` or `z`. `x`, `y`, and `z` are optional as a group. If absent, the profile is centered on the current player block position. `length` defaults to `17`, must be odd, and must be `1..33`. `verticalRadius` defaults to `16` and must be `1..64`.

The API scans from `center.y + verticalRadius` down to `center.y - verticalRadius`. Each point reports the first standable feet position in that column, plus the floor, feet, and head blocks.

Legend:

- `P`: player XZ
- `C`: center XZ
- `.`: walkable
- `^`: step up from previous point
- `v`: step down from previous point
- `|`: cliff or height change greater than 2
- `#`: blocked
- `~`: fluid
- `!`: hazard
- `_`: drop/no floor in scan range

Returns:

```json
{
  "code": "ok",
  "data": {
    "format": "terrain-profile-v1",
    "dim": "minecraft:overworld",
    "axis": "x",
    "center": {"x": 10, "y": 64, "z": -20},
    "length": 17,
    "scanY": {"min": 48, "max": 80},
    "summary": {
      "minSurfaceY": 62,
      "maxSurfaceY": 68,
      "maxStep": 4,
      "hasCliff": true,
      "hasOpenBelow": false,
      "hasFluid": false,
      "walkable": 14,
      "blocked": 2,
      "drops": 1
    },
    "legend": {
      ".": "walkable",
      "^": "step up from previous point",
      "v": "step down from previous point",
      "|": "cliff or height change greater than 2"
    },
    "profile": "..^^C..|_",
    "points": [
      {
        "offset": 0,
        "pos": {"x": 10, "y": 65, "z": -20},
        "surfaceY": 64,
        "standY": 65,
        "floorBlockId": "minecraft:grass_block",
        "feetBlockId": "minecraft:air",
        "headBlockId": "minecraft:air",
        "walkable": true,
        "deltaFromPrev": 1,
        "symbol": "C",
        "note": "walkable"
      }
    ]
  }
}
```

Use `profile` for quick reasoning and `points[]` for exact coordinates. `deltaFromPrev` compares `standY` to the previous point's `standY`.

Errors:

- `bad_axis`: `axis` is missing or is not `x` or `z`.
- `bad_pos`: only some of x/y/z were provided, or one coordinate is not an integer.
- `bad_terrain_length`: `length` is not an odd integer in `1..33`.
- `bad_vertical_radius`: `verticalRadius` is not an integer in `1..64`.
- `no_player`: no local player is loaded and x/y/z were omitted.
- `chunk_not_loaded`: part of the requested profile is not loaded.
- `section_out_of_range`: scan Y range is outside world build height.
