### `POST /break?x=10&y=64&z=-20`

Use to break a loaded block using the player's current main hand item. The target position must be in the current dimension, loaded, and within 32 blocks of the player.

The player does not need to face, look at, or stand next to the target block. This is a server-side remote break within 32 blocks. If the API returns `too_far`, then choose a nearby safe position and call `/move` before retrying `/break`.

The server uses normal block breaking logic, so tool durability, harvest checks, drops, block break events, protection checks, and item break events follow Minecraft and NeoForge rules.

Do not break any player container, storage block, chest, barrel, shulker box, machine inventory, or modded container unless the player explicitly asks for it. If breaking such a block is necessary, ask the player for permission before calling this API.

After a successful break, pick up harvested drops by default unless the user explicitly says not to pick them up. Use `POST /entity/pickup-item?radius=64&limit=256`.

Returns:

```json
{
  "code": "ok",
  "data": {
    "action": "break",
    "changed": true,
    "pos": {"x": 10, "y": 64, "z": -20},
    "beforeBlockId": "minecraft:stone",
    "afterBlockId": "minecraft:air",
    "mainHandBefore": {},
    "mainHandAfter": {},
    "inventory": {}
  }
}
```

Errors:

- `bad_pos`: `x`, `y`, or `z` is missing or is not an integer.
- `no_player`: the local player is not in a loaded world.
- `too_far`: target is outside the allowed 32-block interaction range. Move closer with `/move`, then retry.
- `chunk_not_loaded`: target chunk is not loaded.
- `no_block`: target position is air.
- `break_failed`: Minecraft block breaking logic refused the action.

