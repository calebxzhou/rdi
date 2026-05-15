### `GET /mainhand`

Use to get the local player's current main hand item stack. The response uses a compact ItemStack format for LLM reading. Add `detail=true` only when exact raw `snbt` is needed.

Returns:

```json
{
  "code": "ok",
  "data": {
    "id": "minecraft:diamond_pickaxe",
    "count": 1
  }
}
```

If the main hand is empty, `data` is `null`.

