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

