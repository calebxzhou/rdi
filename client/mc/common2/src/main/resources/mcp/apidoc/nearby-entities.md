### `GET /nearby-entities?radius=64&category=monster,animal&limit=64`

Use to discover nearby monsters and animals from the client-loaded entity set. This is a brief semantic scan. Use `/entity?uuid=...` afterwards only when full runtime or NBT details are needed.

`pos` is optional and defaults to the local player's current block position. `radius` defaults to `64` and must be `0..128`. `limit` defaults to `64` and must be `1..128`. `category` is comma-separated; common values are `monster`, `animal`, or `all`.

Prefer omitting `pos` when the scan should be centered on the player.

Returns:

```json
{
  "code": "ok",
  "data": {
    "format": "nearby-entities-v1",
    "dim": "minecraft:overworld",
    "center": {
      "block": {"x": 10, "y": 64, "z": -20},
      "chunkX": 0,
      "chunkZ": -2,
      "sectionY": 4
    },
    "radius": 64.0,
    "summary": {
      "total": 8,
      "monsters": 3,
      "animals": 5,
      "nearestMonster": {"type": "minecraft:zombie", "distance": 12.4},
      "nearestAnimal": {"type": "minecraft:cow", "distance": 7.1}
    },
    "entities": [
      {
        "dim": "minecraft:overworld",
        "uuid": "00000000-0000-0000-0000-000000000000",
        "type": "minecraft:zombie",
        "name": "Zombie",
        "category": "monster",
        "pos": {},
        "distance": 12.4,
        "health": 20.0,
        "maxHealth": 20.0,
        "hostile": true,
        "baby": false
      }
    ]
  }
}
```

Use `summary` for quick threat or animal availability answers. Use `entities[].uuid` with `/entity` only for a specific entity that needs detail.

Errors:

- `bad_pos`: provided `pos` is malformed.
- `bad_radius`: `radius` is not a number or is outside `0..128`.
- `bad_limit`: `limit` is not an integer or is outside `1..128`.
- `no_player`: the local player is not in a loaded world.
- `dim_not_loaded`: provided `pos` dimension is not the client's current loaded dimension.

