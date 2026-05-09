### `POST /menu/drop`

Use to throw items out of the currently open Minecraft menu. First call `GET /menu`, choose a slot from `data.slots[]`, then call this endpoint.

Body:

```json
{
  "slot": 12,
  "count": 1,
  "dryRun": false
}
```

Query parameters are also accepted:

```text
POST /menu/drop?slot=12&count=1&dryRun=true
```

Rules:

- `slot` must be a menu slot index returned by `GET /menu`.
- `count` must be positive and no larger than the current stack count in that slot.
- Use `count=1` to throw one item.
- Use `count` equal to the stack count to throw the whole stack.
- Use `dryRun=true` before throwing valuable items.

Returns:

```json
{
  "code": "ok",
  "data": {
    "format": "menu-drop-v1",
    "dryRun": false,
    "slot": 12,
    "requestedCount": 1,
    "droppedCount": 1,
    "changed": true,
    "beforeSlot": {},
    "afterSlot": {},
    "menu": {}
  }
}
```
