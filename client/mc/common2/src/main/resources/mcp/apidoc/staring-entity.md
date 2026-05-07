### `GET /staring-entity`

Use to identify the entity the local player is looking at. The trace range is 64 blocks.

Returns:

```json
{
  "code": "ok",
  "data": {
    "dim": "minecraft:overworld",
    "uuid": "00000000-0000-0000-0000-000000000000",
    "type": "minecraft:zombie",
    "name": "Zombie",
    "pos": {
      "dim": "minecraft:overworld",
      "x": 12.0,
      "y": 64.0,
      "z": -20.0,
      "yaw": 0.0,
      "pitch": 0.0
    },
    "distance": 4.2
  }
}
```

Call `/entity?uuid=...` after this only if full details are needed.

