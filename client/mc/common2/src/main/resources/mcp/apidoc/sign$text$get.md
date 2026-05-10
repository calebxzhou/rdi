### `GET /sign/text?x=10&y=64&z=-20&side=both`

Use to read text from a sign in the current player dimension. This endpoint reads the server-side sign block entity and returns plain text lines plus sign text metadata.

Query parameters:

- `x`, `y`, `z`: required block position.
- `side`: optional, one of `front`, `back`, or `both`; default is `both`.

Returns:

```json
{
  "code": "ok",
  "data": {
    "pos": { "x": 10, "y": 64, "z": -20 },
    "waxed": false,
    "front": {
      "color": "black",
      "glowing": false,
      "lines": ["Base", "Iron: 32", "", ""],
      "hasText": true
    },
    "back": {
      "color": "black",
      "glowing": false,
      "lines": ["", "", "", ""],
      "hasText": false
    }
  }
}
```

When `side=front`, `back` is `null`. When `side=back`, `front` is `null`. Use the default `side=both` unless you already know which face matters.

Use `POST /sign/text` to write Markdown text to one side of a sign. If exact sign NBT or Component JSON is needed, call `/blockentity?pos=dim,x,y,z`.

Common errors:

- `missing_pos`: x/y/z is missing.
- `bad_pos`: x/y/z is not an integer.
- `bad_sign_side`: side is not `front`, `back`, or `both`.
- `no_block_entity`: no loaded block entity exists at the position.
- `not_sign`: target block entity is not a sign.
- `chunk_not_loaded`: target chunk is not loaded.
