### `POST /container/put/batch`

Use to move multiple item stacks from the current player's inventory into known containers, storage blocks, or machine blocks. This action runs each step on the server through normal item handler insertion rules; it does not require opening a container GUI and does not edit NBT directly.

Request body:

```json
{
  "moves": [
    {
      "fromInventorySlot": 12,
      "to": {
        "pos": "minecraft:overworld,10,64,-20",
        "side": null,
        "slot": null
      },
      "count": 16
    },
    {
      "fromInventorySlot": 13,
      "to": {
        "pos": "minecraft:overworld,10,64,-20",
        "side": null,
        "slot": 1
      },
      "count": 8
    }
  ],
  "dryRun": false,
  "stopOnError": false
}
```

At most 64 moves are accepted per request. Each `fromInventorySlot` is a raw player inventory slot from `GET /inventory`: hotbar slots are `0..8`, main inventory slots are `9..35`. Armor and offhand are not valid for this API.

Each `to.slot=null` means auto-insert into the first compatible target slots. Pass a specific `to.slot` only when a machine requires a known input slot. Steps run in array order, and each later step sees the inventory/container state changed by earlier successful steps.

Use `dryRun=true` to preview the batch without changing inventory or containers. Use `stopOnError=true` when later steps depend on earlier steps.

Returns:

```json
{
  "code": "ok",
  "data": {
    "action": "container-put",
    "failedMoves": [
      {
        "index": 1,
        "code": "target_full"
      }
    ]
  }
}
```

Inspect `failedMoves`. If it is empty, every accepted move succeeded. The top-level `code=ok` means the batch request was accepted.

Call `GET /inventory` to choose source slots. Call `GET /container?pos=...` when choosing specific target slots. Prefer `to.slot=null` unless machine slot rules are already known. Re-read `/inventory` or `/container` after the batch only when you need updated slot state.

Use `POST /container/take/batch` for the opposite direction, from known block containers into the current player's inventory.
