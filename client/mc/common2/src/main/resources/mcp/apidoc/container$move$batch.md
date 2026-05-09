### `POST /container/move/batch`

Use to move multiple item stacks between block containers or machine inventories. This is an action API. It runs each step on the server through normal item handler extraction and insertion rules; it does not edit NBT directly.

Request body:

```json
{
  "moves": [
    {
      "from": {
        "pos": "minecraft:overworld,10,64,-20",
        "side": null,
        "slot": 0
      },
      "to": {
        "pos": "minecraft:overworld,11,64,-20",
        "side": null,
        "slot": null
      },
      "count": 16
    },
    {
      "from": {
        "pos": "minecraft:overworld,10,64,-20",
        "side": null,
        "slot": 1
      },
      "to": {
        "pos": "minecraft:overworld,11,64,-20",
        "side": null,
        "slot": null
      },
      "count": 64
    }
  ],
  "dryRun": false,
  "stopOnError": false
}
```

At most 64 moves are accepted per request. Each `from.slot` is required. Each `to.slot=null` means auto-insert into the first compatible target slots. Steps run in array order, and each later step sees the container state changed by earlier successful steps.

Use `dryRun=true` to preview the whole batch without changing containers. Use `stopOnError=true` when later steps depend on earlier steps.

Returns:

```json
{
  "code": "ok",
  "data": {
    "format": "container-move-batch-v1",
    "dryRun": false,
    "stopOnError": false,
    "requestedMoves": 2,
    "succeededMoves": 2,
    "failedMoves": 0,
    "totalMovedCount": 80,
    "results": [
      {
        "index": 0,
        "code": "ok",
        "requestedCount": 16,
        "movedCount": 16,
        "movedItem": {},
        "from": {},
        "to": {},
        "fromContainer": {},
        "toContainer": {}
      }
    ]
  }
}
```

Inspect every `results[].code`. The top-level `code=ok` means the batch request was accepted, not that every move succeeded.

Call `GET /container` for source and target containers before moving. Use exact source slots from the source response. Prefer `to.slot=null` unless a machine requires a specific target slot.

Use this API only for container-to-container movement. If the source is the current player's inventory and the destination is a known block position, use `POST /container/put` or `POST /container/put/batch`. If the source is a known block container and the target is the player's inventory, use `POST /container/take` or `POST /container/take/batch`.
