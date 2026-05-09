### `POST /craft`

Use to craft from exact player inventory slots. This is an action API. The server builds the virtual crafting grid from `shape`, matches the normal Minecraft crafting recipe server-side, consumes ingredients from the specified inventory slots, and puts the result into `outputSlot`. No crafting table block, crafting table GUI, open menu, or recipe book state is required.

Request body:

```json
{
  "slots": {
    "A": 19,
    "B": 12
  },
  "shape": "AAA|ABA|AAA",
  "outputSlot": 2,
  "times": 1,
  "dryRun": false
}
```

`slots` maps each non-empty shape symbol to a raw player inventory slot `0..35`. `shape` is 1 to 3 rows separated by `|`; every row must have the same width `1..3`; use `.` for empty cells. `outputSlot` is a raw player inventory slot `0..35`. `times` defaults to `1` and means how many times to perform the matched recipe.

Returns:

```json
{
  "code": "ok",
  "data": {
    "format": "craft-v2",
    "recipeId": "minecraft:diamond_pickaxe",
    "resultId": "minecraft:diamond_pickaxe",
    "requestedCount": 1,
    "craftedCount": 1,
    "dryRun": false,
    "outputSlot": 2,
    "inventory": {}
  }
}
```

Use `/recipe?itemId=...` and `/inventory` first to decide the shape and source slots. Use `dryRun=true` to verify the shape, ingredient counts, result item, and output slot capacity without consuming materials.

