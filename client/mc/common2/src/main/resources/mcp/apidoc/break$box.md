### `POST /break/box`

Use to break every block inside the cuboid formed by two vertices. At most 512 blocks are accepted after expanding the box.

The player does not need to face, look at, or stand next to the target blocks. This is a server-side remote break within 32 blocks per target. If targets are too far, move closer with `/move`, then retry the remaining area.

Request body:

```json
{
  "from": {"x": 10, "y": 64, "z": -20},
  "to": {"x": 13, "y": 65, "z": -20}
}
```

The response shape is the same as `/break/batch`.

Batch and box action rules:

- Use `/place/batch` or `/break/batch` for non-contiguous positions.
- Use `/place/box` or `/break/box` only when every block in the cuboid should be acted on.
- Do not include any player container, storage block, chest, barrel, shulker box, machine inventory, or modded container in a box break unless the player explicitly asks for it. If breaking such a block is necessary, ask the player for permission before calling this API.
- Inspect `results[].code`; the top-level `code=ok` means the request was accepted, not that every block changed.
- Re-read `/inventory` or use the returned `inventory` after a large action because item count, durability, and drops may change.
- After a successful break box action, pick up harvested drops by default unless the user explicitly says not to. Use `POST /entity/pickup-item?radius=64&limit=256`.
