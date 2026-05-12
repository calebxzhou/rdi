### `POST /item/drop`

Drop an item stack from the player's inventory as an item entity at a specific loaded world position.

Use this when the player asks to throw, drop, or place an inventory item on the ground at a known coordinate. This endpoint only uses the player's inventory; it does not drop from open menus or block containers.

Request JSON:

```json
{
  "from": "hotbar:0",
  "count": 1,
  "pos": {
    "dim": "minecraft:overworld",
    "x": 12.5,
    "y": 64.0,
    "z": -8.5
  },
  "pickupDelay": 20,
  "dryRun": false
}
```

`from` uses the same player inventory aliases as `/inventory/swap`:

- `inventory:0..35`
- `hotbar:0..8`
- `main:0..26`
- `armor:0..3`
- `offhand:0`

`pos.dim` is optional and defaults to the current player dimension. If provided, it must match the current dimension. The target chunk must already be loaded and the target must be within 64 blocks of the player.

Use `dryRun=true` before dropping valuable items or when the source slot is uncertain. A dry run validates the slot, count, target position, dimension, distance, and loaded chunk, but does not remove items or create an entity.

Success response:

```json
{
  "code": "ok",
  "data": {
    "code": "ok",
    "from": "inventory:0",
    "dryRun": false,
    "changed": true,
    "requestedCount": 1,
    "droppedCount": 1,
    "beforeItem": {"id": "minecraft:cobblestone", "count": 64},
    "afterItem": {"id": "minecraft:cobblestone", "count": 63},
    "entity": {
      "uuid": "11111111-1111-1111-1111-111111111111",
      "itemId": "minecraft:cobblestone",
      "count": 1,
      "snbt": "{count:1,id:\"minecraft:cobblestone\"}",
      "pos": {"dim": "minecraft:overworld", "x": 12.5, "y": 64.0, "z": -8.5}
    },
    "inventory": {}
  }
}
```

Common error codes:

- `bad_request`: missing `from`, `count`, or `pos`, malformed JSON, or invalid `pickupDelay`.
- `bad_pos`: coordinates are missing, non-finite, or outside world bounds.
- `dim_not_loaded`: `pos.dim` does not match the current dimension.
- `chunk_not_loaded`: the target chunk is not loaded.
- `too_far`: target position is more than 64 blocks away.
- `protected`: the player cannot interact at the target position.
- `bad_slot`: `from` is not a supported player inventory slot.
- `empty_source`: source slot is empty.
- `bad_count`: `count` is larger than the source stack.
