### `POST /blockstate/batch`

Use when you already know several block positions and need exact block ID/state for each one. This is cheaper than calling `/blockstate` repeatedly.

The request reads only the client's current loaded dimension. If `dim` is absent or blank, the current player dimension is used. If `dim` is provided and is not the current loaded dimension, the request fails with `dim_not_loaded`.

At most 512 positions are accepted per request.

Request body:

```json
{
  "dim": "minecraft:overworld",
  "positions": [
    {"x": 10, "y": 64, "z": -20},
    {"x": 11, "y": 64, "z": -20}
  ]
}
```

Returns `RMcpBlockStateBatchData`:

```json
{
  "code": "ok",
  "data": {
    "format": "blockstate-batch-v1",
    "dim": "minecraft:overworld",
    "requestedCount": 2,
    "blocks": [
      {
        "pos": {"x": 10, "y": 64, "z": -20},
        "id": "minecraft:oak_log",
        "state": "minecraft:oak_log[axis=y]"
      },
      {
        "pos": {"x": 11, "y": 64, "z": -20},
        "id": "minecraft:air",
        "state": "minecraft:air"
      }
    ]
  }
}
```

Air is valid data and returns an air block state, not an error.

Errors:

- `bad_positions`: request body is missing, empty, malformed, or has no `positions`.
- `too_many_blocks`: more than 512 positions were requested.
- `no_player`: the local player is not in a loaded world.
- `dim_not_loaded`: requested `dim` is not the client's current loaded dimension.
