### `GET /buildings`

Use to read the available building index. The response is Markdown from `mcp/buildings/index.md`.

Call this first whenever the player asks the LLM to build something, such as a house, starter base, shelter, tower, farm, bridge, platform, room, wall, or other structure. Do not guess available templates from memory.

Returns Markdown:

```http
HTTP/1.1 200 OK
Content-Type: text/markdown; charset=utf-8

<building index markdown>
```

Use this before planning a template build. The index contains building IDs and short descriptions.

Workflow after reading the index:

1. Pick the closest matching building ID.
2. Call `GET /buildings/{id}` for size, palette, materials, and valid layers.
3. Call `GET /buildings/{id}?layer=Y` only for the layer currently being built.
4. Build from bottom to top, skipping `.` air cells.
5. If no template matches the request, tell the player the available IDs and ask whether to use the closest one.

Error codes:

- `buildings_not_found`: the bundled building index resource was not found.
