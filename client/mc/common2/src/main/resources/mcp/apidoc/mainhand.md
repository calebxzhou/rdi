### `GET /mainhand`

Use to get the local player's current main hand item stack. The response uses a compact ItemStack format for LLM reading.

Returns:

```json
{
  "code": "ok",
  "data": {
    "id": "minecraft:diamond_pickaxe",
    "count": 1,
    "snbt": "{count:1,id:\"minecraft:diamond_pickaxe\"}"
  }
}
```

If the main hand is empty, `data` is `null`.

