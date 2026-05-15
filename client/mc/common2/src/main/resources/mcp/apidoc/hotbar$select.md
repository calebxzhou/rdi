### `POST /hotbar/select`

Use to change which hotbar slot the current player is holding. This action runs on the server and syncs the selected slot back to the client.

Request body:

```json
{
  "slot": 3,
  "dryRun": false
}
```

`slot` must be a hotbar slot `0..8`. Main inventory, armor, and offhand slots are not valid for this API.

Returns:

```json
{
  "code": "ok",
  "data": {
    "dryRun": false,
    "requestedSlot": 3,
    "beforeSlot": 0,
    "afterSlot": 3,
    "beforeSelectedItem": {
      "section": "hotbar",
      "slot": 0,
      "hotbarSlot": 0,
      "id": "minecraft:stone_pickaxe",
      "count": 1
    },
    "afterSelectedItem": {
      "section": "hotbar",
      "slot": 3,
      "hotbarSlot": 3,
      "id": "minecraft:torch",
      "count": 32
    },
    "inventory": {}
  }
}
```

Call `GET /inventory` first when you need to choose the slot by item ID. If the desired item is in the main inventory, first use `POST /inventory/swap` or `POST /inventory/move` to put it into hotbar `0..8`, then call `POST /hotbar/select`.
