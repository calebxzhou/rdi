### `POST /break/batch`

Use to break multiple sparse target positions using the player's current main hand item. Each target follows the same rules as `/break`. At most 512 positions are accepted per request.

Request body:

```json
{
  "positions": [
    {"x": 10, "y": 64, "z": -20},
    {"x": 11, "y": 64, "z": -20}
  ]
}
```

The response shape is the same as `/place/batch`, with `"action": "break"`.

