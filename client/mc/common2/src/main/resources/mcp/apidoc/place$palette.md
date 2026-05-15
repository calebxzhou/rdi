### `POST /place/palette`

Use to place multiple block types at multiple target positions. This is the preferred API for mixed-material structures, patterned floors/walls, machine layouts, and template-like layouts where repeated symbols can share one block definition.

Request body:

```json
{
  "palette": {
    "A": {"blockId": "minecraft:oak_planks"},
    "B": {"blockId": "minecraft:oak_stairs", "state": {"facing": "north", "half": "bottom"}},
    "C": {"blockId": "minecraft:glass"}
  },
  "targets": [
    {"pos": {"x": 10, "y": 64, "z": -20}, "key": "A"},
    {"pos": {"x": 11, "y": 64, "z": -20}, "key": "B"},
    {"pos": {"x": 12, "y": 64, "z": -20}, "key": "C"}
  ],
  "dryRun": true
}
```

Each `targets[].key` must reference one key in `palette`. Use multiple palette keys for the same `blockId` when the same block needs different states, such as stair directions.

The server scans the player's hotbar and main inventory, counts all matching block items by `blockId`, and consumes them automatically by inventory slot order. Do not choose or swap slots manually before calling this API.

At most 512 targets are accepted. If the inventory does not contain enough matching block items for the whole request, the API returns `missing_ingredients` and does not place anything.

Palette `state` is optional. Only safe placement state overrides are accepted:

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
        "pos": {"x": 11, "y": 64, "z": -20},
        "code": "target_not_air"
      }
    ]
  }
}
```

If `failedBlocks` is empty, all accepted targets succeeded.
