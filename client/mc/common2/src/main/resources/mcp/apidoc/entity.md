### `GET /entity?uuid=uuid`

Default response is compact and omits raw SNBT. Use `GET /entity/detail?ref=uuid` for Markdown, or `GET /entity/detail.json?ref=uuid` for exact JSON/NBT. `.detail.md` is a compatibility alias for Markdown. Use `view=full` only for debugging or extraction.

Use to fetch detailed data for a loaded entity by UUID.

Returns:

```json
{
  "code": "ok",
  "data": {
    "dim": "minecraft:overworld",
    "uuid": "00000000-0000-0000-0000-000000000000",
    "type": "minecraft:zombie",
    "name": "Zombie",
    "pos": {},
    "runtime": {},
    "nbt": {},
    "snbt": "{...}"
  }
}
```

Use `type`, `name`, and `pos` for concise answers. Use `nbt` or `snbt` only for exact entity state.

