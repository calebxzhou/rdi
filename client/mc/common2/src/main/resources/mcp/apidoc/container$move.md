### `POST /container/move`

Use to move items between two block containers or machine inventories. This is an action API. It runs on the server through normal item handler extraction and insertion rules; it does not edit NBT directly.

Request body:

```json
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
  "count": 16,
  "dryRun": false
}
```

`from.slot` is required. `to.slot=null` means auto-insert into the first compatible target slots. Use `dryRun=true` to preview the transfer without changing either container.

Returns:

```json
{
  "code": "ok",
  "data": {
    "dryRun": false,
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
    "from": {},
    "to": {}
  }
}
```

Call `GET /container` for both source and target before moving. Use the exact source slot from the source response. Prefer `to.slot=null` unless a machine requires a specific target slot. Re-read `/container` after the action only when you need the updated slot state.

Use `POST /container/move/batch` when moving multiple known source slots between containers.

Use `POST /container/take` when the source is a known block container and the target is the current player's inventory.

