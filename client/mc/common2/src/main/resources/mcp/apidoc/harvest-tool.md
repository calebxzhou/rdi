### `GET /harvest-tool?blockId=namespace:path`

Use to ask the server which tools can harvest a block and what drops each tool can produce. This endpoint uses the server `rdi:mcp` bridge and active server loot tables.

You can also use a world position:

```text
/harvest-tool?pos=10,64,-20
```

Use `pos` when block state or block entity data may matter. `pos` is `x,y,z` in the current dimension. Use `blockId` for a generic default-state answer.

Returns:

```json
{
  "code": "ok",
  "data": {
    "stateSource": "default",
    "block": {
      "id": "minecraft:diamond_ore",
      "state": "minecraft:diamond_ore",
      "pos": null,
      "requiresCorrectToolForDrops": true,
      "mineableWith": ["pickaxe"],
      "minimumTier": "iron"
    },
    "scenarios": [
      {
        "tool": {
          "id": "minecraft:air",
          "category": "hand",
          "tier": null,
          "enchantments": {}
        },
        "correctToolForDrops": false,
        "harvestable": false,
        "drops": []
      },
      {
        "tool": {
          "id": "minecraft:iron_pickaxe",
          "category": "pickaxe",
          "tier": "iron",
          "enchantments": {}
        },
        "correctToolForDrops": true,
        "harvestable": true,
        "drops": [
          {"id": "minecraft:diamond", "countMin": 1, "countMax": 1, "snbt": "{count:1,id:\"minecraft:diamond\"}"}
        ]
      },
      {
        "tool": {
          "id": "minecraft:diamond_pickaxe",
          "category": "pickaxe",
          "tier": "diamond",
          "enchantments": {"minecraft:silk_touch": 1}
        },
        "correctToolForDrops": true,
        "harvestable": true,
        "drops": [
          {"id": "minecraft:diamond_ore", "countMin": 1, "countMax": 1, "snbt": "{count:1,id:\"minecraft:diamond_ore\"}"}
        ]
      },
      {
        "tool": {
          "id": "minecraft:diamond_pickaxe",
          "category": "pickaxe",
          "tier": "diamond",
          "enchantments": {"minecraft:fortune": 3}
        },
        "correctToolForDrops": true,
        "harvestable": true,
        "drops": [
          {"id": "minecraft:diamond", "countMin": 1, "countMax": 4, "snbt": "{count:1,id:\"minecraft:diamond\"}"}
        ]
      }
    ],
    "notes": [
      "Drops are simulated on the server from the active loot table.",
      "Wrong tools return no drops when the block requires a correct tool.",
      "Random loot is sampled 16 times and summarized as countMin/countMax."
    ]
  }
}
```

Use `mineableWith` and `minimumTier` for the short answer. Use `scenarios` when the user asks what changes with silk touch, fortune, shears, or wrong tools.

