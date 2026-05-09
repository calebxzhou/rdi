### `GET /blockstate?x=10&y=64&z=-20`

Use when you already know the block position and only need block ID/state. This is cheaper than `/blockentity`.

This endpoint reads only the client's current loaded dimension. Pass only block coordinates.

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

Errors:

- `missing_pos`: one of `x`, `y`, or `z` is absent or blank.
- `bad_pos`: one of `x`, `y`, or `z` is not an integer.
- `no_player`: the local player is not in a loaded world.

