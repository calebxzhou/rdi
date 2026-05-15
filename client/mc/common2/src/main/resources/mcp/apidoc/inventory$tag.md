### `GET /inventory/tag`

Use to find inventory stacks matching an item tag. This is useful when a recipe accepts tags such as `minecraft:logs`, `minecraft:planks`, or `c:ingots/iron`, and the agent needs an exact source slot for a follow-up action.

Query parameters:

- `tag`: required item tag ID. Both `minecraft:logs` and `#minecraft:logs` are accepted.
- `scope`: optional. Defaults to `all`. Accepted values: `all`, `player`, `inventory`, `hotbar`, `main`, `armor`, `offhand`, `container`.
- `limit`: optional. Defaults to `64`, maximum `512`. Limits returned match entries, not the counted item total.

Returns:

```json
{
  "code": "ok",
  "data": {
    "tag": "minecraft:logs",
    "scope": "all",
    "containerOpen": false,
    "totalCount": 12,
    "matchCount": 2,
    "matches": [
      {
        "source": "player",
        "area": "hotbar",
        "slot": 2,
        "menuSlot": 38,
        "item": {
          "section": "hotbar",
          "slot": 2,
          "hotbarSlot": 2,
          "id": "minecraft:oak_log",
          "count": 8
        }
      }
    ]
  }
}
```

Use `source=player` and `area=hotbar/main` slots directly with inventory actions that accept raw player inventory slots. Use `menuSlot` when interacting with the currently open menu. When a container is open, `scope=container` only scans non-player slots in that open menu.
