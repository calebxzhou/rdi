### `POST /break/batch`

Use to break multiple sparse target positions using the player's current main hand item. Each target follows the same rules as `/break`. At most 512 positions are accepted per request.

The player does not need to face, look at, or stand next to the target blocks. This is a server-side remote break within 32 blocks per target. If targets are too far, move closer with `/move`, then retry the remaining positions.

Request body:

```json
{
  "positions": [
    {"x": 10, "y": 64, "z": -20},
    {"x": 11, "y": 64, "z": -20}
  ]
}
```

The response shape is the same as `/place/batch`, with `"action": "break"`.

Do not include any player container, storage block, chest, barrel, shulker box, machine inventory, or modded container in a batch break unless the player explicitly asks for it. If breaking such a block is necessary, ask the player for permission before calling this API.

After a successful batch break, pick up harvested drops by default unless the user explicitly says not to pick them up. Use `POST /entity/pickup-item?radius=64&limit=256`.

