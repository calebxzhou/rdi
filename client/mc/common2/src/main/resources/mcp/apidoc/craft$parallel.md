### `POST /craft/parallel`

Use to run several independent crafting-table recipes from exact player inventory slots in one action. This is a parallel batch API: every craft is planned from the inventory state at the start of the request. A craft in the same request cannot use another craft's output as an ingredient.

Request body:

```json
{
  "crafts": [
    {
      "slots": {
        "A": 19
      },
      "shape": "AA|AA",
      "outputSlot": 2,
      "times": 1
    }
  ],
  "dryRun": false
}
```

Each `crafts[]` entry uses the same `slots`, `shape`, `outputSlot`, and `times` fields as `POST /craft`, except `dryRun` belongs to the whole batch. The batch accepts at most 64 entries.

The server aggregates all ingredient consumption before applying outputs. If multiple entries consume the same source slot and the combined count is larger than the starting stack count, all entries that depend on that slot fail with `missing_ingredients`. Other independent entries may still succeed.

Returns:

```json
{
  "code": "ok",
  "data": {
    "action": "craft-parallel",
    "dryRun": false,
    "changed": true,
    "requestedCount": 4,
    "craftedCount": 4,
    "results": [
      {
        "index": 0,
        "recipeId": "minecraft:stone",
        "resultId": "minecraft:stone",
        "requestedCount": 4,
        "craftedCount": 4,
        "outputSlot": 2
      }
    ],
    "failedCrafts": [],
    "before": {},
    "after": {}
  }
}
```

Use this for independent repeated conversions such as several groups of small stones into larger stones. Use `POST /craft` for sequential crafting where a later craft must use a previous craft's output.
