### `POST /entity/pickup-item`

Use to directly pick up still dropped item entities into the player's inventory. The item entities must be loaded in the current server dimension, inside `radius`, and not moving.

Direct nearby pickup:

```text
POST /entity/pickup-item?radius=64&limit=256
```

This picks up nearby non-moving dropped items without needing `ids`.

Filtered pickup:

1. Optionally call `/nearby-entities?category=item&radius=64&limit=256`.
2. Choose dropped item entity UUIDs from `entities[].uuid`.
3. Call this API with those UUIDs in `ids`.

Request body:

```json
{
  "radius": 64,
  "limit": 256,
  "ids": [
    "11111111-1111-1111-1111-111111111111",
    "22222222-2222-2222-2222-222222222222"
  ]
}
```

Simple query form is also accepted:

```text
POST /entity/pickup-item?ids=11111111-1111-1111-1111-111111111111,22222222-2222-2222-2222-222222222222
```

Parameters:

- `ids`: optional UUID strings. If omitted, the server scans nearby loaded item entities and picks up non-moving ones.
- `radius`: optional pickup radius, `1..64`, default `64`.
- `limit`: optional auto-scan pickup limit, `0..2048`, default `256`. `limit=0` returns only inventory with no pickup when `ids` is omitted.

When `ids` is provided, it may include at most 2048 UUID strings. Duplicate UUIDs are ignored by the HTTP layer.

Returns a batch result. A failed item does not stop later item pickups:

```json
{
  "code": "ok",
  "data": {
    "requestedCount": 2,
    "pickedCount": 1,
    "failedCount": 1,
    "results": [
      {
        "id": "11111111-1111-1111-1111-111111111111",
        "code": "ok",
        "picked": true,
        "pickedCount": 3,
        "beforeItem": "{id:\"minecraft:iron_ingot\",count:3}",
        "remainingItem": null,
        "motionSqr": 0.0,
        "distance": 4.2
      },
      {
        "id": "22222222-2222-2222-2222-222222222222",
        "code": "moving_item_entity",
        "picked": false,
        "pickedCount": 0,
        "beforeItem": "{id:\"minecraft:oak_log\",count:1}",
        "remainingItem": "{id:\"minecraft:oak_log\",count:1}",
        "motionSqr": 0.03,
        "distance": 5.1
      }
    ],
    "inventory": {}
  }
}
```

Use `results[].code` per entity. `remainingItem=null` means the entity stack was fully picked up or the entity disappeared. If the inventory only accepts part of a stack, `picked=true`, `pickedCount` is the moved count, and `remainingItem` contains the remaining stack SNBT.

Errors:

- `bad_ids`: `ids` contains malformed UUID text or more than 2048 entries.
- `bad_radius`: `radius` is not a number or is outside `1..64`.
- `bad_limit`: `limit` is not an integer or is outside `0..2048`.
- `no_player`: the local player is not in a loaded world.
- `server_mcp_unavailable`: the connected server does not expose the server-side RMCP bridge.
- `server_timeout`: the server did not answer the request in time.

Per-item result codes:

- `ok`: that item entity was picked up at least partially.
- `bad_ids`: one entry is not a UUID string.
- `no_entity`: the entity is not loaded or already disappeared.
- `not_item_entity`: the UUID belongs to a non-item entity.
- `too_far`: the item entity is outside `radius`.
- `moving_item_entity`: the item entity still has motion and is refused.
- `inventory_full`: the player's inventory could not accept any item from that entity.
