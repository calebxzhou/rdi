### `GET /buildings/{id}`

Use to read an LLM-friendly schematic summary for one bundled building.

Call this after `GET /buildings` when the player asks the LLM to build something and a building ID has been selected. Do not start placing blocks until this endpoint has been read.

`id` must be a building ID from `GET /buildings`. The endpoint reads `mcp/buildings/{id}.schem` and returns generated Markdown.

By default this endpoint returns the summary and palette only, with no layer map. Use `GET /buildings/{id}?layer=Y` to read one y layer. Layer responses return only that layer map, without repeating the summary or palette, to save tokens. `Y` is the schematic-local layer index, from `0` to `height - 1`.

Build workflow:

1. Read `GET /buildings/{id}` without `layer` to get size, palette, and `layerRange`.
2. Choose a safe origin in the current world.
3. Keep the palette from the summary response in memory.
4. For each y layer from bottom to top, call `GET /buildings/{id}?layer=Y`.
5. Read each layer carefully before building it; do not skip rows or columns.
6. For every non-`.` symbol, map it through the palette from the summary response to the modern `minecraft:*` block resource location.
7. Place only target cells that should contain blocks; never place air.
8. Use exact block checks before placement when the target area may already contain blocks.

Returns Markdown:

```http
HTTP/1.1 200 OK
Content-Type: text/markdown; charset=utf-8

# Building: starter_house
...
```

The Markdown includes:

- size and entity counts
- palette symbols with both legacy `blockId:data` and modern `minecraft:*` block resource locations
- `layerRange`, showing valid y layer indexes

When `layer=Y` is provided, the Markdown includes only that y-layer map.

Layer map format: each row is z and each character is x. `.` is air and should be skipped.

Error codes:

- `bad_building_id`: the id is empty or contains invalid characters.
- `bad_building_layer`: `layer` is present but is not an integer or is outside the building height.
- `unknown_building`: no bundled schematic exists for that id.
- `bad_building_schematic`: the schematic exists but could not be parsed.
