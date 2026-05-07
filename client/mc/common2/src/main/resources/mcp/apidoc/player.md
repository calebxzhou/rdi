### `GET /player?uuid=uuid&detail=true`

Use to fetch player data. If `uuid` is absent, the local player is used. If `detail=true` is absent, a brief response is returned.

Brief response:

```json
{
  "code": "ok",
  "data": {
    "dim": "minecraft:overworld",
    "uuid": "00000000-0000-0000-0000-000000000000",
    "name": "Player",
    "pos": {},
    "health": 20.0,
    "maxHealth": 20.0,
    "food": 20,
    "gameMode": "survival",
    "latency": 50
  }
}
```

Detailed response:

```json
{
  "code": "ok",
  "data": {
    "brief": {},
    "entity": {},
    "profile": {},
    "player": {},
    "inventory": {}
  }
}
```

Use brief mode by default. Use `detail=true` only when inventory, profile, NBT, or extra player runtime fields are needed.

