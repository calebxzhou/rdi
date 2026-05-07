### `POST /inventory/swap`

Use to swap two slots in the local player's inventory. This is an action API. It sends normal Minecraft inventory click actions so the server accepts and syncs the result.

Request body:

```json
{
  "from": "hotbar:0",
  "to": "main:12",
  "dryRun": false
}
```

Slot references:

- `inventory:0..35`: raw player inventory; `0..8` is hotbar, `9..35` is main inventory.
- `hotbar:0..8`: alias for `inventory:0..8`.
- `main:0..26`: alias for `inventory:9..35`.
- `armor:0..3`: armor slots.
- `offhand:0`: offhand slot.

Returns:

```json
{
  "code": "ok",
  "data": {
    "format": "inventory-swap-v1",
    "from": "inventory:0",
    "to": "inventory:21",
    "dryRun": false,
    "changed": true,
    "beforeFrom": {},
    "beforeTo": {},
    "afterFrom": {},
    "afterTo": {},
    "inventory": {}
  }
}
```

Use `dryRun=true` to preview the two slots without changing inventory. If both slots contain the same stackable item, the API returns `unsupported_merge_risk` instead of accidentally merging stacks.

