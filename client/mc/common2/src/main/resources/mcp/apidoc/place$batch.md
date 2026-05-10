### `POST /place/batch`

Use to place the player's current main hand block item into multiple sparse target positions. Each target follows the same rules as `/place`: loaded, within 32 blocks of the player, target block is air, and Minecraft server placement logic decides the final result.

At most 512 positions are accepted per request. The action continues through per-position failures and returns only failed positions.

Request body:

```json
{
  "positions": [
    {"x": 10, "y": 64, "z": -20},
    {"x": 11, "y": 64, "z": -20}
  ]
}
```

Returns `RMcpBlockBatchActionData`. Successful targets are omitted to reduce response noise; only failed block poses are returned:

```json
{
  "code": "ok",
  "data": {
    "action": "place",
    "failedBlocks": [
      {
        "pos": {"x": 10, "y": 64, "z": -20},
        "code": "target_not_air"
      }
    ]
  }
}
```

If `failedBlocks` is empty, all accepted targets succeeded.

