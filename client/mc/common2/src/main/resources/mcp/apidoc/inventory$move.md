### `POST /inventory/move`

Use to move a specific number of items from one local player inventory slot to another. This is an action API. It sends normal Minecraft inventory click actions so the server accepts and syncs the result.

Request body:

```json
{
  "from": "inventory:21",
  "to": "hotbar:2",
  "count": 16,
  "dryRun": false
}
```

Slot references are the same as `POST /inventory/swap`.

Returns:

```json
{
  "code": "ok",
  "data": {
    "from": "inventory:21",
    "to": "inventory:2",
    "count": 16,
    "dryRun": false,
    "changed": true,
    "movedCount": 16,
    "beforeFrom": {},
    "beforeTo": {},
    "afterFrom": {},
    "afterTo": {},
    "inventory": {}
  }
}
```

Use this endpoint when the requested action is "move N items" or "put N items into a slot". The target slot must be empty or contain the same item stack components, and it must have enough remaining stack capacity. Use `dryRun=true` when the slot choice is uncertain.

