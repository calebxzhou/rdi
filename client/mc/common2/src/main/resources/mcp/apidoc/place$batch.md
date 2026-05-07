### `POST /place/batch`

Use to place the player's current main hand block item into multiple sparse target positions. Each target follows the same rules as `/place`: loaded, less than 9 blocks from the player, target block is air, and Minecraft server placement logic decides the final result.

At most 512 positions are accepted per request. The action continues through per-position failures and reports each position separately.

Request body:

```json
{
  "positions": [
    {"x": 10, "y": 64, "z": -20},
    {"x": 11, "y": 64, "z": -20}
  ]
}
```

Returns `RMcpBlockBatchActionData`:

```json
{
  "code": "ok",
  "data": {
    "format": "block-batch-action-v1",
    "action": "place",
    "requestedCount": 2,
    "changedCount": 2,
    "failedCount": 0,
    "results": [
      {
        "pos": {"x": 10, "y": 64, "z": -20},
        "code": "ok",
        "changed": true,
        "beforeBlockId": "minecraft:air",
        "afterBlockId": "minecraft:dirt"
      }
    ],
    "mainHandBefore": {},
    "mainHandAfter": {},
    "inventory": {}
  }
}
```

