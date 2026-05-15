### `POST /container/take`

Use to move items from a known container, storage block, or machine block into the current player's inventory. This action runs on the server through the source block's item handler extraction rules; it does not require opening the container GUI and does not edit NBT directly.

Request body:

```json
{
  "from": {
    "pos": "10,64,-20",
    "side": null,
    "slot": 0
  },
  "toInventorySlot": null,
  "count": 16,
  "dryRun": false
}
```

`from.slot` is required and must come from `GET /container?pos=...`. `toInventorySlot=null` means auto-insert into the first compatible player inventory slots. Pass a specific `toInventorySlot` only when the item must go into a known hotbar or main inventory slot.

`toInventorySlot` is the raw player inventory slot from `GET /inventory`: hotbar slots are `0..8`, main inventory slots are `9..35`. Armor and offhand are not valid for this API.

Use `dryRun=true` to preview the transfer without changing the player inventory or source container.

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
    "toInventorySlot": null,
    "targetInventorySlots": [0, 9]
  }
}
```

Call `GET /container?pos=...` first to choose `from.slot`. Call `GET /inventory` first when choosing a specific `toInventorySlot`. Prefer `toInventorySlot=null` unless a hotbar or exact slot placement is required. Re-read `/inventory` or `/container` after the action only when you need updated slot state.

Use `POST /container/take/batch` when moving multiple known container slots into the current player's inventory.

Common errors:

- `bad_request`: body is malformed, `from` is missing, `from.slot` is missing, or `count<=0`.
- `bad_pos`: `from.pos` is not `x,y,z`.
- `bad_side`: `from.side` is not one of `up`, `down`, `north`, `south`, `west`, or `east`.
- `bad_slot`: `from.slot` or `toInventorySlot` is outside the valid range.
- `empty_source`: source container slot is empty or cannot extract items.
- `target_full`: the player's inventory cannot accept the item.
- `incompatible_target`: a specific `toInventorySlot` contains an incompatible stack.
- `no_item_handler`: source block does not expose an item container or machine inventory.
- `chunk_not_loaded`: source block chunk is not loaded.
- `too_far`: source block is outside the allowed interaction range.
