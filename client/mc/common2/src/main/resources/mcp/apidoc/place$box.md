### `POST /place/box`

Use to place blocks into every position inside the cuboid formed by two vertices. This is for small filled shapes, floors, walls, or columns. At most 512 blocks are accepted after expanding the box.

Request body:

```json
{
  "from": {"x": 10, "y": 64, "z": -20},
  "to": {"x": 13, "y": 64, "z": -20}
}
```

The response shape is the same as `/place/batch`.

