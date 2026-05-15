### `POST /item/use-on-block`

Use to right-click a loaded block with an item through Minecraft's normal item-on-block interaction logic. This is the general endpoint for actions such as using wheat or saplings on a composter, using bottles or buckets on fluid-like blocks, inserting records into jukeboxes, applying tools to blocks, and modded block item interactions.

Request body:

```json
{
  "pos": "10,64,-20",
  "face": "up",
  "itemId": "minecraft:wheat",
  "fromInventorySlot": 12,
  "hand": "mainhand",
  "times": 1,
  "dryRun": false
}
```

Fields:

- `pos` is required and uses `x,y,z` in the current dimension.
- `face` is optional. Valid values are `up`, `down`, `north`, `south`, `west`, and `east`. Default is `up`.
- `itemId` is optional when `fromInventorySlot` or the requested hand already identifies the item. If present, the source stack must match it.
- `fromInventorySlot` is optional and uses player inventory slots `0..35`; hotbar is `0..8`, main inventory is `9..35`.
- `hand` is optional. Use `mainhand` by default, or `offhand` when the interaction must come from the offhand.
- `times` is optional, defaults to `1`, and must be `1..64`.
- `dryRun=true` validates target, source item, range, and loaded chunk without performing the click.

The server can temporarily use an item from hotbar/main inventory for the interaction and then restore the selected hotbar slot. You do not need to call `/hotbar/select` first when `itemId` or `fromInventorySlot` is known.

Returns:

```json
{
  "code": "ok",
  "data": {
    "action": "item_use_on_block",
    "dryRun": false,
    "pos": {"x": 10, "y": 64, "z": -20},
    "face": "up",
    "requestedItemId": "minecraft:wheat",
    "requestedInventorySlot": 12,
    "hand": "mainhand",
    "requestedTimes": 1,
    "performedTimes": 1,
    "changedBlock": true,
    "beforeBlockId": "minecraft:composter",
    "afterBlockId": "minecraft:composter",
    "beforeBlockState": "minecraft:composter[level=3]",
    "afterBlockState": "minecraft:composter[level=4]",
    "itemBefore": {},
    "itemAfter": {},
    "inventory": {}
  }
}
```

Errors:

- `bad_pos`: `pos` is missing or not `x,y,z`.
- `bad_side`: `face` is present but is not a valid direction.
- `bad_item_id`: `itemId` is not a loaded item ID.
- `bad_slot`: `fromInventorySlot` is outside `0..35`.
- `bad_count`: `times` is outside `1..64`.
- `too_far`: target is outside the allowed interaction range.
- `chunk_not_loaded`: target chunk is not loaded.
- `protected`: the server refused interaction at the target.
- `no_usable_item`: the requested source item is absent, empty, or mismatched.
- `item_use_failed`: Minecraft item-on-block logic refused the first requested click.
