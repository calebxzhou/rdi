### `POST /crafting/open`

Use to open a nearby crafting table GUI for 3x3 crafting without requiring the player to look at the table. This is an action API. It asks the server to find a real crafting table near the player and open its normal crafting menu.

Request body:

```json
{
  "radius": 4,
  "dryRun": false
}
```

`radius` defaults to `4` and must be `0..8`. The server still uses normal interaction distance, so a larger scan radius only helps find candidates that are actually interactable.

Returns:

```json
{
  "code": "ok",
  "data": {
    "format": "crafting-open-v1",
    "opened": true,
    "dryRun": false,
    "menu": "crafting",
    "radius": 4,
    "tablePos": {"x": 10, "y": 64, "z": -20}
  }
}
```

Call this before a 3x3 crafting action if the current menu is not already a crafting table. Use `dryRun=true` to check whether a usable nearby table exists without opening the menu.

