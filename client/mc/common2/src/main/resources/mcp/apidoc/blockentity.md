### `GET /blockentity?pos=dim,x,y,z`

Use for detailed block entity data at a known position. This is for chests, machines, signs, spawners, and modded blocks that store extra data. This endpoint uses the `rdi:mcp` server bridge, so the returned data is read from the server rather than from the client's partial block entity copy.

Returns:

```json
{
  "code": "ok",
  "data": {
    "dim": "minecraft:overworld",
    "pos": {"x": 10, "y": 64, "z": -20},
    "blockId": "minecraft:chest",
    "blockState": "minecraft:chest[facing=north,type=single,waterlogged=false]",
    "type": "minecraft:chest",
    "runtimeClass": "net.minecraft.world.level.block.entity.ChestBlockEntity",
    "snbt": "{...}"
  }
}
```

Use `blockId`, `blockState`, `type`, and `runtimeClass` for concise answers. Use `snbt` only when exact stored data is needed.

