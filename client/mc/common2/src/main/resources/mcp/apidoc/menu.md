### `GET /menu`

Use to inspect the currently open Minecraft menu, including player inventory, chest, machine, crafting, or other server-synced container menus.

The `slots[].slot` values are menu slot indexes. They are not `/inventory` aliases and are not guaranteed to match raw player inventory indexes. Call this endpoint immediately before `POST /menu/drop`.

Returns:

```json
{
  "code": "ok",
  "data": {
    "dim": "minecraft:overworld",
    "containerId": 3,
    "menuClass": "net.minecraft.world.inventory.ChestMenu",
    "inventoryMenu": false,
    "carriedItem": null,
    "slotCount": 63,
    "slots": [
      {
        "slot": 0,
        "empty": false,
        "mayPickup": true,
        "id": "minecraft:iron_ingot",
        "count": 32,
        "limit": 64,
        "snbt": "{count:32,id:\"minecraft:iron_ingot\"}"
      }
    ]
  }
}
```

`empty=true` slots have `id=null`, `count=0`, and `snbt=null`.
