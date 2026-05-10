### `POST /place?x=10&y=64&z=-20&face=up`

Use to place the player's current main hand block item into a target air block position. The target position must be in the current dimension, loaded, and within 32 blocks of the player.

The target block must be air. The server uses normal item block placement logic, so item consumption, placement rules, collision checks, block entity data, sounds, and game events follow Minecraft rules.

`face` is optional and can be `up`, `down`, `north`, `south`, `west`, or `east`. Use it when the placed block has direction-sensitive state. For example, logs use the clicked face axis, so `face=up` or `face=down` is better for vertical logs, while `face=east` or `face=west` is better for X-axis logs.

If `face` is absent, the server first tries to click a nearby non-air block face. If no adjacent block exists, it still tries to place the block at the air target, so ordinary blocks can float when Minecraft rules allow it.

Returns:

```json
{
  "code": "ok",
  "data": {
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

Errors:

- `bad_pos`: `x`, `y`, or `z` is missing or is not an integer.
- `bad_side`: `face` is present but is not a valid direction.
- `no_player`: the local player is not in a loaded world.
- `too_far`: target is outside the allowed interaction range.
- `chunk_not_loaded`: target chunk is not loaded.
- `target_not_air`: target block is already occupied.
- `no_place_item`: the main hand item is not a placeable block item.
- `place_failed`: Minecraft placement logic refused the action.

