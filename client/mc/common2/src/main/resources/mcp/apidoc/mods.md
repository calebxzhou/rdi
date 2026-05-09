### `GET /mods`

Use to read running NeoForge mod IDs from the current Minecraft client.

Without `id`, returns a compact array of loaded mod IDs:

```json
{
  "code": "ok",
  "data": [
    "minecraft",
    "neoforge",
    "rdi"
  ]
}
```

With `id`, returns detailed metadata for that mod:

```http
GET /mods?id=rdi
```

```json
{
  "code": "ok",
  "data": {
    "id": "rdi",
    "name": "RDI",
    "version": "1.0.0",
    "description": "",
    "dependencies": []
  }
}
```

Dependency entries contain:

```json
{
  "id": "neoforge",
  "versionRange": "[21.0,)",
  "type": "required",
  "ordering": "none",
  "side": "both"
}
```

Common errors:

- `bad_mod_id`: `id` is blank or contains invalid characters.
- `no_mod`: no running mod has that id.

Call `/mods` first when you only need to know whether a mod is loaded. Call `/mods?id=...` only when name, version, description, or dependencies are needed.
