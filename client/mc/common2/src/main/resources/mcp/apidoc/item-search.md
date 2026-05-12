### `GET /item-search?text=query`

Use to resolve a player-visible item name, Chinese name, English name, screenshot tooltip text, common name, registry path, or full registry ID into concrete loaded item IDs.

Call this before `/recipe`, `/item/use-on-block`, `/place/box`, `/place/discrete`, `/place/palette`, or any action that needs an exact item/block ID when the player did not provide `namespace:path`.

Example:

```text
/item-search?text=盆栽盒&modId=bonsaitrees4
```

`modId` is optional. Pass it when the user mentions a mod or a screenshot tooltip shows the mod name/namespace. In modpacks, common names may refer to modded items instead of vanilla items.

Returns:

```json
{
  "code": "ok",
  "data": {
    "text": "盆栽盒",
    "modId": "bonsaitrees4",
    "limit": 10,
    "results": [
      {
        "itemId": "bonsaitrees4:bonsaipot",
        "namespace": "bonsaitrees4",
        "langkey": "block.bonsaitrees4.bonsaipot",
        "englishName": "Bonsai Pot",
        "chineseName": "盆栽盒",
        "modId": "bonsaitrees4",
        "modName": "Bonsai Trees 4",
        "score": 6.0,
        "match": "exact_chinese"
      }
    ]
  }
}
```

Use `results[0].itemId` only when it is clearly the requested item. If several candidates are plausible and the user did not provide enough context, ask a short clarification instead of guessing.

Rules:

- Do not invent `minecraft:*` from memory for user-visible names.
- Prefer candidates from `modId` when the screenshot or user text identifies a mod.
- Prefer exact Chinese/English display-name matches over fuzzy matches.
- Prefer modded candidates over vanilla candidates when both match a generic name and the user is playing a modpack.
- Use `/recipe?itemId=results[0].itemId` after choosing the target item.

Common error codes:

- `missing_text`: `text` is absent or blank.
- `bad_limit`: `limit` is not an integer in `1..50`.
