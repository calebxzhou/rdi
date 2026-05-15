### `GET /inventory`

Use to get the local player's inventory slots as CSV. This is the default inventory format and is intended for LLM slot selection. Empty slots are returned as `minecraft:air` with `count=0`. It omits raw `snbt`; use `GET /inventory/detail?section=hotbar&slot=0` or `GET /inventory/detail?slot=13` only when exact NBT for one slot is needed.

Returns:

```csv
section,slot,id,count
hotbar,0,minecraft:stone_pickaxe,1
hotbar,1,minecraft:air,0
inventory,13,minecraft:oak_planks,28
inventory,14,minecraft:air,0
armor,0,minecraft:iron_boots,1
offhand,0,minecraft:torch,32
```

CSV fields:

- `section`: `hotbar`, `inventory`, `armor`, or `offhand`.
- `slot`: section slot. For `hotbar` and `inventory`, this is the raw player inventory slot usable by inventory actions.
- `id`: item ID, or `minecraft:air` for an empty slot.
- `count`: stack count, or `0` for an empty slot.

Use `GET /inventory/detail?section=...&slot=...` for exact NBT of one CSV row. If `section` is omitted, `slot=0..35` is interpreted as a raw player inventory slot.

Detail response:

```json
{
  "code": "ok",
  "data": {
    "section": "inventory",
    "slot": 13,
    "hotbarSlot": null,
    "id": "minecraft:oak_planks",
    "count": 28,
    "snbt": "{count:28,id:\"minecraft:oak_planks\"}"
  }
}
```
