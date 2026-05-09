### `POST /move`

Use to move the current player to a nearby position in the current server dimension.

Query form:

```text
POST /move?x=10.5&y=64&z=-20.5
```

JSON body form:

```json
{
  "x": 10,
  "y": 64,
  "z": -20
}
```

`x`, `y`, and `z` may be integers or decimals. They are interpreted as the player's target feet position.

The target position must be at most 128 blocks from the player's current position. The action is executed on the Minecraft server side and keeps the player's current yaw and pitch.

Important safety rule: the `x/y/z` target is the player's feet position. Do not call this API with an unverified structure center, room center, `/pos` value, or guessed coordinate. A safe target has air/non-colliding space at the feet and head positions, plus a non-air floor below. Prefer `GET /blockmap/walkable` and move only to a `cells[].pos` entry with `symbol="."`.

Returns:

```json
{
  "code": "ok",
  "data": {
    "format": "player-move-v1",
    "moved": true,
    "from": {
      "dim": "minecraft:overworld",
      "x": 10.0,
      "y": 64.0,
      "z": -20.0,
      "yaw": 0.0,
      "pitch": 0.0,
      "chunkX": 0,
      "chunkZ": -2,
      "sectionY": 4
    },
    "to": {
      "dim": "minecraft:overworld",
      "x": 10.5,
      "y": 64.0,
      "z": -20.5,
      "yaw": 0.0,
      "pitch": 0.0,
      "chunkX": 0,
      "chunkZ": -2,
      "sectionY": 4
    },
    "distance": 0.7071067811865476,
    "onGround": true
  }
}
```

Common errors:

- `bad_pos`: `x`, `y`, or `z` is missing, not a number, NaN, or infinity.
- `too_far`: the target is more than 128 blocks from the player.
- `chunk_not_loaded`: the target chunk is not loaded on the server.
- `section_out_of_range`: the target y is outside the world build height.
- `server_mcp_unavailable`: the connected server does not expose the server MCP bridge.

This API does not find a safe floor or path. Use `/blockmap/walkable` first for normal movement. If constructing a target manually, use `POST /blockstate/batch` first to verify the feet block and head block are air and the floor block below is not air.
