### `POST /place?x=10&y=64&z=-20`

Use to place the player's current main hand block item into a target air block position. The target position must be in the current dimension, loaded, and less than 9 blocks from the player.

The target block must be air. The server automatically finds a non-air adjacent block face and uses normal item block placement logic, so item consumption, placement rules, collision checks, block entity data, sounds, and game events follow Minecraft rules.

Returns:

```json
{
  "code": "ok",
  "data": {
    "format": "block-action-v1",
    "action": "place",
    "changed": true,
    "pos": {"x": 10, "y": 64, "z": -20},
    "beforeBlockId": "minecraft:air",
    "afterBlockId": "minecraft:dirt",
    "mainHandBefore": {},
    "mainHandAfter": {},
    "inventory": {}
  }
}
```

