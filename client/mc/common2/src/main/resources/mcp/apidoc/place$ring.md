### `POST /place/ring`

Use to place one block type from the player's inventory into only the outer ring or framework of a box in the current dimension. This is for foundations, rectangular outlines, simple frames, and cuboid edge skeletons. At most 512 target blocks are accepted after expanding the ring.

Request body:

```json
{
  "blockId": "minecraft:oak_planks",
  "startPos": {"x": 10, "y": 64, "z": -20},
  "endOffset": {"x": 4, "y": 0, "z": 4},
  "state": {},
  "dryRun": true
}
```

`endOffset` is relative to `startPos`, inclusive. Offsets can be negative. The example describes a flat 5x5 area and places only its outside ring, so it targets 16 blocks instead of 25.

Ring expansion rules:

- If one axis changes, every block on that line is targeted.
- If two axes change, only the rectangle outline is targeted.
- If three axes change, only the cuboid edges are targeted; faces and interior are skipped.

Use `/place/box` when the whole cuboid should be filled. Use `/place/discrete` or `/place/palette` when the shape is irregular or needs mixed block IDs.

`blockId` is the block to place. The server scans the player's hotbar and main inventory, counts all matching block items, and consumes them automatically by inventory slot order. Do not choose or swap slots manually before calling this API.

If the inventory does not contain enough matching block items for the expanded ring, the API returns `missing_ingredients` and does not place anything.

`state` is optional. Only safe placement state overrides are accepted:

- `axis`, for logs/pillars and similar blocks.
- `facing`, for blocks with a normal facing property.
- `open`, for blocks that expose this property.
- `rotation`, for blocks that expose this property.
- `half`, only for stairs and trapdoors.

Do not send unsafe or game-logic-owned states such as `waterlogged`, `type`, `part`, `shape`, `powered`, `lit`, or modded runtime properties. Minecraft placement logic decides those.

There is no `inventorySlot` field. There is no `face` field. There is no `stopOnError` field. The API always stops at the first failed target and returns only failed block poses.

The response shape is the same as `/place/batch`: `data.action` plus `data.failedBlocks`. Use `dryRun=true` before the real action when the block count, target area, or state override is uncertain.
