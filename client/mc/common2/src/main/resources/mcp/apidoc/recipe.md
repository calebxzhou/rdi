### `GET /recipe?itemId=namespace:path`

Use to fetch recipes whose result item exactly matches a known item registry ID. The client must be in a loaded world because recipes are read from the live synced recipe manager.

Example:

```text
/recipe?itemId=minecraft:crafting_table
```

Returns:

```json
{
  "code": "ok",
  "data": [
    {
      "id": "minecraft:crafting_table",
      "type": "minecraft:crafting_shaped",
      "group": "",
      "special": false,
      "result": {
        "id": "minecraft:crafting_table",
        "langKey": "block.minecraft.crafting_table",
        "count": 1
      },
      "category": "building",
      "key": {
        "A": {
          "items": [
            {"id": "minecraft:oak_planks", "langKey": "block.minecraft.oak_planks", "count": 1}
          ]
        }
      },
      "pattern": ["AA", "AA"]
    }
  ]
}
```

If no synced recipe produces the item, `data` is an empty array.

