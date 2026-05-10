### `GET /section?x=chunkX&y=sectionY&z=chunkZ`

Use to understand the structure, layout, and content of one loaded 16x16x16 chunk section. This is not a full deserializable blockstate snapshot. It is a lossy semantic view for LLM reasoning.

Returns:

```json
{
  "code": "ok",
  "data": {
    "dim": "minecraft:overworld",
    "chunk": {"x": 0, "z": 0},
    "sectionY": 4,
    "blockY": {"min": 64, "max": 79},
    "summary": {
      "empty": false,
      "nonAir": 812,
      "paletteSize": 9,
      "topBlocks": [
        {"id": "minecraft:air", "count": 3284},
        {"id": "minecraft:stone", "count": 520}
      ],
      "bboxNonAir": {
        "min": {"x": 0, "y": 64, "z": 0},
        "max": {"x": 15, "y": 78, "z": 15}
      }
    },
    "layers": [
      {
        "y": 0,
        "grid": [
          "................",
          "....AAAA........"
        ],
        "topBlocks": [{"id": "minecraft:air", "count": 240}]
      }
    ],
    "legend": {
      ".": "minecraft:air",
      "A": "minecraft:stone",
      "?": "other"
    },
    "features": {
      "hasFluids": false,
      "hasBlockEntities": true,
      "solidRegions": 3,
      "airRegions": 2
    }
  }
}
```

Layer grid rules:

- `layers[].y` is local section Y `0..15`; world block Y is `blockY.min + layers[].y`.
- Each grid line is local Z, and each character in the line is local X.
- `.` always means air.
- Symbols are explained by `legend`.
- `?` means low-priority blocks grouped as `other`; use `/blockstate` for exact cells when needed.

