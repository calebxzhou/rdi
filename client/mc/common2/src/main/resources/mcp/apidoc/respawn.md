### `POST /respawn`

Use to respawn the current player after confirming the player is dead. This action runs on the server through Minecraft's normal respawn logic.

Request body is optional and ignored:

```json
{}
```

Returns:

```json
{
  "code": "ok",
  "data": {
    "format": "respawn-v1",
    "wasDead": true,
    "respawned": true,
    "hardcore": false,
    "beforeHealth": 0.0,
    "afterHealth": 20.0,
    "before": {
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
    "after": {
      "dim": "minecraft:overworld",
      "x": 0.5,
      "y": 64.0,
      "z": 0.5,
      "yaw": 0.0,
      "pitch": 0.0,
      "chunkX": 0,
      "chunkZ": 0,
      "sectionY": 4
    }
  }
}
```

If the player is already alive, the API returns `wasDead=false` and `respawned=false`.

Use this API only after `GET /player` shows `health<=0`, or after an action such as `/move` returns the same `from` and `to` position and `GET /player` confirms death. After a successful respawn, call `/situation` or `/player` again before continuing the previous task.
