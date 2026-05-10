### `GET /chunk?x=chunkX&z=chunkZ`

Use to understand the loaded chunk at a high level before fetching any section layout. This endpoint reads only the current client dimension and does not force-load chunks.

Returns a lossy semantic overview:

```json
{
  "code": "ok",
  "data": {
    "dim": "minecraft:overworld",
    "chunk": {"x": 0, "z": 0},
    "summary": {
      "sections": 24,
      "nonEmptySections": 8,
      "nonAir": 21033,
      "topBlocks": [
        {"id": "minecraft:stone", "count": 12000},
        {"id": "minecraft:air", "count": 8000}
      ],
      "heightRangeNonAir": {"min": -20, "max": 91}
    },
    "sections": [
      {
        "sectionY": 4,
        "blockY": {"min": 64, "max": 79},
        "empty": false,
        "nonAir": 812,
        "topBlocks": [{"id": "minecraft:air", "count": 3284}],
        "sectionApi": "/section?x=0&y=4&z=0"
      }
    ]
  }
}
```

