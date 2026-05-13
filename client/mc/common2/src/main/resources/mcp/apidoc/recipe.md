### `GET /recipe?itemId=namespace:path`

Use to fetch an LLM-readable recipe summary for recipes whose result item exactly matches a known item registry ID. The client must be in a loaded world because recipes are read from live synced recipe data and JEI when available.

Default response is compact. It hides high-noise recipe kinds such as loot tables and decorative/chisel variants, aggregates repeated inputs, and gives a stable `ref` for each shown recipe. Use the Markdown `detail` URL only for recipes that are actually relevant. Use `detailJson` when exact structured fields are needed.

Example:

```text
/recipe?itemId=minecraft:stone
```

Returns:

```json
{
  "code": "ok",
  "data": {
    "itemId": "minecraft:stone",
    "totalCount": 87,
    "shownCount": 4,
    "hiddenCount": 83,
    "recipes": [
      {
        "ref": "minecraft~minecraft_smelting~1~6f2a9c1d",
        "id": "minecraft:stone",
        "source": "minecraft",
        "type": "minecraft:smelting",
        "kind": "crafting_or_smelting",
        "category": "blocks",
        "title": null,
        "inputItems": [{"id": "minecraft:cobblestone", "name": "block.minecraft.cobblestone", "count": 1}],
        "inputFluids": [],
        "inputTags": [],
        "outputItems": [{"id": "minecraft:stone", "name": "block.minecraft.stone", "count": 1}],
        "outputFluids": [],
        "catalysts": [],
        "detail": "/recipe/detail?itemId=minecraft:stone&ref=minecraft~minecraft_smelting~1~6f2a9c1d",
        "detailJson": "/recipe/detail.json?itemId=minecraft:stone&ref=minecraft~minecraft_smelting~1~6f2a9c1d"
      }
    ]
  }
}
```

Query options:

- `limit=1..50`: maximum summary entries, default `16`.
- `kind=crafting_or_smelting|machine|conversion|loot|decorative`: show only one recipe kind.
- `includeHidden=true` or `include=all`: include loot/decorative entries that are hidden by default.
- `view=full`: return the old full recipe JSON array. Use only for debugging or when compact/detail data is insufficient.

Recipe kinds:

- `crafting_or_smelting`: crafting table, furnace, blasting, smoking, campfire, and similar vanilla-style recipes.
- `machine`: modded JEI machine recipes.
- `conversion`: cutting, compression/decompression, and similar transformations.
- `loot`: block/chest/entity loot style entries. Usually noisy for crafting questions.
- `decorative`: large decorative variant sets, such as chisel-style stone variants. Usually noisy for crafting questions.

### `GET /recipe/detail?itemId=namespace:path&ref=...`

Returns concise Markdown for one recipe by `ref`.

### `GET /recipe/detail.json?itemId=namespace:path&ref=...`

Returns exact full JSON for one recipe by `ref`.

`ref` is generated from the current `itemId` recipe list as:

```text
{source}~{type}~{index}~{hash}
```

Every ref part is URL-safe. The hash is calculated from the recipe content, and the index is the original position in the full recipe list. The server does not store recipe state; it recomputes the same refs for the current live recipe list.

### `GET /recipe/detail.md?itemId=namespace:path&ref=...`

Alias for `GET /recipe/detail`; kept for compatibility.

Rules:

- Call `/item-search?text=...` first when the player gave a display name instead of an exact `namespace:path`.
- Read compact `/recipe` first.
- Pick a relevant summary entry by `kind`, `title`, inputs, outputs, and catalysts.
- Open Markdown `detail` only when the compact entry does not contain enough information.
- Open `detailJson` only when exact structured fields are needed.
- Use `view=full` only for debugging or data extraction, not normal planning.

Common error codes:

- `missing_item_id`: `itemId` is absent or blank.
- `missing_recipe_ref`: `ref` is absent or blank for detail endpoints.
- `bad_recipe_ref`: `ref` is malformed or no longer matches the current live recipe list for that `itemId`.
- `bad_limit`: `limit` is not in `1..50`.
