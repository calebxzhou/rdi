### `GET /container?pos=dim,x,y,z&side=north`

Use to read item slots from a container, storage block, or machine block. This endpoint uses the server item handler capability, so it can read vanilla containers and modded machine inventories when they expose item slots.

`side` is optional. Use it when side-specific machine rules matter. Valid values are `up`, `down`, `north`, `south`, `west`, and `east`.

Returns:

```json
{
  "code": "ok",
  "data": {
    "format": "container-v1",
    "dim": "minecraft:overworld",
    "pos": {"x": 10, "y": 64, "z": -20},
    "side": null,
    "slots": 27,
    "items": [
      {
        "slot": 0,
        "id": "minecraft:iron_ingot",
        "count": 32,
        "limit": 64,
        "canInsert": true,
        "snbt": "{count:32,id:\"minecraft:iron_ingot\"}"
      }
    ]
  }
}
```

Only occupied slots are listed in `items`. Use `slots` to know the valid slot range.

