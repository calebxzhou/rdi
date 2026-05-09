### `POST /container/take/batch`

Use to move multiple item stacks from known containers, storage blocks, or machine blocks into the current player's inventory. This action runs each step on the server through normal item handler extraction rules; it does not require opening a container GUI and does not edit NBT directly.

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
      "toInventorySlot": null,
      "count": 16
    },
    {
      "from": {
        "pos": "minecraft:overworld,10,64,-20",
        "side": null,
        "slot": 1
      },
      "toInventorySlot": 8,
      "count": 1
    }
  ],
  "dryRun": false,
  "stopOnError": false
}
```

At most 64 moves are accepted per request. Each `from.slot` is required and must come from `GET /container?pos=...`. `toInventorySlot=null` means auto-insert into the first compatible player inventory slots. Pass a specific `toInventorySlot` only when an item must go into a known hotbar or main inventory slot.

`toInventorySlot` is the raw player inventory slot from `GET /inventory`: hotbar slots are `0..8`, main inventory slots are `9..35`. Armor and offhand are not valid for this API.

Steps run in array order, and each later step sees the inventory/container state changed by earlier successful steps. Use `dryRun=true` to preview the batch without changing inventory or containers. Use `stopOnError=true` when later steps depend on earlier steps.

Returns:

```json
{
  "code": "ok",
  "data": {
    "format": "container-take-batch-v1",
    "dryRun": false,
    "stopOnError": false,
    "requestedMoves": 2,
    "succeededMoves": 2,
    "failedMoves": 0,
    "totalMovedCount": 17,
    "results": [
      {
        "index": 0,
        "code": "ok",
        "requestedCount": 16,
        "movedCount": 16,
        "movedItem": {},
        "from": {},
        "toInventorySlot": null,
        "targetInventorySlots": [0, 9],
        "beforeToInventorySlot": null,
        "afterToInventorySlot": null,
        "inventory": {},
        "container": {}
      }
    ]
  }
}
```

Inspect every `results[].code`. The top-level `code=ok` means the batch request was accepted, not that every move succeeded.

Call `GET /container?pos=...` first to choose source slots. Call `GET /inventory` first when choosing a specific `toInventorySlot`. Prefer `toInventorySlot=null` unless hotbar or exact slot placement is required.

Use `POST /container/put/batch` for the opposite direction, from the current player's inventory into known block containers.
