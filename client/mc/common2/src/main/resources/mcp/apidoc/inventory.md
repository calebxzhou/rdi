### `GET /inventory`

Use to get the local player's full inventory in an LLM-readable form. It separates hotbar, main inventory, armor, and offhand, and includes a summary for quick capability checks.

Returns:

```json
{
  "code": "ok",
  "data": {
    "dim": "minecraft:overworld",
    "selectedHotbarSlot": 0,
    "selectedItem": {
      "section": "hotbar",
      "slot": 0,
      "hotbarSlot": 0,
      "id": "minecraft:stone_pickaxe",
      "count": 1,
      "snbt": "{count:1,id:\"minecraft:stone_pickaxe\"}"
    },
    "hotbar": [],
    "items": [],
    "armor": [],
    "offhand": [],
    "summary": {
      "occupiedSlots": 5,
      "emptySlots": 36,
      "totalItems": 80,
      "topItems": [{"id": "minecraft:cobblestone", "count": 64}],
      "hasFood": true,
      "hasTool": true,
      "hasWeapon": false,
      "hasBlock": true
    }
  }
}
```

Use `summary` first. Read `hotbar`, `items`, `armor`, and `offhand` when the exact stack or slot matters.

