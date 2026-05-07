### `POST /break/box`

Use to break every block inside the cuboid formed by two vertices. At most 512 blocks are accepted after expanding the box.

Request body:

```json
{
  "from": {"x": 10, "y": 64, "z": -20},
  "to": {"x": 13, "y": 65, "z": -20}
}
```

The response shape is the same as `/break/batch`.

Batch and box action rules:

- Use `/place/batch` or `/break/batch` for non-contiguous positions.
- Use `/place/box` or `/break/box` only when every block in the cuboid should be acted on.
- Inspect `results[].code`; the top-level `code=ok` means the request was accepted, not that every block changed.
- Re-read `/inventory` or use the returned `inventory` after a large action because item count, durability, and drops may change.

