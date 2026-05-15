### `POST /container/put`

Use to move items from the current player's inventory into a known container, storage block, or machine block at a specific position. This action runs on the server through the target block's item handler insertion rules; it does not require opening the container GUI and does not edit NBT directly.

Request body:

```json
{
  "fromInventorySlot": 12,
  "to": {
    "pos": "10,64,-20",
    "side": null,
    "slot": 0
  },
  "count": 16,
  "dryRun": false
}
```

`fromInventorySlot` is the raw player inventory slot from `GET /inventory`: hotbar slots are `0..8`, main inventory slots are `9..35`. Armor and offhand are not valid for this API.

`to.slot=null` means auto-insert into the first compatible target slots. Pass a specific `to.slot` only when a machine requires a known input slot. Use `dryRun=true` to preview the transfer without changing the player inventory or target container.

Returns:

```json
{
  "code": "ok",
  "data": {
    "dryRun": false,
    "fromInventorySlot": 12,
    "requestedCount": 16,
    "movedCount": 16,
    "movedItem": {
      "slot": -1,
      "id": "minecraft:iron_ingot",
      "count": 16,
      "limit": 64,
      "canInsert": true,
      "snbt": "{count:16,id:\"minecraft:iron_ingot\"}"
    },
    "to": {}
  }
}
```

Call `GET /inventory` to choose the source inventory slot. Call `GET /container?pos=...` when you need to inspect target slots first. Prefer `to.slot=null` unless a machine input slot is already known. Re-read `/inventory` or `/container` after the action only when you need updated slot state.

Use `POST /container/put/batch` when moving multiple known player inventory slots into containers.

Use `POST /container/take` for the opposite direction, from a known block container into the current player's inventory.
