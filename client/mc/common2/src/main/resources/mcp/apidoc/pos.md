### `GET /pos`

Use to get the local player's current dimension, position, yaw, and pitch.
`chunkX`, `chunkZ`, and `sectionY` are derived from the block position using floor division, so negative coordinates match Minecraft chunk math.

Returns `RMcpPosData`:

```json
{
  "code": "ok",
  "data": {
    "dim": "minecraft:overworld",
    "x": 10.5,
    "y": 64.0,
    "z": -20.5,
    "yaw": 90.0,
    "pitch": 15.0,
    "chunkX": 0,
    "chunkZ": -2,
    "sectionY": 4
  }
}
```

