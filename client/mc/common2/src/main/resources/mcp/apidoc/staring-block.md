### `GET /staring-block?fluid=true`

Use to inspect the block the local player is looking at. The trace range is long enough for normal inspection. Add `fluid=true` only when fluid information is needed.

Returns:

```json
{
  "code": "ok",
  "data": {
    "dim": "minecraft:overworld",
    "pos": {"x": 10, "y": 64, "z": -20},
    "id": "minecraft:water",
    "state": "minecraft:water[level=0]",
    "fluid": {
      "id": "minecraft:water",
      "state": "minecraft:water[level=0]"
    }
  }
}
```

If `fluid` is omitted or false, `fluid` is `null`.

