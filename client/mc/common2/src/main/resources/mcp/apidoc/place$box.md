### `POST /place/box`

Use to place one block type from the player's inventory into every position inside a continuous cuboid in the current dimension. This is for small filled shapes, floors, walls, columns, or template layer runs. At most 512 blocks are accepted after expanding the box.

Request body:

```json
{
  "blockId": "minecraft:oak_log",
  "startPos": {"x": 10, "y": 64, "z": -20},
  "endOffset": {"x": 3, "y": 0, "z": 0},
  "state": {"axis": "x"},
  "dryRun": true
}
```

`endOffset` is relative to `startPos`, inclusive. The example places 4 blocks from `10,64,-20` through `13,64,-20`. Offsets can be negative.

`blockId` is the block to place. The server scans the player's hotbar and main inventory, counts all matching block items, and consumes them automatically by inventory slot order. Do not choose or swap slots manually before calling this API.

If the inventory does not contain enough matching block items for the whole expanded box, the API returns `missing_ingredients` and does not place anything.

`state` is optional. Only safe placement state overrides are accepted:

- `axis`, for logs/pillars and similar blocks.
- `facing`, for blocks with a normal facing property.
- `open`, for blocks that expose this property.
- `rotation`, for blocks that expose this property.
- `half`, only for stairs and trapdoors.

Do not send unsafe or game-logic-owned states such as `waterlogged`, `type`, `part`, `shape`, `powered`, `lit`, or modded runtime properties. Minecraft placement logic decides those.

There is no `inventorySlot` field. There is no `face` field. There is no `stopOnError` field. The API always stops at the first failed target and returns only failed block poses.

The response shape is the same as `/place/batch`: `data.action` plus `data.failedBlocks`. Use `dryRun=true` before the real action when the block count or state override is uncertain.

