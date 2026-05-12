### `GET /recipe?itemId=namespace:path`

Use to fetch recipes whose result item exactly matches a known item registry ID. The client must be in a loaded world because recipes are read from live synced recipe data.

When JEI is installed and ready, this endpoint prefers JEI recipe data. JEI usually understands modded machine recipe categories better because tech mods register their own JEI integration. If JEI is absent, not ready, or returns no match, the endpoint falls back to Minecraft's synced recipe manager.

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
      "source": "minecraft",
      "type": "minecraft:crafting_shaped",
      "category": "building",
      "title": null,
      "runtimeClass": null,
      "inputs": [
        {
          "role": "input",
          "items": [
            {"id": "minecraft:oak_planks", "langKey": "block.minecraft.oak_planks", "count": 1}
          ],
          "fluids": []
        }
      ],
      "outputs": [
        {
          "role": "output",
          "items": [
            {"id": "minecraft:crafting_table", "langKey": "block.minecraft.crafting_table", "count": 1}
          ],
          "fluids": []
        }
      ],
      "catalysts": [],
      "renderOnly": [],
      "extra": {
        "pattern": ["AA", "AA"]
      }
    }
  ]
}
```

If no synced recipe produces the item, `data` is an empty array.

For JEI-sourced recipes, `inputs`, `outputs`, `catalysts`, and `renderOnly` are extracted from JEI's recipe layout. This can include modded machine recipes such as Create crushing, deploying, milling, and mixing when those mods provide JEI compatibility. Machine-specific fields like duration, EU/t, or output chance may not always be structured here; later raw JSON/codec sources can add those fields.

