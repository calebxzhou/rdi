### `POST /break?x=10&y=64&z=-20`

Use to break a loaded block using the player's current main hand item. The target position must be in the current dimension and less than 9 blocks from the player.

The server uses normal block breaking logic, so tool durability, harvest checks, drops, block break events, protection checks, and item break events follow Minecraft and NeoForge rules.

Returns:

```json
{
  "code": "ok",
  "data": {
    "format": "block-action-v1",
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

