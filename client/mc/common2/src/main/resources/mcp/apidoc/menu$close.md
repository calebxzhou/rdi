### `POST /menu/close`

Use to close the currently open Minecraft menu or container before retrying player inventory actions. This endpoint does not move items and has no request body.

Rules:

- If the player is already on the default inventory menu, this is a no-op and returns `changed=false`, `wasOpen=false`.
- If a container, chest, machine, crafting, or other non-default menu is open, it closes that menu and returns `changed=true`, `wasOpen=true`.
- If `GET /menu` shows `data.carriedItem` is not `null`, do not close the menu. The endpoint returns `carried_item_not_empty`.

Recovery flow for `busy_container_open` from `/inventory/swap` or `/inventory/move`:

1. Call `GET /menu`.
2. If `data.carriedItem` is `null`, call `POST /menu/close`. If it is not `null`, stop and resolve the carried cursor item first.
3. Call `GET /inventory`.
4. Retry the original inventory action with fresh slot data.

Returns:

```json
{
  "code": "ok",
  "data": {
    "changed": true,
    "wasOpen": true,
    "before": {},
    "after": {}
  }
}
```
