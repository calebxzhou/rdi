### `GET /situation?entityRadius=32&resourceChunkRadius=1&resourceSectionRadius=1`

Use before planning an action. This is a compact context snapshot that combines player state, local environment, inventory summary, nearby monsters/animals, and nearby resource hints.

Query parameters are optional. `entityRadius` defaults to `32` and must be `0..128`. `resourceChunkRadius` and `resourceSectionRadius` default to `1` and must be `0..4`.

Returns:

```json
{
  "code": "ok",
  "data": {
    "format": "situation-v1",
    "player": {},
    "environment": {
      "dim": "minecraft:overworld",
      "biome": "minecraft:plains",
      "gameTime": 12000,
      "dayTime": 6000,
      "timeOfDay": 6000,
      "timeBucket": "day",
      "raining": false,
      "thundering": false,
      "difficulty": "normal",
      "light": {"block": 0, "sky": 15, "raw": 15},
      "canSeeSky": true,
      "inWater": false,
      "underWater": false,
      "onGround": true
    },
    "inventory": {
      "selectedHotbarSlot": 0,
      "selectedItem": {"section": "hotbar", "slot": 0, "hotbarSlot": 0, "id": "minecraft:stone_pickaxe", "count": 1},
      "armor": [],
      "offhand": null,
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
    },
    "nearby": {
      "range": {"entityRadius": 32.0, "resourceChunkRadius": 1, "resourceSectionRadius": 1},
      "entities": {},
      "entitySamples": [],
      "resources": {},
      "resourceSamples": []
    }
  }
}
```

Use this endpoint as the first read before deciding what to do next. Call `/inventory`, `/nearby-entities`, or `/nearby-resources` afterwards only when the snapshot is not detailed enough.

