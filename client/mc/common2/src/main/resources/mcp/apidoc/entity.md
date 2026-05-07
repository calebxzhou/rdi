### `GET /entity?uuid=uuid`

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

