### `POST /place/discrete`

Use to place one block type at multiple non-contiguous positions where each target may need a different safe block state. This is useful for spiral stairs, rotated decorations, or sparse template blocks.

If the request needs multiple block IDs, use `/place/palette` instead.

Request body:

```json
{
  "blockId": "minecraft:oak_stairs",
  "targets": [
    {
      "pos": {"x": 10, "y": 64, "z": -20},
      "state": {"facing": "north", "half": "bottom"}
    },
    {
      "pos": {"x": 11, "y": 65, "z": -20},
      "state": {"facing": "east", "half": "bottom"}
    }
  ],
  "dryRun": true
}
```

`blockId` is the block to place. The server scans the player's hotbar and main inventory, counts all matching block items, and consumes them automatically by inventory slot order. Do not choose or swap slots manually before calling this API.

At most 512 targets are accepted. If the inventory does not contain enough matching block items for all targets, the API returns `missing_ingredients` and does not place anything.

Each target `state` is optional. Only safe placement state overrides are accepted:

- `axis`, for logs/pillars and similar blocks.
- `facing`, for blocks with a normal facing property.
- `open`, for blocks that expose this property.
- `rotation`, for blocks that expose this property.
- `half`, only for stairs and trapdoors.

Do not send unsafe or game-logic-owned states such as `waterlogged`, `type`, `part`, `shape`, `powered`, `lit`, or modded runtime properties. Minecraft placement logic decides those.

There is no `inventorySlot` field. There is no `face` field. There is no `stopOnError` field. The API always stops at the first failed target and returns only failed block poses.

Response:

```json
{
  "code": "ok",
  "data": {
    "action": "place",
    "failedBlocks": [
      {
        "pos": {"x": 11, "y": 65, "z": -20},
        "code": "bad_block_state"
      }
    ]
  }
}
```

If `failedBlocks` is empty, all accepted targets succeeded.
