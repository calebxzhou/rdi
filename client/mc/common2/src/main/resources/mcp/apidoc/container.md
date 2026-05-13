### `GET /container?pos=dim,x,y,z&side=north`

Default response is compact and omits item SNBT. Use `GET /container/detail?ref=dim,x,y,z~side` for Markdown, or `GET /container/detail.json?ref=dim,x,y,z~side` for exact JSON/SNBT. `.detail.md` is a compatibility alias for Markdown. Use `view=full` only for debugging or extraction.

Use to read item slots from a container, storage block, or machine block. This endpoint uses the server item handler capability, so it can read vanilla containers and modded machine inventories when they expose item slots.

`side` is optional. Use it when side-specific machine rules matter. Valid values are `up`, `down`, `north`, `south`, `west`, and `east`.

Returns:

```json
{
  "code": "ok",
  "data": {
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

Use these slot numbers with `POST /container/take`, `POST /container/take/batch`, `POST /container/move`, or `POST /container/move/batch`. Use `POST /container/put` or `POST /container/put/batch` for the opposite direction from player inventory into this container.
