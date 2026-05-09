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

`x`, `y`, and `z` may be integers or decimals. They are interpreted as the requested feet-position search center.

The final safe position selected by the server must be at most 128 blocks from the player's current position. The action is executed on the Minecraft server side and keeps the player's current yaw and pitch.

Important safety rule: the `x/y/z` target is a nearby search center, not a guaranteed final position. The API searches within 4 blocks of it for a safe standable feet position, then moves the player to the center of the selected safe block. A safe target has empty/non-colliding feet and head spaces, a solid floor below, and no obvious hazard such as lava, fire, magma block, cactus, or sweet berry bush. If no nearby safe position is found, the API returns an error and does not move the player. Prefer `GET /blockmap/walkable` and pass a `cells[].pos` entry with `symbol="."` when possible.

After a successful move, the API immediately gives the player `Slow Falling` for 3 seconds.

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

The `to` field is the player's actual position after the server-side move attempt. It may differ from the requested `x/y/z` because the server can choose a nearby safe block. If `moved=false`, or if `to.dim/x/y/z` equals `from.dim/x/y/z`, the player did not actually move. This often means the player is dead or otherwise unable to teleport. Immediately call `GET /player` and check `data.health`. If health is `0` or below, call `POST /respawn`, then re-read `/situation` or `/player` before continuing.

Common errors:

- `bad_pos`: `x`, `y`, or `z` is missing, not a number, NaN, or infinity.
- `too_far`: the selected safe target is more than 128 blocks from the player.
- `chunk_not_loaded`: no loaded candidate blocks were available near the requested search center.
- `section_out_of_range`: no candidate blocks near the requested search center are inside the world build height.
- `move_target_blocked`: no nearby safe standable block was found.
- `server_mcp_unavailable`: the connected server does not expose the server MCP bridge.

This API finds a nearby safe standable destination, but it does not pathfind. Use `/blockmap/walkable` first for normal movement and pass a known walkable `cells[].pos` when possible. Always trust `data.to` as the actual moved position.
